# Kilo Gateway

A lightweight Spring Boot starter for the Kilo AI Gateway. OpenAI-compatible chat, SSE streaming, and model discovery using the JDK HTTP client and Jackson.

[![Build](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml/badge.svg)](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml)
[![JitPack](https://jitpack.io/v/BangShou1st/kilo-gateway.svg)](https://jitpack.io/#BangShou1st/kilo-gateway)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](#requirements)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

English · [简体中文](README.zh-CN.md)

- Inject a ready-to-use `KiloGatewayClient` into your Spring services.
- Preserve full JSON requests and responses, including tool calls and metadata.
- Restrict requests to free model IDs by default, with optional ordered fallback on upstream server errors.
- Detect truncated SSE streams instead of treating partial output as a complete response.

## Requirements

Java 21 and Spring Boot 3.3.2 (the version used to build this starter). No Kilo Code or OpenCode installation is required.

## Installation

### JitPack

Add the repository and starter to your Gradle Kotlin build:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.BangShou1st.kilo-gateway:kilo-gateway-spring-boot-starter:v0.2.3")
}
```

Public JitPack artifacts require no download credentials. If your build manages repositories in `settings.gradle.kts`, add the JitPack repository there instead.

<details>
<summary>Alternative: GitHub Packages</summary>

Use `com.sharedlibs:kilo-gateway-spring-boot-starter:0.2.3` with this repository:

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/BangShou1st/kilo-gateway")
    credentials {
        username = providers.gradleProperty("kiloRepoUser").orNull
        password = providers.gradleProperty("kiloRepoToken").orNull
    }
}
```

Store `kiloRepoUser` and `kiloRepoToken` in your user-level `~/.gradle/gradle.properties`. The token needs `read:packages` and access to the package. Keep credentials out of source control.

</details>

## Quick start

The starter registers a client automatically. Inject it into a service:

```java
import com.sharedlibs.kilogateway.KiloGatewayClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final KiloGatewayClient client;

    public AiService(KiloGatewayClient client) {
        this.client = client;
    }

    public String ask(String question) {
        return client.ask(question);
    }
}
```

Override the model or other settings in `application.yml`:

```yaml
kilo:
  gateway:
    model: poolside/laguna-s-2.1:free
    free-only: true
```

This is the library's default model ID; availability and pricing are controlled by the upstream service. Use `models()` or `freeModels()` to inspect its catalog. Free-ID matching is a local policy, not a billing guarantee.

## Client API

| Method | Result |
| --- | --- |
| `ask(String prompt)` | Text from the first response choice; empty when no text is present |
| `chat(ObjectNode request)` | Full JSON response, including `tool_calls` |
| `streamChat(ObjectNode request, Consumer<JsonNode> onChunk)` | Synchronous SSE delivery, one JSON chunk per callback |
| `models()` | Raw model catalog JSON |
| `freeModels()` | Catalog entries whose IDs match the free-model policy |

Build a request with Jackson, then choose either chat method:

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

ObjectNode request = new ObjectMapper().createObjectNode();
request.putArray("messages").addObject()
        .put("role", "user").put("content", "Explain virtual threads.");

JsonNode response = client.chat(request);
client.streamChat(request, chunk -> {
    String text = chunk.path("choices").path(0).path("delta").path("content").asText("");
    System.out.print(text);
});
```

The two calls above send separate requests. Add message history, tool definitions, or other supported fields directly to the request. The client copies it before setting its model and stream flag. Tool execution remains the caller's responsibility.

## Configuration

All properties use the `kilo.gateway` prefix.

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Enable automatic client registration |
| `base-url` | `https://ai-gateway.kilo.ai/api/v1` | Gateway API base URL |
| `model` | `poolside/laguna-s-2.1:free` | Model when a request omits one |
| `free-only` | `true` | Reject IDs other than `*:free` or `kilo-auto/free` |
| `api-key` | empty | Optional bearer token; required for non-free IDs |
| `fallback-models` | `[]` | Ordered alternatives for upstream HTTP 500/502/503/504 |
| `connect-timeout` | `10s` | HTTP connection timeout |
| `request-timeout` | `90s` | JDK HTTP request timeout; not an SSE read-idle timeout |
| `proxy-host` | empty | Optional HTTP proxy host |
| `proxy-port` | `0` | Required valid port when a proxy host is set |

Providing your own `KiloGatewayClient` bean replaces the default. Set `enabled: false` to disable this auto-configuration.

## Request behavior

- **Authentication:** an empty API key sends no `Authorization` header. A configured key is sent with every gateway request. Non-free models require both `free-only: false` and an API key.
- **Fallback:** only HTTP 500, 502, 503, and 504 advance to the next configured model. Authentication errors, rate limits, and transport failures propagate immediately. Without fallback models there is one attempt.
- **Streaming:** success requires `data: [DONE]`. EOF without it raises `KiloGatewayException` with `STATUS_INCOMPLETE_STREAM`. Error frames and malformed JSON also fail the stream. Partial output is never replayed against another model.
- **Transport:** HTTPS is required, with HTTP permitted for loopback addresses. Redirects are disabled.
- **Errors:** `KiloGatewayException` exposes the status code and provider name when supplied by the gateway. A tool-only response can legitimately produce an empty string from `ask()`; use `chat()` to inspect the full response.

## Build from source

With JDK 21 and Gradle 8.9 installed:

```sh
gradle --no-daemon clean build
```

The project has two modules: `kilo-gateway-spring-boot-autoconfigure` contains the client and configuration; `kilo-gateway-spring-boot-starter` is the dependency entry point.

## License

[MIT](LICENSE) © 2026 BangShou1st.
