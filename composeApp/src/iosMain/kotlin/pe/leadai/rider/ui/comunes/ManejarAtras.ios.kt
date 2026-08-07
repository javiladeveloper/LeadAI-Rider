package pe.leadai.rider.ui.comunes

import androidx.compose.runtime.Composable

// iOS no tiene botón atrás del sistema: se vuelve con el gesto de la barra de
// navegación, que maneja el propio contenedor. Acá no hay nada que
// interceptar.
@Composable
actual fun ManejarAtras(habilitado: Boolean, alVolver: () -> Unit) = Unit
