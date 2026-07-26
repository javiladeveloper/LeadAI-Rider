package pe.leadai.rider.ui.login

/**
 * Stub de Fase A/B: Google Sign-In en iOS (Google Sign-In SDK para
 * Apple/`ASAuthorizationController`) queda para Fase D, cuando el proyecto
 * compile en una Mac (ver `docs/ARQUITECTURA.md`, tabla de fases). Devolver
 * `null` hace que `LoginViewModel.entrarConGoogle` muestre el mensaje amable
 * sin romper el grafo multiplataforma — mismo patrón que
 * `push/RegistroPush.ios.kt`.
 */
actual suspend fun obtenerIdTokenGoogle(): String? = null
