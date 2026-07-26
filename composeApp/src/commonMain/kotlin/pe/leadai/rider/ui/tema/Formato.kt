package pe.leadai.rider.ui.tema

/**
 * Helpers de formato puros (testeables, sin dependencias de plataforma).
 *
 * OJO multiplataforma: nada de `String.format` (es solo-JVM, no compila en
 * iOS/commonMain). Todo aquí es aritmética entera + padStart.
 */

/** Formatea centavos (Long) como soles con el prefijo "S/", ej. 3800 -> "S/38.00". */
fun centavosASoles(centavos: Long): String {
    val centavosAbs = if (centavos < 0) -centavos else centavos
    val signo = if (centavos < 0) "-" else ""
    val soles = centavosAbs / 100
    val resto = (centavosAbs % 100).toString().padStart(2, '0')
    return "$signo" + "S/" + "$soles.$resto"
}

/**
 * Tiempo relativo en español para timestamps recientes (feed de pedidos/leads).
 * <1 min -> "ahora"; <60 min -> "hace N min"; en adelante -> "hace N h".
 */
fun haceMinutos(desdeEpochMs: Long, ahoraEpochMs: Long): String {
    val diffMs = ahoraEpochMs - desdeEpochMs
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "ahora"
        diffMin < 60 -> "hace $diffMin min"
        else -> "hace ${diffMin / 60} h"
    }
}

/** Días acumulados desde 1970-01-01 hasta el 1 de enero de [anio] (calendario gregoriano). */
private fun diasHastaInicioDeAnio(anio: Int): Long {
    val y = anio - 1970
    // Cuenta de años bisiestos en [1970, anio) sin depender de librerías de fecha.
    fun bisiestosHasta(n: Int): Int = (n / 4) - (n / 100) + (n / 400)
    val bisiestos = bisiestosHasta(anio - 1) - bisiestosHasta(1969)
    return y * 365L + bisiestos
}

private fun esBisiesto(anio: Int): Boolean =
    (anio % 4 == 0 && anio % 100 != 0) || anio % 400 == 0

/**
 * Convierte una fecha `"YYYY-MM-DD"` a "días desde 1970-01-01" (epoch day),
 * aritmética pura de calendario — mismo motor que [epochMsDesdeIso] pero sin
 * componente de hora. Usado por `ui/reservas` para sumar/restar días (chips
 * de fecha, cruces de mes/año) sin depender de `kotlinx-datetime`.
 */
fun epochDiaDesdeFechaIso(fechaIso: String): Long {
    val anio = fechaIso.substring(0, 4).toInt()
    val mes = fechaIso.substring(5, 7).toInt()
    val dia = fechaIso.substring(8, 10).toInt()

    var dias = diasHastaInicioDeAnio(anio)
    for (m in 1 until mes) {
        dias += DIAS_POR_MES[m - 1]
        if (m == 2 && esBisiesto(anio)) dias += 1
    }
    return dias + (dia - 1)
}

/** Inverso de [epochDiaDesdeFechaIso]: "días desde 1970-01-01" -> `"YYYY-MM-DD"`. */
fun fechaIsoDesdeEpochDia(epochDia: Long): String {
    // Búsqueda lineal de año: parte de una estimación (365.25 días/año) y
    // corrige +-1 año — nunca hace falta más de un par de iteraciones porque
    // la estimación con 365.25 es exacta a lo sumo a un año de distancia.
    var anio = 1970 + (epochDia / 365.2425).toInt()
    while (diasHastaInicioDeAnio(anio) > epochDia) anio--
    while (diasHastaInicioDeAnio(anio + 1) <= epochDia) anio++

    var restante = (epochDia - diasHastaInicioDeAnio(anio)).toInt()
    var mes = 1
    while (true) {
        val diasDelMes = DIAS_POR_MES[mes - 1] + if (mes == 2 && esBisiesto(anio)) 1 else 0
        if (restante < diasDelMes) break
        restante -= diasDelMes
        mes++
    }
    val dia = restante + 1

    val mesStr = mes.toString().padStart(2, '0')
    val diaStr = dia.toString().padStart(2, '0')
    return "$anio-$mesStr-$diaStr"
}

private val DIAS_POR_MES = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/**
 * Parsea un timestamp ISO-8601 UTC tal como lo emite el backend
 * (`"2026-07-21T10:00:00.000Z"`) a epoch milliseconds, sin depender de
 * `kotlinx-datetime` ni de `java.time` (no compila en iOS/commonMain sin
 * bindings extra) — aritmética pura de calendario, igual que el resto de este
 * archivo. Asume siempre UTC (`Z`), que es el único formato que envía el
 * backend para `creadoEn`/`actualizadoEn`.
 */
fun epochMsDesdeIso(iso: String): Long {
    // "2026-07-21T10:00:00.000Z" -> fecha "2026-07-21", hora "10:00:00.000"
    val fecha = iso.substring(0, 10)
    val hora = iso.substring(11).removeSuffix("Z")

    val anio = fecha.substring(0, 4).toInt()
    val mes = fecha.substring(5, 7).toInt()
    val dia = fecha.substring(8, 10).toInt()

    val horaPartes = hora.split(":")
    val h = horaPartes[0].toInt()
    val min = horaPartes[1].toInt()
    val segYMs = horaPartes[2].split(".")
    val seg = segYMs[0].toInt()
    val ms = if (segYMs.size > 1) segYMs[1].padEnd(3, '0').take(3).toInt() else 0

    var diasDesdeEpoch = diasHastaInicioDeAnio(anio)
    for (m in 1 until mes) {
        diasDesdeEpoch += DIAS_POR_MES[m - 1]
        if (m == 2 && esBisiesto(anio)) diasDesdeEpoch += 1
    }
    diasDesdeEpoch += (dia - 1)

    val msDelDia = ((h * 60L + min) * 60L + seg) * 1000L + ms
    return diasDesdeEpoch * 86_400_000L + msDelDia
}
