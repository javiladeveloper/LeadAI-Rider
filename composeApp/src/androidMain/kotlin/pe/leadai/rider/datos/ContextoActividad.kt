package pe.leadai.rider.datos

import android.app.Activity

/**
 * Holder de la Activity actual (Task B6, Google Sign-In). Mismo patrón que
 * [ContextoApp] pero con la Activity en vez del Application context: a
 * diferencia de DataStore (que solo necesita un `Context` cualquiera),
 * `CredentialManager.getCredential()` necesita un `Context` que sea (o
 * envuelva) una Activity — es quien dibuja el selector de cuentas de Google
 * encima de la pantalla actual.
 *
 * `MainActivity` setea/limpia [activity] en `onCreate`/`onDestroy` — se
 * limpia en `onDestroy` para no retener una Activity destruida (misma
 * preocupación de fuga de memoria que motivó `ContextoApp` a guardar
 * `applicationContext` en vez de `this`). Es nullable: si por algún motivo
 * `obtenerIdTokenGoogle()` corre sin Activity viva (no debería pasar, el
 * botón de Google solo existe dentro de `LoginPantalla`, que ya está en
 * pantalla), el `actual` de `RegistroPush`-style devuelve `null` en vez de
 * `!!`-crashear.
 */
object ContextoActividad {
    var activity: Activity? = null
}
