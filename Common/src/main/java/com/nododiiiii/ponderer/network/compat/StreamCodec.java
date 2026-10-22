package com.nododiiiii.ponderer.network.compat;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** FriendlyByteBuf adapter for the shared 1.21 payload codecs. */
public record StreamCodec<B, T>(BiConsumer<B, T> encoder, Function<B, T> decoder) {
    public static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>(encoder, decoder);
    }
    public void encode(B buffer, T value) { encoder.accept(buffer, value); }
    public T decode(B buffer) { return decoder.apply(buffer); }
}
