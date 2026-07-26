package pe.leadai.rider.push

/**
 * Stub de Fase A: iOS push (APNs/FCM) queda para Fase D, cuando el proyecto
 * compile en una Mac (ver `docs/ARQUITECTURA.md`, tabla de fases). Devolver
 * `null` hace que `RegistroPushRepositorio.registrar()/desregistrar()` sean
 * no-op en iOS sin romper el grafo multiplataforma — mismo patrón que otros
 * `expect/actual` de esta app cuando una plataforma no tiene la
 * funcionalidad todavía.
 */
actual suspend fun tokenPushActual(): String? = null
