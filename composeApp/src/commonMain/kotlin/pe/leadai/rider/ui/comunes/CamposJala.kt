package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Campos de texto de Jala: 56dp de alto, 16dp de radio, etiqueta ARRIBA del
 * campo (no flotando adentro).
 *
 * La etiqueta arriba y no como placeholder es a propósito: cuando el campo
 * tiene texto, un placeholder desaparece y el usuario ya no sabe qué estaba
 * llenando. Con la etiqueta afuera siempre se ve.
 */
@Composable
fun CampoJala(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    habilitado: Boolean = true,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    prefijo: String? = null,
    maxLineas: Int = 1,
) {
    val colores = ColoresJala.actuales
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El prefijo (ej. "+51") va en su propia caja: así el usuario ve
            // que no tiene que escribirlo.
            if (prefijo != null) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .background(
                            color = colores.tintaSecundaria.copy(alpha = 0.10f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        prefijo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
            OutlinedTextField(
                value = valor,
                onValueChange = onCambio,
                enabled = habilitado,
                singleLine = maxLineas == 1,
                maxLines = maxLineas,
                placeholder = if (placeholder.isNotBlank()) {
                    { Text(placeholder, color = colores.tintaSecundaria) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colores.tintaSecundaria.copy(alpha = 0.25f),
                    focusedContainerColor = colores.superficieCard,
                    unfocusedContainerColor = colores.superficieCard,
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(if (maxLineas == 1) Modifier.height(56.dp) else Modifier),
            )
        }
    }
}

/**
 * Selector de dos opciones tipo pestañas — "Pasajero / Encomienda" en el
 * pedido, "Moto / Auto" en el alta.
 *
 * Se usa en vez de un dropdown porque son solo dos opciones y las dos deben
 * verse de una: esconder una mitad detrás de un menú es una decisión que el
 * usuario no debería tener que descubrir.
 */
@Composable
fun SelectorDos(
    opciones: List<Pair<String, String>>,
    seleccionada: String,
    onSeleccionar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colores.tintaSecundaria.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opciones.forEach { (valor, etiqueta) ->
            val activa = seleccionada == valor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(
                        color = if (activa) colores.superficieCard else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable { onSeleccionar(valor) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (activa) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        colores.tintaSecundaria
                    },
                )
            }
        }
    }
}
