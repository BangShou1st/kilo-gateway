<div align="center">

# Kilo Gateway for Java

A Spring Boot starter for the Kilo AI Gateway.

[![Build](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml/badge.svg)](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3.3.2](https://img.shields.io/badge/Spring_Boot-3.3.2-6DB33F?logo=springboot&logoColor=white)
[![JitPack](https://jitpack.io/v/BangShou1st/kilo-gateway.svg)](https://jitpack.io/#BangShou1st/kilo-gateway)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**English** · [简体中文](README.zh-CN.md)

[Quick start](#quick-start) · [Usage](#usage) · [Configuration](#configuration) · [Development](#development)

</div>

Call the Kilo AI Gateway from Java through its OpenAI-compatible API. The starter provides an auto-configured client for chat, streaming, and model discovery, built on the JDK HTTP client and Jackson.

## Features

- **Spring Boot integration** — inject `KiloGatewayClient`, or provide your own bean to override the default.
- **Full chat requests** — preserve message history, tool definitions, and response metadata, with both text and JSON APIs.
- **SSE streaming** — receive JSON chunks with completion checks and detection of truncated streams, error frames, and malformed JSON.
- **Free-model policies** — free model ID validation by default, plus catalog and free-entry discovery.
- **Ordered fallback** — optional alternatives on upstream HTTP 500/502/503/504. No automatic retries or model switching on HTTP 401/403/429.
- **Explicit networking** — configurable connection and request timeouts, optional HTTP proxy, and HTTPS by default.

Tool execution, credential storage, and application workflows belong to the consuming application.

## Quick start

### Requirements

- Java 21 or later.
- Spring Boot dependencies are built against **3.3.2**; other versions should be verified in your application.
- Artifacts are distributed through **JitPack**. The documented version is **`v0.2.3`**, including the leading `v`.

### Install

**Gradle Kotlin DSL**

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

**Maven**

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.BangShou1st.kilo-gateway</groupId>
        <artifactId>kilo-gateway-spring-boot-starter</artifactId>
        <version>v0.2.3</version>
    </dependency>
</dependencies>
```

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

### Send a chat request

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

## Usage

### Client methods

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

### Credentials, networking, and errors

- **Authentication:** an empty API key sends no `Authorization` header. A configured key is sent with every gateway request. Non-free models require both `free-only: false` and an API key.
- **Fallback:** only HTTP 500, 502, 503, and 504 advance to the next configured model. Authentication errors, rate limits, and transport failures propagate immediately. Without fallback models there is one attempt.
- **Streaming:** success requires `data: [DONE]`. EOF without it raises `KiloGatewayException` with `STATUS_INCOMPLETE_STREAM`. Error frames and malformed JSON also fail the stream. Partial output is never replayed against another model.
- **Transport:** HTTPS is required, with HTTP permitted for loopback addresses. Redirects are disabled.
- **Errors:** `KiloGatewayException` exposes the status code and provider name when supplied by the gateway. A tool-only response can legitimately produce an empty string from `ask()`; use `chat()` to inspect the full response.

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

## Project structure

| Module | Purpose |
| --- | --- |
| [`kilo-gateway-spring-boot-starter`](kilo-gateway-spring-boot-starter) | Dependency entry point for Spring Boot applications. |
| [`kilo-gateway-spring-boot-autoconfigure`](kilo-gateway-spring-boot-autoconfigure) | HTTP client, free-model policy, SSE decoding, properties, and auto-configuration. |

## Development

```bash
git clone https://github.com/BangShou1st/kilo-gateway.git
cd kilo-gateway
gradle --no-daemon clean build
```

Requires JDK 21 and Gradle 8.9. Gradle may download build dependencies; the build makes no Kilo AI Gateway API calls. For changes, keep both README files aligned and include behavior changes and verification performed in your pull request.

## License

[MIT](LICENSE) © 2026 BangShou1st.
