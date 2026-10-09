import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val productionSigningFile = rootProject.file("signing.properties")
val productionSigning = Properties().apply {
    if (productionSigningFile.isFile) productionSigningFile.inputStream().use { load(it) }
}
val requiredSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val signingReady = requiredSigningKeys.all { !productionSigning.getProperty(it).isNullOrBlank() }
val productionKeystore = productionSigning.getProperty("storeFile")?.let { rootProject.file(it) }

gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("Release") &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package") || it.name.startsWith("validateSigning")) }) {
        check(signingReady && productionKeystore?.isFile == true) {
            "Release requer signing.properties na raiz com storeFile, storePassword, keyAlias e keyPassword, e um keystore existente."
        }
    }
}

android {
    namespace = "com.araujo.karaoke_ia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.araujo.karaoke_ia"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (signingReady && productionKeystore?.isFile == true) {
            create("production") {
                storeFile = productionKeystore
                storeType = "JKS"
                storePassword = productionSigning.getProperty("storePassword")
                keyAlias = productionSigning.getProperty("keyAlias")
                keyPassword = productionSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.zxing.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
