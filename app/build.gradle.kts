import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localSigningFile = rootProject.file("signing.properties")
val localSigningProps = Properties().apply {
    if (localSigningFile.exists()) {
        localSigningFile.reader(Charsets.UTF_8).use { load(it) }
    }
}
val releaseStorePath = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("storeFile")?.trim()?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("SIGNING_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("keyAlias")?.trim()?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: localSigningProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
val releaseStoreFile = releaseStorePath?.let { file(it) }?.takeIf { it.exists() }
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.yubrae.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yubrae.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"

        buildConfigField("String", "GITHUB_OWNER", "\"Agonie0v0\"")
        buildConfigField("String", "GITHUB_REPO", "\"yubrae\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12") {
        exclude(group = "log4j", module = "log4j")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

afterEvaluate {
    listOf(
        "assembleDebug" to "debug",
        "assembleRelease" to "release"
    ).forEach { (taskName, variantName) ->
        tasks.matching { it.name == taskName }.configureEach {
            doLast {
                val apkPrefix = if (variantName == "release" && !hasReleaseSigning) {
                    "app-$variantName-unsigned.apk"
                } else {
                    "app-$variantName.apk"
                }
                val builtApk = layout.buildDirectory.file("outputs/apk/$variantName/$apkPrefix").get().asFile
                val mirrorDir = file("${projectDir}/build/outputs/apk/$variantName")
                val mirrorApk = file("${mirrorDir}/$apkPrefix")
                if (builtApk.exists() && builtApk.canonicalFile != mirrorApk.canonicalFile) {
                    mirrorDir.mkdirs()
                    builtApk.copyTo(mirrorApk, overwrite = true)
                }
            }
        }
    }
}
