package pe.leadai.rider.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.leadai.rider.datos.PerfilMotorizadoDto
import pe.leadai.rider.datos.TemaRepositorio
import pe.leadai.rider.ui.carreras.componentes.colorDeEstadoRider
import pe.leadai.rider.ui.comunes.BadgeEstado
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.comunes.EncabezadoMarca
import pe.leadai.rider.ui.comunes.PieDeVersion
import pe.leadai.rider.ui.comunes.SelectorDeTema
import pe.leadai.rider.ui.comunes.TituloDeSeccion
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.Formas

/**
 * El perfil del rider: quién es, su vehículo, y los accesos a configuración.
 *
 * Antes esto vivía al final del feed, después de scrollear todo el historial.
 * Con su propia pestaña está a un toque.
 *
 * El diseño: un encabezado con el degradado de la marca y, debajo, secciones
 * con título. Antes era una pila de tarjetas blancas sobre gris —correcta pero
 * sin nada de la marca, y sin jerarquía: "Cerrar sesión" pesaba lo mismo que
 * "Editar mi perfil"—.
 */
@Composable
fun PerfilPantalla(
    nombreUsuario: String,
    emailUsuario: String,
    perfil: PerfilMotorizadoDto,
    onEditarPerfil: () -> Unit,
    onVerPermisos: () -> Unit,
    onCambiarModo: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        // El encabezado va de borde a borde: el padding lo pone cada sección.
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // La identidad sobre el degradado de marca: es lo primero que se ve, y
        // lo que hace que la pantalla se reconozca como Light Drive antes de
        // leer una palabra.
        val (textoEstado, colorEstado) = colorDeEstadoRider(perfil.estado)
        EncabezadoMarca(
            titulo = nombreUsuario.ifBlank { "Motorizado" },
            inicial = nombreUsuario.take(1).uppercase().ifBlank { "M" },
            subtitulo = emailUsuario.takeIf { it.isNotBlank() },
            insignia = { BadgeEstado(texto = textoEstado, color = colorEstado) },
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TituloDeSeccion("Mis datos")
            CardJala(modifier = Modifier.fillMaxWidth()) {
                DatoDePerfil("Distrito", perfil.distrito)
                if (!perfil.dni.isNullOrBlank()) DatoDePerfil("DNI", perfil.dni)
                if (!perfil.telefono.isNullOrBlank()) DatoDePerfil("Teléfono", perfil.telefono)
                if (!perfil.placa.isNullOrBlank()) DatoDePerfil("Placa", perfil.placa)
                DatoDePerfil(
                    "Vehículo",
                    if (perfil.tipoVehiculo == "auto") "🚗 Auto" else "🛵 Moto",
                )
            }

            TituloDeSeccion("Mi cuenta")
            // Las tres juntas en una card y separadas por una línea suave: son
            // el mismo tipo de acción. Con una card por opción, la pantalla se
            // volvía una escalera de rectángulos sueltos.
            CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 0) {
                OpcionDePerfil("✏️", "Editar mi perfil", colores.marcaAmarillo, onEditarPerfil)
                SeparadorSuave()
                OpcionDePerfil("⚙️", "Permisos de la app", colores.exito, onVerPermisos)
                SeparadorSuave()
                OpcionDePerfil("🛵", "Cambiar a modo cliente", colores.espera, onCambiarModo)
            }

            TituloDeSeccion("Apariencia")
            SelectorDeTema()

            // Cerrar sesión aparte y en rojo: es destructivo, no debe estar
            // junto a las opciones normales donde se toca por error.
            CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 0) {
                OpcionDePerfil(
                    "🚪",
                    "Cerrar sesión",
                    colores.calor,
                    onCerrarSesion,
                    colorTexto = colores.calor,
                )
            }

            // Al pie: la primera pregunta al reportar algo raro es siempre "¿qué
            // versión tenés?", y deducirlo de Play confunde bugs ya arreglados
            // con bugs nuevos.
            PieDeVersion()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DatoDePerfil(etiqueta: String, valor: String) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = colores.tintaSecundaria)
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Una línea entre opciones de la misma card.
 *
 * Al 40% y con sangría del ancho del ícono: separa sin cortar. Una línea de
 * borde a borde y a color pleno divide la card en cajas y se lee como si cada
 * fila fuera otra cosa.
 */
@Composable
private fun SeparadorSuave() {
    Box(
        modifier = Modifier
            .padding(start = 64.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/**
 * Una fila de opción, con el ícono dentro de una pastilla de color.
 *
 * El emoji suelto sobre blanco se perdía y las tres filas se leían como un
 * bloque gris. En su pastilla cada opción tiene identidad y se encuentra por
 * color antes de leer el texto.
 */
@Composable
private fun OpcionDePerfil(
    icono: String,
    texto: String,
    colorIcono: Color,
    onClick: () -> Unit,
    colorTexto: Color? = null,
) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                // Al 15%: el color se insinúa detrás del emoji sin taparlo ni
                // competir con el texto de al lado.
                .background(colorIcono.copy(alpha = 0.15f), Formas.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(icono, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(14.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colorTexto ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = colores.tintaSecundaria)
    }
}
