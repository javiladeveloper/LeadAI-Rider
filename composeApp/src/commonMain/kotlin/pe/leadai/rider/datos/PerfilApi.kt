package pe.leadai.rider.datos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * El perfil de la PERSONA (cliente o rider) y su verificación de identidad.
 *
 * Separado de `MotorizadosApi` a propósito: esto lo usa cualquiera, hasta
 * quien nunca va a manejar. `/mi-perfil` y no `/perfil` porque ese path ya lo
 * ocupa el playbook de la IA del negocio.
 */
class PerfilApi(private val api: ApiCliente) {

    suspend fun miPerfil(): Resultado<MiPerfilDto> =
        api.get<MiPerfilDto>("/mi-perfil")

    /**
     * Guarda los datos editables. El DNI y el estado de verificación NO se
     * mandan: los fija la verificación, y dejarlos editar invalidaría una
     * aprobación ya dada.
     *
     * Solo viaja lo que se pasa: mandar `null` en un campo lo dejaría en
     * blanco sin querer al guardar solo el teléfono.
     */
    suspend fun guardar(
        nombre: String? = null,
        telefono: String? = null,
        direccionHabitual: String? = null,
        direccionLat: Double? = null,
        direccionLng: Double? = null,
    ): Resultado<GuardarPerfilResponseDto> =
        api.put<JsonObject, GuardarPerfilResponseDto>(
            path = "/mi-perfil",
            body = buildJsonObject {
                nombre?.let { put("nombre", it) }
                telefono?.let { put("telefono", it) }
                direccionHabitual?.let { put("direccionHabitual", it) }
                direccionLat?.let { put("direccionLat", it) }
                direccionLng?.let { put("direccionLng", it) }
            },
        )

    /**
     * Sube la foto de un documento. Queda pendiente hasta que un humano la
     * revise.
     *
     * En base64 y no multipart: mandar un JSON es lo mismo en Android y en
     * iOS, sin depender del plugin de multipart del backend.
     */
    suspend fun subirDocumento(
        tipo: String,
        contenidoBase64: String,
        mime: String = "image/jpeg",
    ): Resultado<SubirDocumentoResponseDto> =
        api.post<JsonObject, SubirDocumentoResponseDto>(
            path = "/verificacion/documentos",
            body = buildJsonObject {
                put("tipo", tipo)
                put("contenidoBase64", contenidoBase64)
                put("mime", mime)
            },
            requiereSesion = true,
        )
}
