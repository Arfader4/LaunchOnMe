// StickOnMe — studio naklejek. Biblioteka Androida (nie osobna aplikacja): trafia do tego samego APK co launcher,
// ale ma własną aktywność z ikoną w szufladzie. Dzięki osobnemu modułowi da się ją kiedyś wydzielić do osobnej aplikacji.
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
    // Automatyczne wycinanie obiektu (ta sama biblioteka co w launcherze — w APK jest raz).
    implementation(libs.mlkit.subject.segmentation)
}
