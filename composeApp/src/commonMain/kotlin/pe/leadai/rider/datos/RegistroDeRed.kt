package pe.leadai.rider.datos

/**
 * Deja constancia de por qué falló una llamada.
 *
 * La app le muestra al usuario "Sin conexión", que está bien —no le sirve un
 * stack trace— pero eso tapa cosas muy distintas: un timeout, un DNS que no
 * resuelve, un TLS rechazado, un certificado vencido.
 *
 * Sin el detalle en el log hay que adivinar cuál fue. Pasó buscando por qué el
 * buscador de direcciones no cargaba: la excepción se tragaba en silencio y no
 * quedaba rastro en ningún lado, así que no se podía distinguir "el servidor
 * no responde" de "la app ni siquiera llamó".
 *
 * Se lee con `adb logcat -s LeadAIRed`.
 */
expect fun registrarFalloDeRed(e: Exception)
