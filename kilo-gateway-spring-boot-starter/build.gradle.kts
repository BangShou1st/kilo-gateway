plugins {
    `java-library`
    `maven-publish`
}
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}
repositories { mavenCentral() }
dependencies {
    api(project(":kilo-gateway-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter:3.3.2")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        // Only GitHub Packages is a publishing destination. Never use mavenLocal
        // to publish or consume this library in user projects.
        if (System.getenv("JITPACK") != "true") {
            maven {
                name = "GitHubPackages"
                val targetRepository = System.getenv("GITHUB_REPOSITORY")
                    ?.takeIf { it.isNotBlank() } ?: "BangShou1st/kilo-gateway"
                url = uri("https://maven.pkg.github.com/$targetRepository")
                credentials {
                    username = providers.gradleProperty("kiloPublishUser").orNull
                        ?: System.getenv("KILO_GITHUB_USER")
                        ?: System.getenv("GITHUB_ACTOR").orEmpty()
                    password = providers.gradleProperty("kiloPublishToken").orNull
                        ?: System.getenv("KILO_GITHUB_TOKEN")
                        ?: System.getenv("GITHUB_TOKEN").orEmpty()
                }
            }
        }
    }
}
