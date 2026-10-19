// StickOnMe — studio naklejek. Biblioteka Androida z całym kodem studia; aplikację (własny APK) robi z niej
// moduł :stickonme. Launcher od 1.9.0 NIE zależy od tego modułu — rozmawia ze StickOnMe intencjami (StickOnMeBridge).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pl.rafal.stickonme"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Automatyczne wycinanie obiektu.
    implementation(libs.mlkit.subject.segmentation)
}
