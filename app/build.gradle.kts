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
    compileSdk = 36

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
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // PHP backend URL — used for any future REST API endpoints.
        // Emulator: 10.0.2.2 reaches the host machine's 8080.
        // Physical device over USB: `adb reverse tcp:8080 tcp:8080` then use 127.0.0.1:8080 here.
        // Physical device over Wi-Fi: use the Mac's LAN IP.
        // Runtime override available via DevPrefs / Dev Settings dialog.
        // Production: ZenXii backend on Lightsail, fronted by https://www.zenxii.com.
        // The /Grader/school/ subpath serves the legacy PHP REST endpoints; the
        // host root serves the Node auth routes (e.g. /auth/clear_must_change),
        // which AuthApi reaches via leading-slash paths.
        // A developer can still point a debug build at a LAN IP at runtime via
        // the hidden Dev Settings dialog (DevPrefs override).
        buildConfigField("String", "BASE_URL", "\"https://www.zenxii.com/Grader/school/\"")
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
        }
        // Debug builds inherit the production BASE_URL from defaultConfig
        // (https://www.zenxii.com/Grader/school/). To test against a LAN PC,
        // override at runtime via the hidden Dev Settings dialog instead of
        // hardcoding a tunnel here.
        debug {
            // Generates the en-XA pseudolocale (~30% longer strings, bracketed).
            // This is the primary layout-overflow detector for the multi-language
            // work: it exposes clipping before any real translation exists, and
            // approximates Tamil/Telugu expansion. Reachable by switching the
            // device language to "English (XA)" in system settings.
            isPseudoLocalesEnabled = true
        }
    }
    compileOptions {
        // Backports java.time (and other Java 8 APIs) to API 24-25.
        //
        // Both apps declare minSdk 24 but use java.time throughout — which
        // requires API 26. Without this, an Android 7.0/7.1 device installs
        // the app happily and then throws NoClassDefFoundError the moment it
        // touches LocalDate/YearMonth (e.g. opening Attendance). Lint reported
        // 167 NewApi errors for exactly this.
        //
        // Desugaring is chosen over raising minSdk to 26 deliberately: the
        // users still on Android 7 are on old low-end handsets, which is the
        // same group the regional-language support was built for. Dropping
        // them would undercut the point.
        isCoreLibraryDesugaringEnabled = true
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

    // MUST stay disabled. Play's default behaviour is to strip every locale the
    // installing device isn't set to — so a parent whose phone is in English and
    // who picks Gujarati inside the app would silently get English back, with
    // nothing in logcat. Debug APKs are unaffected, so this fails ONLY on the
    // production artifact. Verify by installing the release AAB through Play
    // internal testing on an English-locale device, then switching in-app.
    bundle {
        language { enableSplit = false }
    }

    lint {
        // Translation correctness is enforced at build time, not by review.
        // MissingTranslation is what stops the catalogue rotting as new features
        // add English-only strings; the StringFormat checks stop a translated
        // "%1$s" that lost or reordered its argument from throwing
        // IllegalFormatException inside a composable at runtime.
        error += listOf(
            "MissingTranslation",
            "ExtraTranslation",
            "StringFormatInvalid",
            "StringFormatMatches",
            "ImpliedQuantity"
        )
    }
    testOptions {
        // JVM unit tests only touch pure companion helpers; return default
        // values for any incidentally-linked android.jar stubs instead of
        // throwing "Method ... not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Required by isCoreLibraryDesugaringEnabled above.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    // Support Desk: bake EXIF rotation into attached photos before upload.
    // Re-encoding drops EXIF, so without this a sideways receipt stays
    // sideways everywhere it is later shown. Dropping the rest of the
    // metadata is the point -- it includes GPS coordinates.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
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

    // Tests
    testImplementation("junit:junit:4.13.2")
    // Match kotlinx-coroutines-android version (1.7.3) above.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

