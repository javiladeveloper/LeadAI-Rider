package pe.leadai.rider.ui.navegacion

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.datos.SesionGuardada
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.push.RegistroPushRepositorio
import pe.leadai.rider.ui.alta.AltaRiderPantalla
import pe.leadai.rider.ui.carreras.CarrerasPantalla
import pe.leadai.rider.ui.login.LoginPantalla
import pe.leadai.rider.ui.registro.RegistroPantalla

/**
 * Distingue "todavía no llegó la primera emisión de DataStore" de "sí llegó
 * y no hay sesión" — ambos casos colapsarían a `null` si se usara
 * `SesionGuardada?` directo, y solo el segundo debe decidir la ruta inicial.
 */
private sealed class EstadoSesionInicial {
    data object Cargando : EstadoSesionInicial()
    data class Resuelto(val sesion: SesionGuardada?) : EstadoSesionInicial()
}

/**
 * Con sesión, la ruta inicial depende de si el usuario YA se dio de alta como
 * motorizado (`GET /motorizados/mi-perfil`). Mientras esa llamada está en
 * vuelo el grafo no se dibuja: mejor no mostrar nada que parpadear el alta
 * para saltar a las carreras una fracción de segundo después.
 */
private sealed class DestinoConSesion {
    data object Cargando : DestinoConSesion()
    data class Resuelto(val ruta: String) : DestinoConSesion()
}

/** Nombres de ruta del grafo. */
object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"

    /** Alta de motorizado: distrito, DNI, teléfono y placa. */
    const val ALTA = "alta"

    /** "✏️ Editar mi perfil": el mismo alta, pre-llenado. */
    const val EDITAR = "editar"

    /** Pantalla principal: pool de carreras, mapa, monedero e historial. */
    const val CARRERAS = "carreras"
}

private const val DURACION_FADE_MS = 220

/**
 * A dónde va un usuario que ACABA de autenticarse (o que abre la app con
 * sesión guardada): a las carreras si ya tiene perfil de motorizado, al alta
 * si no. Un rider que se vuelve a loguear no debería re-escribir su DNI,
 * teléfono y placa.
 *
 * Si el chequeo falla (sin conexión, backend caído) manda al alta: es el
 * único destino que funciona sin perfil, y para el rider que ya lo tiene el
 * formulario se pre-llena solo al recuperar la red.
 */
internal suspend fun rutaTrasIniciarSesion(motorizadosApi: MotorizadosApi): String =
    when (val resultado = motorizadosApi.miPerfil()) {
        is Resultado.Ok -> if (resultado.valor != null) Rutas.CARRERAS else Rutas.ALTA
        is Resultado.Error -> Rutas.ALTA
    }

/**
 * Grafo de navegación raíz. A diferencia de la app de negocios, acá el camino
 * es uno solo: te logueas, te das de alta como motorizado y trabajas. No hay
 * selector de empresa ni alta de negocio — las `empresas` de la sesión se
 * ignoran por completo (un rider puede además ser dueño de un restaurante;
 * eso lo maneja la OTRA app, con el mismo usuario y la misma API).
 *
 * Ruta inicial:
 * - sin sesión → [Rutas.LOGIN]
 * - con sesión y CON perfil de motorizado → [Rutas.CARRERAS]
 * - con sesión y SIN perfil → [Rutas.ALTA]
 *
 * **Deep link del push**: `LeadAIFirebaseService` arma un `PendingIntent` a
 * `MainActivity` cuando llega "nueva carrera en tu zona". Acá no hace falta
 * navegar a ningún lado con ese dato: el destino del push del rider ES
 * [Rutas.CARRERAS], que ya es su pantalla principal — abrir la app basta.
 * Por eso este composable no recibe el `pedidoId` del intent.
 */
@Composable
fun NavegacionRaiz(navController: NavHostController = rememberNavController()) {
    val sesionRepositorio = koinInject<SesionRepositorio>()
    val registroPush = koinInject<RegistroPushRepositorio>()
    val motorizadosApi = koinInject<MotorizadosApi>()
    val scope = rememberCoroutineScope()

    val estadoSesion by produceState<EstadoSesionInicial>(
        initialValue = EstadoSesionInicial.Cargando,
        sesionRepositorio,
    ) {
        sesionRepositorio.observar().collect { value = EstadoSesionInicial.Resuelto(it) }
    }

    // Mientras no sabemos si hay sesión, no dibujamos nada del grafo — evita
    // un parpadeo de login antes de saltar a las carreras.
    val resuelto = estadoSesion as? EstadoSesionInicial.Resuelto ?: return
    val haySesion = resuelto.sesion != null

    val destino by produceState<DestinoConSesion>(
        initialValue = DestinoConSesion.Cargando,
        haySesion,
        motorizadosApi,
    ) {
        if (!haySesion) return@produceState
        value = DestinoConSesion.Resuelto(rutaTrasIniciarSesion(motorizadosApi))
    }

    if (haySesion && destino is DestinoConSesion.Cargando) return

    val rutaInicial = when {
        !haySesion -> Rutas.LOGIN
        else -> (destino as DestinoConSesion.Resuelto).ruta
    }

    NavHost(
        navController = navController,
        startDestination = rutaInicial,
        enterTransition = { fadeIn(animationSpec = tween(DURACION_FADE_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(DURACION_FADE_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(DURACION_FADE_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(DURACION_FADE_MS)) },
    ) {
        composable(Rutas.LOGIN) {
            LoginPantalla(
                alExito = {
                    scope.launch {
                        navController.navigate(rutaTrasIniciarSesion(motorizadosApi)) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                alRegistrarse = { navController.navigate(Rutas.REGISTRO) },
            )
        }
        composable(Rutas.REGISTRO) {
            RegistroPantalla(
                alExito = {
                    navController.navigate(Rutas.ALTA) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                alVolverALogin = { navController.popBackStack() },
            )
        }
        composable(Rutas.ALTA) {
            AltaRiderPantalla(
                alTerminar = {
                    // `popUpTo(0)`: el alta es un punto de entrada, no un push
                    // más al back stack — desde las carreras, "atrás" debe
                    // salir de la app, no volver al formulario.
                    navController.navigate(Rutas.CARRERAS) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Rutas.EDITAR) {
            AltaRiderPantalla(
                alTerminar = {
                    navController.navigate(Rutas.CARRERAS) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modoEditar = true,
            )
        }
        composable(Rutas.CARRERAS) {
            CarrerasPantalla(
                alCambiarDistrito = { navController.navigate(Rutas.EDITAR) },
                alCerrarSesion = {
                    scope.launch {
                        // Desregistra el token push ANTES de cerrar sesión,
                        // mientras todavía hay un Bearer válido para autenticar
                        // el DELETE — best-effort, nunca lanza.
                        registroPush.desregistrar()
                        sesionRepositorio.cerrar()
                        navController.navigate(Rutas.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
