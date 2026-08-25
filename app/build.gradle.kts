plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.edvinlinge.hemma.mathstars2"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.edvinlinge.hemma.mathstars2"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    // Shared debug key so CI artifacts, GitHub Release APKs, and local
    // assembleDebug installs can update each other instead of conflicting.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            // Parallel install next to the Play Store app.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val releaseBuild = findProperty("releaseBuild")?.toString()?.toBooleanStrictOrNull() == true
val versionName = extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
    .defaultConfig.versionName

base {
    archivesName = if (releaseBuild) {
        "Golden-Stars-$versionName"
    } else {
        // Master CI and local builds: distinguish post-release test APKs from tagged releases.
        "Golden-Stars-$versionName-test"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // Used directly by MandelbrotView; previously only reached transitively.
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
