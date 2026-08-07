package pe.leadai.rider.ui.comunes

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun ManejarAtras(habilitado: Boolean, alVolver: () -> Unit) {
    BackHandler(enabled = habilitado, onBack = alVolver)
}
