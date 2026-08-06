package pe.leadai.rider.datos

import kotlinx.serialization.Serializable

/** `GET /app/version` → la última publicada en la tienda. */
@Serializable
data class VersionPublicadaDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    /** `true` = la app no deja posponer (una versión vieja dejó de servir). */
    val obligatoria: Boolean = false,
    val notas: String = "",
    val url: String = "",
)

/** Lo que la UI necesita saber para decidir si molesta al usuario. */
data class ChequeoVersion(
    val hayActualizacion: Boolean,
    val obligatoria: Boolean,
    val versionName: String,
    val notas: String,
    val urlTienda: String,
)

/**
 * Pregunta si hay una versión más nueva que ESTA. El CI escribe la última al
 * publicar en Play, así que el aviso sale solo.
 *
 * Endpoint público: se consulta al arrancar, antes de que haya sesión.
 * Cualquier fallo devuelve `null` — quedarse sin avisar es mejor que molestar
 * con un diálogo por un problema de red.
 */
class VersionApi(private val api: ApiCliente) {

    suspend fun chequear(): ChequeoVersion? {
        val resultado = api.get<VersionPublicadaDto>(
            path = "/app/version",
            params = mapOf("plataforma" to VersionApp.plataforma),
        )
        val publicada = (resultado as? Resultado.Ok)?.valor ?: return null
        // Sin URL no hay a dónde mandar al usuario: mejor no avisar.
        if (publicada.url.isBlank()) return null
        return ChequeoVersion(
            hayActualizacion = publicada.versionCode > VersionApp.codigo,
            obligatoria = publicada.obligatoria,
            versionName = publicada.versionName,
            notas = publicada.notas,
            urlTienda = publicada.url,
        )
    }
}
