package com.nododiiiii.ponderer.editor.capture.network;

import com.nododiiiii.ponderer.editor.capture.NbtPickState;
import com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtResponsePayload;
import static com.nododiiiii.ponderer.editor.capture.protocol.CaptureBlockEntityNbtResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class CaptureBlockEntityNbtResponseHandler {
    private CaptureBlockEntityNbtResponseHandler() {}

    public static void handle(CaptureBlockEntityNbtResponsePayload payload) {
        NbtPickState.handleServerBlockEntityCapture(payload.pos(), payload.nbt());
    }
}
