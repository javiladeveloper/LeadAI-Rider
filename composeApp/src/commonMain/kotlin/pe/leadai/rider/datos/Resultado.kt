package pe.leadai.rider.datos

/**
 * Resultado de cualquier llamada a la API. Nunca se propagan excepciones a la
 * UI: todo error (de red o del backend) se normaliza a [Resultado.Error] con
 * un mensaje en español listo para mostrar.
 */
sealed class Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>()

    /**
     * [codigo] es el status HTTP cuando el backend respondió con error
     * (`null` si ni siquiera hubo respuesta: sin conexión, JSON roto). La UI
     * casi nunca lo necesita — se agregó para el único caso donde un código
     * cambia el flujo: `GET /perfil` con 404 significa "negocio sin playbook
     * todavía" (no un error real, ver `CartaViewModel.cargar`).
     */
    data class Error(val mensaje: String, val codigo: Int? = null) : Resultado<Nothing>()
}
