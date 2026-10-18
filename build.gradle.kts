plugins { base }

// GitHub Packages keeps the stable com.sharedlibs coordinates. JitPack uses its own
// group+tag coordinates so public builds can be downloaded anonymously.
val onJitPack = System.getenv("JITPACK")?.equals("true", ignoreCase = true) == true
group = if (onJitPack) {
    val owner = System.getenv("GROUP")?.takeIf { it.isNotBlank() } ?: "com.github.BangShou1st"
    val repo = System.getenv("ARTIFACT")?.takeIf { it.isNotBlank() } ?: "kilo-gateway"
    "$owner.$repo"
} else {
    "com.sharedlibs"
}
version = if (onJitPack) {
    System.getenv("VERSION")?.takeIf { it.isNotBlank() } ?: "v0.2.3"
} else {
    "0.2.3"
}

subprojects {
    group = rootProject.group
    version = rootProject.version
}

// Used by the release workflow to guard immutable GitHub Packages versions.
tasks.register("printProjectVersion") { doLast { println(project.version) } }
