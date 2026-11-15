import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing: local.properties `releaseSigningProperties` points to a keystore.properties kept
// outside the repository (storeFile, storePassword, keyAlias, keyPassword). Without it release stays unsigned.
val releaseSigning: Properties? = Properties().let { local ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(local::load)
    local.getProperty("releaseSigningProperties")?.let(::file)?.takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
}

android {
    namespace = "com.routeonomy.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.routeonomy.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.fusedlocation)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
