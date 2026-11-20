plugins {
    alias(libs.plugins.android.application)
}

// Set cloudUrl=https://your-server.example.com in the ignored local.properties file.
val cloudUrl = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}.getProperty("cloudUrl", "").trimEnd('/')
require(cloudUrl.isEmpty() || Regex("https?://[A-Za-z0-9.-]+(?::[0-9]+)?").matches(cloudUrl))

android {
    namespace = "com.example.photoviewer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.photoviewer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CLOUD_URL", "\"$cloudUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
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
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
