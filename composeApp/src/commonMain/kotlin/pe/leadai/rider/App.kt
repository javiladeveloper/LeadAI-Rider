package pe.leadai.rider

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import org.koin.compose.koinInject
import pe.leadai.rider.datos.ChequeoVersion
import pe.leadai.rider.datos.TemaRepositorio
import pe.leadai.rider.datos.VersionApi
import pe.leadai.rider.ui.comunes.AberturaApp
import pe.leadai.rider.ui.comunes.DialogoActualizacion
import pe.leadai.rider.ui.navegacion.NavegacionRaiz
import pe.leadai.rider.ui.tema.JalaTheme

@Composable
fun App() {
    val temaRepo = koinInject<TemaRepositorio>()
    // `SISTEMA` como valor inicial: es el default, así que la primera
    // composición ya sale con el tema correcto y no hay parpadeo mientras
    // DataStore lee del disco.
    val tema by temaRepo.observar().collectAsState(initial = TemaRepositorio.SISTEMA)

    JalaTheme(
        oscuro = when (tema) {
            TemaRepositorio.CLARO -> false
            TemaRepositorio.OSCURO -> true
            else -> isSystemInDarkTheme()
        },
    ) {
        NavegacionRaiz()
        AvisoDeActualizacion()
    }
}

/**
 * "Hay una versión nueva", sobre cualquier pantalla. Se consulta una vez al
 * arrancar y se descarta con "Más tarde" hasta el próximo arranque.
 *
 * Va DESPUÉS de la navegación a propósito: así el diálogo queda por encima de
 * lo que esté abajo, sea el login o el pool de carreras.
 */
@Composable
private fun AvisoDeActualizacion() {
    val versionApi = koinInject<VersionApi>()
    val abridor = LocalUriHandler.current
    var chequeo by remember { mutableStateOf<ChequeoVersion?>(null) }
    var descartado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Si entró tocando una notificación NO se chequea: vino a atender algo
        // concreto y el diálogo le tapa justo eso. Se avisa la próxima vez que
        // abra la app por su cuenta.
        if (AberturaApp.desdeNotificacion) return@LaunchedEffect
        // Silencioso: sin red no hay aviso, y está bien — molestar con un
        // diálogo por un problema de conexión es peor que no avisar.
        chequeo = versionApi.chequear()
    }

    val c = chequeo ?: return
    if (!c.hayActualizacion) return
    if (descartado && !c.obligatoria) return

    DialogoActualizacion(
        versionName = c.versionName,
        notas = c.notas,
        obligatoria = c.obligatoria,
        onActualizar = {
            abridor.openUri(c.urlTienda)
            // No se descarta con una obligatoria: si vuelve sin actualizar,
            // el diálogo tiene que seguir ahí.
            if (!c.obligatoria) descartado = true
        },
        onMasTarde = { descartado = true },
    )
}
