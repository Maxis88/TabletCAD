plugins {
    id("com.android.application")
}

android {
    namespace = "pl.tabletcad.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.tabletcad.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 98
        versionName = "0.87.0-face-projection"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    ndkVersion = "30.0.16248370"
}
