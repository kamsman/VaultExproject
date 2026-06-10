import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.vaultex"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vaultex"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "INFURA_API_KEY",  "\"REPLACE_WITH_YOUR_INFURA_KEY\"")
        buildConfigField("String", "ALCHEMY_API_KEY", "\"REPLACE_WITH_YOUR_ALCHEMY_KEY\"")
        buildConfigField("String", "TRONGRID_API_KEY","\"REPLACE_WITH_YOUR_TRONGRID_KEY\"")
        buildConfigField("String", "ONEINCH_API_KEY", "\"REPLACE_WITH_YOUR_1INCH_KEY\"")
        buildConfigField("String", "COINGECKO_API_KEY","\"REPLACE_WITH_YOUR_COINGECKO_KEY\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_ETH","\"0xREPLACE_WITH_YOUR_FEE_WALLET_EVM\"")
        buildConfigField("String", "VAULTEX_FEE_RECIPIENT_TRX","\"TREPLACE_WITH_YOUR_FEE_WALLET_TRX\"")
        buildConfigField("double", "VAULTEX_FEE_PERCENT","1.5")
        // API keys read from local.properties (gitignored) — set them there, not here
        buildConfigField("String", "ETHERSCAN_KEY",   "\"${localProps.getProperty("etherscan.key",   "")}\"")
        buildConfigField("String", "BSCSCAN_KEY",     "\"${localProps.getProperty("bscscan.key",     "")}\"")
        buildConfigField("String", "CHANGENOW_KEY",   "\"${localProps.getProperty("changenow.key",   "")}\"")
        buildConfigField("String", "FLUTTERWAVE_KEY", "\"${localProps.getProperty("flutterwave.key", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

dependencies {

    // ANDROIDX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // COMPOSE
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // HILT
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ROOM
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SECURITY
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // CRYPTO (PROPRE)
    implementation("org.web3j:core:4.9.8-android")
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")

    // ❌ SUPPRIMÉ (IMPORTANT)
    // BIP39
    // BIP44
    // BOUNCYCASTLE MANUEL

    // NETWORK
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // UI / QR
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.g00fy2.quickie:quickie-bundled:1.10.0")
    // ROOT DETECTION
    implementation("com.scottyab:rootbeer-lib:0.1.0")

    // FIREBASE
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // TEST
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
}