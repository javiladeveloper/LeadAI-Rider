package pe.leadai.rider.datos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * El lado CLIENTE: pedir una moto. Encomienda ("tráeme un chifa del Salón
 * Cantón" o "llevá esta caja") o pasajero.
 *
 * Los pedidos de restaurante NO nacen acá — esos los crea el negocio en su
 * propia app y le llegan al rider por el pool.
 */
class CarrerasClienteApi(private val api: ApiCliente) {

    /**
     * `POST /carreras/sugerir` — cuánto conviene ofrecer, SIN crear nada. Es
     * una sugerencia editable, no una tarifa: el precio final lo acuerdan el
     * cliente y el rider.
     */
    suspend fun sugerir(
        tipo: String,
        origenTexto: String,
        origenLat: Double? = null,
        origenLng: Double? = null,
        destinoTexto: String,
        destinoLat: Double? = null,
        destinoLng: Double? = null,
    ): Resultado<SugerenciaDto> =
        api.post<JsonObject, SugerenciaDto>(
            path = "/carreras/sugerir",
            body = buildJsonObject {
                put("tipo", tipo)
                put("origenTexto", origenTexto)
                origenLat?.let { put("origenLat", it) }
                origenLng?.let { put("origenLng", it) }
                put("destinoTexto", destinoTexto)
                destinoLat?.let { put("destinoLat", it) }
                destinoLng?.let { put("destinoLng", it) }
            },
            requiereSesion = true,
        )

    /**
     * `POST /carreras` — pedir la moto. Devuelve 409 si el cliente ya tiene
     * una carrera activa: una a la vez, para no llenar el pool de pedidos
     * que nadie va a atender.
     *
     * `montoCompraEstimadoCentavos` viaja SEPARADO del flete: es plata que el
     * rider adelanta y el cliente le devuelve, no parte del precio.
     */
    suspend fun pedir(
        tipo: String,
        origenTexto: String,
        origenLat: Double?,
        origenLng: Double?,
        destinoTexto: String,
        destinoLat: Double?,
        destinoLng: Double?,
        montoOfrecidoCentavos: Long?,
        montoCompraEstimadoCentavos: Long?,
        notas: String?,
        contacto: String?,
    ): Resultado<CarreraCreadaDto> =
        api.post<JsonObject, CarreraCreadaDto>(
            path = "/carreras",
            body = buildJsonObject {
                put("tipo", tipo)
                put("origenTexto", origenTexto)
                origenLat?.let { put("origenLat", it) }
                origenLng?.let { put("origenLng", it) }
                put("destinoTexto", destinoTexto)
                destinoLat?.let { put("destinoLat", it) }
                destinoLng?.let { put("destinoLng", it) }
                montoOfrecidoCentavos?.let { put("montoOfrecidoCentavos", it) }
                montoCompraEstimadoCentavos?.let { put("montoCompraEstimadoCentavos", it) }
                if (!notas.isNullOrBlank()) put("notas", notas)
                if (!contacto.isNullOrBlank()) put("contacto", contacto)
            },
            requiereSesion = true,
        )

    /**
     * `GET /carreras/mia` → la carrera activa, o `null` si no tiene ninguna.
     * `null` NO es un error: es el estado normal de quien no pidió nada.
     */
    suspend fun miCarrera(): Resultado<CarreraClienteDto?> =
        when (val respuesta = api.get<MiCarreraClienteDto>("/carreras/mia")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.carrera)
            is Resultado.Error -> respuesta
        }

    /** `GET /carreras/historial` → las carreras ya cerradas, de la más nueva a la más vieja. */
    suspend fun historial(): Resultado<List<CarreraClienteDto>> =
        when (val respuesta = api.get<HistorialClienteDto>("/carreras/historial")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.carreras)
            is Resultado.Error -> respuesta
        }

    /** `POST /carreras/:id/cancelar` — 409 si un rider ya la tomó (está yendo). */
    suspend fun cancelar(carreraId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/cancelar",
            body = buildJsonObject { },
            requiereSesion = true,
        )
}
