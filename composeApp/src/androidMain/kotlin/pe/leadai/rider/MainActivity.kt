package pe.leadai.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import pe.leadai.rider.push.EXTRA_DESDE_NOTIFICACION
import pe.leadai.rider.ui.comunes.AberturaApp
import androidx.activity.compose.setContent
import pe.leadai.rider.datos.ContextoActividad

/**
 * Única Activity de la app. El push "nueva carrera en tu zona" abre
 * directamente acá y no trae deep link que resolver: el destino de ese aviso
 * ES la pantalla principal del rider (el pool de carreras), así que basta con
 * que la app se abra. Por eso no hay `onNewIntent` leyendo extras, a
 * diferencia de la app de negocios, donde el push apunta al detalle de UN
 * pedido concreto.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ContextoApp.context y Koin ya están listos: los inicializa
        // LeadAIRiderApp (Application.onCreate corre antes que cualquier
        // Activity).
        // ContextoActividad: Google Sign-In vía Credential Manager necesita el
        // Context de la Activity para dibujar el selector de cuentas — ver
        // ContextoActividad.kt.
        ContextoActividad.activity = this
        // De dónde vino esta apertura: si el rider tocó una notificación,
        // viene a atender algo concreto y el diálogo de actualización le tapa
        // justo eso.
        AberturaApp.desdeNotificacion =
            intent?.getBooleanExtra(EXTRA_DESDE_NOTIFICACION, false) == true

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Evita retener una Activity destruida (ver ContextoActividad.kt).
        if (ContextoActividad.activity === this) {
            ContextoActividad.activity = null
        }
    }
}
