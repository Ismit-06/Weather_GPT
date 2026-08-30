import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties =
    Properties().apply {
        val file =
            rootProject.file("local.properties")

        if (file.exists()) {
            file.inputStream().use {
                load(it)
            }
        }
    }

android {

    namespace =
        "com.example.weathergpt"

    compileSdk {
        version =
            release(37)
    }

    defaultConfig {

        applicationId =
            "com.example.weathergpt"

        minSdk =
            26

        targetSdk =
            37

        versionCode =
            1

        versionName =
            "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${
                localProperties.getProperty(
                    "MAPTILER_API_KEY",
                    ""
                )
            }\""
        )
    }

    buildTypes {

        release {

            optimization {
                enable = false
            }
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    buildFeatures {

        compose =
            true

        buildConfig =
            true
    }
}

dependencies {
    implementation("androidx.compose.material:material-icons-extended")

    implementation(
        "com.maptiler:maptiler-sdk-kotlin:1.3.0"
    )

    implementation(
        "org.osmdroid:osmdroid-android:6.1.20"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.9.2"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2"
    )

    implementation(
        "com.squareup.retrofit2:converter-gson:3.0.0"
    )

    implementation(
        "com.squareup.retrofit2:retrofit:3.0.0"
    )

    implementation(
        "androidx.navigation:navigation-compose:2.10.0"
    )

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}


configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (
            requested.group == "org.jetbrains.kotlin" &&
            requested.name.startsWith("kotlin-")
        ) {
            useVersion("2.2.10")
            because("Keep Kotlin libraries compatible with the Kotlin 2.2.0 compiler")
        }
    }
}
