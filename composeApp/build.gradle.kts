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
        versionCode = 5
        versionName = "0.1.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "leadai2026pe"
            keyAlias = "leadai"
            keyPassword = "leadai2026pe"
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
