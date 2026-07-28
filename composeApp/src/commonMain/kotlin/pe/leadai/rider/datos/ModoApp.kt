package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * En qué modo está usando la app: pidiendo una moto o manejando. Se guarda
 * porque un usuario que ya eligió no debería tener que elegir cada vez.
 *
 * `null` = todavía no eligió. Un usuario CON perfil de motorizado nunca ve la
 * pantalla de elección: entra directo a conducir.
 */
class ModoRepositorio(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val CLIENTE = "cliente"
        const val CONDUCTOR = "conductor"
    }

    suspend fun guardar(modo: String) {
        dataStore.edit { prefs -> prefs[KEY_MODO] = modo }
    }

    fun observar(): Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_MODO] }
}

private val KEY_MODO = stringPreferencesKey("modo_app")
