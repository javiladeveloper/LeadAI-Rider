# App — Modo cliente Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un usuario pueda pedir una moto desde la app —encomienda o pasajero—, esperar a que un rider la tome y seguir el viaje.

**Architecture:** La app pasa de ser solo-rider a tener dos modos. Al arrancar se elige "Pido un motorizado" o "Manejo"; un rider con perfil ya creado entra directo a conducir, sin ver pantallas nuevas. El modo cliente es una capa nueva (`datos/CarrerasClienteApi`, `ui/cliente/*`) que consume los cuatro endpoints que el backend ya expone. El modo conductor no se toca.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor, Koin, DataStore, kotlin.test + MockEngine.

**Repo:** `d:\Personal Proyects\LeadAI-Rider` — todos los commits van acá.

## Global Constraints

- Todo el código, comentarios y textos de UI en **español**.
- Montos en **centavos** (`Long`), formateados con `centavosASoles()` de `ui/tema/Formato.kt`.
- **Nunca sumar `montoOfrecido` + `montoCompraEstimado`.** El flete es lo que paga por el servicio; el monto de compra es lo que le devuelve al rider por lo que compró. Van siempre separados, con etiquetas distintas.
- Todos los campos de DTO llevan **default** — el backend puede omitirlos sin romper la deserialización.
- Design system "Brand Harmony": teal `MaterialTheme.colorScheme.primary` acción, `TokensExtra.calor` coral urgencia, `TokensExtra.espera` ámbar.
- **El modo conductor no se rompe:** un usuario con perfil de motorizado entra a conducir exactamente igual que hoy. Hay un test que lo fija.
- Tests con `./gradlew.bat :composeApp:testDebugUnitTest`, `MockEngine` inyectado al `ApiCliente` y dispatcher inyectado al ViewModel (patrón de `CarrerasViewModelTest.kt`).
- Commits en español con prefijo `feat:` / `test:` / `fix:`.

## Contrato del backend (verificado en producción 2026-07-28)

**`POST /carreras/sugerir`** → `{ kmEstimado, montoSugerido, origen: {texto, lat, lng}, destino: {texto, lat, lng} }`

**`POST /carreras`** con `{ tipo, origenTexto, origenLat?, origenLng?, destinoTexto, destinoLat?, destinoLng?, montoOfrecidoCentavos?, montoCompraEstimadoCentavos?, notas?, contacto? }` → `{ ok, id, montoSugerido, montoOfrecido, expiraEnMinutos }`
- `tipo` solo acepta `"encomienda"` o `"pasajero"`.
- **409** si ya tiene una carrera activa (con `carreraId` en el body).

**`GET /carreras/mia`** → `{ carrera: null }` o `{ carrera: { id, tipo, estado, origenTexto, destinoTexto, montoOfrecido, montoCompraEstimado, kmEstimado, notas, recogido, creadoEn, expiraEn, riderNombre, riderTelefono, riderPlaca, riderVehiculo } }`
- `estado`: `disponible` | `aceptada` | `recogida` | `entregada` | `cancelada` | `expirada`
- Los campos `rider*` vienen `null` mientras nadie la tomó.

**`POST /carreras/:id/cancelar`** → `{ ok: true }`, o **409** si ya la tomó un rider.

---

### Task 1: API del cliente

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/CarrerasClienteApi.kt`
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt` (agregar DTOs al final)
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/CarrerasClienteApiTest.kt`

**Interfaces:**
- Consumes: `ApiCliente`, `Resultado`
- Produces:
  - `CarrerasClienteApi.sugerir(...): Resultado<SugerenciaDto>`
  - `CarrerasClienteApi.pedir(...): Resultado<CarreraCreadaDto>`
  - `CarrerasClienteApi.miCarrera(): Resultado<CarreraClienteDto?>`
  - `CarrerasClienteApi.cancelar(id: String): Resultado<Unit>`

- [ ] **Step 1: Escribir el test que falla**

Crear `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/CarrerasClienteApiTest.kt`:

```kotlin
package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "cliente_api_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private fun apiCon(engine: MockEngine) =
    CarrerasClienteApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine))

class CarrerasClienteApiTest {

    @Test
    fun sugerir_devuelve_monto_y_km() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"kmEstimado":3.0,"montoSugerido":760,
                    "origen":{"texto":"Av. Grau 240","lat":-18.0,"lng":-70.24},
                    "destino":{"texto":"Jose Olaya 110","lat":-18.01,"lng":-70.25}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).sugerir(
            tipo = "pasajero",
            origenTexto = "Av. Grau 240",
            origenLat = -18.0,
            origenLng = -70.24,
            destinoTexto = "Jose Olaya 110",
        )

        val s = (r as Resultado.Ok).valor
        assertEquals(760L, s.montoSugerido)
        assertEquals(3.0, s.kmEstimado)
    }

    @Test
    fun pedir_manda_el_flete_y_la_compra_por_separado() = runTest {
        var cuerpo = ""
        val engine = MockEngine { peticion ->
            cuerpo = (peticion.body as io.ktor.http.content.TextContent).text
            respond(
                content = """{"ok":true,"id":"c1","montoSugerido":760,"montoOfrecido":800,"expiraEnMinutos":15}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).pedir(
            tipo = "encomienda",
            origenTexto = "Chifa Salon Canton",
            origenLat = null,
            origenLng = null,
            destinoTexto = "Jose Olaya 110",
            destinoLat = null,
            destinoLng = null,
            montoOfrecidoCentavos = 800,
            montoCompraEstimadoCentavos = 6000,
            notas = "combinado sin verduras",
            contacto = "952123456",
        )

        assertTrue(r is Resultado.Ok)
        // Flete y compra viajan como campos DISTINTOS, nunca sumados.
        assertTrue(cuerpo.contains("\"montoOfrecidoCentavos\":800"))
        assertTrue(cuerpo.contains("\"montoCompraEstimadoCentavos\":6000"))
    }

    @Test
    fun pedir_con_carrera_activa_devuelve_409() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Ya tienes una carrera en curso","carreraId":"c-vieja"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).pedir(
            tipo = "pasajero", origenTexto = "A", origenLat = null, origenLng = null,
            destinoTexto = "B", destinoLat = null, destinoLng = null,
            montoOfrecidoCentavos = null, montoCompraEstimadoCentavos = null,
            notas = null, contacto = null,
        )

        assertEquals(409, (r as Resultado.Error).codigo)
    }

    @Test
    fun mi_carrera_sin_carrera_activa_es_null_no_error() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).miCarrera()

        assertNull((r as Resultado.Ok).valor)
    }

    @Test
    fun mi_carrera_aceptada_trae_los_datos_del_rider() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":{"id":"c1","tipo":"pasajero","estado":"aceptada",
                    "origenTexto":"Av. Grau 240","destinoTexto":"Jose Olaya 110",
                    "montoOfrecido":760,"montoCompraEstimado":null,"kmEstimado":3.0,
                    "notas":"","recogido":false,"creadoEn":"2026-07-28T10:00:00.000Z",
                    "expiraEn":null,"riderNombre":"Ana","riderTelefono":"952123456",
                    "riderPlaca":"ABC-123","riderVehiculo":"moto"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val c = (apiCon(engine).miCarrera() as Resultado.Ok).valor!!

        assertEquals("aceptada", c.estado)
        assertEquals("Ana", c.riderNombre)
        assertEquals("ABC-123", c.riderPlaca)
    }

    @Test
    fun cancelar_una_carrera_ya_tomada_devuelve_409() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Esa carrera ya no se puede cancelar"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).cancelar("c1")

        assertEquals(409, (r as Resultado.Error).codigo)
    }
}
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest --tests "*CarrerasClienteApiTest*"
```

Expected: FAIL — no existe `CarrerasClienteApi`.

- [ ] **Step 3: Agregar los DTOs**

Al final de `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt`:

```kotlin
// ── Modo CLIENTE: pedir una moto ────────────────────────────────────────

/** Un punto del mapa resuelto por el backend (GPS del cliente o geocodificado). */
@Serializable
data class UbicacionDto(
    val texto: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

/** `POST /carreras/sugerir` → cuánto conviene ofrecer, antes de crear nada. */
@Serializable
data class SugerenciaDto(
    val kmEstimado: Double? = null,
    val montoSugerido: Long = 0,
    val origen: UbicacionDto = UbicacionDto(),
    val destino: UbicacionDto = UbicacionDto(),
)

/** `POST /carreras` → la carrera recién pedida. */
@Serializable
data class CarreraCreadaDto(
    val ok: Boolean = false,
    val id: String = "",
    val montoSugerido: Long = 0,
    val montoOfrecido: Long = 0,
    val expiraEnMinutos: Int = 15,
)

/**
 * `GET /carreras/mia` → la carrera activa del cliente. Los campos `rider*`
 * llegan `null` mientras nadie la tomó.
 */
@Serializable
data class CarreraClienteDto(
    val id: String,
    val tipo: String = "pasajero",
    /** `disponible` | `aceptada` | `recogida` | `entregada` | `cancelada` | `expirada`. */
    val estado: String = "disponible",
    val origenTexto: String = "",
    val destinoTexto: String = "",
    /** El FLETE: lo que el cliente paga por el servicio. */
    val montoOfrecido: Long = 0,
    /**
     * Solo cuando el rider tiene que comprar algo: lo que le devuelve el
     * cliente además del flete. NUNCA se suma al monto ofrecido.
     */
    val montoCompraEstimado: Long? = null,
    val kmEstimado: Double? = null,
    val notas: String = "",
    val recogido: Boolean = false,
    val creadoEn: String = "",
    val expiraEn: String? = null,
    val riderNombre: String? = null,
    val riderTelefono: String? = null,
    val riderPlaca: String? = null,
    val riderVehiculo: String? = null,
)

/** `GET /carreras/mia` → `{"carrera": null | {...}}`. */
@Serializable
data class MiCarreraClienteDto(
    val carrera: CarreraClienteDto? = null,
)
```

- [ ] **Step 4: Implementar la API**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/CarrerasClienteApi.kt`:

```kotlin
package pe.leadai.rider.datos

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * El lado CLIENTE: pedir una moto. Encomienda ("tráeme un chifa del Salón
 * Cantón" o "llevá esta caja") o pasajero.
 *
 * Los pedidos de restaurante NO nacen acá — esos los crea el negocio en su
 * propia app y le llegan al rider por el pool.
 */
class CarrerasClienteApi(private val api: ApiCliente) {

    /**
     * `POST /carreras/sugerir` — cuánto conviene ofrecer, SIN crear nada. Es
     * una sugerencia editable, no una tarifa: el precio final lo acuerdan el
     * cliente y el rider.
     */
    suspend fun sugerir(
        tipo: String,
        origenTexto: String,
        origenLat: Double? = null,
        origenLng: Double? = null,
        destinoTexto: String,
        destinoLat: Double? = null,
        destinoLng: Double? = null,
    ): Resultado<SugerenciaDto> =
        api.post<JsonObject, SugerenciaDto>(
            path = "/carreras/sugerir",
            body = buildJsonObject {
                put("origenTexto", origenTexto)
                origenLat?.let { put("origenLat", it) }
                origenLng?.let { put("origenLng", it) }
                put("destinoTexto", destinoTexto)
                destinoLat?.let { put("destinoLat", it) }
                destinoLng?.let { put("destinoLng", it) }
            },
            requiereSesion = true,
        )

    /**
     * `POST /carreras` — pedir la moto. Devuelve 409 si el cliente ya tiene
     * una carrera activa: una a la vez, para no llenar el pool de pedidos
     * que nadie va a atender.
     *
     * `montoCompraEstimadoCentavos` viaja SEPARADO del flete: es plata que el
     * rider adelanta y el cliente le devuelve, no parte del precio.
     */
    suspend fun pedir(
        tipo: String,
        origenTexto: String,
        origenLat: Double?,
        origenLng: Double?,
        destinoTexto: String,
        destinoLat: Double?,
        destinoLng: Double?,
        montoOfrecidoCentavos: Long?,
        montoCompraEstimadoCentavos: Long?,
        notas: String?,
        contacto: String?,
    ): Resultado<CarreraCreadaDto> =
        api.post<JsonObject, CarreraCreadaDto>(
            path = "/carreras",
            body = buildJsonObject {
                put("tipo", tipo)
                put("origenTexto", origenTexto)
                origenLat?.let { put("origenLat", it) }
                origenLng?.let { put("origenLng", it) }
                put("destinoTexto", destinoTexto)
                destinoLat?.let { put("destinoLat", it) }
                destinoLng?.let { put("destinoLng", it) }
                montoOfrecidoCentavos?.let { put("montoOfrecidoCentavos", it) }
                montoCompraEstimadoCentavos?.let { put("montoCompraEstimadoCentavos", it) }
                if (!notas.isNullOrBlank()) put("notas", notas)
                if (!contacto.isNullOrBlank()) put("contacto", contacto)
            },
            requiereSesion = true,
        )

    /**
     * `GET /carreras/mia` → la carrera activa, o `null` si no tiene ninguna.
     * `null` NO es un error: es el estado normal de quien no pidió nada.
     */
    suspend fun miCarrera(): Resultado<CarreraClienteDto?> =
        when (val respuesta = api.get<MiCarreraClienteDto>("/carreras/mia")) {
            is Resultado.Ok -> Resultado.Ok(respuesta.valor.carrera)
            is Resultado.Error -> respuesta
        }

    /** `POST /carreras/:id/cancelar` — 409 si un rider ya la tomó (está yendo). */
    suspend fun cancelar(carreraId: String): Resultado<AvanzarEstadoResponseDto> =
        api.post<JsonObject, AvanzarEstadoResponseDto>(
            path = "/carreras/$carreraId/cancelar",
            body = buildJsonObject { },
            requiereSesion = true,
        )
}
```

- [ ] **Step 5: Registrar en Koin**

En `composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt`, junto a los otros `single`:

```kotlin
    single { CarrerasClienteApi(get()) }
```

Y el import correspondiente.

- [ ] **Step 6: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*CarrerasClienteApiTest*"
```

Expected: PASS — 6 tests.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/ composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/CarrerasClienteApiTest.kt
git commit -m "feat: API del cliente para pedir carreras"
```

---

### Task 2: Elección de modo al arrancar

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/ModoApp.kt`
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/modo/ElegirModoPantalla.kt`
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/ModoAppTest.kt`

**Interfaces:**
- Consumes: `SesionRepositorio`, `MotorizadosApi`
- Produces:
  - `ModoRepositorio.observar(): Flow<String?>` — `"cliente"` | `"conductor"` | `null`
  - `ModoRepositorio.guardar(modo: String)`
  - `rutaTrasIniciarSesion(motorizadosApi, modoRepositorio): String`

**Contexto crítico:** hoy [`Navegacion.kt:74-78`](../../../composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt) manda al alta de motorizado a cualquiera sin perfil. Un cliente quedaría atrapado ahí, pidiéndole DNI y placa.

**Regla que no se puede violar:** un usuario **con perfil de motorizado** entra directo a conducir, sin ver la pantalla de elección. Su experiencia no cambia en nada.

- [ ] **Step 1: Escribir el test que falla**

Crear `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/ModoAppTest.kt`:

```kotlin
package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "modo_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

class ModoAppTest {

    @Test
    fun sin_elegir_el_modo_es_null() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        assertNull(repo.observar().first())
    }

    @Test
    fun el_modo_elegido_se_recuerda() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        repo.guardar(ModoRepositorio.CLIENTE)

        assertEquals(ModoRepositorio.CLIENTE, repo.observar().first())
    }

    @Test
    fun se_puede_cambiar_de_modo() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        repo.guardar(ModoRepositorio.CLIENTE)
        repo.guardar(ModoRepositorio.CONDUCTOR)

        assertEquals(ModoRepositorio.CONDUCTOR, repo.observar().first())
    }
}
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*ModoAppTest*"
```

Expected: FAIL — no existe `ModoRepositorio`.

- [ ] **Step 3: Implementar el repositorio**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/ModoApp.kt`:

```kotlin
package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * En qué modo está usando la app: pidiendo una moto o manejando. Se guarda
 * porque un usuario que ya eligió no debería tener que elegir cada vez.
 *
 * `null` = todavía no eligió. Un usuario CON perfil de motorizado nunca ve la
 * pantalla de elección: entra directo a conducir.
 */
class ModoRepositorio(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val CLIENTE = "cliente"
        const val CONDUCTOR = "conductor"
    }

    suspend fun guardar(modo: String) {
        dataStore.edit { prefs -> prefs[KEY_MODO] = modo }
    }

    fun observar(): Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_MODO] }
}

private val KEY_MODO = stringPreferencesKey("modo_app")
```

- [ ] **Step 4: La pantalla de elección**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/modo/ElegirModoPantalla.kt`:

```kotlin
package pe.leadai.rider.ui.modo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Lo primero que ve alguien que abre la app sin haber elegido todavía: ¿viene
 * a pedir una moto o a manejar?
 *
 * Un usuario que YA tiene perfil de motorizado no pasa por acá — entra directo
 * a conducir.
 */
@Composable
fun ElegirModoPantalla(
    alElegirCliente: () -> Unit,
    alElegirConductor: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🛵", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "¿Qué vas a hacer?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Podés cambiar cuando quieras",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = alElegirCliente,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("🛵 Pido un motorizado", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = alElegirConductor,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("🏍️ Manejo", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Text(
                "Para manejar te vamos a pedir tu DNI y los datos de tu vehículo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 5: Cambiar el arranque**

En `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt`:

Agregar la ruta nueva a `object Rutas`:

```kotlin
    /** Elegir si viene a pedir una moto o a manejar. */
    const val ELEGIR_MODO = "elegir_modo"

    /** Modo cliente: pedir una moto y seguir el viaje. */
    const val CLIENTE = "cliente"
```

Reemplazar `rutaTrasIniciarSesion` por:

```kotlin
/**
 * A dónde va un usuario que acaba de autenticarse (o que abre la app con
 * sesión guardada).
 *
 * El perfil de motorizado MANDA: quien ya se dio de alta como rider entra
 * directo a trabajar, sin pasar por la elección de modo. Solo quien no es
 * rider elige — y si ya eligió antes, se respeta lo que eligió.
 */
internal suspend fun rutaTrasIniciarSesion(
    motorizadosApi: MotorizadosApi,
    modoGuardado: String?,
): String {
    val perfil = when (val r = motorizadosApi.miPerfil()) {
        is Resultado.Ok -> r.valor
        // Sin conexión no se puede saber si es rider. Que elija: es el único
        // camino que no lo deja trabado en un formulario que quizás no le toca.
        is Resultado.Error -> return modoGuardado?.let { rutaDeModo(it) } ?: Rutas.ELEGIR_MODO
    }
    if (perfil != null) return Rutas.CARRERAS
    return when (modoGuardado) {
        ModoRepositorio.CLIENTE -> Rutas.CLIENTE
        ModoRepositorio.CONDUCTOR -> Rutas.ALTA
        else -> Rutas.ELEGIR_MODO
    }
}

private fun rutaDeModo(modo: String): String =
    if (modo == ModoRepositorio.CONDUCTOR) Rutas.ALTA else Rutas.CLIENTE
```

En el composable `NavegacionRaiz`, inyectar el repositorio y observar el modo:

```kotlin
    val modoRepositorio = koinInject<ModoRepositorio>()
    val modoGuardado by modoRepositorio.observar().collectAsState(initial = null)
```

y pasar `modoGuardado` a las dos llamadas de `rutaTrasIniciarSesion`.

Agregar las dos rutas al `NavHost`:

```kotlin
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
```

(`ClientePantalla` se crea en el Task 4. Hasta entonces, para que compile, dejá un stub temporal — pero **preferentemente hacé el Task 3 y 4 antes de este paso**, o creá el archivo con un `Column` vacío que se completa después.)

Registrar `ModoRepositorio` en Koin (`Modulos.kt`):

```kotlin
    single { ModoRepositorio(get()) }
```

- [ ] **Step 6: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
```

Expected: PASS — toda la suite. Si algún test de navegación existente rompe por el parámetro nuevo de `rutaTrasIniciarSesion`, adaptalo pasando `modoGuardado = null`.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/ModoApp.kt composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/modo/ composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/ModoAppTest.kt
git commit -m "feat: elegir entre pedir una moto y manejar"
```

---

### Task 3: ViewModel del cliente

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModelTest.kt`

**Interfaces:**
- Consumes: `CarrerasClienteApi` (Task 1), `AvisosGlobales`, `obtenerUbicacionActual`
- Produces: `ClienteUiState`, `ClienteViewModel` con `cargar()`, `pedir()`, `cancelar()`, y los setters del formulario

- [ ] **Step 1: Escribir el test que falla**

Crear `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModelTest.kt`, siguiendo el patrón de `CarrerasViewModelTest.kt` (dispatcher inyectado, `esperarCondicion`, MockEngine):

```kotlin
package pe.leadai.rider.ui.cliente

// (imports siguiendo el patrón de CarrerasViewModelTest.kt)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClienteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest fun antes() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun despues() { Dispatchers.resetMain() }

    @Test
    fun sin_carrera_activa_muestra_el_formulario() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertNull(vm.estado.value.miCarrera)
    }

    @Test
    fun con_carrera_activa_la_expone() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":{"id":"c1","tipo":"pasajero","estado":"aceptada",
                    "origenTexto":"A","destinoTexto":"B","montoOfrecido":760,
                    "riderNombre":"Ana","riderPlaca":"ABC-123"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.cargar()
        vm.estado.esperarCondicion { it.miCarrera != null }
        advanceUntilIdle()

        assertEquals("aceptada", vm.estado.value.miCarrera?.estado)
        assertEquals("Ana", vm.estado.value.miCarrera?.riderNombre)
    }

    @Test
    fun pedir_sin_destino_avisa_y_no_llama_al_backend() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        advanceUntilIdle()
        val antes = llamadas

        vm.cambiarOrigen("Av. Grau 240")
        vm.pedir() // sin destino

        advanceUntilIdle()
        assertEquals(antes, llamadas)
        assertEquals("Falta el destino", vm.estado.value.error)
    }

    @Test
    fun el_409_avisa_que_ya_tiene_una_carrera() = runTest {
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras")) {
                respond(
                    content = """{"error":"Ya tienes una carrera en curso"}""",
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"carrera":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        advanceUntilIdle()

        vm.cambiarOrigen("A")
        vm.cambiarDestino("B")
        vm.pedir()
        advanceUntilIdle()

        assertTrue(vm.estado.value.error?.contains("carrera en curso") == true)
    }

    @Test
    fun el_monto_de_compra_solo_aplica_a_encomienda() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.elegirTipo("encomienda")
        vm.cambiarMontoCompra("60")
        assertEquals("60", vm.estado.value.montoCompra)

        // Al pasar a pasajero, el monto de compra se limpia: un pasajero no
        // manda al rider a comprar nada.
        vm.elegirTipo("pasajero")
        assertEquals("", vm.estado.value.montoCompra)
    }
}
```

Agregar el helper `vmDePrueba(engine)` que arma el `ClienteViewModel` con `CarrerasClienteApi`, `AvisosGlobales()`, el `testDispatcher` y un `obtenerUbicacion` que devuelve `null` (sin GPS en tests).

- [ ] **Step 2: Correr para verificar que falla**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*ClienteViewModelTest*"
```

Expected: FAIL — no existe `ClienteViewModel`.

- [ ] **Step 3: Implementar**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModel.kt`:

```kotlin
package pe.leadai.rider.ui.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.ui.carreras.UbicacionRider
import pe.leadai.rider.ui.carreras.obtenerUbicacionActual
import pe.leadai.rider.ui.comunes.AvisosGlobales

/** Estado de la pantalla del cliente: o está pidiendo, o está esperando/siguiendo. */
data class ClienteUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    /** La carrera activa. Si no es null, la pantalla muestra el seguimiento. */
    val miCarrera: CarreraClienteDto? = null,
    /** `encomienda` | `pasajero`. */
    val tipo: String = "pasajero",
    val origen: String = "",
    val origenLat: Double? = null,
    val origenLng: Double? = null,
    val destino: String = "",
    /** Lo que ofrece pagar, en soles como texto (lo edita el usuario). */
    val monto: String = "",
    /** Solo encomienda: cuánto cuesta lo que el rider va a comprar. */
    val montoCompra: String = "",
    val notas: String = "",
    val contacto: String = "",
    /** Lo que sugiere el sistema, en centavos — punto de partida editable. */
    val montoSugerido: Long? = null,
    val kmEstimado: Double? = null,
    val pidiendo: Boolean = false,
)

private const val MENSAJE_SIN_ORIGEN = "Falta el origen"
private const val MENSAJE_SIN_DESTINO = "Falta el destino"

/**
 * El lado CLIENTE: pedir una moto y seguir el viaje.
 *
 * El monto es una SUGERENCIA editable, no una tarifa: LeadAI enlaza, no fija
 * precios. El pago es en efectivo entre el cliente y el rider.
 */
class ClienteViewModel(
    private val api: CarrerasClienteApi,
    private val avisos: AvisosGlobales,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val obtenerUbicacion: suspend () -> UbicacionRider? = { obtenerUbicacionActual() },
) : ViewModel() {

    private val _estado = MutableStateFlow(ClienteUiState())
    val estado: StateFlow<ClienteUiState> = _estado.asStateFlow()

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (val r = api.miCarrera()) {
                is Resultado.Ok -> _estado.update { it.copy(cargando = false, miCarrera = r.valor) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, error = r.mensaje) }
            }
        }
        usarMiUbicacion()
    }

    /** Refresco silencioso: un fallo puntual no debe borrar lo que ya se ve. */
    fun refrescar() {
        viewModelScope.launch(dispatcher) {
            when (val r = api.miCarrera()) {
                is Resultado.Ok -> _estado.update { it.copy(miCarrera = r.valor) }
                is Resultado.Error -> Unit
            }
        }
    }

    /** "Estoy acá": toma el GPS del teléfono como origen. Silencioso si no hay. */
    fun usarMiUbicacion() {
        viewModelScope.launch(dispatcher) {
            val u = obtenerUbicacion() ?: return@launch
            _estado.update {
                it.copy(
                    origenLat = u.lat,
                    origenLng = u.lng,
                    origen = if (it.origen.isBlank()) "Mi ubicación actual" else it.origen,
                )
            }
        }
    }

    fun elegirTipo(tipo: String) {
        _estado.update {
            // Un pasajero no manda al rider a comprar nada: si cambia de tipo,
            // el monto de compra deja de tener sentido.
            it.copy(
                tipo = tipo,
                montoCompra = if (tipo == "encomienda") it.montoCompra else "",
                error = null,
            )
        }
    }

    fun cambiarOrigen(valor: String) {
        // Si escribe el origen a mano, el GPS deja de aplicar.
        _estado.update { it.copy(origen = valor, origenLat = null, origenLng = null, error = null) }
    }

    fun cambiarDestino(valor: String) = _estado.update { it.copy(destino = valor, error = null) }
    fun cambiarMonto(valor: String) = _estado.update { it.copy(monto = soloNumeros(valor), error = null) }
    fun cambiarMontoCompra(valor: String) = _estado.update { it.copy(montoCompra = soloNumeros(valor), error = null) }
    fun cambiarNotas(valor: String) = _estado.update { it.copy(notas = valor, error = null) }
    fun cambiarContacto(valor: String) = _estado.update { it.copy(contacto = valor, error = null) }

    /** Pide la sugerencia de monto al backend, para mostrarla antes de confirmar. */
    fun pedirSugerencia() {
        val a = _estado.value
        if (a.origen.isBlank() || a.destino.isBlank()) return
        viewModelScope.launch(dispatcher) {
            when (
                val r = api.sugerir(
                    tipo = a.tipo,
                    origenTexto = a.origen,
                    origenLat = a.origenLat,
                    origenLng = a.origenLng,
                    destinoTexto = a.destino,
                )
            ) {
                is Resultado.Ok -> _estado.update {
                    it.copy(
                        montoSugerido = r.valor.montoSugerido,
                        kmEstimado = r.valor.kmEstimado,
                        // Solo pre-llena si el usuario no escribió su monto.
                        monto = if (it.monto.isBlank()) (r.valor.montoSugerido / 100).toString() else it.monto,
                    )
                }
                is Resultado.Error -> Unit // la sugerencia es un nice-to-have
            }
        }
    }

    fun pedir() {
        val a = _estado.value
        if (a.pidiendo) return
        if (a.origen.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_ORIGEN) }
            return
        }
        if (a.destino.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_DESTINO) }
            return
        }

        _estado.update { it.copy(pidiendo = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (
                val r = api.pedir(
                    tipo = a.tipo,
                    origenTexto = a.origen,
                    origenLat = a.origenLat,
                    origenLng = a.origenLng,
                    destinoTexto = a.destino,
                    destinoLat = null,
                    destinoLng = null,
                    montoOfrecidoCentavos = aCentavos(a.monto),
                    // El monto de compra SOLO en encomienda: es lo que el rider
                    // adelanta y el cliente le devuelve, nunca parte del flete.
                    montoCompraEstimadoCentavos = if (a.tipo == "encomienda") aCentavos(a.montoCompra) else null,
                    notas = a.notas,
                    contacto = a.contacto,
                )
            ) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(pidiendo = false) }
                    avisos.mostrar("🛵 Buscando motorizado…")
                    refrescar()
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(pidiendo = false, error = r.mensaje) }
                    avisos.mostrar(r.mensaje)
                }
            }
        }
    }

    fun cancelar() {
        val id = _estado.value.miCarrera?.id ?: return
        viewModelScope.launch(dispatcher) {
            when (val r = api.cancelar(id)) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(miCarrera = null) }
                    avisos.mostrar("Carrera cancelada")
                }
                is Resultado.Error -> avisos.mostrar(r.mensaje)
            }
        }
    }
}

private fun soloNumeros(valor: String): String = valor.filter { it.isDigit() }.take(5)

/** Soles como texto → centavos. Vacío = null (que el backend sugiera). */
private fun aCentavos(soles: String): Long? =
    soles.trim().takeIf { it.isNotBlank() }?.toLongOrNull()?.let { it * 100 }
```

- [ ] **Step 4: Registrar en Koin**

En `Modulos.kt`:

```kotlin
    viewModel { ClienteViewModel(get(), get()) }
```

- [ ] **Step 5: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*ClienteViewModelTest*"
```

Expected: PASS — 5 tests.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/cliente/
git commit -m "feat: ViewModel del cliente para pedir carreras"
```

---

### Task 4: Pantalla del cliente

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClientePantalla.kt`

**Interfaces:**
- Consumes: `ClienteViewModel` (Task 3), `MapaEmbebido`, `centavosASoles`
- Produces: `ClientePantalla(alCambiarModo, alCerrarSesion)`

**Contexto:** tres estados excluyentes, en orden de prioridad — igual que `CarrerasPantalla`:
1. **Con carrera activa** → seguimiento (mapa + estado + datos del rider)
2. **Sin carrera** → formulario para pedir
3. **Cargando** → spinner

- [ ] **Step 1: Implementar la pantalla**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClientePantalla.kt`.

Estructura, siguiendo el estilo de `CarrerasPantalla.kt`:

```kotlin
package pe.leadai.rider.ui.cliente

// (imports de Compose, Koin, tema — mirá CarrerasPantalla.kt)

/** Cada cuánto se refresca el estado de la carrera. Mismo ritmo que el pool del rider. */
private const val INTERVALO_POLLING_MS = 10_000L

private const val URL_BASE_TRACKING = "https://api.leadai-pe.com"

@Composable
fun ClientePantalla(
    alCambiarModo: () -> Unit,
    alCerrarSesion: () -> Unit,
    viewModel: ClienteViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val avisos = koinInject<AvisosGlobales>()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.cargar()
        while (isActive) {
            delay(INTERVALO_POLLING_MS)
            viewModel.refrescar()
        }
    }
    LaunchedEffect(Unit) {
        avisos.avisos.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                estado.cargando -> PantallaCargando()
                estado.miCarrera != null -> SeguimientoCarrera(
                    carrera = estado.miCarrera!!,
                    onCancelar = viewModel::cancelar,
                )
                else -> FormularioPedir(
                    estado = estado,
                    onTipo = viewModel::elegirTipo,
                    onOrigen = viewModel::cambiarOrigen,
                    onUsarMiUbicacion = viewModel::usarMiUbicacion,
                    onDestino = viewModel::cambiarDestino,
                    onMonto = viewModel::cambiarMonto,
                    onMontoCompra = viewModel::cambiarMontoCompra,
                    onNotas = viewModel::cambiarNotas,
                    onContacto = viewModel::cambiarContacto,
                    onSugerir = viewModel::pedirSugerencia,
                    onPedir = viewModel::pedir,
                    onCambiarModo = alCambiarModo,
                    onCerrarSesion = alCerrarSesion,
                )
            }
        }
    }
}
```

**`FormularioPedir`** debe tener, en este orden:
1. Selector de tipo: dos botones, `🚕 Que me lleven` / `📦 Que traigan o lleven algo` (encomienda).
2. Campo **origen** con un botón "📍 Estoy acá" que llama a `onUsarMiUbicacion`.
3. Campo **destino**. Al perder el foco, llamar a `onSugerir`.
4. Campo **monto** en soles. Si hay `montoSugerido`, mostrar debajo: `"Sugerido: S/7.60 · 3.0 km"`, con la aclaración `"Podés ofrecer más o menos — lo acordás con el motorizado"`.
5. **Solo si `tipo == "encomienda"`**: campo `montoCompra` con la etiqueta `"¿Cuánto cuesta lo que va a comprar?"` y la ayuda `"Se lo devolvés aparte del flete"`. **En ámbar (`TokensExtra.espera`), visualmente separado del monto.**
6. Campo **notas** (`"combinado sin verduras", "caja mediana"`).
7. Campo **contacto** (teléfono).
8. Botón grande: `"🛵 Pedir motorizado"`, con spinner cuando `estado.pidiendo`.
9. Al pie: botón de texto `"🏍️ Quiero manejar"` (→ `onCambiarModo`) y `"Cerrar sesión"`.

**`SeguimientoCarrera`** muestra según `carrera.estado`:

- **`disponible`**: `"🔍 Buscando motorizado…"`, cuánto lleva esperando (calculado desde `creadoEn`), el mensaje `"Si nadie la toma en unos minutos, probá ofreciendo un poco más"`, y botón **"Cancelar"**.
- **`aceptada` / `recogida`**: el `MapaEmbebido` con `"$URL_BASE_TRACKING/track/${carrera.id}?embebido=1"`, una card con `riderNombre`, `riderPlaca`, `riderVehiculo`, y botones de **WhatsApp** y **Llamar** usando `riderTelefono` (reusá `telefonoDeContacto` de `ui/carreras/CarrerasPantalla.kt`). Sin botón de cancelar — el rider ya está en camino.
- **Si `montoCompraEstimado != null`**: mostrar `"💵 Llevá S/60 para pagarle la compra"` en ámbar, **separado** del flete.

- [ ] **Step 2: Compilar y correr la suite**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
./gradlew.bat :composeApp:assembleDebug
```

Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClientePantalla.kt
git commit -m "feat: pantalla del cliente para pedir y seguir la carrera"
```

---

### Task 5: Verificación final

- [ ] **Step 1: Suite completa y APK**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest
./gradlew.bat :composeApp:assembleDebug
```

Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 2: Verificar la regla del dinero**

```bash
grep -rn "montoCompraEstimado\|montoCompra" composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/
```

Revisar cada aparición: **ninguna** debe sumarse con `monto` / `montoOfrecido`. El monto de compra siempre en su propia línea, con su propia etiqueta.

- [ ] **Step 3: Verificar que el rider no se rompió**

El punto más importante: alguien con perfil de motorizado debe entrar directo a conducir, sin ver la pantalla de elección.

```bash
grep -n "perfil != null" composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt
```

Expected: existe la línea que devuelve `Rutas.CARRERAS` cuando hay perfil, **antes** de mirar el modo guardado.

- [ ] **Step 4: Confirmar la cobertura**

Deben existir y pasar:
- `CarrerasClienteApiTest` — los cuatro endpoints, el 409 de carrera activa, el 409 de cancelar
- `ModoAppTest` — el modo se guarda y se recuerda
- `ClienteViewModelTest` — formulario vs. carrera activa, validaciones, monto de compra solo en encomienda

---

## Qué NO cubre este plan

- **Push al cliente cuando un rider acepta** — hoy se entera por el polling de 10s. El push necesita trabajo del backend también.
- **Elegir origen/destino tocando el mapa** — por ahora es texto + GPS. La confirmación visual del pin queda para cuando haya uso real.
- **Historial de carreras del cliente** — solo se ve la activa.
- **Direcciones favoritas** ("Casa", "Trabajo").
- **Contraofertas de riders** — el pool sigue siendo "el primero gana".
- **Calificaciones** entre cliente y rider.
- **iOS** — los `actual` siguen siendo stubs; el GPS del cliente en iOS no funciona hasta que haya una Mac.
