plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.masstamilan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.masstamilan.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is opt-in: provide a keystore via env vars
    // (MASSTAMILAN_KEYSTORE_PATH/PASSWORD, MASSTAMILAN_KEY_ALIAS/PASSWORD)
    // or Gradle properties (masstamilan.keystore.path/password, masstamilan.key.alias/password).
    // Without them the release APK builds unsigned (Masstamilan_<version>.apk, unsigned).
    signingConfigs {
        create("release") {
            val ksPath: String? =
                System.getenv("MASSTAMILAN_KEYSTORE_PATH")
                    ?: project.findProperty("masstamilan.keystore.path") as String?
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword =
                    System.getenv("MASSTAMILAN_KEYSTORE_PASSWORD")
                        ?: project.findProperty("masstamilan.keystore.password") as String?
                keyAlias =
                    System.getenv("MASSTAMILAN_KEY_ALIAS")
                        ?: project.findProperty("masstamilan.key.alias") as String?
                keyPassword =
                    System.getenv("MASSTAMILAN_KEY_PASSWORD")
                        ?: project.findProperty("masstamilan.key.password") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when a real keystore file is present; otherwise unsigned.
            val ksPath: String? =
                System.getenv("MASSTAMILAN_KEYSTORE_PATH")
                    ?: project.findProperty("masstamilan.keystore.path") as String?
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Custom APK name: Masstamilan_<version>.apk (e.g. Masstamilan_1.0.0.apk)
    // instead of the default app-release.apk.
    applicationVariants.all {
        outputs.all {
            outputFileName = "Masstamilan_${defaultConfig.versionName}.apk"
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Media3 (ExoPlayer)
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("com.google.guava:guava:32.1.2-android")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Hilt DI
    val hiltVersion = "2.50"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // OkHttp (for web scraping)
    val okhttpVersion = "4.12.0"
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager for downloads
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Timber Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
