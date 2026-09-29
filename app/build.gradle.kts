import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localDiscord = Properties().apply {
    val configFile = rootProject.file("discord.local.properties")
    if (configFile.isFile) configFile.inputStream().use(::load)
}

fun discordBuildString(name: String): String {
    val value = localDiscord.getProperty(name, "")
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
}

android {
    namespace = "io.github.rolfwessels.cordlet"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rolfwessels.cordlet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "DISCORD_BOT_TOKEN", discordBuildString("botToken"))
            buildConfigField("String", "DISCORD_CHANNEL_ID", discordBuildString("channelId"))
        }
        getByName("release") {
            // Never put a test bot token in a distributable release APK.
            buildConfigField("String", "DISCORD_BOT_TOKEN", "\"\"")
            buildConfigField("String", "DISCORD_CHANNEL_ID", "\"\"")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.appwidget)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
