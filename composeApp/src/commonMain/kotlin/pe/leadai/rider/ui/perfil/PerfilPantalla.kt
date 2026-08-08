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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.leadai.rider.datos.PerfilMotorizadoDto
import pe.leadai.rider.datos.TemaRepositorio
import pe.leadai.rider.ui.carreras.componentes.colorDeEstadoRider
import pe.leadai.rider.ui.comunes.BadgeEstado
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.comunes.PieDeVersion
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * El perfil del rider: quién es, su vehículo, y los accesos a configuración.
 *
 * Antes esto vivía al final del feed, después de scrollear todo el historial.
 * Con su propia pestaña está a un toque.
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "Mi perfil",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Identidad: avatar con la inicial, nombre, correo y estado.
        CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 20) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colores.marcaAmarillo, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        nombreUsuario.take(1).uppercase().ifBlank { "🏍️" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colores.marcaCarbon,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nombreUsuario.ifBlank { "Motorizado" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (emailUsuario.isNotBlank()) {
                        Text(
                            emailUsuario,
                            style = MaterialTheme.typography.labelSmall,
                            color = colores.tintaSecundaria,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val (textoEstado, colorEstado) = colorDeEstadoRider(perfil.estado)
            BadgeEstado(texto = textoEstado, color = colorEstado)
        }

        // Los datos que el rider dio en el alta.
        CardJala(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Mis datos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            DatoDePerfil("Distrito", perfil.distrito)
            if (!perfil.dni.isNullOrBlank()) DatoDePerfil("DNI", perfil.dni)
            if (!perfil.telefono.isNullOrBlank()) DatoDePerfil("Teléfono", perfil.telefono)
            if (!perfil.placa.isNullOrBlank()) DatoDePerfil("Placa", perfil.placa)
            DatoDePerfil(
                "Vehículo",
                if (perfil.tipoVehiculo == "auto") "🚗 Auto" else "🛵 Moto",
            )
        }

        // Accesos. Cada uno con su ícono para distinguirlos de un vistazo.
        CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 0) {
            OpcionDePerfil("✏️", "Editar mi perfil", onEditarPerfil)
            OpcionDePerfil("⚙️", "Permisos de la app", onVerPermisos)
            OpcionDePerfil("🛵", "Cambiar a modo cliente", onCambiarModo)
        }

        SelectorDeTema()

        // Cerrar sesión aparte y en rojo: es destructivo, no debe estar junto
        // a las opciones normales donde se toca por error.
        CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 0) {
            OpcionDePerfil("🚪", "Cerrar sesión", onCerrarSesion, color = colores.calor)
        }

        // Al pie: la primera pregunta al reportar algo raro es siempre "¿qué
        // versión tenés?", y deducirlo de Play confunde bugs ya arreglados con
        // bugs nuevos.
        PieDeVersion()
    }
}

/**
 * Claro / Oscuro / Automático.
 *
 * Tres opciones y no un interruptor: "Automático" sigue al teléfono, pero un
 * rider que anda de día quiere poder forzar el claro aunque su Android esté en
 * oscuro. Con un binario esa elección se le pierde cuando el sistema cambia
 * solo al atardecer.
 */
@Composable
private fun SelectorDeTema() {
    val temaRepo = koinInject<TemaRepositorio>()
    val scope = rememberCoroutineScope()
    val actual by temaRepo.observar().collectAsState(initial = TemaRepositorio.SISTEMA)
    val colores = ColoresJala.actuales

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Apariencia",
            style = MaterialTheme.typography.labelLarge,
            color = colores.tintaSecundaria,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OpcionDeTema("☀️", "Claro", TemaRepositorio.CLARO, actual, Modifier.weight(1f)) {
                scope.launch { temaRepo.guardar(it) }
            }
            OpcionDeTema("🌙", "Oscuro", TemaRepositorio.OSCURO, actual, Modifier.weight(1f)) {
                scope.launch { temaRepo.guardar(it) }
            }
            OpcionDeTema("📱", "Auto", TemaRepositorio.SISTEMA, actual, Modifier.weight(1f)) {
                scope.launch { temaRepo.guardar(it) }
            }
        }
    }
}

/** Una de las tres opciones de tema. La elegida va con borde y fondo de marca. */
@Composable
private fun OpcionDeTema(
    icono: String,
    texto: String,
    valor: String,
    actual: String,
    modifier: Modifier = Modifier,
    onElegir: (String) -> Unit,
) {
    val elegida = valor == actual
    val colores = ColoresJala.actuales
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (elegida) colores.marcaAmarillo.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                width = if (elegida) 2.dp else 1.dp,
                color = if (elegida) colores.marcaAmarillo
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onElegir(valor) }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icono, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = if (elegida) MaterialTheme.colorScheme.onSurface else colores.tintaSecundaria,
        )
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

@Composable
private fun OpcionDePerfil(
    icono: String,
    texto: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icono, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(14.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = colores.tintaSecundaria)
    }
}
