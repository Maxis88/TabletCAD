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
        versionCode = 56
        versionName = "0.49.0-extrusion-manipulator"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    ndkVersion = "30.0.16248370"
}
