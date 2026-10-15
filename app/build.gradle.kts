import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val signingPropsFile = rootProject.file(".secrets/signing.properties")
val signingProps = Properties().apply { if (signingPropsFile.exists()) signingPropsFile.inputStream().use(::load) }

android {
    namespace = "it.dueffe.health"
    compileSdk = 36
    defaultConfig {
        val updateRepo = providers.gradleProperty("updateRepository").getOrElse("carellice/android-app-releases")
        require(updateRepo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "updateRepository must be owner/repo" }
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepo\"")
        applicationId = "it.dueffe.health"
        minSdk = 26
        targetSdk = 36
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
    }
    signingConfigs {
        if (signingPropsFile.exists()) create("releaseLocal") {
            storeFile = rootProject.file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingPropsFile.exists()) signingConfig = signingConfigs.getByName("releaseLocal")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    val bom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
