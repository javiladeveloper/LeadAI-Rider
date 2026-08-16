package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Por qué cancela el cliente.
 *
 * De 28 carreras hubo 17 canceladas y ningún motivo registrado. Ese número no
 * dice nada: no distingue a alguien que se arrepintió de un rider que nunca
 * llegó, y son problemas completamente distintos —uno es normal, el otro es un
 * rider que hay que revisar—.
 *
 * De un toque y con salida: "Cerrar" cancela igual sin elegir nada. Obligar a
 * responder para poder cancelar sería castigar al cliente justo cuando ya está
 * molesto.
 */

/** Los motivos, con el valor que entiende el backend. */
private val MOTIVOS = listOf(
    "rider_no_aparece" to "El motorizado no aparece",
    "tarda_mucho" to "Está tardando mucho",
    "consegui_otro" to "Conseguí otro por mi cuenta",
    "precio" to "Por el precio",
    "me_arrepenti" to "Ya no lo necesito",
    "otro" to "Otro motivo",
)

@Composable
fun DialogoMotivoCancelar(
    /** Si el rider ya la aceptó: cambia qué motivos tienen sentido. */
    conRiderAsignado: Boolean,
    onCancelarCarrera: (motivo: String?) -> Unit,
    onCerrar: () -> Unit,
) {
    val colores = ColoresJala.actuales

    // Con un rider en camino, "el motorizado no aparece" y "tarda mucho" son
    // los motivos reales. Sin rider todavía, no tienen sentido: no hay a quién
    // esperar.
    val opciones = if (conRiderAsignado) {
        MOTIVOS
    } else {
        MOTIVOS.filter { it.first != "rider_no_aparece" }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Por qué cancelás?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Nos ayuda a mejorar el servicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSecundaria,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                opciones.forEach { (valor, etiqueta) ->
                    Text(
                        etiqueta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCancelarCarrera(valor) }
                            // 14dp de alto de toque: la lista se usa con el
                            // pulgar y estas opciones están pegadas entre sí.
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            // Cancelar SIN decir por qué sigue siendo posible: el dato es
            // valioso, pero no al precio de atrapar a alguien que quiere salir.
            TextButton(onClick = { onCancelarCarrera(null) }) {
                Text("Cancelar sin decir", color = colores.tintaSecundaria)
            }
        },
    )
}
