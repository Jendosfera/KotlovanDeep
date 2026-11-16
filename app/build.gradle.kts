import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

// Получаем количество коммитов для versionCode (если Git есть)
val gitCommitCount = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
} catch (e: Exception) {
    1 // fallback, если Git недоступен
}

// Формируем дату и время для versionName
val buildTimestamp = SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault()).format(Date())

android {
    namespace = "com.example.kotlovan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kotlovan"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount ($buildTimestamp)"
    }

    buildFeatures { 
        compose = true 
        buildConfig = true // <-- ВАЖНО: включает генерацию BuildConfig
    }
    composeOptions { 
        kotlinCompilerExtensionVersion = "1.5.8" 
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { 
        jvmTarget = "17" 
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.panpf.zoomimage:zoomimage-compose-coil2:1.2.0")
}

// Принудительно фиксируем версию androidx.core
configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}