# Kilo Gateway

轻量的 Kilo AI Gateway Spring Boot Starter。基于 JDK HTTP Client 和 Jackson，提供兼容 OpenAI 格式的对话、SSE 流式响应与模型查询。

[![Build](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml/badge.svg)](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml)
[![JitPack](https://jitpack.io/v/BangShou1st/kilo-gateway.svg)](https://jitpack.io/#BangShou1st/kilo-gateway)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](#环境要求)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](README.md) · 简体中文

- 在 Spring 服务中直接注入 `KiloGatewayClient`。
- 使用完整 JSON 请求与响应，保留工具调用和元数据。
- 默认限制免费模型 ID，可配置上游服务错误时的顺序回退。
- 检测 SSE 中断，避免把部分输出当作完整响应。

## 环境要求

Java 21、Spring Boot 3.3.2（本 Starter 构建时使用的版本）。无需安装 Kilo Code 或 OpenCode。

## 安装

### JitPack

在 Gradle Kotlin 构建中加入仓库与依赖：

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.BangShou1st.kilo-gateway:kilo-gateway-spring-boot-starter:v0.2.3")
}
```

公开 JitPack 产物无需下载凭据。如果项目在 `settings.gradle.kts` 集中管理仓库，请将 JitPack 仓库配置放在那里。

<details>
<summary>其他渠道：GitHub Packages</summary>

使用坐标 `com.sharedlibs:kilo-gateway-spring-boot-starter:0.2.3`，并配置仓库：

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/BangShou1st/kilo-gateway")
    credentials {
        username = providers.gradleProperty("kiloRepoUser").orNull
        password = providers.gradleProperty("kiloRepoToken").orNull
    }
}
```

将 `kiloRepoUser` 和 `kiloRepoToken` 放在用户级 `~/.gradle/gradle.properties` 中。Token 需要 `read:packages` 和对应包的访问权限，不要将凭据提交到仓库。

</details>

## 快速开始

Starter 自动注册客户端，在业务服务中注入即可：

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

通过 `application.yml` 覆盖模型等配置：

```yaml
kilo:
  gateway:
    model: poolside/laguna-s-2.1:free
    free-only: true
```

这是库内的默认模型 ID；实际可用性与价格由上游决定。通过 `models()` 或 `freeModels()` 查询目录。免费 ID 匹配属于本地策略，不是计费承诺。

## 客户端 API

| 方法 | 返回内容 |
| --- | --- |
| `ask(String prompt)` | 首个候选回答的文本；无文本时返回空字符串 |
| `chat(ObjectNode request)` | 完整 JSON 响应，包含 `tool_calls` |
| `streamChat(ObjectNode request, Consumer<JsonNode> onChunk)` | 同步消费 SSE，每个 JSON 分片触发一次回调 |
| `models()` | 原始模型目录 JSON |
| `freeModels()` | ID 符合免费模型规则的目录条目 |

使用 Jackson 构建请求，然后选择普通或流式调用：

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

ObjectNode request = new ObjectMapper().createObjectNode();
request.putArray("messages").addObject()
        .put("role", "user").put("content", "解释一下虚拟线程。");

JsonNode response = client.chat(request);
client.streamChat(request, chunk -> {
    String text = chunk.path("choices").path(0).path("delta").path("content").asText("");
    System.out.print(text);
});
```

以上两个调用会分别发送请求。历史消息、工具定义等字段直接放入请求；客户端先复制请求，再设置模型和流式标记。工具执行由调用方负责。

## 配置

以下属性均以 `kilo.gateway` 为前缀。

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 启用客户端自动注册 |
| `base-url` | `https://ai-gateway.kilo.ai/api/v1` | 网关 API 基础地址 |
| `model` | `poolside/laguna-s-2.1:free` | 请求未指定模型时使用 |
| `free-only` | `true` | 仅接受 `*:free` 或 `kilo-auto/free` |
| `api-key` | 空 | 可选 Bearer Token；非免费 ID 必填 |
| `fallback-models` | `[]` | HTTP 500/502/503/504 时依次尝试的模型 |
| `connect-timeout` | `10s` | HTTP 连接超时 |
| `request-timeout` | `90s` | JDK HTTP 请求超时，不是 SSE 读取空闲超时 |
| `proxy-host` | 空 | 可选 HTTP 代理地址 |
| `proxy-port` | `0` | 设置代理地址时需提供有效端口 |

自行声明 `KiloGatewayClient` Bean 可覆盖默认客户端。设置 `enabled: false` 可关闭此自动配置。

## 请求行为

- **认证：** API Key 为空时不发送 `Authorization`，配置后每个网关请求都会携带它。使用非免费模型需同时设置 `free-only: false` 和 API Key。
- **回退：** 仅 HTTP 500、502、503、504 会尝试下一个配置模型。认证失败、限流和传输错误直接抛出；没有备用模型时只尝试一次。
- **流式完成：** 必须收到 `data: [DONE]`。提前 EOF 会抛出状态为 `STATUS_INCOMPLETE_STREAM` 的 `KiloGatewayException`；错误帧、无效 JSON 同样使流失败。部分输出不会切换模型后重放。
- **传输：** 要求 HTTPS，允许 loopback 地址使用 HTTP；不跟随重定向。
- **错误：** `KiloGatewayException` 提供状态码与上游返回的提供商名称。纯工具调用响应可能让 `ask()` 合法返回空字符串，需用 `chat()` 查看完整结果。

## 源码构建

安装 JDK 21 和 Gradle 8.9 后运行：

```sh
gradle --no-daemon clean build
```

项目包含两个模块：`kilo-gateway-spring-boot-autoconfigure` 提供客户端与自动配置；`kilo-gateway-spring-boot-starter` 为依赖入口。

## 许可证

[MIT](LICENSE) © 2026 BangShou1st。
