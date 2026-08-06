plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// `google-services` CONDICIONAL (mismo criterio que la app de negocios): el
// plugin rompe el build si falta `google-services.json`, así que se aplica
// solo cuando el archivo existe. `assembleDebug`/`testDebugUnitTest` deben
// pasar limpio SIN el archivo presente.
val tieneGoogleServicesJson = file("google-services.json").exists()
if (tieneGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
}

// versionCode: en CI sale de GITHUB_RUN_NUMBER + OFFSET, así nunca hay que
// acordarse de subirlo a mano ni choca con Play (que rechaza duplicados).
// El OFFSET supera el último subido a mano (5), para que el primer build
// automático empiece por encima. BASE es el valor de los builds locales.
val VERSION_CODE_OFFSET = 10
val VERSION_CODE_BASE = 5

/**
 * versionName: sale del TAG que disparó la publicación
 * (`interna-v0.1.6` → `0.1.6`), así el número que ve el usuario siempre
 * coincide con lo que se etiquetó. Antes estaba fijo acá y quedaba
 * desincronizado: el tag decía v0.1.6 y la app mostraba 0.1.4.
 *
 * En local (sin tag) queda el valor de respaldo.
 */
val VERSION_NAME_LOCAL = "0.1.6"
val versionNameDelTag: String =
    System.getenv("GITHUB_REF_NAME")
        ?.substringAfterLast("-v", "")
        ?.takeIf { it.isNotBlank() }
        ?: VERSION_NAME_LOCAL

// ── Tokens de diseño ──────────────────────────────────────────────────────
// `design/jala-design-tokens.json` es la FUENTE DE VERDAD de todo lo visual.
// Esta tarea lo traduce a Kotlin antes de compilar, así el compilador verifica
// los nombres y no hay que parsear JSON en cada arranque de la app.
val generarTokens = tasks.register<GenerarTokensTask>("generarTokens") {
    archivoTokens.set(rootProject.file("design/jala-design-tokens.json"))
    directorioSalida.set(layout.buildDirectory.dir("generated/tokens"))
}

// Cualquier compilación de Kotlin depende de que los tokens estén generados.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generarTokens)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    // Targets iOS declarados desde ya; en Windows no se compilan
    // (kotlin.native.ignoreDisabledTargets en gradle.properties).
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/tokens"))
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.datastore.preferences)
            implementation(libs.okio)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            // Push: "nueva carrera en tu zona" (el rider tiene que enterarse
            // aunque no esté mirando la app).
            implementation(libs.firebase.messaging.ktx)
            implementation(libs.kotlinx.coroutines.play.services)
            // Login con Google — misma cascada de 3 flujos que la app de
            // negocios (ver ObtenerIdTokenGoogle.android.kt).
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.gms.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "pe.leadai.rider"
    compileSdk = 35

    defaultConfig {
        // App SEPARADA de `pe.leadai.app`: en Play son dos apps del mismo
        // desarrollador (como Uber y Uber Driver). El rider ya no vive
        // pegado a la app del restaurante.
        applicationId = "pe.leadai.rider"
        minSdk = 26
        targetSdk = 35
        // En CI cada corrida incrementa solo; en local queda fijo en BASE.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(VERSION_CODE_OFFSET)
            ?: VERSION_CODE_BASE
        versionName = versionNameDelTag
    }
    // BuildConfig: de ahí sale el versionCode que la app compara con la
    // última publicada para avisar de actualizaciones.
    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Credenciales de firma por variable de entorno, con el valor local como
    // respaldo: así el CI las inyecta desde los secretos de GitHub y el build
    // en la máquina de Jonathan sigue andando sin configurar nada.
    //
    // El keystore NO está en el repo (.gitignore); el CI lo reconstruye desde
    // el secreto ANDROID_KEYSTORE_BASE64.
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "leadai2026pe"
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "leadai"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: "leadai2026pe"
        }
    }

    buildTypes {
        getByName("release") {
            // La firma solo se aplica si el keystore existe (el repo no lo
            // trae; se genera/copia al preparar el release).
            if (file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
