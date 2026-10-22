<div align="center">

# Kilo Gateway for Java

面向 Kilo AI Gateway 的 Spring Boot Starter。

[![Build](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml/badge.svg)](https://github.com/BangShou1st/kilo-gateway/actions/workflows/test.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3.3.2](https://img.shields.io/badge/Spring_Boot-3.3.2-6DB33F?logo=springboot&logoColor=white)
[![JitPack](https://jitpack.io/v/BangShou1st/kilo-gateway.svg)](https://jitpack.io/#BangShou1st/kilo-gateway)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](README.md) · **简体中文**

[快速开始](#快速开始) · [使用](#使用) · [配置](#配置) · [开发](#开发)

</div>

通过 Java 调用 Kilo AI Gateway 的兼容 OpenAI 接口。Starter 自动装配客户端，提供对话、流式响应和模型查询；底层使用 JDK HTTP Client 与 Jackson，无需安装 Kilo Code 或 OpenCode。

## 功能

- **Spring Boot 集成** — 注入 `KiloGatewayClient` 即可使用，也可声明自己的 Bean 覆盖默认实现。
- **完整对话请求** — 保留历史消息、工具定义和响应元数据，支持文本便捷调用与完整 JSON 调用。
- **SSE 流式响应** — 逐片回调 JSON，检查完成标记，识别中断、错误帧与无效 JSON。
- **免费模型策略** — 默认校验免费模型 ID，并提供模型目录与免费条目查询。
- **顺序回退** — 可配置上游 HTTP 500/502/503/504 时的备用模型；401/403/429 不自动重试或切换模型。
- **显式网络配置** — 支持连接与请求超时、HTTP 代理，默认使用 HTTPS。

工具执行、凭据存储和业务流程由应用负责。

## 快速开始

### 环境要求

- Java 21 或更高版本。
- Spring Boot 依赖基于 **3.3.2** 构建；其他版本需在应用中验证。
- 默认通过 **JitPack** 分发，本文使用版本 **`v0.2.3`**，包括前缀 `v`。

### 安装

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

公开 JitPack 产物无需下载凭据。如果项目在 `settings.gradle.kts` 集中管理仓库，请将 JitPack 仓库配置放在那里。

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

### 发起对话

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

## 使用

### 客户端方法

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

### 认证、网络与错误

- **认证：** API Key 为空时不发送 `Authorization`，配置后每个网关请求都会携带它。使用非免费模型需同时设置 `free-only: false` 和 API Key。
- **回退：** 仅 HTTP 500、502、503、504 会尝试下一个配置模型。认证失败、限流和传输错误直接抛出；没有备用模型时只尝试一次。
- **流式完成：** 必须收到 `data: [DONE]`。提前 EOF 会抛出状态为 `STATUS_INCOMPLETE_STREAM` 的 `KiloGatewayException`；错误帧、无效 JSON 同样使流失败。部分输出不会切换模型后重放。
- **传输：** 要求 HTTPS，允许 loopback 地址使用 HTTP；不跟随重定向。
- **错误：** `KiloGatewayException` 提供状态码与上游返回的提供商名称。纯工具调用响应可能让 `ask()` 合法返回空字符串，需用 `chat()` 查看完整结果。

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

## 项目结构

| 模块 | 用途 |
| --- | --- |
| [`kilo-gateway-spring-boot-starter`](kilo-gateway-spring-boot-starter) | Spring Boot 应用的依赖入口。 |
| [`kilo-gateway-spring-boot-autoconfigure`](kilo-gateway-spring-boot-autoconfigure) | HTTP 客户端、免费模型策略、SSE 解码、配置属性与自动装配。 |

## 开发

```bash
git clone https://github.com/BangShou1st/kilo-gateway.git
cd kilo-gateway
gradle --no-daemon clean build
```

需要 JDK 21 和 Gradle 8.9。Gradle 可能下载构建依赖；构建过程不会调用 Kilo AI Gateway。提交更改时，请同步维护中英文 README，并在 Pull Request 中说明行为变化与验证结果。

## 许可证

[MIT](LICENSE) © 2026 BangShou1st。
