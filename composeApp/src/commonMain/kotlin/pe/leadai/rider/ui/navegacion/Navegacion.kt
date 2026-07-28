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
import pe.leadai.rider.datos.ModoRepositorio
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.datos.SesionGuardada
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.push.RegistroPushRepositorio
import pe.leadai.rider.ui.alta.AltaRiderPantalla
import pe.leadai.rider.ui.carreras.CarrerasPantalla
import pe.leadai.rider.ui.cliente.ClientePantalla
import pe.leadai.rider.ui.login.LoginPantalla
import pe.leadai.rider.ui.modo.ElegirModoPantalla
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

/**
 * Mismo truco que [EstadoSesionInicial] para el modo guardado: "todavía no
 * emitió DataStore" y "emitió y no hay modo elegido" son ambos `null`, y
 * confundirlos mandaría a la pantalla de elección a alguien que ya eligió.
 */
private sealed class EstadoModoInicial {
    data object Cargando : EstadoModoInicial()
    data class Resuelto(val modo: String?) : EstadoModoInicial()
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

    /** Elegir si viene a pedir una moto o a manejar. */
    const val ELEGIR_MODO = "elegir_modo"

    /** Modo cliente: pedir una moto y seguir el viaje. */
    const val CLIENTE = "cliente"
}

private const val DURACION_FADE_MS = 220

/**
 * A dónde va un usuario que acaba de autenticarse (o que abre la app con
 * sesión guardada).
 *
 * El perfil de motorizado MANDA: quien ya se dio de alta como rider entra
 * directo a trabajar, sin pasar por la elección de modo. Su experiencia no
 * cambia en nada respecto de la app solo-rider. Solo quien NO es rider elige
 * — y si ya eligió antes, se respeta lo que eligió.
 */
internal suspend fun rutaTrasIniciarSesion(
    motorizadosApi: MotorizadosApi,
    modoGuardado: String?,
): String {
    val perfil = when (val r = motorizadosApi.miPerfil()) {
        is Resultado.Ok -> r.valor
        // Sin conexión no se puede saber si es rider. Que elija: es el único
        // camino que no lo deja trabado en un formulario que quizás no le toca
        // (un cliente en el alta de motorizado no puede hacer nada).
        is Resultado.Error -> return modoGuardado?.let { rutaDeModo(it) } ?: Rutas.ELEGIR_MODO
    }
    // El chequeo del perfil va ANTES de mirar el modo guardado, a propósito.
    if (perfil != null) return Rutas.CARRERAS
    return when (modoGuardado) {
        ModoRepositorio.CLIENTE -> Rutas.CLIENTE
        ModoRepositorio.CONDUCTOR -> Rutas.ALTA
        else -> Rutas.ELEGIR_MODO
    }
}

private fun rutaDeModo(modo: String): String =
    if (modo == ModoRepositorio.CONDUCTOR) Rutas.ALTA else Rutas.CLIENTE

/**
 * Grafo de navegación raíz. A diferencia de la app de negocios, acá hay dos
 * caminos: pedir una moto (modo cliente) o manejar (modo conductor: te das de
 * alta como motorizado y trabajas). No hay
 * selector de empresa ni alta de negocio — las `empresas` de la sesión se
 * ignoran por completo (un rider puede además ser dueño de un restaurante;
 * eso lo maneja la OTRA app, con el mismo usuario y la misma API).
 *
 * Ruta inicial:
 * - sin sesión → [Rutas.LOGIN]
 * - con sesión y CON perfil de motorizado → [Rutas.CARRERAS] (siempre: el
 *   perfil manda sobre el modo guardado)
 * - con sesión, SIN perfil y con modo ya elegido → [Rutas.CLIENTE] o [Rutas.ALTA]
 * - con sesión, SIN perfil y sin modo elegido → [Rutas.ELEGIR_MODO]
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
    val modoRepositorio = koinInject<ModoRepositorio>()
    val scope = rememberCoroutineScope()

    val estadoSesion by produceState<EstadoSesionInicial>(
        initialValue = EstadoSesionInicial.Cargando,
        sesionRepositorio,
    ) {
        sesionRepositorio.observar().collect { value = EstadoSesionInicial.Resuelto(it) }
    }

    val estadoModo by produceState<EstadoModoInicial>(
        initialValue = EstadoModoInicial.Cargando,
        modoRepositorio,
    ) {
        modoRepositorio.observar().collect { value = EstadoModoInicial.Resuelto(it) }
    }

    // Mientras no sabemos si hay sesión, no dibujamos nada del grafo — evita
    // un parpadeo de login antes de saltar a las carreras.
    val resuelto = estadoSesion as? EstadoSesionInicial.Resuelto ?: return
    val haySesion = resuelto.sesion != null

    // Ídem con el modo: decidir la ruta inicial con un modo a medio leer
    // mandaría a elegir a quien ya eligió.
    val modoResuelto = estadoModo as? EstadoModoInicial.Resuelto ?: return
    val modoGuardado = modoResuelto.modo

    val destino by produceState<DestinoConSesion>(
        initialValue = DestinoConSesion.Cargando,
        haySesion,
        motorizadosApi,
        modoGuardado,
    ) {
        if (!haySesion) return@produceState
        value = DestinoConSesion.Resuelto(rutaTrasIniciarSesion(motorizadosApi, modoGuardado))
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
                        navController.navigate(rutaTrasIniciarSesion(motorizadosApi, modoGuardado)) {
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
        composable(Rutas.ELEGIR_MODO) {
            ElegirModoPantalla(
                alElegirCliente = {
                    scope.launch {
                        modoRepositorio.guardar(ModoRepositorio.CLIENTE)
                        navController.navigate(Rutas.CLIENTE) { popUpTo(0) { inclusive = true } }
                    }
                },
                alElegirConductor = {
                    scope.launch {
                        modoRepositorio.guardar(ModoRepositorio.CONDUCTOR)
                        navController.navigate(Rutas.ALTA) { popUpTo(0) { inclusive = true } }
                    }
                },
            )
        }
        composable(Rutas.CLIENTE) {
            ClientePantalla(
                alCambiarModo = {
                    scope.launch {
                        modoRepositorio.guardar(ModoRepositorio.CONDUCTOR)
                        navController.navigate(Rutas.ALTA) { popUpTo(0) { inclusive = true } }
                    }
                },
                alCerrarSesion = {
                    scope.launch {
                        registroPush.desregistrar()
                        sesionRepositorio.cerrar()
                        navController.navigate(Rutas.LOGIN) { popUpTo(0) { inclusive = true } }
                    }
                },
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
