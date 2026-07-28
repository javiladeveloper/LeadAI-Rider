package pe.leadai.rider.ui.permisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Los tres permisos que el rider necesita para que la app funcione EN LA
 * CALLE, no solo en demo.
 *
 * Cada card dice PARA QUÉ sirve, no cómo se llama el permiso: al rider no le
 * importa "ACCESS_BACKGROUND_LOCATION", le importa que el cliente lo vea
 * moverse. Ese encuadre es la diferencia entre que lo concedan o que lo
 * nieguen.
 *
 * No hay check de "ya está dado": leer el estado real de cada permiso desde
 * `commonMain` exigiría tres `expect/actual` más, y aun así el de batería no
 * es consultable de forma confiable entre fabricantes. Se muestra siempre la
 * lista completa; tocar un botón que ya estaba concedido es inofensivo.
 */
@Composable
fun PermisosPantalla(alVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text("⚙️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Para trabajar tranquilo",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tres permisos del teléfono para que no se te caigan las carreras " +
                "mientras manejás. Cada uno se activa desde Configuración.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        TarjetaPermiso(
            emoji = "📍",
            titulo = "Ubicación en todo momento",
            porQue = "Para seguir compartiendo tu ubicación cuando guardás el " +
                "teléfono en el bolsillo. Sin esto el cliente te ve congelado " +
                "en el mapa aunque estés yendo.",
            comoLlegar = "Elegí “Permitir todo el tiempo” en Ubicación.",
            alTocar = ::abrirAjustesDeLaApp,
        )
        Spacer(Modifier.height(12.dp))
        TarjetaPermiso(
            emoji = "🔋",
            titulo = "Batería sin restricciones",
            porQue = "Para que el teléfono no cierre la app mientras trabajás. " +
                "Algunos equipos la matan solos “para ahorrar batería”, justo " +
                "en plena carrera.",
            comoLlegar = "Elegí “Sin restricciones” en Batería.",
            alTocar = ::abrirAjustesDeBateria,
        )
        Spacer(Modifier.height(12.dp))
        TarjetaPermiso(
            emoji = "🔔",
            titulo = "Notificaciones",
            porQue = "Para avisarte cuando aparezca una carrera cerca tuyo, " +
                "aunque tengas la app cerrada.",
            comoLlegar = "Activá las notificaciones de la app.",
            alTocar = ::abrirAjustesDeLaApp,
        )

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Text(
                "Tu ubicación solo se comparte mientras llevás una carrera. " +
                    "Al entregar, se deja de reportar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = alVolver,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Volver", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Una card por permiso: qué es, para qué sirve y el botón que lleva al ajuste. */
@Composable
private fun TarjetaPermiso(
    emoji: String,
    titulo: String,
    porQue: String,
    comoLlegar: String,
    alTocar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                porQue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                comoLlegar,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = alTocar,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Configurar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
