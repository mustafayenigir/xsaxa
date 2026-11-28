import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kitap.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        // Her derlemede sürüm artar; uygulama içi "Güncelle" bunu karşılaştırır
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = maxOf(run, 2)
        versionName = "1.$run"
    }

    // Aynı kod iki uygulama: Kitap (herkes) ve Üye Yönetim (yönetim)
    flavorDimensions += "rol"
    productFlavors {
        create("kitap") {
            dimension = "rol"
            applicationId = "com.kitap.okur"
            buildConfigField("boolean", "ADMIN", "false")
        }
        create("yonetim") {
            dimension = "rol"
            applicationId = "com.kitap.yonetim"
            buildConfigField("boolean", "ADMIN", "true")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
