package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import pe.leadai.rider.ui.comunes.MapaRadar
import pe.leadai.rider.ui.comunes.PuntoMapa
import pe.leadai.rider.ui.tema.ColoresJala
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import pe.leadai.rider.ui.comunes.MapaQueSeMide
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.MapaEmbebido

/**
 * El radar mientras se busca motorizado: un pulso que crece y va revelando
 * las motos que hay cerca.
 *
 * Dice tres cosas que un texto no dice: que hay gente alrededor, que la
 * búsqueda está EN CURSO, y cuántos hay. No es lo mismo esperar sabiendo que
 * hay diez motos a la vuelta que esperar sin saber si hay alguien en la
 * ciudad — y esos dos casos piden decisiones distintas: aguantar, o subir la
 * oferta.
 *
 * Ocupa todo el espacio disponible a propósito: es lo único que pasa en esa
 * pantalla mientras nadie responde.
 */
@Composable
fun RadarMotos(
    lat: Double?,
    lng: Double?,
    modifier: Modifier = Modifier,
    /** Las motos disponibles alrededor, del mismo endpoint que el contador. */
    motos: List<pe.leadai.rider.datos.MotoCercaDto> = emptyList(),
) {
    if (lat == null || lng == null) return

    // NATIVO, no un WebView.
    //
    // El radar tiene que crecer y la cámara alejarse con él, y eso en la
    // página web nunca quedó fluido: cada cambio de zoom hace que Leaflet pida
    // tiles nuevos y tire los viejos, y ese ciclo se ve como un parpadeo. Se
    // probó bajar la frecuencia, agrandar el buffer de tiles y hasta escalar
    // el mapa por CSS —esto último además rompía las coordenadas de Leaflet y
    // dejaba el mapa corrido—.
    //
    // Acá la cámara la anima Google Maps sobre lo que ya tiene dibujado: no
    // hay nada que recargar, así que no puede titilar. Y de paso desaparece
    // todo el problema del alto, que en el WebView había que mandar por la
    // URL porque reportaba un viewport que no era el suyo.
    MapaRadar(
        centro = PuntoMapa(lat, lng),
        // Las motos de verdad, moviéndose. Ver una moto cerca dice algo que
        // el contador no: que hay ALGUIEN ahí, no un número.
        motos = motos.map { PuntoMapa(it.lat, it.lng) },
        modifier = modifier,
    )
}
