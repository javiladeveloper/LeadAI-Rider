package pe.leadai.rider.ui.comunes

/**
 * Cómo se abrió la app en esta sesión.
 *
 * Existe por una sola razón: si el rider entró TOCANDO una notificación, vino
 * a atender algo concreto —una carrera nueva, el cliente que llegó— y meterle
 * el diálogo de actualización encima le tapa justo eso. El aviso se guarda
 * para cuando abra la app por su cuenta.
 *
 * Es un holder simple y no estado inyectado porque lo escribe la Activity
 * antes de que exista cualquier pantalla, y lo lee un único composable.
 */
object AberturaApp {
    /** `true` si esta apertura vino de tocar una notificación. */
    var desdeNotificacion: Boolean = false
}
