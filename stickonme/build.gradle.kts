// StickOnMe — osobna aplikacja (własny APK i własna instalacja, pakiet pl.rafal.stickonme).
// Cały kod studia mieszka w module biblioteki :studio; ten moduł tylko go „opakowuje” w aplikację
// (jak projekt .exe w .NET, który odwołuje się do biblioteki .dll z całą logiką).
// Bez wtyczki Compose: ten moduł nie ma kodu Compose (tylko klasa aplikacji), a wtyczka bez biblioteki
// compose-runtime na ścieżce kompilacji kończy się błędem „Compose Compiler requires the Compose Runtime”.
plugins {
    alias(libs.plugins.android.application)
}

android {
    // Inny namespace niż biblioteka (pl.rafal.stickonme) — inaczej dwie klasy R miałyby ten sam pakiet.
    namespace = "pl.rafal.stickonme.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "pl.rafal.stickonme"
        minSdk = 29
        targetSdk = 36
        // Własna numeracja StickOnMe (od wydzielenia z LaunchOnMe 1.9.0): X·10000 + Y·100 + Z.
        versionCode = 10202
        versionName = "1.2.2"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":studio"))
    implementation(libs.androidx.core.ktx)
}
