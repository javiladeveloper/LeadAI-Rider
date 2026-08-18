package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.leadai.rider.datos.TemaRepositorio
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.Formas

/**
 * Claro / Oscuro / Automático.
 *
 * Tres opciones y no un interruptor: "Automático" sigue al teléfono, pero
 * alguien que anda de día quiere poder forzar el claro aunque su Android esté
 * en oscuro. Con un binario esa elección se le pierde cuando el sistema cambia
 * solo al atardecer.
 *
 * Vive acá y no en el perfil del rider porque las DOS cuentas lo necesitan: el
 * modo noche estaba implementado y funcionando, pero el selector existía solo
 * del lado del motorizado. Desde la cuenta de cliente no había forma de
 * activarlo, así que para un cliente la app simplemente no tenía modo noche.
 */
@Composable
fun SelectorDeTema(modifier: Modifier = Modifier) {
    val temaRepo = koinInject<TemaRepositorio>()
    val scope = rememberCoroutineScope()
    val actual by temaRepo.observar().collectAsState(initial = TemaRepositorio.SISTEMA)

    CardJala(modifier = modifier.fillMaxWidth()) {
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
            .clip(Formas.chip)
            .background(
                if (elegida) colores.marcaAmarillo.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                width = if (elegida) 2.dp else 1.dp,
                color = if (elegida) colores.marcaAmarillo
                else MaterialTheme.colorScheme.outlineVariant,
                shape = Formas.chip,
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
