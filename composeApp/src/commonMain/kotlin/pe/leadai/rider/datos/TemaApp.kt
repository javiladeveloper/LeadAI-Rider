package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Si la app se ve clara u oscura. Mismo patrón que [ModoRepositorio].
 *
 * Tres opciones a propósito, no un interruptor de dos:
 * [SISTEMA] es el default y respeta el ajuste del teléfono, pero un rider que
 * anda de día con el celular en el manubrio quiere forzar el claro aunque su
 * Android esté en oscuro (y al revés de noche). Con un interruptor binario esa
 * elección se pierde en cuanto el sistema cambia solo al atardecer.
 */
class TemaRepositorio(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val SISTEMA = "sistema"
        const val CLARO = "claro"
        const val OSCURO = "oscuro"
    }

    suspend fun guardar(tema: String) {
        dataStore.edit { prefs -> prefs[KEY_TEMA] = tema }
    }

    /** `SISTEMA` mientras el usuario no haya elegido: es el default. */
    fun observar(): Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_TEMA] ?: SISTEMA }
}

private val KEY_TEMA = stringPreferencesKey("tema_app")
