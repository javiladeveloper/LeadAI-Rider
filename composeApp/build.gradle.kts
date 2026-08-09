import java.util.Properties
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
//
// El OFFSET tiene que superar SIEMPRE al mayor versionCode que ya vio Play.
// Estaba en 10 con el run_number en 9, o sea que el CI proponía 19 — un
// código ya usado, y Play lo rechazaba con "Version code 19 has already been
// used". Ese es el motivo real de que los últimos releases se hayan hecho a
// mano: el CI compilaba bien y moría al subir.
//
// Ahora en 200, muy por encima del 65 que llegó a Play con los builds
// manuales. El margen es a propósito: si algún día vuelve a subirse algo a
// mano, el CI sigue ganando sin tener que tocar esto de nuevo.
/**
 * La clave de Google Maps.
 *
 * Sale de `MAPS_API_KEY` en `local.properties` (que está en .gitignore) o de la
 * variable de entorno homónima en el CI. Nunca se escribe en el repo.
 *
 * Si falta, el mapa se ve GRIS y el log dice "Authorization failure" — que es
 * un síntoma bastante mudo, así que conviene revisar esto primero.
 */
val claveDeMapas: String = run {
    val props = Properties()
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { props.load(it) }
    props.getProperty("MAPS_API_KEY")
        ?: System.getenv("MAPS_API_KEY")
        ?: ""
}

val VERSION_CODE_OFFSET = 200
// Los builds LOCALES (sin GITHUB_RUN_NUMBER) usan esto. Queda bajo a
// propósito: un AAB local no debería poder pisar lo que publica el CI.
val VERSION_CODE_BASE = 5

/**
 * versionName: sale del TAG que disparó la publicación
 * (`interna-v0.1.6` → `0.1.6`), así el número que ve el usuario siempre
 * coincide con lo que se etiquetó. Antes estaba fijo acá y quedaba
 * desincronizado: el tag decía v0.1.6 y la app mostraba 0.1.4.
 *
 * En local (sin tag) queda el valor de respaldo.
 */
// El respaldo sale del ÚLTIMO TAG de git, no de un número escrito a mano.
//
// Escrito a mano se desincroniza igual que antes: el tag ya iba por 0.3.2 y
// esto seguía diciendo 0.2.3, así que un APK de prueba mostraba una versión
// que no era la que se estaba probando —justo cuando uno mira el pie de
// pantalla para saber qué tiene instalado—.
//
// En el CI no se usa: ahí manda GITHUB_REF_NAME. Esto es solo para builds
// locales, donde git siempre está a mano.
val VERSION_NAME_LOCAL: String = try {
    val p = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val salida = p.inputStream.bufferedReader().readText().trim()
    p.waitFor()
    salida.substringAfterLast("-v", "").ifBlank { "0.0.0" }
} catch (e: Exception) {
    // Sin git (o sin tags) el build no debe romperse por esto.
    "0.0.0"
}

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
            // Mapas nativos: se dibujan con Compose, no con HTML dentro de un
            // WebView que reporta un tamaño que no es el suyo.
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
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
        manifestPlaceholders["MAPS_API_KEY"] = claveDeMapas
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
