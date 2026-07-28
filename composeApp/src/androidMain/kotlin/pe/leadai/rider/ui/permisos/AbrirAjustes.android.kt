package pe.leadai.rider.ui.permisos

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import pe.leadai.rider.datos.ContextoApp

/**
 * `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:<applicationId>` cae
 * DIRECTO en la ficha de esta app. La alternativa (`ACTION_SETTINGS`, la
 * lista general) deja al rider buscando "LeadAI Rider" entre cien apps, y ahí
 * es donde la mayoría abandona. Es lo que hace inDrive.
 *
 * `FLAG_ACTIVITY_NEW_TASK` es obligatorio: se arranca desde el
 * `applicationContext`, que no tiene una task propia donde apilar la Activity.
 */
actual fun abrirAjustesDeLaApp() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ContextoApp.context.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    // Que no tumbe la app si el fabricante no expone esa pantalla.
    runCatching { ContextoApp.context.startActivity(intent) }
}

actual fun abrirAjustesDeBateria() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    // Algunos fabricantes no exponen esa pantalla: si falla, que al menos
    // llegue a los ajustes de la app, desde donde también se puede llegar.
    if (runCatching { ContextoApp.context.startActivity(intent) }.isFailure) abrirAjustesDeLaApp()
}
