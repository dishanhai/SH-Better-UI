package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.function.Function;

public interface ConfigCodec<T> {
    String encode(T value);

    T decode(String value);

    static <T> ConfigCodec<T> of(Function<T, String> encode, Function<String, T> decode) {
        Objects.requireNonNull(encode, "encode");
        Objects.requireNonNull(decode, "decode");
        return new ConfigCodec<>() {
            @Override public String encode(T value) { return Objects.requireNonNull(encode.apply(value)); }
            @Override public T decode(String value) { return Objects.requireNonNull(decode.apply(value)); }
        };
    }

    static ConfigCodec<String> strings() { return of(Function.identity(), Function.identity()); }
    static ConfigCodec<Integer> integers() { return of(Object::toString, Integer::valueOf); }
    static ConfigCodec<Long> longs() { return of(Object::toString, Long::valueOf); }
    static ConfigCodec<Double> doubles() { return of(Object::toString, Double::valueOf); }
    static ConfigCodec<Boolean> booleans() {
        return of(Object::toString, value -> {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean");
            return Boolean.valueOf(value);
        });
    }
    static <E extends Enum<E>> ConfigCodec<E> enumeration(Class<E> type) {
        return of(Enum::name, value -> Enum.valueOf(type, value));
    }
}
