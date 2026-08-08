package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.VersionApp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * La versión instalada, al pie del perfil.
 *
 * Sin esto no hay forma de saber qué build se está probando: al reportar algo
 * raro, la primera pregunta es siempre "¿qué versión tenés?" — y si hay que
 * deducirlo de Play o del `dumpsys`, se pierde tiempo y se confunden bugs ya
 * arreglados con bugs nuevos.
 *
 * Muestra el `versionCode` además del nombre porque es el número que se
 * compara con Play y con lo que anuncia el backend; el nombre puede repetirse
 * entre builds.
 */
@Composable
fun PieDeVersion(modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Text(
        "Light Drive ${VersionApp.nombre} (${VersionApp.codigo})",
        style = MaterialTheme.typography.labelSmall,
        color = colores.tintaSecundaria,
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = TextAlign.Center,
    )
}
