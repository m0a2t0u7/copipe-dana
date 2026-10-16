plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.shinyalatte.copipecho"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.shinyalatte.copipecho"
        minSdk = 26; targetSdk = 34
        versionCode = 3; versionName = "1.2"
    }
    signingConfigs {
        create("release") {
            storeFile = file("copipecho.jks")
            storePassword = "copipecho"; keyAlias = "copipecho"; keyPassword = "copipecho"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("sh.calvin.reorderable:reorderable:2.4.0")
}
