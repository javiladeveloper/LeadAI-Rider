package pe.leadai.rider.ui.alta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.SesionGuardada
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.esperarCondicion
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "alta_rider_vm_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private suspend fun repoConSesion(): SesionRepositorio {
    val repo = SesionRepositorio(dataStoreDePrueba())
    repo.guardar(
        SesionGuardada(
            token = "hilo_u_abc123",
            usuarioNombre = "Rosa",
            usuarioEmail = "rosa@leadai-pe.com",
        ),
    )
    return repo
}

/** Registro de requests bajo Mutex — evita el ConcurrentModificationException documentado en ARQUITECTURA.md. */
private class RegistroRequests {
    private val mutex = Mutex()
    private val lista = mutableListOf<HttpRequestData>()
    suspend fun agregar(r: HttpRequestData) = mutex.withLock { lista.add(r) }
    suspend fun snapshot(): List<HttpRequestData> = mutex.withLock { lista.toList() }
}

private fun engineExitoso(requests: RegistroRequests): MockEngine = MockEngine { request ->
    requests.agregar(request)
    when {
        request.url.encodedPath == "/motorizados/distritos" && request.method == HttpMethod.Get -> respond(
            content = """{"distritos":["Los Olivos","Comas"]}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
        request.url.encodedPath == "/motorizados/validar-dni" && request.method == HttpMethod.Post -> respond(
            // Contrato real de la fila 16: {encontrado, nombreCompleto}.
            content = """{"encontrado":true,"nombreCompleto":"JONATHAN JOAN AVILA HUAMOLLE"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
        request.url.encodedPath == "/motorizados/mi-perfil" && request.method == HttpMethod.Get -> respond(
            // Perfil existente para el modo edición (fila 16 con dni).
            content = """{"perfil":{"id":"m1","usuarioId":"u1","distrito":"Pocollay, Tacna","telefono":"987654321","placa":"ABC-123","dni":"44247191","estado":"pendiente","creadoEn":"2026-07-21T10:00:00.000Z"}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
        request.url.encodedPath == "/motorizados/mi-perfil" && request.method == HttpMethod.Post -> respond(
            // Fixture del contrato real: prisma `PerfilMotorizado.placa` es
            // `String @default("")` — el backend nunca devuelve `null`.
            content = """{"perfil":{"id":"m1","usuarioId":"u1","distrito":"Los Olivos","telefono":"987654321","placa":"","estado":"pendiente","creadoEn":"2026-07-21T10:00:00.000Z"}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
        else -> respond(
            content = """{"error":"ruta no simulada: ${request.url.encodedPath}"}""",
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
}

/**
 * En esta app el alta es SIEMPRE de motorizado: el VM solo depende de
 * [MotorizadosApi] (+ dispatcher de test). Ya no hay empresasApi/configApi/
 * perfilApi/sesion/avisos como en el `CrearNegocioViewModel` del que se portó.
 */
private fun vmDePrueba(
    engine: MockEngine,
    sesionRepo: SesionRepositorio,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
): AltaRiderViewModel = AltaRiderViewModel(
    motorizadosApi = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine)),
    dispatcher = dispatcher,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AltaRiderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun antes() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun despues() {
        Dispatchers.resetMain()
    }

    @Test
    fun cargar_distritos_trae_el_catalogo_del_backend() = runTest {
        // La pantalla llama `cargarDistritos()` al montarse (no hay tarjeta de
        // tipo que lo dispare como en la app de negocio).
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)

        vm.cargarDistritos()
        vm.estado.esperarCondicion { it.distritosDisponibles.isNotEmpty() }
        advanceUntilIdle()

        assertEquals(listOf("Los Olivos", "Comas"), vm.estado.value.distritosDisponibles)
    }

    @Test
    fun crear_motorizado_sin_distrito_muestra_error_local_sin_llamar_la_red() = runTest {
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)
        vm.cambiarTelefono("987654321")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Elige tu distrito", vm.estado.value.error)
        // La validación local frena ANTES del POST.
        assertFalse(requests.snapshot().any { it.url.encodedPath == "/motorizados/mi-perfil" })
    }

    @Test
    fun crear_motorizado_sin_telefono_muestra_error_local() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Ponle tu teléfono para que te contactemos", vm.estado.value.error)
    }

    @Test
    fun crear_motorizado_con_telefono_corto_muestra_error_local() = runTest {
        // `crearSchema` (motorizados.ts) exige `telefono` de mínimo 6
        // caracteres — sin este guard local, el backend devolvería un 400
        // con mensaje crudo de Zod.
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("12345")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Ese teléfono se ve muy corto — revísalo", vm.estado.value.error)
    }

    @Test
    fun crear_motorizado_feliz_manda_post_mi_perfil_y_llama_alExito() = runTest {
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("987654321")
        vm.cambiarPlaca("ABC-123")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(seLlamoAlExito)
        assertNull(vm.estado.value.error)

        val post = requests.snapshot().first {
            it.url.encodedPath == "/motorizados/mi-perfil" && it.method == HttpMethod.Post
        }
        val cuerpo = post.body.toByteArray().decodeToString()
        assertTrue(cuerpo.contains("\"distrito\":\"Los Olivos\""))
        assertTrue(cuerpo.contains("\"telefono\":\"987654321\""))
        assertTrue(cuerpo.contains("\"placa\":\"ABC-123\""))
    }

    @Test
    fun crear_motorizado_placa_es_opcional() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("987654321")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(seLlamoAlExito)
    }

    @Test
    fun crear_motorizado_error_del_backend_expone_el_mensaje_y_no_llama_alExito() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"distrito es requerido"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine, repoConSesion(), testDispatcher)
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("987654321")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("distrito es requerido", vm.estado.value.error)
    }

    // ---- ubicación estructurada del rider (decisión 8) ----

    @Test
    fun distrito_para_backend_arma_el_string_por_departamento() {
        // Lima: el distrito del catálogo va tal cual.
        assertEquals("Los Olivos", distritoParaBackend("Lima", "Los Olivos", ""))
        // Fuera de Lima: distrito del catálogo local + departamento.
        assertEquals("Pocollay, Tacna", distritoParaBackend("Tacna", "Pocollay", ""))
        // "Otro…": va el texto libre (+ departamento si no es Lima).
        assertEquals("Locumba, Tacna", distritoParaBackend("Tacna", "Otro…", " Locumba "))
        assertEquals("Chosica", distritoParaBackend("Lima", "Otro", "Chosica"))
        // Faltantes → null (validación local los frena).
        assertEquals(null, distritoParaBackend("Tacna", null, ""))
        assertEquals(null, distritoParaBackend("Tacna", "Otro…", "  "))
    }

    @Test
    fun distritos_de_usa_backend_para_lima_y_catalogo_local_para_el_resto() {
        val lima = distritosDe("Lima", listOf("Los Olivos", "Comas", "Otro"))
        assertEquals(listOf("Los Olivos", "Comas", "Otro"), lima)

        val tacna = distritosDe("Tacna", emptyList())
        assertTrue("Pocollay" in tacna)
        assertEquals("Otro…", tacna.last())
    }

    @Test
    fun crear_motorizado_fuera_de_lima_manda_distrito_con_departamento() = runTest {
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)
        vm.cargarDistritos()
        advanceUntilIdle()

        vm.elegirDepartamento("Tacna")
        vm.elegirDistrito("Pocollay")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("987654321")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(seLlamoAlExito)
        val post = requests.snapshot().last {
            it.url.encodedPath == "/motorizados/mi-perfil" && it.method == HttpMethod.Post
        }
        val cuerpo = post.body.toByteArray().decodeToString()
        assertTrue(cuerpo.contains("\"distrito\":\"Pocollay, Tacna\""))
    }

    @Test
    fun dni_de_menos_de_8_digitos_muestra_error_local() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.cargarDistritos()
        advanceUntilIdle()
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("4424")
        vm.cambiarTelefono("987654321")

        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("Tu DNI de 8 dígitos — lo necesitamos para verificarte", vm.estado.value.error)
    }

    @Test
    fun dni_completo_valida_en_vivo_y_muestra_el_nombre_oficial() = runTest {
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)
        vm.cargarDistritos()
        advanceUntilIdle()

        // Solo dígitos y máximo 8 — la basura se filtra al escribir.
        vm.cambiarDni("44a24-7191xx")
        assertEquals("44247191", vm.estado.value.dni)
        advanceUntilIdle()
        vm.estado.esperarCondicion { !it.validandoDni }

        assertEquals("JONATHAN JOAN AVILA HUAMOLLE", vm.estado.value.nombreOficialDni)
        assertTrue(requests.snapshot().any { it.url.encodedPath == "/motorizados/validar-dni" })

        // El alta manda el dni al backend.
        vm.elegirDistrito("Los Olivos")
        vm.cambiarTelefono("987654321")
        vm.guardar { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        val post = requests.snapshot().last {
            it.url.encodedPath == "/motorizados/mi-perfil" && it.method == HttpMethod.Post
        }
        assertTrue(post.body.toByteArray().decodeToString().contains("\"dni\":\"44247191\""))
    }

    @Test
    fun placa_se_normaliza_y_un_formato_raro_frena_con_error_local() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.cargarDistritos()
        advanceUntilIdle()

        // Normalización al escribir: mayúsculas, sin espacios ni símbolos raros.
        vm.cambiarPlaca(" abc 123! ")
        assertEquals("ABC123", vm.estado.value.placa)

        // Funciones puras del chequeo laxo.
        assertTrue(placaLuceValida(""))          // opcional: bici sin placa
        assertTrue(placaLuceValida("ABC-123"))   // auto
        assertTrue(placaLuceValida("1234-AB"))   // moto
        assertFalse(placaLuceValida("AB"))       // typo obvio
        assertFalse(placaLuceValida("ABCD-12345678")) // demasiado larga

        // Con placa rara, el alta frena ANTES de la red.
        vm.elegirDistrito("Los Olivos")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("987654321")
        vm.cambiarPlaca("AB")
        var seLlamoAlExito = false
        vm.guardar { seLlamoAlExito = true }
        advanceUntilIdle()
        assertFalse(seLlamoAlExito)
        assertEquals("Esa placa se ve rara — revísala (ej: ABC-123 o 1234-AB)", vm.estado.value.error)
    }

    @Test
    fun ubicacion_desde_backend_invierte_la_convencion() {
        assertEquals("Tacna" to "Pocollay", ubicacionDesdeBackend("Pocollay, Tacna"))
        assertEquals("Lima" to "Los Olivos", ubicacionDesdeBackend("Los Olivos"))
        // Sufijo que no es un departamento conocido: se trata como Lima entero.
        assertEquals("Lima" to "Villa María, Sector 3", ubicacionDesdeBackend("Villa María, Sector 3"))
    }

    @Test
    fun preparar_edicion_rider_prellena_el_formulario_con_el_perfil() = runTest {
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)

        vm.prepararEdicion()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.distrito != null }

        assertEquals("Tacna", vm.estado.value.departamento)
        assertEquals("Pocollay", vm.estado.value.distrito)
        assertEquals("987654321", vm.estado.value.telefono)
        assertEquals("ABC-123", vm.estado.value.placa)
        assertEquals("44247191", vm.estado.value.dni)
        // El pre-llenado del DNI re-dispara la validación en vivo.
        vm.estado.esperarCondicion { !it.validandoDni }
        advanceUntilIdle()
        assertEquals("JONATHAN JOAN AVILA HUAMOLLE", vm.estado.value.nombreOficialDni)
    }

    // ---- tipo de vehículo: la sugerencia de monto depende de él ----

    @Test
    fun el_tipo_de_vehiculo_arranca_en_moto() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)

        assertEquals("moto", vm.estado.value.tipoVehiculo)
    }

    @Test
    fun se_puede_elegir_auto() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)

        vm.elegirTipoVehiculo("auto")

        assertEquals("auto", vm.estado.value.tipoVehiculo)
    }

    @Test
    fun guardar_manda_el_tipo_de_vehiculo() = runTest {
        // El backend usa `tipoVehiculo` para sugerir el monto: un auto
        // consume ~3x más que una moto, así que su tarifa por km es mayor.
        val requests = RegistroRequests()
        val vm = vmDePrueba(engineExitoso(requests), repoConSesion(), testDispatcher)
        vm.elegirDepartamento("Tacna")
        vm.elegirDistrito("Tacna")
        vm.cambiarDni("44247191")
        vm.cambiarTelefono("952123456")
        vm.elegirTipoVehiculo("auto")

        vm.guardar { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        val post = requests.snapshot().last {
            it.url.encodedPath == "/motorizados/mi-perfil" && it.method == HttpMethod.Post
        }
        val cuerpo = post.body.toByteArray().decodeToString()
        assertTrue(cuerpo.contains("\"tipoVehiculo\":\"auto\""))
    }

    @Test
    fun preparar_edicion_prellena_el_tipo_de_vehiculo_del_perfil() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)

        vm.prepararEdicion()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.distrito != null }

        // El fixture de `/mi-perfil` no trae `tipoVehiculo` (perfil viejo):
        // el default del DTO mantiene "moto" en vez de reventar.
        assertEquals("moto", vm.estado.value.tipoVehiculo)
    }

    @Test
    fun cambiar_departamento_limpia_el_distrito_elegido() = runTest {
        val vm = vmDePrueba(engineExitoso(RegistroRequests()), repoConSesion(), testDispatcher)
        vm.cargarDistritos()
        advanceUntilIdle()
        vm.elegirDistrito("Los Olivos")

        vm.elegirDepartamento("Cusco")

        assertEquals(null, vm.estado.value.distrito)
        assertEquals("Cusco", vm.estado.value.departamento)
    }
}
