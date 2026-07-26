package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toOkioPath
import java.io.File

private const val NOMBRE_ARCHIVO = "leadai_sesion.preferences_pb"

actual fun crearDataStore(): DataStore<Preferences> {
    val archivo = File(ContextoApp.context.filesDir, NOMBRE_ARCHIVO)
    return PreferenceDataStoreFactory.createWithPath(produceFile = { archivo.toOkioPath() })
}
