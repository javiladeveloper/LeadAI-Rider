package pe.leadai.rider.datos

import pe.leadai.rider.BuildConfig

/** Android: sale de BuildConfig, que Gradle genera desde `versionCode`. */
actual object VersionApp {
    actual val codigo: Int = BuildConfig.VERSION_CODE
    actual val nombre: String = BuildConfig.VERSION_NAME
    actual val plataforma: String = "android"
}
