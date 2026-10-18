import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "kilo-gateway"
include("kilo-gateway-spring-boot-autoconfigure", "kilo-gateway-spring-boot-starter")

// JitPack builders can be throttled by Maven Central (HTTP 429) on shared IPs.
// Use Google's public Maven Central cache there; keep Maven Central as a fallback.
// Local and GitHub Actions builds keep the repositories declared by subprojects.
if (System.getenv("JITPACK")?.equals("true", ignoreCase = true) == true) {
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
        repositories {
            maven {
                name = "GoogleMavenCentralMirror"
                url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            }
            mavenCentral()
        }
    }
}
