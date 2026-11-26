import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val zoomSdk = rootProject.file("mobilertc/mobilertc.aar").exists()

android {
    namespace = "com.oku.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Aynı kod iki uygulama: Oku (öğrenci) ve Okut (veli)
    flavorDimensions += "rol"
    productFlavors {
        create("oku") {
            dimension = "rol"
            applicationId = "com.oku.ogrenci"
            buildConfigField("boolean", "PARENT", "false")
            buildConfigField("boolean", "ZOOM_SDK", zoomSdk.toString())
            if (zoomSdk) {
                minSdk = 28 // Zoom Meeting SDK en az Android 9 ister
                ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            }
        }
        create("okut") {
            dimension = "rol"
            applicationId = "com.oku.veli"
            buildConfigField("boolean", "PARENT", "true")
            buildConfigField("boolean", "ZOOM_SDK", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Zoom köprüsü: SDK varsa gerçek uygulama içi katılma, yoksa boş köprü
    sourceSets {
        getByName("oku").java.srcDir(if (zoomSdk) "src/zoomsdk/java" else "src/nozoom/java")
        getByName("okut").java.srcDir("src/nozoom/java")
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // Her derlemede aynı imza: yeni APK eskisinin üzerine kurulabilsin
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    if (zoomSdk) add("okuImplementation", project(":mobilertc"))
}
