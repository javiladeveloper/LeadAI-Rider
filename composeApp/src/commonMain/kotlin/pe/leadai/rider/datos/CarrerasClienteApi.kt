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
        /** Delivery donde el rider tiene que pedir en el local y esperar. */
        esperaEnLocal: Boolean = false,
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
                if (esperaEnLocal) put("esperaEnLocal", true)
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

    /**
     * Todo lo de la pantalla en UNA llamada: carrera, ofertas y motos cerca.
     *
     * Existe para no pagar tres veces la latencia del servidor —cerca de un
     * segundo cada request— en cada vuelta del polling.
     */
    suspend fun miCarreraCompleta(): Resultado<MiCarreraClienteDto> =
        api.get<MiCarreraClienteDto>("/carreras/mia")

    /** `GET /carreras/historial` → las carreras ya cerradas, de la más nueva a la más vieja. */
    suspend fun historial(): Resultado<List<CarreraClienteDto>> =
        when (val respuesta = api.get<HistorialClienteDto>("/carreras/historial")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.carreras)
            is Resultado.Error -> respuesta
        }

    /**
     * `GET /carreras/direcciones` — direcciones que coinciden con lo escrito.
     *
     * Las coordenadas del cliente van en la consulta para acotar a SU ciudad:
     * hay una "Av. Bolognesi" en casi toda ciudad del Perú, y sin eso la
     * primera sugerencia sería la de Lima.
     */
    suspend fun buscarDirecciones(
        consulta: String,
        lat: Double? = null,
        lng: Double? = null,
    ): Resultado<List<SugerenciaDireccionDto>> {
        val coords = if (lat != null && lng != null) "&lat=$lat&lng=$lng" else ""
        return when (
            val r = api.get<SugerenciasDireccionDto>("/carreras/direcciones?q=${paraUrl(consulta)}$coords")
        ) {
            is Resultado.Ok -> Resultado.Ok(r.valor.sugerencias)
            is Resultado.Error -> r
        }
    }

    /**
     * `GET /mapa/motos-cerca` — cuántas motos hay alrededor de un punto.
     *
     * El MISMO endpoint que alimenta el radar: si el mapa dibuja tres motos y
     * el texto dice otra cosa, el cliente deja de creerle a los dos.
     */
    suspend fun motosCerca(lat: Double, lng: Double): Resultado<Int> =
        when (val r = api.get<MotosCercaDto>("/mapa/motos-cerca?lat=$lat&lng=$lng")) {
            is Resultado.Ok -> Resultado.Ok(r.valor.motos.size)
            is Resultado.Error -> r
        }

    /** `GET /carreras/direccion-en` — qué dirección hay en un punto del mapa. */
    suspend fun direccionEn(lat: Double, lng: Double): Resultado<String?> =
        when (val r = api.get<DireccionEnPuntoDto>("/carreras/direccion-en?lat=$lat&lng=$lng")) {
            is Resultado.Ok -> Resultado.Ok(r.valor.direccion)
            is Resultado.Error -> r
        }

    /** `GET /carreras/:id/ofertas` — quiénes quieren llevarla y por cuánto. */
    suspend fun ofertas(carreraId: String): Resultado<OfertasResponseDto> =
        api.get<OfertasResponseDto>("/carreras/$carreraId/ofertas")

    /**
     * `POST /carreras/:id/elegir` — el cliente elige a un rider.
     *
     * 409 si otra cosa pasó mientras miraba (el rider retiró su oferta, o él
     * mismo eligió desde otro lado).
     */
    suspend fun elegir(carreraId: String, ofertaId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/elegir",
            body = buildJsonObject { put("ofertaId", ofertaId) },
            requiereSesion = true,
        )

    /**
     * `POST /carreras/:id/monto` — subir lo que ofrece porque nadie le ofertó.
     *
     * Sin esto solo podría cancelar y volver a pedir, perdiendo las ofertas
     * que ya tenía.
     */
    suspend fun cambiarMonto(carreraId: String, montoCentavos: Long): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/monto",
            body = buildJsonObject { put("montoCentavos", montoCentavos) },
            requiereSesion = true,
        )

    /** `POST /carreras/:id/calificar` — 1 a 5 estrellas al terminar. */
    suspend fun calificar(
        carreraId: String,
        estrellas: Int,
        comentario: String? = null,
    ): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/calificar",
            body = buildJsonObject {
                put("estrellas", estrellas)
                if (!comentario.isNullOrBlank()) put("comentario", comentario)
            },
            requiereSesion = true,
        )

    /** `POST /carreras/:id/cancelar` — 409 si un rider ya la tomó (está yendo). */
    /**
     * Cancela la carrera, contando POR QUÉ.
     *
     * El motivo no es un formalismo: sin él, "17 canceladas de 28" no dice
     * nada —no distingue a alguien que se arrepintió de un rider que nunca
     * llegó, y son problemas distintos—.
     *
     * Va vacío si el cliente cierra el diálogo sin elegir: la cancelación
     * nunca se bloquea por esto.
     */
    suspend fun cancelar(
        carreraId: String,
        motivo: String? = null,
    ): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/cancelar",
            body = buildJsonObject { if (motivo != null) put("motivo", motivo) },
            requiereSesion = true,
        )

    // ── Chat con el motorizado ──────────────────────────────────────────

    /**
     * `GET /carreras/:id/chat` → la conversación.
     *
     * Al traerla se marcan como leídos los mensajes del otro: si abriste el
     * chat, ya los viste. Una llamada aparte solo para eso sería otro segundo
     * de espera.
     */
    suspend fun chat(carreraId: String): Resultado<ChatCarreraDto> =
        api.get<ChatCarreraDto>("/carreras/$carreraId/chat")

    /** `POST /carreras/:id/chat` → manda un mensaje. */
    suspend fun enviarMensaje(
        carreraId: String,
        texto: String,
    ): Resultado<EnviarMensajeResponseDto> =
        api.post<JsonObject, EnviarMensajeResponseDto>(
            path = "/carreras/$carreraId/chat",
            body = buildJsonObject { put("texto", texto) },
            requiereSesion = true,
        )
}
