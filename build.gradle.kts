// Proyecto raíz. Los plugins se aplican en los módulos.
//
// `googleServices` se declara acá con `apply false` (patrón estándar del
// Plugin DSL: el classpath se resuelve una sola vez en el proyecto raíz) pero
// SOLO se aplica de verdad dentro de `composeApp/build.gradle.kts`, y ahí de
// forma CONDICIONAL a que exista `composeApp/google-services.json` — Jonathan
// todavía no crea el proyecto Firebase, así que el build debe compilar limpio
// sin ese archivo (ver decisión completa en `composeApp/build.gradle.kts`).
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
}
