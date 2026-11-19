import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// La firma de release se lee de keystore.properties (NO incluido en el repositorio).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.travelindex.app"
    compileSdk = 35

    defaultConfig {
        applicationId = providers.gradleProperty("travelindex.applicationId").get()
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("travelindex.versionCode").get().toInt()
        versionName = providers.gradleProperty("travelindex.versionName").get()
        // Idiomas de la app: el APK solo empaqueta estos (las librerías traen docenas que no usamos).
        resourceConfigurations += listOf("en", "es")
        // Fila «Apoya el proyecto» de Perfil. Ponla en false (travelindex.showDonations) para una tienda que no admita enlaces de donación.
        buildConfigField("boolean", "SHOW_DONATIONS", providers.gradleProperty("travelindex.showDonations").getOrElse("true").toBooleanStrict().toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Clave fija SOLO para builds de debug: así todas las builds (CI incluido) firman igual y el APK se
        // actualiza sin desinstalar. Es una clave de pruebas con la contraseña estándar pública «android»;
        // no guarda ningún secreto y no sirve para publicar (el release usa tu keystore privado).
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    testOptions {
        // Robolectric necesita los recursos reales de la app (textos, temas).
        unitTests.isIncludeAndroidResources = true
        // Si un test falla, el log de CI enseña el mensaje y no solo la línea (sin el informe HTML no se sabría por qué).
        unitTests.all { test ->
            test.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    lint {
        // Puerta de calidad en CI: un error de lint tumba el build.
        abortOnError = true
        // La paridad de idiomas la garantiza tools/check_resources.py (probado con mutación); el aviso duplicaría ruido.
        disable += "MissingTranslation"
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    bundle {
        // El idioma se elige dentro de la app (Perfil → Idioma): los dos idiomas deben ir siempre en el AAB.
        // Con el reparto por idioma de Play, un móvil en español no recibiría los textos en inglés.
        language { enableSplit = false }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    // Escáner de documentos con recorte automático (módulo de Google Play Services, funciona sin internet en la app).
    implementation(libs.mlkit.document.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Tests de interfaz en el JVM (Robolectric + Compose): pulsan composables reales sin emulador.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
