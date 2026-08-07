package pe.leadai.rider.datos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Alta/lectura del perfil de motorizado (Fase B.5, Task T3-doble — visión
 * completa del pool en ARQUITECTURA.md Fase E). El perfil es a nivel
 * PLATAFORMA (`usuarioId` único), nunca una membresía a un tenant: por eso
 * los 3 endpoints se llaman autenticados solo con Bearer de usuario, SIN
 * `X-Tenant-Id` (mismo patrón que `POST /empresas` antes de elegir tenant).
 */
class MotorizadosApi(private val api: ApiCliente) {

    /** `GET /motorizados/distritos` → catálogo de distritos para el dropdown del mini-form. */
    suspend fun distritos(): Resultado<List<String>> =
        when (val respuesta = api.get<DistritosMotorizadosDto>("/motorizados/distritos")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.distritos)
            is Resultado.Error -> respuesta
        }

    /**
     * `GET /motorizados/mi-perfil` → `{"perfil": null | {...}}`. `null`
     * significa "el usuario todavía no se dio de alta como motorizado" — no
     * es un error, así que se propaga como `Resultado.Ok(null)`.
     */
    suspend fun miPerfil(): Resultado<PerfilMotorizadoDto?> =
        when (val respuesta = api.get<MiPerfilMotorizadoDto>("/motorizados/mi-perfil")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.perfil)
            is Resultado.Error -> respuesta
        }

    /**
     * `POST /motorizados/mi-perfil` — upsert (alta o edición del
     * distrito/teléfono/placa). Body armado con `buildJsonObject` (mismo
     * patrón que el PATCH parcial de `ConfigApi.guardar`) porque el schema
     * del backend (`crearSchema`, `motorizados.ts`) marca `telefono`/`placa`
     * como `.optional()` — Zod acepta el campo AUSENTE, pero rechaza un
     * `null` explícito, y `ApiCliente.json` (con `encodeDefaults = true`)
     * serializa los nullable de un DTO como `"placa":null`.
     */
    suspend fun guardarMiPerfil(
        distrito: String,
        telefono: String?,
        placa: String?,
        dni: String? = null,
        /** `moto` | `auto` — el backend sugiere el monto según el vehículo. */
        tipoVehiculo: String = "moto",
    ): Resultado<PerfilMotorizadoDto> =
        when (
            val respuesta = api.post<JsonObject, MiPerfilMotorizadoDto>(
                path = "/motorizados/mi-perfil",
                body = buildJsonObject {
                    put("distrito", distrito)
                    if (!telefono.isNullOrBlank()) put("telefono", telefono)
                    if (!placa.isNullOrBlank()) put("placa", placa)
                    if (!dni.isNullOrBlank()) put("dni", dni)
                    put("tipoVehiculo", tipoVehiculo)
                },
                requiereSesion = true,
            )
        ) {
            is Resultado.Ok -> respuesta.valor.perfil?.let { Resultado.Ok(it) }
                ?: Resultado.Error(MENSAJE_ERROR_GENERICO)
            is Resultado.Error -> respuesta
        }

    /**
     * `POST /motorizados/validar-dni {dni}` (fila 16) — el backend consulta
     * MAXFIND server-to-server y responde `{encontrado, nombreCompleto}`.
     * NUNCA bloquea: cualquier fallo del proveedor llega como
     * `encontrado: false` y el alta sigue (validación ligera, decisión 8).
     */
    suspend fun validarDni(dni: String): Resultado<ValidarDniResponseDto> =
        api.post<JsonObject, ValidarDniResponseDto>(
            path = "/motorizados/validar-dni",
            body = buildJsonObject { put("dni", dni) },
            requiereSesion = true,
        )

    /** `GET /motorizados/carreras` (POOL v0): disponibles de la zona + la carrera en curso del rider. */
    suspend fun carreras(): Resultado<CarrerasResponseDto> =
        api.get("/motorizados/carreras")

    /** `POST /motorizados/carreras/:id/aceptar` — el PRIMERO gana; 409 si otro rider la tomó. */
    suspend fun aceptarCarrera(pedidoId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/carreras/$pedidoId/aceptar",
            body = buildJsonObject { },
            requiereSesion = true,
        )

    /**
     * `POST /motorizados/posicion {lat, lng}` (tracking nivel 2) — la última
     * posición del rider, que alimenta el mapa público `/track/:pedidoId`
     * del cliente. La manda el polling de la sala mientras hay carrera.
     */
    suspend fun reportarPosicion(lat: Double, lng: Double): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/posicion",
            body = buildJsonObject {
                put("lat", lat)
                put("lng", lng)
            },
            requiereSesion = true,
        )

    /** `GET /motorizados/historial` — entregas del rider con km reales + resumen de hoy. */
    suspend fun historial(): Resultado<HistorialRiderResponseDto> =
        api.get("/motorizados/historial")

    /**
     * `POST /motorizados/dispositivo {token, plataforma}` — registro push del
     * RIDER (sin tenant): así le suena el teléfono cuando aparece carrera en
     * su zona. Gemelo de `/dispositivos-push` (que exige empresa).
     */
    suspend fun registrarDispositivo(token: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/dispositivo",
            body = buildJsonObject {
                put("token", token)
                put("plataforma", "android")
            },
            requiereSesion = true,
        )

    /**
     * `POST /motorizados/carreras/:id/recogido` — cierra el tramo 1 (el rider
     * ya recogió en el local) y abre el 2 (rumbo al cliente). El mapa cambia
     * de destino y el cliente pasa de "yendo al local" a "en camino".
     */
    suspend fun recogiCarrera(pedidoId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/carreras/$pedidoId/recogido",
            body = buildJsonObject { },
            requiereSesion = true,
        )

    /** `POST /motorizados/carreras/:id/entregar` — cierra la carrera propia en camino. */
    suspend fun entregarCarrera(pedidoId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/carreras/$pedidoId/entregar",
            body = buildJsonObject { },
            requiereSesion = true,
        )

    /**
     * `POST /motorizados/carreras/:id/cancelar` — suelta una carrera aceptada:
     * vuelve al pool y se reintegra la comisión.
     *
     * Solo ANTES de recoger (el backend responde 409 después). Sin esto, un
     * rider que aceptó por error quedaba atrapado: la app muestra una carrera
     * activa a la vez, así que no podía tomar ninguna otra.
     */
    suspend fun cancelarCarrera(pedidoId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/motorizados/carreras/$pedidoId/cancelar",
            body = buildJsonObject { },
            requiereSesion = true,
        )
}
