package pe.leadai.rider.ui.permisos

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import pe.leadai.rider.ui.tema.Formas
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.BotonPrincipal
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Los permisos que el rider necesita para trabajar — sigue el diseño de
 * Stitch ("Permisos Necesarios").
 *
 * Cada permiso es una card con ícono, qué es, PARA QUÉ sirve, y su botón de
 * Configurar. La explicación importa: un permiso pedido sin motivo se niega,
 * y una vez negado dos veces Android no lo vuelve a preguntar.
 */
@Composable
fun PermisosPantalla(alVolver: () -> Unit) {
    val colores = ColoresJala.actuales

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            "Para empezar a ganar…",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Necesitamos algunos permisos para que la app funcione perfectamente " +
                "mientras conducís.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        CardPermiso(
            emoji = "📍",
            titulo = "Ubicación",
            descripcion = "Necesitamos tu ubicación siempre para enviarte trabajos cerca.",
            onConfigurar = { abrirAjustesDeLaApp() },
        )
        Spacer(Modifier.height(12.dp))
        CardPermiso(
            emoji = "🔋",
            titulo = "Batería",
            descripcion = "Permití el uso sin restricciones para que la app no se cierre.",
            onConfigurar = { abrirAjustesDeBateria() },
        )
        Spacer(Modifier.height(12.dp))
        CardPermiso(
            emoji = "🔔",
            titulo = "Notificaciones",
            descripcion = "Enterate al instante cuando haya un nuevo pedido.",
            onConfigurar = { abrirAjustesDeLaApp() },
        )

        Spacer(Modifier.height(20.dp))

        // Pedir "ubicación siempre" asusta: aclarar qué se hace con el dato
        // baja el rechazo más que cualquier otra cosa.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colores.exito.copy(alpha = 0.10f),
                    shape = Formas.chip,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛡️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(10.dp))
            Text(
                "Privacidad garantizada: tu ubicación solo se comparte mientras " +
                    "tenés una carrera en curso.",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }

        Spacer(Modifier.height(24.dp))

        BotonPrincipal(texto = "Listo, continuar", onClick = alVolver)

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Un permiso: ícono en cuadrado, nombre, para qué sirve, y "Configurar".
 *
 * El botón manda a los ajustes DE ESTA APP directamente, no a la lista
 * general — desde Android 11 "ubicación siempre" no se puede pedir con un
 * diálogo, y hacer que el usuario busque la app entre cien es donde se pierde.
 */
@Composable
private fun CardPermiso(
    emoji: String,
    titulo: String,
    descripcion: String,
    onConfigurar: () -> Unit,
) {
    val colores = ColoresJala.actuales

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = Formas.chip,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    descripcion,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = Formas.chip,
                )
                .clickable { onConfigurar() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Configurar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
