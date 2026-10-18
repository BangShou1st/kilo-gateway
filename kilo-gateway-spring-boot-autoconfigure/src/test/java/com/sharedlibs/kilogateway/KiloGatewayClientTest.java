package com.sharedlibs.kilogateway;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlibs.kilogateway.autoconfigure.KiloGatewayProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KiloGatewayClientTest {
    private HttpServer server;
    private KiloGatewayProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        props = new KiloGatewayProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void anonymousFreeChatHasNoBearerHeader() {
        server.createContext("/chat/completions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("poolside/laguna-s-2.1:free");
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");
        });
        server.start();
        assertThat(new KiloGatewayClient(props).ask("Reply OK")).isEqualTo("OK");
    }

    @Test void fallsBackAfterProvider502() {
        props.setFallbackModels(List.of("cohere/north-mini-code:free"));
        server.createContext("/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.incrementAndGet();
            if (body.contains("poolside/laguna-s-2.1:free")) {
                respond(exchange, 502, "{\"error\":{\"message\":\"Provider returned error\",\"metadata\":{\"provider_name\":\"Nvidia\"}}}");
            } else {
                respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"fallback\"}}]}");
            }
        });
        server.start();
        assertThat(new KiloGatewayClient(props).ask("test")).isEqualTo("fallback");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void doesNotBypassRateLimit429() {
        props.setFallbackModels(List.of("cohere/north-mini-code:free"));
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().close();
            respond(exchange, 429, "{\"error\":{\"message\":\"Rate limit\"}}");
        });
        server.start();
        assertThatThrownBy(() -> new KiloGatewayClient(props).ask("test"))
                .isInstanceOf(KiloGatewayException.class).extracting("statusCode").isEqualTo(429);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void forbidsPaidModelsBeforeNetwork() {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "openai/gpt-5.4");
        body.putArray("messages").addObject().put("role", "user").put("content", "hello");
        assertThatThrownBy(() -> new KiloGatewayClient(props).chat(body))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Free-only");
    }

    @Test void streamsChunksAndDone() {
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            String events = "data: {\"choices\":[{\"delta\":{\"content\":\"O\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"K\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            respond(exchange, 200, events);
        });
        server.start();
        ObjectNode req = mapper.createObjectNode();
        req.putArray("messages").addObject().put("role", "user").put("content", "hi");
        List<String> chunks = new ArrayList<>();
        new KiloGatewayClient(props).streamChat(req, item ->
                chunks.add(item.path("choices").path(0).path("delta").path("content").asText()));
        assertThat(chunks).containsExactly("O", "K");
    }

    @Test void doneFlushedAtEofWithoutTrailingBlankLineStillCompletes() {
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            respond(exchange, 200,
                    "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n");
        });
        server.start();
        List<String> chunks = new ArrayList<>();
        new KiloGatewayClient(props).streamChat(streamRequest(), item ->
                chunks.add(item.path("choices").path(0).path("delta").path("content").asText()));
        assertThat(chunks).containsExactly("OK");
    }

    @Test void eofWithoutDoneIsAnIncompleteStreamFailure() {
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            respond(exchange, 200,
                    "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n");
        });
        server.start();
        List<JsonNode> chunks = new ArrayList<>();
        assertThatThrownBy(() -> new KiloGatewayClient(props).streamChat(streamRequest(), chunks::add))
                .isInstanceOfSatisfying(KiloGatewayException.class, failure -> {
                    assertThat(failure.getStatusCode())
                            .isEqualTo(KiloGatewayException.STATUS_INCOMPLETE_STREAM);
                    assertThat(failure.isIncompleteStream()).isTrue();
                    assertThat(failure.isTransientUpstreamError()).isFalse();
                });
        assertThat(chunks).hasSize(1);
    }

    @Test void incompleteStreamIsNeverFailedOverToAnotherModel() {
        props.setFallbackModels(List.of("cohere/north-mini-code:free"));
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            calls.incrementAndGet();
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n");
        });
        server.start();
        assertThatThrownBy(() -> new KiloGatewayClient(props).streamChat(streamRequest(), item -> { }))
                .isInstanceOf(KiloGatewayException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void midStreamErrorFrameFailsWithoutReplayingEarlierOutput() {
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            respond(exchange, 200,
                    "data: {\"choices\":[{\"delta\":{\"content\":\"O\"}}]}\n\n"
                    + "data: {\"error\":{\"message\":\"upstream exploded\",\"metadata\":{\"provider_name\":\"Nvidia\"}}}\n\n"
                    + "data: [DONE]\n\n");
        });
        server.start();
        List<JsonNode> chunks = new ArrayList<>();
        assertThatThrownBy(() -> new KiloGatewayClient(props).streamChat(streamRequest(), chunks::add))
                .isInstanceOfSatisfying(KiloGatewayException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(KiloGatewayException.STATUS_TRANSPORT);
                    assertThat(failure.getProvider()).isEqualTo("Nvidia");
                    assertThat(failure.isTransientUpstreamError()).isFalse();
                });
        assertThat(chunks).hasSize(1);
    }

    @Test void undecodableFrameFailsTheStream() {
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            respond(exchange, 200, "data: {not-json\n\n");
        });
        server.start();
        assertThatThrownBy(() -> new KiloGatewayClient(props).streamChat(streamRequest(), item -> { }))
                .isInstanceOfSatisfying(KiloGatewayException.class, failure ->
                        assertThat(failure.getStatusCode()).isEqualTo(KiloGatewayException.STATUS_TRANSPORT));
    }

    private ObjectNode streamRequest() {
        ObjectNode req = mapper.createObjectNode();
        req.putArray("messages").addObject().put("role", "user").put("content", "hi");
        return req;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
