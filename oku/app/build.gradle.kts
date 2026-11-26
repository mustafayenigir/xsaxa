plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oku.app"
    compileSdk = 34

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
        }
        create("okut") {
            dimension = "rol"
            applicationId = "com.oku.veli"
            buildConfigField("boolean", "PARENT", "true")
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
