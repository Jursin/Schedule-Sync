@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.spotless)
}

val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

android {
    namespace = "com.schedule.vela"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.schedule.vela"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "2.0.1"
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                )
        }
    }

    val releaseStoreFile = localProps.getProperty("signing.storeFile", "")
    if (releaseStoreFile.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProps.getProperty("signing.storePassword", "")
                keyAlias = localProps.getProperty("signing.keyAlias", "")
                keyPassword = localProps.getProperty("signing.keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

base {
    archivesName = "schedule-sync"
}

aboutLibraries {
    export {
        outputFile = file("src/main/assets/aboutlibraries.json")
        prettyPrint = true
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.nav)
    implementation(libs.kotlinx.serialization.json)
    implementation(files("libs/xms-wearable-lib_1.4_release.aar"))
    implementation(libs.snakeyaml)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
}
