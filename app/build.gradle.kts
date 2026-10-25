import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Wersja z pliku VERSION; CI podaje własny numer (-PversionCode, -PversionName). Bez parametrów
// (np. budowanie w F-Droid) kod wersji wynika z VERSION: 1.4.0 → 10400 – ten sam kod z tego samego źródła.
val versionFromFile = rootProject.file("VERSION").readText().trim()
val versionCodeFromFile = versionFromFile.split('.').map { it.toIntOrNull() ?: 0 }.plus(listOf(0, 0, 0))
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: versionCodeFromFile
val appVersionName = (project.findProperty("versionName") as String?) ?: versionFromFile
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val hasKeystore = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "com.blacksingularities.openexplorer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blacksingularities.openexplorer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_REPO", "\"BlackSingularities/OpenExplorer\"")
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystorePath!!)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Podpis wydania ustawiają warianty dystrybucji niżej.
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Dystrybucja: „github” – wydania z GitHub z wbudowanym aktualizatorem; „fdroid” – bez
    // samoaktualizacji (F-Droid nie dopuszcza instalowania aktualizacji spoza swojego repozytorium).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "UPDATER", "true")
            // Bez sekretów (fork, lokalne budowanie) wydanie podpisuje klucz debug, żeby dało się je zainstalować.
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER", "false")
            // F-Droid podpisuje APK sam – bez klucza wydania powstaje niepodpisany plik.
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // Robolectric (testy bazy historii) potrzebuje zasobów i manifestu aplikacji.
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Nowoczesne API WebView: profile (incognito), ciemne strony.
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Logo stron na kafelkach.
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Kody QR adresów stron.
    implementation("com.google.zxing:core:3.5.3")
    // Sprawdzanie i pobieranie aktualizacji w tle.
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // Historia przeglądania w bazie SQLite.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Prawdziwe org.json w testach jednostkowych (w android.jar są tylko zaślepki).
    testImplementation("org.json:json:20240303")
}
