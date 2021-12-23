
public enum RespDataType {
    SIMPLE_STRING('+'),
    ERROR('-'),
    INTEGER(':'),
    BULK_STRING('$'),
    ARRAY('*'),
    ;

    private byte sigil;

    RespDataType(sigil) {
        this.sigil = sigil;
    }

    public byte sigil() {
        return sigil;
    }
}
