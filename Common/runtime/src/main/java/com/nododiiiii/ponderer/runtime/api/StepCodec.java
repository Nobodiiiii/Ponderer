package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.JsonObject;

/** Parameters belong to the content mod and remain JSON data when its code is unavailable. */
public interface StepCodec<P> {
    P decode(JsonObject parameters);
    JsonObject encode(P parameters);
}
