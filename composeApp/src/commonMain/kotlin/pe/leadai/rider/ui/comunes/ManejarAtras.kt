package pe.leadai.rider.ui.comunes

import androidx.compose.runtime.Composable

/**
 * Intercepta el botón/gesto ATRÁS del sistema.
 *
 * Existe porque varias "pantallas" de la app no son rutas de navegación sino
 * ESTADO local (la pestaña activa, el diálogo de recarga, los permisos).
 * Android no sabe que existen, así que atrás cerraba la app entera en vez de
 * retroceder un paso.
 *
 * Cubre las dos formas de volver: el gesto de deslizar desde el borde y los
 * botones táctiles/físicos, que llegan por el mismo evento del sistema.
 *
 * [habilitado] en `false` deja pasar el evento — así el atrás del nivel más
 * alto sí cierra la app, que es lo que el usuario espera ahí.
 *
 * `expect/actual` porque en iOS no hay botón atrás: allá es un no-op (mismo
 * patrón que `iniciarServicioCarrera`).
 */
@Composable
expect fun ManejarAtras(habilitado: Boolean = true, alVolver: () -> Unit)
