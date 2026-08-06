package pe.leadai.rider.ui.modo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import pe.leadai.rider.ui.comunes.BotonAcento
import pe.leadai.rider.ui.comunes.BotonPrincipal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Lo primero que ve alguien que abre la app sin haber elegido todavía: ¿viene
 * a pedir una moto o a manejar?
 *
 * Un usuario que YA tiene perfil de motorizado no pasa por acá — entra directo
 * a conducir.
 */
@Composable
fun ElegirModoPantalla(
    alElegirCliente: () -> Unit,
    alElegirConductor: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🛵", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "¿Qué vas a hacer?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Podés cambiar cuando quieras",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // El amarillo va en "pedir": es la acción que más se usa y la que
        // trae gente nueva. Manejar es la secundaria — quien va a manejar ya
        // sabe que viene a eso.
        BotonAcento(texto = "🛵 Pido un motorizado", onClick = alElegirCliente)
        Spacer(Modifier.height(12.dp))
        BotonPrincipal(texto = "🏍️ Manejo", onClick = alElegirConductor)
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Text(
                "Para manejar te vamos a pedir tu DNI y los datos de tu vehículo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
