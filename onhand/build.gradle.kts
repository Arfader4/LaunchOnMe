// OnHand — notatki. Biblioteka Androida jak :studio (StickOnMe): ten sam APK co launcher, własna aktywność
// z ikoną w szufladzie i własna baza (onhand.db). Moduł nie zna launchera — launcher zależy od niego, nie odwrotnie
// (jak biblioteka klas w .NET, do której odwołuje się aplikacja, a nie na odwrót).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pl.rafal.onhand"
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
    // Baza notatek (Room, jak w launcherze; w APK biblioteka jest raz).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
