package pe.leadai.rider.datos

/**
 * Dónde vive el backend y cómo se arman sus URLs.
 *
 * La dirección estaba escrita a mano en seis constantes distintas repartidas
 * por las pantallas (`URL_BASE_TRACKING`, `URL_MAPA_RUTA`, `URL_RADAR`…).
 * Cambiar de servidor —o apuntar a uno local para probar— obligaba a buscarlas
 * todas, y alcanzaba con que una quedara atrás para que una pantalla siguiera
 * hablándole al servidor viejo sin que nadie lo notara.
 *
 * Acá está una sola vez.
 */
object Rutas {
    /** La base de la API. Único lugar donde se escribe la dirección. */
    const val BASE = "https://api.leadai-pe.com"

    /**
     * Las páginas de mapa.
     *
     * Todas reciben `alto` en dp porque el WebView reporta un viewport que no
     * coincide con su tamaño real: sin ese dato la página se dibuja contra un
     * número equivocado. Por eso el alto es un parámetro OBLIGATORIO —olvidarlo
     * fue el origen del mapa cuadrado y del radar que no se veía—.
     */
    object Mapas {
        /** El pulso mientras se busca motorizado, con las motos alrededor. */
        fun radar(lat: Double, lng: Double, altoDp: Int, oscuro: Boolean = false): String =
            "$BASE/mapa/radar?lat=$lat&lng=$lng&alto=$altoDp" + siOscuro(oscuro)

        /** El recorrido dibujado entre dos puntos. */
        fun ruta(
            origenLat: Double,
            origenLng: Double,
            destinoLat: Double,
            destinoLng: Double,
            altoDp: Int,
            oscuro: Boolean = false,
        ): String =
            "$BASE/mapa/ruta?oLat=$origenLat&oLng=$origenLng" +
                "&dLat=$destinoLat&dLng=$destinoLng&alto=$altoDp" + siOscuro(oscuro)

        /** Un punto para confirmar una dirección. */
        fun punto(lat: Double, lng: Double, altoDp: Int, oscuro: Boolean = false): String =
            "$BASE/mapa/punto?lat=$lat&lng=$lng&alto=$altoDp" + siOscuro(oscuro)

        /**
         * El seguimiento en vivo de una carrera, embebido en la app.
         *
         * @param esRider quién mira. El MOTORIZADO entra en modo navegación
         * una vez que recogió: la cámara lo sigue de cerca. El CLIENTE ve
         * siempre el viaje completo —a él la calle donde va la moto no le
         * dice nada, quiere saber cuánto falta—.
         *
         * Va explícito y no deducido de `embebido`: los dos entran embebidos,
         * así que el backend no podía distinguirlos y metía al cliente en modo
         * navegación sin razón.
         */
        fun tracking(
            pedidoId: String,
            altoDp: Int,
            esRider: Boolean = false,
            /**
             * Lo que tapa la tarjeta del viaje, en dp.
             *
             * La dibuja la app POR ENCIMA del mapa, así que la página no
             * tiene forma de saber su alto: sin esto centraba la moto en el
             * medio del div y quedaba escondida detrás. En 0 la página usa
             * su estimación vieja.
             */
            altoTarjetaDp: Int = 0,
        ): String =
            "$BASE/track/$pedidoId?embebido=1&alto=$altoDp" +
                (if (esRider) "&modo=rider" else "") +
                (if (altoTarjetaDp > 0) "&tarjeta=$altoTarjetaDp" else "")

        private fun siOscuro(oscuro: Boolean) = if (oscuro) "&oscuro=1" else ""
    }

    /**
     * La pasarela de pago, que se abre en un WebView.
     *
     * Lleva el token en la URL porque el WebView no comparte la sesión de la
     * app: sin él la página pediría login de nuevo.
     */
    fun pagoRider(token: String, paqueteId: String): String =
        "$BASE/pago/rider?token=$token&paquete=$paqueteId"
}
