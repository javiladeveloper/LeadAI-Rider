package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sesión activa del usuario logueado. Se persiste como JSON string en
 * DataStore (una sola key) porque es un blob pequeño que siempre se
 * lee/escribe entero — no amerita el modelo de preferencias sueltas.
 *
 * [empresas] y [tenantIdActivo] vienen del contrato de `/auth/login`, que es
 * el MISMO para las dos apps: un rider puede además ser dueño de un
 * restaurante. Acá se guardan pero no se usan (el rider es un rol de
 * plataforma, sin tenant); quien los aprovecha es la app de negocios.
 */
@Serializable
data class SesionGuardada(
    val token: String,
    val usuarioNombre: String,
    val usuarioEmail: String,
    val empresas: List<EmpresaResumen> = emptyList(),
    val tenantIdActivo: String? = null,
)

private val KEY_SESION = stringPreferencesKey("sesion_json")

class SesionRepositorio(private val dataStore: DataStore<Preferences>) {

    suspend fun guardar(sesion: SesionGuardada) {
        dataStore.edit { prefs ->
            prefs[KEY_SESION] = Json.encodeToString(SesionGuardada.serializer(), sesion)
        }
    }

    fun observar(): Flow<SesionGuardada?> =
        dataStore.data.map { prefs ->
            prefs[KEY_SESION]?.let { json ->
                runCatching { Json.decodeFromString(SesionGuardada.serializer(), json) }.getOrNull()
            }
        }

    suspend fun cerrar() {
        dataStore.edit { prefs -> prefs.remove(KEY_SESION) }
    }
}
