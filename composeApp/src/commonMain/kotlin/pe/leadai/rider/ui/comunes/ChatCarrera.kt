package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.MensajeChatDto
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * La conversación entre el cliente y su motorizado.
 *
 * Existe porque la causa más común de que una carrera se caiga es que el rider
 * no encuentra la dirección y no tiene cómo preguntar. La alternativa era
 * pasarse el teléfono, que ninguno de los dos quiere hacer.
 *
 * Lo primero son los MENSAJES RÁPIDOS, no el teclado: el rider está en la
 * calle, con una mano y a veces con casco. Tocar un botón es lo único
 * razonable ahí; escribir es la excepción.
 */
@Composable
fun ChatCarrera(
    mensajes: List<MensajeChatDto>,
    /** 'cliente' o 'rider': de qué lado va cada globo. */
    yo: String,
    rapidos: List<String>,
    enviando: Boolean,
    onEnviar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    val lista = rememberLazyListState()

    // Al llegar un mensaje se baja al final: lo último es lo que importa, y
    // quedarse arriba haría que el rider no vea la respuesta que esperaba.
    LaunchedEffect(mensajes.size) {
        if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (mensajes.isEmpty()) {
            // Un chat vacío no se explica solo: se dice para qué sirve.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Escribile para coordinar el encuentro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSecundaria,
                )
            }
        } else {
            LazyColumn(
                state = lista,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(mensajes, key = { it.id }) { mensaje ->
                    GloboMensaje(mensaje = mensaje, esMio = mensaje.de == yo)
                }
            }
        }

        // LOS RÁPIDOS, primero y siempre a mano.
        //
        // En scroll horizontal y no en una grilla: ocupan una línea, no roban
        // la conversación, y alcanza con deslizar el pulgar para ver todos.
        if (rapidos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rapidos.forEach { texto ->
                    Text(
                        texto,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !enviando) { onEnviar(texto) }
                            // 12dp verticales: se toca manejando, con guantes.
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }

        CampoDeMensaje(enviando = enviando, onEnviar = onEnviar)
    }
}

/**
 * Un mensaje. Los míos a la derecha y en color; los del otro a la izquierda.
 *
 * Es la convención de cualquier mensajería: nadie tiene que aprenderla, y sin
 * ella hay que leer cada globo para saber quién lo dijo.
 */
@Composable
private fun GloboMensaje(mensaje: MensajeChatDto, esMio: Boolean) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (esMio) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                // Nunca todo el ancho: un globo que llega a los dos bordes no
                // se distingue del otro lado.
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (esMio) colores.marcaAmarillo else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                mensaje.texto,
                style = MaterialTheme.typography.bodyMedium,
                color = if (esMio) colores.marcaCarbon else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Escribir a mano: la excepción, pero tiene que estar. */
@Composable
private fun CampoDeMensaje(enviando: Boolean, onEnviar: (String) -> Unit) {
    val colores = ColoresJala.actuales
    var texto by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = texto,
                onValueChange = { texto = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                decorationBox = { campo ->
                    if (texto.isEmpty()) {
                        Text(
                            "Escribí un mensaje",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tintaSecundaria,
                        )
                    }
                    campo()
                },
            )
        }
        Spacer(Modifier.size(8.dp))

        // El botón solo cuando hay algo que mandar: un "enviar" apagado al lado
        // de un campo vacío es ruido.
        val puedeEnviar = texto.isNotBlank() && !enviando
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (puedeEnviar) colores.marcaAmarillo
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable(enabled = puedeEnviar) {
                    onEnviar(texto)
                    texto = ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "➤",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = if (puedeEnviar) colores.marcaCarbon else colores.tintaSecundaria,
            )
        }
    }
}
