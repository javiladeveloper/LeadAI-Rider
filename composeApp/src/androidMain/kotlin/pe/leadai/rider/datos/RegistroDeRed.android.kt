package pe.leadai.rider.datos

actual fun registrarFalloDeRed(e: Exception) {
    // Envuelto porque `android.util.Log` NO existe en los tests unitarios de
    // JVM: ahí lanza "not mocked" y tumbaba tests que solo querían comprobar
    // que un fallo de red devuelve "Sin conexión".
    //
    // Un log de diagnóstico no puede cambiar lo que hace la app: si no se
    // puede escribir, se sigue igual.
    runCatching {
        android.util.Log.w("LeadAIRed", "falló la llamada: ${e::class.simpleName}: ${e.message}")
    }
}
