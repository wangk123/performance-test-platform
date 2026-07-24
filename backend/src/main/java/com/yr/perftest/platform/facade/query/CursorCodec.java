package com.yr.perftest.platform.facade.query;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorCodec {
    public String encode(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public String decode(String cursor) {
        return new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
    }
}
