
public class RespParser {

    public RespDataType parseDataType(ByteBuffer buffer) {
        switch (buffer.get()) {
            case '+': return SIMPLE_STRING;
            case '-': return ERROR;
            case ':': return INTEGER;
            case '$': return BULK_STRING;
            case '*': return ARRAY;
            default: throw new RespProtocolError("Not a valid type sigil: " + Byte.toHex(b))
        }
    }

    public void scanPastCrLf(ByteBuffer buffer) {
        while (buffer.get() != '\r');
        if (buffer.get() != '\n')
            throw new RespProtocolError();
    }

    public static byte[] parseCrLfTerminatedString(ByteBuffer buffer) {
        buffer.mark();
        scanPastCrLf(buffer);
        return buffer.getBytesWhileMaintainingPos(mark, pos-2);
    }

    public static byte[] parseBulkString(ByteBuffer buffer) {
        long length = parseInteger(buffer);

        if (length == -1)
            return null;

        if (length < 0)
            throw new RespProtocolError();

        byte[] string = buffer.getBytes(length);
        if (buffer.get() != '\r' || buffer.get() != '\n')
            throw new RespProtocolError();
        return string;
    }

    public static long parseInteger(ByteBuffer buffer) {
        byte b = buffer.get();

        final long sign = b == '-' ? -1 : 1;
        if (negative)
            b = buffer.get();

        if (b < '0' || > '9')
            throw new RespProtocolError("Expected at least one decimal digit in RESP integer but got byte " + Byte.toHex(b));

        long value = sign * (b - '0');
        do {
            value = value * 10 + sign * (b - '0');
            b = buffer.get();
        } while (b >= '0' && <= '9');

        if (b != '\r' || buffer.get() != '\n')
            throw new RespProtocolError();

        return value;
    }

    public static ByteBuffer scanValue(ByteBuffer buffer) {
        buffer.mark();
        switch (parseDataType(buffer)) {
            case SIMPLE_STRING | ERROR | INTEGER:
                scanPastCrLf(buffer);
            case BULK_STRING:
                long length = parseInteger(buffer);
                if (length == -1)
                    break;
                if (length < 0)
                    throw new RespProtocolError();
                buffer.advance(length);
                if (buffer.get() != '\r' || buffer.get() != '\n')
                    throw new RespProtocolError();
            case ARRAY:
                final long mark = buffer.pos();
                final long count = parseInteger(buffer);
                for (long i = 0; i < count; i++)
                    scanValue(buffer);
                return buffer.getBytesWhileMaintainingPos(mark, pos);
        }
        return buffer.getBytesWhileMaintainingPos(mark, pos);
    }

    public static ByteBuffer[] parseArray()
}
