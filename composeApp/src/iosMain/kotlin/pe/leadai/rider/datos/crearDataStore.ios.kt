package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val NOMBRE_ARCHIVO = "leadai_sesion.preferences_pb"

@OptIn(ExperimentalForeignApi::class)
actual fun crearDataStore(): DataStore<Preferences> {
    val directorio = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val ruta = requireNotNull(directorio?.path) { "No se pudo resolver el directorio de documentos de iOS" }
    return PreferenceDataStoreFactory.createWithPath(produceFile = { "$ruta/$NOMBRE_ARCHIVO".toPath() })
}
