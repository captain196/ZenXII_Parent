import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Load release-signing credentials from a gitignored keystore.properties at the
// project root. Absent on machines that only build debug (release signing is skipped).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.schoolsync.parent"
    compileSdk = 35

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.schoolsync.parent"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // PHP backend URL — used for any future REST API endpoints.
        // Emulator: 10.0.2.2 reaches the host machine's 8080.
        // Physical device over USB: `adb reverse tcp:8080 tcp:8080` then use 127.0.0.1:8080 here.
        // Physical device over Wi-Fi: use the Mac's LAN IP.
        // Runtime override available via DevPrefs / Dev Settings dialog.
        // Production: ZenXii backend on Lightsail, served at the DOMAIN ROOT
        // behind Cloudflare (https://www.zenxii.com/) — confirmed by the
        // server's own config.php (public_base_url = https://www.zenxii.com/).
        // BOTH the PHP REST endpoints (index.php/fee_management/...) AND the
        // Node auth routes (/auth/...) live under this root. `/Grader/school/`
        // is ONLY the local XAMPP dev path and returns 403 on production, which
        // the app surfaces as a false "session expired" on payment — so the
        // base URL must be the bare root for every tenant (the school is
        // resolved from the parent's token claims, not the URL).
        // A developer can still point a debug build at a LAN IP / local XAMPP
        // at runtime via the hidden Dev Settings dialog (DevPrefs override).
        buildConfigField("String", "BASE_URL", "\"https://www.zenxii.com/\"")
    }

    buildTypes {
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // PROD-READINESS (2026-07): the production backend base URL. Defaults
            // to the live ZenX host (served at domain root behind Cloudflare) and
            // can be overridden at build time for staging / other tenants via the
            // `PROD_BASE_URL` Gradle property, e.g.:
            //   ./gradlew :app:bundleRelease -PPROD_BASE_URL=https://staging.zenxii.com/
            // A release build NEVER ships the localhost dev default (that stays
            // debug-only above).
            val prodBaseUrl = (project.findProperty("PROD_BASE_URL") as String?)
                ?: "https://www.zenxii.com/"
            buildConfigField("String", "BASE_URL", "\"$prodBaseUrl\"")
        }
        // Debug builds inherit the production BASE_URL from defaultConfig
        // (https://www.zenxii.com/Grader/school/). To test against a LAN PC,
        // override at runtime via the hidden Dev Settings dialog instead of
        // hardcoding a tunnel here.
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
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions {
        // JVM unit tests only touch pure companion helpers; return default
        // values for any incidentally-linked android.jar stubs instead of
        // throwing "Method ... not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Pull-to-refresh — Material3's PullToRefreshBox isn't in the
    // 1.2.x BoM we're on, so we use the older Material (Compose
    // Material 2) `pullRefresh` API which coexists with Material3.
    implementation("androidx.compose.material:material")
    // Pull-to-refresh — Material3's PullToRefreshBox isn't in the
    // 1.2.x BoM we're on, so we use the older Material (Compose
    // Material 2) `pullRefresh` API which coexists with Material3.
    implementation("androidx.compose.material:material")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Coil video-frame decoder so video thumbnails (event / gallery media)
    // render a poster frame instead of a blank tile when no explicit
    // thumbnail URL is provided.
    implementation("io.coil-kt:coil-video:2.6.0")
    // Video playback (Stories viewer — Round 1a).
    // Media3 = modern ExoPlayer; stable, Compose-friendly via AndroidView.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // Stories viewer upgrade — disk cache for smooth video re-watch /
    // swipe-back, and the cache DB provider.
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-database:1.3.1")

    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Shimmer effect
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.0")

    // Razorpay checkout
    implementation("com.razorpay:checkout:1.6.38")

    // Razorpay checkout
    implementation("com.razorpay:checkout:1.6.38")

    // F10 (2026-07-07) — Google Maps SDK for future GPS live-tracking.
    // Added at F10 to prepare the app dependency graph; NO live tracking
    // is wired at F10 (operator directive — the map surface remains
    // hidden / shows "Live tracking coming soon" until the dedicated
    // GPS phase). play-services-maps for the map view;
    // maps-compose for Composable integration; play-services-location
    // for future device geolocation (parent-side, optional consent).
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    // Match kotlinx-coroutines-android version (1.7.3) above.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

