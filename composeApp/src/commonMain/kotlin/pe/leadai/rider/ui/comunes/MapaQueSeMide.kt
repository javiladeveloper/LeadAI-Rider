package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Un mapa que SE MIDE SOLO y le pasa su alto real a la página.
 *
 * El WebView de Android reporta un viewport que no coincide con su tamaño real
 * (medido: body=0, ventana=160 dentro de un contenedor de 549), así que la
 * página web no puede deducir cuánto mide: `100vh` y `height:100%` le dan un
 * número equivocado y el mapa termina dibujado en una franja, con el resto del
 * espacio gris.
 *
 * La única solución es que la app —que sí sabe cuánto mide— se lo diga. Eso se
 * escribió a mano en cuatro pantallas distintas, y las consecuencias fueron
 * siempre las mismas:
 *
 * - El mapa del rider quedó cuadrado porque nadie le pasó el alto.
 * - El radar tenía un `420` fijo por defecto: dibujaba 420dp de mapa aunque el
 *   espacio fuera mayor, y como los círculos se dimensionan contra ese alto,
 *   el pulso caía fuera de la parte visible y el radar no se veía.
 *
 * Acá eso pasa una sola vez y no se puede olvidar: quien use este componente
 * obtiene el comportamiento correcto sin saber que el problema existe.
 */
@Composable
fun MapaQueSeMide(
    /**
     * Arma la URL a partir del alto ya medido, en dp.
     *
     * Es una función y no una cadena para que sea IMPOSIBLE construir la URL
     * sin el alto: el parámetro llega como argumento.
     */
    url: (altoDp: Int) -> String,
    modifier: Modifier = Modifier,
    /** Esquinas redondeadas. El mapa a sangre completa no las lleva. */
    redondeado: Boolean = true,
) {
    var altoDp by remember { mutableStateOf(0) }
    val densidad = LocalDensity.current

    // Fondo carbón detrás del WebView: si el mapa no carga se ve oscuro y no
    // blanco, que es la señal de que ALGO está ahí. Un hueco del color del
    // fondo de la app es indistinguible de "no se dibujó nada".
    Box(
        modifier = modifier
            .then(if (redondeado) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
            .background(ColoresJala.actuales.marcaCarbon)
            .onSizeChanged { medido ->
                val nuevo = with(densidad) { medido.height.toDp() }.value.toInt()
                // Solo el PRIMER alto, o un cambio grande de verdad.
                //
                // El alto viaja en la URL, y si la URL cambia el WebView
                // RECARGA la página: el radar volvía a empezar en 500 m cada
                // vez. Como el contenedor se remide cuando llega una oferta
                // —se apilan sobre el radar y le quitan alto—, el radar se
                // reiniciaba solo y por eso se veía estático.
                //
                // La página ya reajusta su alto por JS (`ajustarAlto` corre a
                // los 150 y 700 ms), así que unos dp de diferencia no
                // justifican perder el estado. 48 dp: menos que eso es el
                // acomodo normal del layout.
                if (altoDp == 0 || kotlin.math.abs(nuevo - altoDp) > 48) {
                    altoDp = nuevo
                }
            },
    ) {
        // Hasta saber cuánto mide no se carga: con alto 0 la página se dibuja
        // contra un tamaño equivocado y habría que recargarla igual.
        if (altoDp > 0) {
            MapaEmbebido(url = url(altoDp), modifier = Modifier.fillMaxSize())
        }
    }
}
