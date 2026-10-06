plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gitCommit = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().take(7).ifEmpty { "unknown" }

val gitDirty = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "status", "--porcelain")
    isIgnoreExitValue = true
}.standardOutput.asText.get().isNotBlank()

android {
    namespace = "dev.stone.pinshot"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "dev.stone.pinshot"
        minSdk = 30
        targetSdk = 36
        versionCode = 900
        versionName = "0.9.0"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "GIT_DIRTY", gitDirty.toString())
        }
        getByName("release") {
            buildConfigField("boolean", "GIT_DIRTY", "false")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
