package pe.leadai.rider.push

/**
 * Pide el permiso de notificaciones si hace falta.
 *
 * Desde Android 13 `POST_NOTIFICATIONS` es un permiso de runtime, como el de
 * ubicación: declararlo en el manifest NO alcanza. Sin concederlo, Android
 * DESCARTA las notificaciones en silencio — el backend las manda, FCM las
 * acepta, y el teléfono las tira sin avisar a nadie.
 *
 * Eso es lo que pasaba: los tokens estaban bien registrados, FCM respondía OK,
 * y al rider no le llegaba nada. Un fallo silencioso en las dos puntas.
 *
 * `expect/actual` porque solo Android lo necesita: en iOS el permiso se pide
 * distinto y hoy no hay build de iOS.
 */
expect suspend fun pedirPermisoNotificaciones(): Boolean
