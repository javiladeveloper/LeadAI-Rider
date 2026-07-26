package pe.leadai.rider.ui.tema

/**
 * Epoch milliseconds del reloj del sistema. Expect/actual porque el proyecto
 * no trae `kotlinx-datetime` como dependencia (ver decisión en
 * `epochMsDesdeIso`, Formato.kt) — mismo patrón que `crearDataStore()`
 * (`datos/crearDataStore.kt`) para necesidades puntuales que sí dependen de
 * la plataforma. Usado por `CocinaPantalla` para calcular urgencia/"hace X
 * min" en un momento dado (NUNCA dentro de una función pura testeable, que
 * siempre recibe `ahoraMs` como parámetro).
 */
expect fun epochMsAhora(): Long
