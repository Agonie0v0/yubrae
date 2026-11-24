plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// 在包含非 ASCII 字符的本地 Windows 环境下将构建目录重定向到纯 ASCII 路径，规避 AAPT2/KSP 路径问题；
// CI 环境或 Linux/macOS 直接沿用默认 build/ 目录以保障工作流正常归档。
val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
val hasNonAsciiPath = rootProject.projectDir.absolutePath.any { it.code > 127 }
if (!isCi && isWindows && hasNonAsciiPath && java.io.File("D:/DevTools").exists()) {
    val rootKey = Integer.toHexString(rootProject.projectDir.absolutePath.hashCode())
    allprojects {
        layout.buildDirectory.set(java.io.File("D:/DevTools/yubrae-build/$rootKey/${project.name}"))
    }
}
