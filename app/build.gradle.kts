plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kiddo.autotapper"
    compileSdk = 34

    defaultConfig {
        // Package officiel GAME Lv-UP. À partir d'ici, TOUTES les builds
        // partagent la même applicationId + la même clé de signature →
        // les mises à jour s'installent par-dessus l'ancienne version
        // sans désinstaller et sans refaire les autorisations.
        applicationId = "com.gamelvupclicker"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Keystore stable généré une fois par la CI (keystore/debug.keystore)
    // et commité : signature identique à chaque build.
    val stableKeystore = rootProject.file("keystore/debug.keystore")
    signingConfigs {
        create("stable") {
            if (stableKeystore.exists()) {
                storeFile = stableKeystore
                storePassword = "kidcli2026"
                keyAlias = "kidcli"
                keyPassword = "kidcli2026"
            }
        }
    }

    buildTypes {
        debug {
            if (stableKeystore.exists()) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
            if (stableKeystore.exists()) {
                signingConfig = signingConfigs.getByName("stable")
            }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
