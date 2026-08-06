package pe.leadai.rider.datos

import platform.Foundation.NSBundle

/** iOS: del Info.plist del bundle. */
actual object VersionApp {
    actual val codigo: Int =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toIntOrNull() ?: 0
    actual val nombre: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?: ""
    actual val plataforma: String = "ios"
}
