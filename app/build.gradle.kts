plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "net.tecogonaz.tcsameuradammonitor"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.tecogonaz.tcsameuradammonitor"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "net.tecogonaz.tcsameuradammonitor.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/tcsameura-upload.jks")
            storePassword = System.getenv("TCSAMEURA_UPLOAD_STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("TCSAMEURA_UPLOAD_KEY_PASSWORD")
                ?: System.getenv("TCSAMEURA_UPLOAD_STORE_PASSWORD")
        }
    }

    buildTypes {
        create("uiTest") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val uploadStoreFile = signingConfigs.getByName("release").storeFile
            val uploadStorePassword = signingConfigs.getByName("release").storePassword
            if (uploadStoreFile != null && uploadStoreFile.exists() && !uploadStorePassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    testBuildType = "uiTest"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        managedDevices {
            localDevices {
                create("phoneApi36") {
                    device = "Pixel 8"
                    apiLevel = 36
                    systemImageSource = "google"
                }
                create("foldableApi36") {
                    device = "Pixel Fold"
                    apiLevel = 36
                    systemImageSource = "google"
                }
                create("tabletApi36") {
                    device = "Pixel Tablet"
                    apiLevel = 36
                    systemImageSource = "google"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs unit tests for the uiTest build; kept as the Android test policy command."
    dependsOn("testUiTestUnitTest")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.arrow.core)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.glance.appwidget.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    "uiTestImplementation"(libs.androidx.ui.tooling)
    "uiTestImplementation"(libs.androidx.ui.test.manifest)
}
