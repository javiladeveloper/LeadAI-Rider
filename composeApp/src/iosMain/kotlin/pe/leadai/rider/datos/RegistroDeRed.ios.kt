package pe.leadai.rider.datos

actual fun registrarFalloDeRed(e: Exception) {
    println("LeadAIRed: falló la llamada: ${e::class.simpleName}: ${e.message}")
}
