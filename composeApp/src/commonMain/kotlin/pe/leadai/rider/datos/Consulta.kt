package pe.leadai.rider.datos

/**
 * Deja un texto listo para viajar dentro de una URL.
 *
 * Un espacio no puede ir crudo en una URL: la request ni siquiera sale. Por eso
 * el buscador de direcciones "se quedaba buscando y no cargaba nada" — con una
 * sola palabra ("barlovento") andaba, pero apenas se escribía la segunda
 * ("jose olaya", "av bolognesi") se rompía. Como casi toda dirección real tiene
 * un espacio, fallaba casi siempre.
 *
 * También escapa acentos y la ñ, que en Tacna aparecen todo el tiempo
 * ("Cañaveral", "Bolognesi Nº 200").
 */
internal fun paraUrl(texto: String): String {
    val seguros = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    val salida = StringBuilder()
    for (byte in texto.encodeToByteArray()) {
        val caracter = byte.toInt().toChar()
        if (caracter in seguros) {
            salida.append(caracter)
        } else {
            // El & sin escapar cortaba la consulta y el resto se leía como otro
            // parámetro; %XX en mayúsculas, como manda el RFC 3986.
            val entero = byte.toInt() and 0xFF
            salida.append('%').append(entero.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return salida.toString()
}
