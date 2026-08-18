package pe.leadai.rider.datos

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

const val MENSAJE_SIN_CONEXION = "Sin conexión. Revisa tu internet 📶"

/** El backend no reconoció la sesión: hay que volver a entrar. */
const val CODIGO_SIN_SESION = 401
const val MENSAJE_SESION_VENCIDA = "Tu sesión venció. Volvé a iniciar sesión."
const val MENSAJE_ERROR_GENERICO = "Ocurrió un error. Intenta de nuevo."

/**
 * Cliente Ktor único de la app: agrega el header de autenticación desde la
 * sesión guardada y normaliza toda respuesta (éxito o error) a [Resultado],
 * para que la UI nunca tenga que manejar excepciones ni códigos HTTP.
 *
 * `engine` permite inyectar un `MockEngine` desde los tests; en producción
 * (engine = null) Ktor elige el motor de la plataforma (OkHttp en Android,
 * Darwin en iOS) vía dependencia de `androidMain`/`iosMain`.
 */
class ApiCliente(
    baseUrl: String = Rutas.BASE,
    private val sesion: SesionRepositorio,
    engine: HttpClientEngine? = null,
) {
    @PublishedApi
    internal val baseUrl: String = baseUrl

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @PublishedApi
    internal val http: HttpClient = if (engine != null) {
        HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            // Importar carta por foto/PDF corre IA de visión (un PDF de varias
            // páginas puede tardar >60s); sin esto el default corta la
            // request y la app muestra "Ocurrió un error".
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                socketTimeoutMillis = 180_000
                connectTimeoutMillis = 30_000
            }
        }
    } else {
        HttpClient {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                socketTimeoutMillis = 180_000
                connectTimeoutMillis = 30_000
            }
        }
    }

    @PublishedApi
    internal suspend fun HttpRequestBuilder.aplicarHeadersSesion(tenantIdOverride: String? = null) {
        val actual = sesion.observar().first()
        if (actual != null) {
            header("Authorization", "Bearer ${actual.token}")
            // [tenantIdOverride] pisa el tenant ACTIVO de la sesión para UNA
            // llamada — lo usa la bandeja global (Fase C2): abrir un lead de
            // otro negocio exige `X-Tenant-Id` del negocio DEL LEAD, sin
            // cambiar la sesión (el modo global nunca cambia el negocio
            // activo).
            (tenantIdOverride ?: actual.tenantIdActivo)?.let { header("X-Tenant-Id", it) }
        }
    }

    /**
     * GET sin body, agrega Authorization + X-Tenant-Id de la sesión activa.
     * [params] se agregan como query string vía `parameter()` de Ktor (que
     * URL-encodea los valores) — antes cada API concatenaba `?k=$v` a mano
     * (deuda del ledger B4, resuelta 2026-07-22).
     */
    suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        tenantId: String? = null,
    ): Resultado<T> =
        ejecutar {
            http.get(baseUrl + path) {
                params.forEach { (clave, valor) -> parameter(clave, valor) }
                aplicarHeadersSesion(tenantIdOverride = tenantId)
            }
        }

    suspend inline fun <reified TRequest, reified TResponse> post(
        path: String,
        body: TRequest,
        requiereSesion: Boolean = false,
        tenantId: String? = null,
    ): Resultado<TResponse> =
        ejecutar {
            http.post(baseUrl + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
                if (requiereSesion) aplicarHeadersSesion(tenantIdOverride = tenantId)
            }
        }

    suspend inline fun <reified TRequest, reified TResponse> patch(
        path: String,
        body: TRequest,
    ): Resultado<TResponse> =
        ejecutar {
            http.patch(baseUrl + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
                aplicarHeadersSesion()
            }
        }

    /** PUT con body — usado por `PUT /perfil` (Fase B.5), que exige el objeto COMPLETO, sin PATCH parcial. */
    suspend inline fun <reified TRequest, reified TResponse> put(
        path: String,
        body: TRequest,
    ): Resultado<TResponse> =
        ejecutar {
            http.put(baseUrl + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
                aplicarHeadersSesion()
            }
        }

    /**
     * DELETE con body JSON (p. ej. `DELETE /dispositivos-push` con
     * `{token}`) — Ktor sí soporta body en DELETE vía `setBody`, aunque no es
     * lo más común; siempre agrega la sesión porque el backend de push
     * autentica igual que el resto de la API.
     */
    suspend inline fun <reified TRequest, reified TResponse> delete(
        path: String,
        body: TRequest,
    ): Resultado<TResponse> =
        ejecutar {
            http.delete(baseUrl + path) {
                contentType(ContentType.Application.Json)
                setBody(body)
                aplicarHeadersSesion()
            }
        }

    @PublishedApi
    internal suspend inline fun <reified T> ejecutar(crossinline llamada: suspend () -> HttpResponse): Resultado<T> {
        // OJO: la cancelación de la corrutina NO es un error de red — hay que
        // relanzarla o el llamador vería un "Sin conexión" fantasma (p. ej. al
        // salir de una pantalla con la carga en vuelo) y además se rompería
        // la cooperación de cancelación de coroutines.
        val respuesta = try {
            llamada()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // AL LOG, no solo al usuario.
            //
            // "Sin conexión" cubre cosas muy distintas —timeout, DNS, TLS,
            // certificado— y sin el detalle hay que adivinar cuál fue. Pasó
            // buscando por qué el buscador de direcciones no cargaba: la
            // excepción se tragaba y no quedaba rastro en ningún lado.
            registrarFalloDeRed(e)
            return Resultado.Error(MENSAJE_SIN_CONEXION)
        }

        val textoBody = try {
            respuesta.bodyAsText()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Resultado.Error(MENSAJE_SIN_CONEXION)
        }

        if (!respuesta.status.isSuccess()) {
            val mensaje = runCatching {
                json.decodeFromString(ErrorResponseDto.serializer(), textoBody).error
            }.getOrNull()

            // El 401 lleva su propio mensaje SOLO si el backend no mandó uno.
            //
            // Un 401 significa dos cosas distintas: en el login es "contraseña
            // incorrecta" —y ahí el backend manda el texto bueno, que hay que
            // respetar—; en cualquier otro endpoint es "tu sesión no vale".
            //
            // Sin este mensaje, una sesión vencida dejaba pantallas vacías sin
            // explicación: el buscador de direcciones "se quedaba buscando" y
            // el origen no se prellenaba, porque cada pantalla se tragaba el
            // error en silencio.
            if (respuesta.status.value == CODIGO_SIN_SESION && mensaje == null) {
                return Resultado.Error(MENSAJE_SESION_VENCIDA, codigo = CODIGO_SIN_SESION)
            }
            return Resultado.Error(mensaje ?: MENSAJE_ERROR_GENERICO, codigo = respuesta.status.value)
        }

        // Respuestas SIN body (204 de `DELETE /flujos/:id`): no hay JSON que
        // decodificar — si el llamador espera `Unit`, el éxito ya está dicho
        // por el status.
        if (textoBody.isBlank() && Unit is T) {
            @Suppress("UNCHECKED_CAST")
            return Resultado.Ok(Unit) as Resultado<T>
        }

        return try {
            val valor = json.decodeFromString(serializer<T>(), textoBody)
            Resultado.Ok(valor)
        } catch (e: Exception) {
            Resultado.Error(MENSAJE_ERROR_GENERICO)
        }
    }
}
