package pe.leadai.rider.datos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Monedero PREPAGO del rider (2026-07-24): compra paquetes de saldo (S/20,
 * S/50, S/100) y cada carrera que acepta le descuenta S/1. Sin saldo el
 * backend responde 402 al aceptar.
 */
class MonederoApi(private val api: ApiCliente) {

    /** `GET /motorizados/monedero` — saldo, carreras que le alcanzan, movimientos y paquetes. */
    suspend fun leer(): Resultado<MonederoDto> = api.get("/motorizados/monedero")

    /**
     * `POST /motorizados/monedero/comprar` — COMPRA un paquete con tarjeta.
     * [tokenTarjeta] es el `tkn_…` que devuelve Culqi tras tokenizar la
     * tarjeta; el precio lo pone el backend (nunca viaja desde acá).
     */
    suspend fun comprar(paqueteId: String, tokenTarjeta: String): Resultado<RecargaResponseDto> =
        api.post<JsonObject, RecargaResponseDto>(
            path = "/motorizados/monedero/comprar",
            body = buildJsonObject {
                put("paqueteId", paqueteId)
                put("tokenTarjeta", tokenTarjeta)
            },
            requiereSesion = true,
        )

    /**
     * `POST /motorizados/monedero/recargar` — acredita un paquete YA pagado
     * por fuera (transferencia conciliada). [referenciaPago] evita acreditar
     * dos veces la misma compra.
     */
    suspend fun recargar(paqueteId: String, referenciaPago: String): Resultado<RecargaResponseDto> =
        api.post<JsonObject, RecargaResponseDto>(
            path = "/motorizados/monedero/recargar",
            body = buildJsonObject {
                put("paqueteId", paqueteId)
                put("referenciaPago", referenciaPago)
            },
            requiereSesion = true,
        )
}
