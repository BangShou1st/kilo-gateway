package com.sharedlibs.kilogateway;

import java.util.Optional;

/** Accumulates SSE data lines until a blank line terminates an event. */
public final class SseFrameDecoder {
    private final StringBuilder data = new StringBuilder();
    public Optional<String> accept(String line) {
        if (line == null) throw new IllegalArgumentException("line must not be null");
        if (line.isEmpty()) return finish();
        if (line.startsWith("data:")) {
            String fragment = line.substring(5);
            if (fragment.startsWith(" ")) fragment = fragment.substring(1);
            if (!data.isEmpty()) data.append('\n');
            data.append(fragment);
        }
        return Optional.empty();
    }
    public Optional<String> finish() {
        if (data.isEmpty()) return Optional.empty();
        String completed = data.toString();
        data.setLength(0);
        return Optional.of(completed);
    }
}
