
public interface RedisEngine {
    public execute(ByteBuffer buffer, Consumer<ByteBuffer> responseConsumer);
}

public interface RedisResponse<F<?>> {
    public <T> F<T> pure(T value);
    public <T> F<T> error(ByteBuffer error);
}

public interface RedisChannel<F<?>> {
    public F<Unit> execute(ByteBuffer buffer);
    public F<long> executeInteger(ByteBuffer buffer);
    public F<HeapByteBuffer> executeString(ByteBuffer buffer);
    public

    public send(ByteBuffer buffer): F<ByteBuffer>;
}

public class SampleRedisCommands<F<?>, S> {
    private static final COMMAND_SET = utf8("SET");

    private final RedisChannel<? super F> channel;

    public SampleRedisCommands(RedisChannel<? super F> channel) {
        this.channel = channel;
    }

    public F<S> GET(S key) {
        return channel.execute(command(COMMAND_SET, key, value));
    }

    public SampleRedisCommands<F<?>, S> MULTI() {
        return new MultiRedisCommands<>(channel);
    }

    public <T> T MULTI(Function<SampleRedisCommands<F<?>, S>, T> accumulate) {
        MultiRedisCommands<F<?>, S> multi = new MultiRedisCommands<>(channel);
        accumulate.apply(multi);
        multi
    }

    public F<Unit> SET(S key, S value) {
        return channel.execute(command(COMMAND_SET, key, value));
    }
}
