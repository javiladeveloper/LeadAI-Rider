package pe.leadai.rider.datos

/**
 * Versión de ESTA build, para compararla con la última publicada en la tienda
 * y avisar cuando hay una nueva.
 *
 * `expect/actual` porque el dato vive en sitios distintos: en Android sale de
 * `BuildConfig` (que Gradle genera desde `versionCode`), en iOS del
 * `CFBundleVersion` del Info.plist.
 */
expect object VersionApp {
    /** `versionCode` en Android, `CFBundleVersion` en iOS. */
    val codigo: Int

    /** Legible: "0.1.5". */
    val nombre: String

    /** `android` | `ios` — para pedir la versión que corresponde. */
    val plataforma: String
}
