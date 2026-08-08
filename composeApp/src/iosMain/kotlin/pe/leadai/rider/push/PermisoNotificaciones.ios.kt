package pe.leadai.rider.push

// iOS pide el permiso por otro camino (UNUserNotificationCenter) y hoy no hay
// build de iOS: se da por concedido para no bloquear el flujo común.
actual suspend fun pedirPermisoNotificaciones(): Boolean = true
