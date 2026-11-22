plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Redirect build outputs to an ASCII path keyed by project dir hash so AAPT2/KSP avoid
// Windows non-ASCII directory issues and parallel Swarm share workspaces never collide.
val rootKey = Integer.toHexString(rootProject.projectDir.absolutePath.hashCode())
allprojects {
    layout.buildDirectory.set(file("D:/DevTools/kairo-build/$rootKey/${project.name}"))
}
