plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Apply the Google Services plugin only when google-services.json is present, so
// the project still builds before you wire up Firebase (see server/README.md).
// The plugin is on the classpath via the root build's `apply false` declaration.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "io.qwqc.claudewatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.qwqc.claudewatch"
        minSdk = 30          // Wear OS 3+. Galaxy Watch 5 Pro runs Wear OS 4.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
            // JSch pulls in duplicate license files on some transitive paths.
            excludes += "/META-INF/versions/**"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    // Native on-watch text entry for the terminal (RemoteInput keyboard dialog).
    implementation(libs.androidx.wear.input)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Maintained JSch fork — pure-Java SSH, supports ed25519/RSA keys + PTY.
    implementation(libs.jsch)

    // QR encoding for the on-watch "scan to copy public key" flow.
    implementation(libs.zxing.core)

    // Animated GIF mascot (Clawd) in Compose.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Push notifications ("Claude is done"). Requires google-services.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.play.services.wearable)
}
