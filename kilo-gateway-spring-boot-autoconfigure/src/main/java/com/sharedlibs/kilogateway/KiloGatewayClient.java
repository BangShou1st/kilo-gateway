package com.sharedlibs.kilogateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlibs.kilogateway.autoconfigure.KiloGatewayProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Lightweight OpenAI-compatible client. JSON is preserved to support tools, images, and metadata. */
public final class KiloGatewayClient {
    private final KiloGatewayProperties props;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI baseUri;

    public KiloGatewayClient(KiloGatewayProperties properties) {
        this(properties, new ObjectMapper(), buildHttpClient(properties));
    }

    /** Injection constructor for tests or callers who need a custom transport. */
    public KiloGatewayClient(KiloGatewayProperties properties, ObjectMapper mapper, HttpClient httpClient) {
        this.props = Objects.requireNonNull(properties, "properties");
        this.json = Objects.requireNonNull(mapper, "mapper");
        this.http = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUri = validateBaseUrl(properties.getBaseUrl());
    }

    private static HttpClient buildHttpClient(KiloGatewayProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(properties.getConnectTimeout()))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.getProxyHost() != null && !properties.getProxyHost().isBlank()) {
            if (properties.getProxyPort() < 1 || properties.getProxyPort() > 65535) {
                throw new IllegalArgumentException("kilo.gateway.proxy-port must be 1..65535");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    properties.getProxyHost(), properties.getProxyPort())));
        }
        return builder.build();
    }

    private static URI validateBaseUrl(String url) {
        URI uri = URI.create(Objects.requireNonNull(url).replaceAll("/+$", ""));
        String host = uri.getHost();
        boolean loopback = host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
        if (!uri.isAbsolute() || host == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme())
                     || ("http".equalsIgnoreCase(uri.getScheme()) && loopback))) {
            throw new IllegalArgumentException("Kilo gateway URL must be HTTPS (HTTP allowed only for loopback tests)");
        }
        return uri;
    }

    /** Retrieve the live model directory (authentication is not needed). */
    public JsonNode models() {
        HttpRequest request = requestBuilder("/models", "application/json")
                .GET().build();
        return decode(execute(request));
    }

    /** Free entries returned by the live catalog, preserving original metadata. */
    public List<JsonNode> freeModels() {
        List<JsonNode> result = new ArrayList<>();
        JsonNode data = models().path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (FreeModelPolicy.isFreeModelId(item.path("id").asText(""))) result.add(item);
            }
        }
        return List.copyOf(result);
    }

    /** Convenience call for one plain-text prompt. Use chat(ObjectNode) for tool calls/history. */
    public String ask(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        ObjectNode body = json.createObjectNode();
        var messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);
        JsonNode result = chat(body);
        JsonNode text = result.path("choices").path(0).path("message").path("content");
        return text.isTextual() ? text.asText() : ""; // tool calls may legitimately have no text
    }

    /**
     * Send a full OpenAI-compatible request, including tool definitions and previous tool results.
     * On upstream HTTP 500/502/503/504, optionally try configured fallback models in order.
     * HTTP 401/403/429 and network timeouts are never automatically retried.
     */
    public JsonNode chat(ObjectNode request) {
        ObjectNode body = prepare(request, false);
        KiloGatewayException last = null;
        for (String model : candidateModels(body.path("model").asText())) {
            checkModel(model);
            body.put("model", model);
            try {
                return decode(execute(requestBuilder("/chat/completions", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(encode(body), StandardCharsets.UTF_8))
                        .build()));
            } catch (KiloGatewayException error) {
                if (!error.isTransientUpstreamError()) throw error;
                last = error;
            }
        }
        throw Objects.requireNonNull(last);
    }

    /**
     * Stream JSON SSE chunks. Does not replay partial output after the first chunk.
     *
     * <p>A stream is only complete when the upstream sends the SSE completion sentinel
     * ({@code data: [DONE]}). Reading EOF without that sentinel — or a mid-stream error frame, or an
     * undecodable frame — fails with {@link KiloGatewayException}: a truncated stream is never
     * reported as success. Completion failures use
     * {@link KiloGatewayException#STATUS_INCOMPLETE_STREAM} and are never transient, so they cannot
     * be retried or failed over after partial output has already been delivered.</p>
     */
    public void streamChat(ObjectNode request, Consumer<JsonNode> onChunk) {
        Objects.requireNonNull(onChunk, "onChunk");
        ObjectNode body = prepare(request, true);
        KiloGatewayException last = null;
        for (String model : candidateModels(body.path("model").asText())) {
            checkModel(model);
            body.put("model", model);
            HttpRequest httpRequest = requestBuilder("/chat/completions", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    KiloGatewayException error = errorFor(response.statusCode(), errorBody);
                    if (!error.isTransientUpstreamError()) throw error;
                    last = error;
                    continue;
                }
                if (!consume(input, onChunk)) {
                    throw new KiloGatewayException(KiloGatewayException.STATUS_INCOMPLETE_STREAM, "",
                            "Kilo SSE stream ended without the [DONE] completion signal");
                }
                return;
            } catch (IOException error) {
                throw new KiloGatewayException(KiloGatewayException.STATUS_TRANSPORT, "",
                        "Streaming I/O failure", error);
            }
        }
        throw Objects.requireNonNull(last);
    }

    /**
     * Reads SSE frames until the completion sentinel.
     *
     * @return {@code true} when {@code [DONE]} was observed (including a final frame flushed at EOF
     *         without a trailing blank line); {@code false} when the stream ended without it.
     */
    private boolean consume(InputStream input, Consumer<JsonNode> onChunk) throws IOException {
        SseFrameDecoder decoder = new SseFrameDecoder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var frame = decoder.accept(line);
                if (frame.isPresent() && emit(frame.get(), onChunk)) return true;
            }
            var tail = decoder.finish();
            return tail.isPresent() && emit(tail.get(), onChunk);
        }
    }

    private boolean emit(String frame, Consumer<JsonNode> onChunk) {
        if ("[DONE]".equals(frame.trim())) return true;
        if (frame.isBlank()) return false;
        JsonNode chunk = decode(frame);
        JsonNode error = chunk.path("error");
        if (!error.isMissingNode() && !error.isNull()) throw errorFrame(error, frame);
        onChunk.accept(chunk);
        return false;
    }

    /** Mid-stream provider error frame: never transient, so partial output is not replayed. */
    private KiloGatewayException errorFrame(JsonNode error, String rawFrame) {
        String provider = error.path("metadata").path("provider_name").asText("");
        String message = error.path("message").asText("");
        if (message.isEmpty()) message = rawFrame;
        if (message.length() > 400) message = message.substring(0, 400);
        return new KiloGatewayException(KiloGatewayException.STATUS_TRANSPORT, provider,
                "Kilo SSE error frame" + (provider.isEmpty() ? "" : " (" + provider + ")") + ": " + message);
    }

    private ObjectNode prepare(ObjectNode request, boolean stream) {
        Objects.requireNonNull(request, "request");
        ObjectNode copy = request.deepCopy();
        if (!copy.has("messages") || !copy.path("messages").isArray()) {
            throw new IllegalArgumentException("messages must be a JSON array");
        }
        if (copy.path("model").asText("").isBlank()) copy.put("model", props.getModel());
        if (!stream && copy.path("stream").asBoolean(false)) {
            throw new IllegalArgumentException("Use streamChat() for stream=true requests");
        }
        copy.put("stream", stream);
        checkModel(copy.path("model").asText());
        return copy;
    }

    private Set<String> candidateModels(String primary) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(primary);
        for (String alternate : props.getFallbackModels()) {
            if (alternate != null && !alternate.isBlank()) candidates.add(alternate);
        }
        return candidates;
    }

    private void checkModel(String model) {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        if (props.isFreeOnly() && !FreeModelPolicy.isFreeModelId(model)) {
            throw new IllegalArgumentException("Free-only mode rejected non-free model: " + model);
        }
        if (!FreeModelPolicy.isFreeModelId(model) && (props.getApiKey() == null || props.getApiKey().isBlank())) {
            throw new IllegalArgumentException("Paid models require kilo.gateway.api-key");
        }
    }

    private HttpRequest.Builder requestBuilder(String suffix, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUri.toString() + suffix))
                .timeout(props.getRequestTimeout())
                .header("Accept", accept)
                .header("Content-Type", "application/json; charset=utf-8");
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + props.getApiKey());
        }
        return builder;
    }

    private String execute(HttpRequest request) {
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw errorFor(response.statusCode(), response.body());
        }
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(request, handler);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new KiloGatewayException(KiloGatewayException.STATUS_TRANSPORT, "",
                    "Kilo API request interrupted", error);
        } catch (IOException error) {
            throw new KiloGatewayException(KiloGatewayException.STATUS_TRANSPORT, "",
                    "Kilo API connection failed", error);
        }
    }

    private KiloGatewayException errorFor(int statusCode, String raw) {
        String provider = "";
        String message = raw == null ? "" : raw;
        try {
            JsonNode error = json.readTree(message).path("error");
            String parsed = error.path("message").asText("");
            if (!parsed.isEmpty()) message = parsed;
            provider = error.path("metadata").path("provider_name").asText("");
        } catch (Exception ignored) {
            // Some providers return an HTML page or plain text on 502.
        }
        if (message.length() > 400) message = message.substring(0, 400);
        return new KiloGatewayException(statusCode, provider,
                "Kilo HTTP " + statusCode + (provider.isEmpty() ? "" : " (" + provider + ")") + ": " + message);
    }

    private JsonNode decode(String raw) {
        try {
            return json.readTree(raw);
        } catch (JsonProcessingException error) {
            throw new KiloGatewayException(KiloGatewayException.STATUS_TRANSPORT, "",
                    "Invalid JSON returned by Kilo API", error);
        }
    }

    private String encode(ObjectNode body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize request", error);
        }
    }
}
