# Kilo Gateway Spring Boot Starter (v0.2.3)

供多个 Spring Boot 项目依赖的 Kilo Gateway 客户端，直接访问 `https://ai-gateway.kilo.ai/api/v1`，**不依赖 Kilo Code、OpenCode 或其他本地 Maven 仓库**。

- Java 21 / Spring Boot 3.3.2 / Gradle Kotlin DSL / 双模块 Starter
- 默认模型：`poolside/laguna-s-2.1:free`（用户在 Windows PowerShell 中实测 HTTP 200 + `OK`；不代表长期稳定）
- `kilo.gateway.free-only=true` 默认拒绝非 `:free` 模型；免费模型匿名调用不发送 Authorization
- 提供 `KiloGatewayClient.ask`、`chat`（保留完整 JSON/工具调用）、`streamChat`（SSE）、`models`、`freeModels`
- 只有配置了备用模型，且主模型返回 500/502/503/504 时才切换；401/403/429 不回退
- 默认仅 HTTPS，loopback HTTP 只用于本地测试；支持可选代理

## 请选择一种远程依赖路径（消费项目不需要 `mavenLocal()`）

### A. GitHub Packages —— 私有仓库首选，纯 GitHub 托管

1. 在 GitHub 新建仓库 `BangShou1st/kilo-gateway`（私有或公开），推送本项目源码。
2. 修改根 `build.gradle.kts` 的 `version` 为目标版本，然后提交代码，打对应标签：

   ```powershell
   git add .
   git commit -m "Release 0.2.3"
   git tag v0.2.3
   git push origin main --tags
   ```

3. GitHub Actions 的 `.github/workflows/publish.yml` 自动安装 JDK 21 + Gradle 8.9，执行测试和 `publish`，使用自动生成的 `GITHUB_TOKEN` 上传两份 artifact 到 **GitHub Packages**。你不需要本地 Gradle、`.ps1`、PAT 上传权限或 `mavenLocal()`。
4. 首次发布成功后，在仓库右侧 **Packages** 查看已发布版本。仓库所有者或路径改变时，调整构建文件里默认 `BangShou1st/kilo-gateway` 地址。
5. GitHub Packages 的 Gradle/Maven registry 即使对公开仓库也要求**下载认证**。其他项目（消费方）需准备带 `read:packages` 的 GitHub PAT **classic**（只放到个人 `~/.gradle/gradle.properties`，不要提交）。

Windows 本机凭据（`$HOME\.gradle\gradle.properties`）：

```properties
kiloRepoUser=BangShou1st
kiloRepoToken=ghp_YOUR_READ_PACKAGES_PAT
```

消费项目 `build.gradle.kts`：

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "KiloGitHubPackages"
        url = uri("https://maven.pkg.github.com/BangShou1st/kilo-gateway")
        credentials {
            username = providers.gradleProperty("kiloRepoUser").orNull
                ?: System.getenv("GITHUB_ACTOR").orEmpty()
            password = providers.gradleProperty("kiloRepoToken").orNull
                ?: System.getenv("GITHUB_TOKEN").orEmpty()
        }
    }
}
dependencies {
    implementation("com.sharedlibs:kilo-gateway-spring-boot-starter:0.2.3")
}
```

> 如果使用了 `dependencyResolutionManagement` 且 `repositoriesMode` 禁止项目仓库，请把这个 Maven 仓库配置移动到 **settings.gradle.kts** 的 `dependencyResolutionManagement.repositories`，不能两个地方随意混用。
>
> GitHub Packages 的 Maven registry 基于仓库权限；消费者的 Token 账号必须有读取对应仓库/Package 的权限；私有项目 CI 则配置相应 Secret 或授权 Actions 访问。

#### 可选：本机手动向 GitHub Packages 发布（不运行 .ps1）

首先安装 Gradle 8.9+ 和 JDK 21；设置临时环境变量后执行：

```powershell
$env:KILO_GITHUB_USER = "BangShou1st"
$env:KILO_GITHUB_TOKEN = "<PAT classic with write:packages>"
gradle --no-daemon test publish
Remove-Item Env:KILO_GITHUB_TOKEN
```

**不建议在命令历史中键入真实 Token**；优先使用 GitHub Actions 自动发布。

### B. 公开 GitHub 仓库 + JitPack —— 最省事、消费者免 Token

1. 将 **新的** `kilo-gateway` 仓库设为 Public（不要把包含内部协议、密钥或其他敏感源码的 `opencode-zen` 公开）。
2. 推送源码并创建 `v0.2.3` tag，访问 https://jitpack.io/#BangShou1st/kilo-gateway ，Look Up 版本并查看首次构建结果。`jitpack.yml` 负责在 **JitPack 服务器上** 使用 JDK 21 + Gradle 8.9；其中的 `publishToMavenLocal` 是 JitPack 构建服务内部的要求，**不是你本机或任何消费者使用 `mavenLocal()`**。
3. JitPack build 成功后，消费项目不需要 GitHub PAT，只需仓库和依赖：

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.BangShou1st.kilo-gateway:kilo-gateway-spring-boot-starter:v0.2.3")
}
```

> 注意：GitHub Packages 和 JitPack 的 group 坐标不同。此项目根脚本在 JitPack 环境下按其 `GROUP` / `ARTIFACT` / `VERSION` 环境变量设置 group 与 version，GitHub Packages 仍用 `com.sharedlibs:...:0.2.3`。JitPack 成功出包之前，上述依赖坐标仅是**拟使用的坐标**，尚未可下载。

### JitPack 构建出现 Maven Central HTTP 429？

JitPack 的共享构建 IP 访问 `https://repo.maven.apache.org/maven2` 时可能遇到限流。本版本在 `JITPACK=true` 时由 `settings.gradle.kts` 优先使用 Google Cloud 的 Maven Central 镜像，其余环境的仓库配置不变。

注意：镜像只降低 Maven Central 限流造成的失败概率，**不保证 JitPack 一定能构建成功**；请在 JitPack 页面查看本版本构建日志。启动时出现的 `git dubious ownership /build` 提示不是当前 HTTP 429 失败的直接原因。

## Spring Boot 项目中的使用

在业务项目 `application.yml`：

```yaml
kilo:
  gateway:
    model: poolside/laguna-s-2.1:free
    free-only: true
    # 如果验证过其他免费模型才推荐启用：
    # fallback-models:
    #   - cohere/north-mini-code:free
    # 可选的 Windows 本地 HTTP 代理：
    # proxy-host: 127.0.0.1
    # proxy-port: 7897
```

Java：

```java
import com.sharedlibs.kilogateway.KiloGatewayClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final KiloGatewayClient kilo;
    public AiService(KiloGatewayClient kilo) { this.kilo = kilo; }
    public String ask(String question) { return kilo.ask(question); }
}
```

使用完整的 `chat(ObjectNode request)` 响应来处理 `tool_calls`，不要把 `ask()` 的空字符串直接视为上游失败（有些模型没有文本回复）。SSE 使用 `streamChat(ObjectNode request, Consumer<JsonNode> onChunk)`。可通过 `models()` / `freeModels()` 查询网关当前列表。

## Windows PowerShell 未签名脚本错误

旧版 `publish-local.ps1` 报 `PSSecurityException`，因为它是未签名的 PowerShell 脚本，并不是 Kilo API 问题。本版**已删除该脚本**，直接交给 GitHub Actions 发布，无需改 Windows 执行策略。若仍需检查旧版脚本，先人工审查脚本内容；不建议为了这个项目全局降低执行策略。

## 构建验证记录

- 构建/发布配置与 Java 源码静态检查，可通过 CI/JitPack 执行 `test` 和远程发布。
- v0.2.0 曾在 GitHub Actions 完成测试并成功发布 GitHub Packages，但 JitPack 首次构建因 Maven Central HTTP 429 失败。
- v0.2.1 镜像策略通过 CI 后仍需在 JitPack 实际构建验证；本地环境无法直接下载外部构建依赖。
- v0.2.2 补上流式完成校验：`streamChat()` 只有观察到上游终止哨兵 `data: [DONE]` 才算成功；
  读取 EOF 而无该哨兵、中途出现错误帧、或帧无法解析，都抛 `KiloGatewayException`
  （完成缺失为 `KiloGatewayException.STATUS_INCOMPLETE_STREAM`，且 `isTransientUpstreamError()` 为 false）。
  消费方不应在未完成流上当作成功；库不会在已输出分片后重放或切换模型。
- v0.2.2 的 JitPack 构建未出包：构建服务器在 `jdk: openjdk21` 阶段下载 JDK 失败
  （`Download has failed, aborting!` → `JAVA_HOME is set to an invalid directory`），属 JitPack 侧
  基础设施抖动而非源码问题；JitPack 会缓存失败结果，请求同一版本不会重建。
  同源码在 v0.2.3 重新出包（`jitpack.yml` 未改动）。v0.2.2 保留为一次失败的标签，未被重写。
- 旧版 Java 核心代码已做过桩依赖编译检查；新版主要修改了构建/交付渠道，没有替换 API 客户端实现。

参考：GitHub Packages Gradle registry: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry ；JitPack 多模块: https://docs.jitpack.io/building/ 。
