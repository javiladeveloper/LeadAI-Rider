# App del rider — Carreras multi-tipo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el rider vea y acepte los cuatro tipos de carrera (delivery, mandado, encomienda, pasajero), con el flete y el monto de compra bien diferenciados.

**Architecture:** El backend ya sirve los cuatro tipos y está en producción. Acá solo se extiende el DTO con los campos nuevos y se adapta la card del pool para que muestre lo correcto según el tipo. El flujo de dos tramos, tracking, monedero e historial no se tocan — ya son agnósticos al tipo.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor, Koin, kotlinx.serialization, kotlin.test + MockEngine.

**Repo:** `d:\Personal Proyects\LeadAI-Rider` — todos los commits de este plan van acá.

## Global Constraints

- Todo el código, comentarios y textos de UI en **español** (convención del repo).
- Montos en **centavos** (`Long`), formateados con `centavosASoles()` de `ui/tema/Formato.kt`.
- **Nunca sumar `montoOfrecido` + `montoCompraEstimado` en un solo total.** El flete es lo que el rider gana; el monto de compra es plata que adelanta y recupera. Un total combinado se lee como una carrera muy rentable y no lo es.
- **La app no muestra costos de combustible ni márgenes calculados.** El rider solo ve: monto ofrecido y lo que recibe tras la comisión.
- Todos los campos nuevos del DTO llevan **default** — el backend puede omitirlos y la deserialización no debe romper.
- Design system "Brand Harmony": teal `#006b5d` acción, coral `TokensExtra.calor` urgencia, `TokensExtra.espera` ámbar.
- Tests con `./gradlew.bat :composeApp:testDebugUnitTest`, con `MockEngine` inyectado al `ApiCliente` y dispatcher inyectado al ViewModel (patrón de `CarrerasViewModelTest.kt`).
- Commits en español con prefijo `feat:` / `test:` / `fix:`.

## Contrato real del backend (verificado en producción 2026-07-28)

`GET /motorizados/carreras` devuelve por carrera:

```json
{
  "pedidoId": "...",        // id de Carrera si no hay Pedido — la app lo usa como identificador
  "carreraId": "...",       // NUEVO: id real de la Carrera
  "tipo": "pedido",         // NUEVO: pedido | mandado | encomienda | pasajero
  "negocio": "El Pollon",   // = origenTexto (compatibilidad)
  "origenTexto": "El Pollon",       // NUEVO
  "destinoTexto": "Jose Olaya 110", // NUEVO
  "direccion": "Jose Olaya 110",    // = destinoTexto (compatibilidad)
  "totalCentavos": 5400,            // = montoOfrecido (compatibilidad)
  "montoOfrecido": 5400,            // NUEVO: el FLETE
  "montoCompraEstimado": 6000,      // NUEVO: solo mandado, null en el resto
  "kmEstimado": 3.0,                // NUEVO
  "notas": "sin verduras",          // NUEVO
  "creadoEn": "...",
  "recogido": false,
  "kmAlNegocio": 1.2
}
```

**Cambio de contrato ya en producción:** `POST /motorizados/carreras/:id/recogido` y `/entregar` ahora responden **409** (antes 404) cuando la carrera no está en el estado esperado. La app hoy trata cualquier error igual, así que no rompe — pero el Task 3 mejora ese mensaje.

---

### Task 1: Campos nuevos en `CarreraDto`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt:132-148`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/MotorizadosApiTest.kt`

**Interfaces:**
- Consumes: nada
- Produces: `CarreraDto` con `carreraId: String?`, `tipo: String`, `origenTexto: String?`, `destinoTexto: String?`, `montoOfrecido: Long`, `montoCompraEstimado: Long?`, `kmEstimado: Double?`, `notas: String`

- [ ] **Step 1: Escribir el test que falla**

Agregar a `composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/MotorizadosApiTest.kt` (mirá primero el archivo para seguir su patrón de `MockEngine`):

```kotlin
@Test
fun carreras_deserializa_los_cuatro_tipos_con_sus_montos() = runTest {
    val engine = MockEngine {
        respond(
            content = """{"carreras":[
                {"pedidoId":"p1","carreraId":"c1","tipo":"pedido","negocio":"El Pollon",
                 "origenTexto":"El Pollon","destinoTexto":"Jose Olaya 110","direccion":"Jose Olaya 110",
                 "totalCentavos":5400,"montoOfrecido":5400,"montoCompraEstimado":null,
                 "kmEstimado":3.0,"notas":"","creadoEn":"2026-07-28T10:00:00.000Z","recogido":false},
                {"pedidoId":"c2","carreraId":"c2","tipo":"mandado","negocio":"Chifa Salon Canton",
                 "origenTexto":"Chifa Salon Canton","destinoTexto":"Jose Olaya 110","direccion":"Jose Olaya 110",
                 "totalCentavos":800,"montoOfrecido":800,"montoCompraEstimado":6000,
                 "kmEstimado":3.0,"notas":"combinado sin verduras","creadoEn":"2026-07-28T10:00:00.000Z","recogido":false}
            ],"miCarrera":null}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val api = MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine))

    val resultado = api.carreras()

    val carreras = (resultado as Resultado.Ok).valor.carreras
    assertEquals(2, carreras.size)
    assertEquals("pedido", carreras[0].tipo)
    assertEquals(null, carreras[0].montoCompraEstimado)
    // El mandado: flete S/8 y compra S/60 llegan SEPARADOS.
    assertEquals("mandado", carreras[1].tipo)
    assertEquals(800L, carreras[1].montoOfrecido)
    assertEquals(6000L, carreras[1].montoCompraEstimado)
    assertEquals("combinado sin verduras", carreras[1].notas)
}

@Test
fun carreras_de_backend_viejo_sin_campos_nuevos_no_rompe() = runTest {
    // Compatibilidad: si el backend omite los campos nuevos, los defaults
    // mantienen la app funcionando en vez de reventar la deserialización.
    val engine = MockEngine {
        respond(
            content = """{"carreras":[
                {"pedidoId":"p1","negocio":"El Pollon","direccion":"Jose Olaya 110",
                 "totalCentavos":5400,"creadoEn":"2026-07-28T10:00:00.000Z"}
            ],"miCarrera":null}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val api = MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine))

    val carreras = (api.carreras() as Resultado.Ok).valor.carreras

    assertEquals("pedido", carreras[0].tipo) // default
    assertEquals(0L, carreras[0].montoOfrecido)
    assertEquals(null, carreras[0].montoCompraEstimado)
}
```

Si `MotorizadosApiTest.kt` no tiene un helper `dataStoreDePrueba()`, copialo de `CarrerasViewModelTest.kt:33-37`.

- [ ] **Step 2: Correr para verificar que falla**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest --tests "*MotorizadosApiTest*"
```

Expected: FAIL — `tipo`, `montoOfrecido`, `montoCompraEstimado` y `notas` no existen en `CarreraDto`.

- [ ] **Step 3: Implementar**

En `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt`, reemplazar el `CarreraDto` completo (líneas 132-148) por:

```kotlin
/**
 * Una carrera del POOL: puede ser el delivery de un negocio cliente, un
 * MANDADO (comprar en un negocio ajeno), una encomienda o un pasajero.
 *
 * Todos los campos nuevos llevan default: si el backend los omite, la app
 * sigue funcionando en vez de reventar la deserialización.
 */
@Serializable
data class CarreraDto(
    /** Identificador que usa la app. Es el id del Pedido, o el de la Carrera si no hay Pedido. */
    val pedidoId: String,
    /** Id real de la Carrera en el backend. */
    val carreraId: String? = null,
    /** `pedido` | `mandado` | `encomienda` | `pasajero`. */
    val tipo: String = "pedido",
    val negocio: String = "",
    val negocioDistrito: String? = null,
    /** De dónde sale: el local del negocio, o una dirección libre. */
    val origenTexto: String? = null,
    /** A dónde va. */
    val destinoTexto: String? = null,
    val direccion: String? = null,
    val totalCentavos: Long = 0,
    /**
     * El FLETE: lo que el rider gana por hacer la carrera. Sobre este monto
     * se calcula la comisión.
     */
    val montoOfrecido: Long = 0,
    /**
     * Solo en `mandado`: lo que cuesta lo que va a COMPRAR. Es plata que el
     * rider adelanta y recupera del cliente — NUNCA se suma al flete, porque
     * un total combinado se lee como una carrera muy rentable y no lo es.
     */
    val montoCompraEstimado: Long? = null,
    val kmEstimado: Double? = null,
    /** Detalle del pedido: "combinado sin verduras", "caja mediana". */
    val notas: String = "",
    val creadoEn: String,
    /** Datos del CLIENTE — solo vienen en `miCarrera` (la aceptada), nunca en el feed abierto. */
    val clienteNombre: String? = null,
    val clienteContacto: String? = null,
    /** Distancia del rider al ORIGEN — null sin GPS fresco. */
    val kmAlNegocio: Double? = null,
    /** Dos tramos: `false` = va al origen a recoger; `true` = ya recogió, va al destino. */
    val recogido: Boolean = false,
)
```

- [ ] **Step 4: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*MotorizadosApiTest*"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/datos/MotorizadosApiTest.kt
git commit -m "feat: CarreraDto soporta los cuatro tipos de carrera"
```

---

### Task 2: Etiquetas y formato por tipo

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/TipoCarrera.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/carreras/TipoCarreraTest.kt`

**Interfaces:**
- Consumes: `CarreraDto` (Task 1)
- Produces:
  - `etiquetaTipo(tipo: String): String` — "🍽️" / "🛍️ Mandado" / "📦 Encomienda" / "🚕 Pasajero"
  - `tituloTramo(carrera: CarreraDto): String` — el título de la pantalla según tramo y tipo
  - `esMandado(carrera: CarreraDto): Boolean`

**Contexto:** funciones puras, sin Compose, para poder testearlas sin UI. La card las consume en el Task 3.

- [ ] **Step 1: Escribir el test que falla**

Crear `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/carreras/TipoCarreraTest.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.CarreraDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun carrera(
    tipo: String = "pedido",
    recogido: Boolean = false,
    montoCompraEstimado: Long? = null,
) = CarreraDto(
    pedidoId = "p1",
    tipo = tipo,
    negocio = "El Pollon",
    origenTexto = "El Pollon",
    destinoTexto = "Jose Olaya 110",
    montoOfrecido = 800,
    montoCompraEstimado = montoCompraEstimado,
    creadoEn = "2026-07-28T10:00:00.000Z",
    recogido = recogido,
)

class TipoCarreraTest {

    @Test
    fun cada_tipo_tiene_su_etiqueta() {
        assertEquals("🍽️ Delivery", etiquetaTipo("pedido"))
        assertEquals("🛍️ Mandado", etiquetaTipo("mandado"))
        assertEquals("📦 Encomienda", etiquetaTipo("encomienda"))
        assertEquals("🚕 Pasajero", etiquetaTipo("pasajero"))
    }

    @Test
    fun un_tipo_desconocido_no_rompe_la_pantalla() {
        // El backend podría agregar un tipo nuevo antes de que la app se
        // actualice: mejor una etiqueta genérica que un crash.
        assertEquals("🛵 Carrera", etiquetaTipo("teletransporte"))
    }

    @Test
    fun el_titulo_dice_en_que_tramo_va() {
        assertEquals("📦 Recoge en el local", tituloTramo(carrera(tipo = "pedido")))
        assertEquals("🛵 Llevando el pedido", tituloTramo(carrera(tipo = "pedido", recogido = true)))
    }

    @Test
    fun el_pasajero_no_se_recoge_se_pasa_a_buscar() {
        assertEquals("🚕 Pasa a buscarlo", tituloTramo(carrera(tipo = "pasajero")))
        assertEquals("🚕 Llevando al pasajero", tituloTramo(carrera(tipo = "pasajero", recogido = true)))
    }

    @Test
    fun el_mandado_dice_que_hay_que_comprar() {
        assertEquals("🛍️ Ve a comprar", tituloTramo(carrera(tipo = "mandado")))
        assertEquals("🛵 Llevando la compra", tituloTramo(carrera(tipo = "mandado", recogido = true)))
    }

    @Test
    fun solo_es_mandado_si_tiene_monto_de_compra() {
        assertTrue(esMandado(carrera(tipo = "mandado", montoCompraEstimado = 6000)))
        // Un mandado sin monto declarado no debe mostrar "llevas S/0".
        assertFalse(esMandado(carrera(tipo = "mandado", montoCompraEstimado = null)))
        assertFalse(esMandado(carrera(tipo = "pasajero", montoCompraEstimado = 6000)))
    }
}
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*TipoCarreraTest*"
```

Expected: FAIL — no existe `TipoCarrera.kt`.

- [ ] **Step 3: Implementar**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/TipoCarrera.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.CarreraDto

/**
 * Cómo se le habla al rider de cada tipo de carrera. Funciones PURAS (sin
 * Compose) para poder testearlas sin levantar UI.
 *
 * Un tipo desconocido nunca rompe la pantalla: el backend puede agregar tipos
 * antes de que la app se actualice en los teléfonos.
 */

/** La etiqueta que identifica el tipo en la card del pool. */
fun etiquetaTipo(tipo: String): String = when (tipo) {
    "pedido" -> "🍽️ Delivery"
    "mandado" -> "🛍️ Mandado"
    "encomienda" -> "📦 Encomienda"
    "pasajero" -> "🚕 Pasajero"
    else -> "🛵 Carrera"
}

/**
 * El título de la pantalla cuando la carrera está en curso: dice en qué tramo
 * va y usa las palabras del tipo — a un pasajero no se lo "recoge", se lo pasa
 * a buscar.
 */
fun tituloTramo(carrera: CarreraDto): String = when (carrera.tipo) {
    "pasajero" -> if (carrera.recogido) "🚕 Llevando al pasajero" else "🚕 Pasa a buscarlo"
    "mandado" -> if (carrera.recogido) "🛵 Llevando la compra" else "🛍️ Ve a comprar"
    "encomienda" -> if (carrera.recogido) "🛵 Llevando la encomienda" else "📦 Recoge la encomienda"
    else -> if (carrera.recogido) "🛵 Llevando el pedido" else "📦 Recoge en el local"
}

/**
 * Si esta carrera exige que el rider ADELANTE plata para comprar. Un mandado
 * sin monto declarado no cuenta: mostrar "llevas S/0" confunde más que ayuda.
 */
fun esMandado(carrera: CarreraDto): Boolean =
    carrera.tipo == "mandado" && (carrera.montoCompraEstimado ?: 0) > 0
```

- [ ] **Step 4: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*TipoCarreraTest*"
```

Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/TipoCarrera.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/carreras/TipoCarreraTest.kt
git commit -m "feat: etiquetas y titulos por tipo de carrera"
```

---

### Task 3: La card del pool muestra los cuatro tipos

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt:558-617` (`CardCarrera`)

**Interfaces:**
- Consumes: `etiquetaTipo`, `esMandado` (Task 2); `CarreraDto` (Task 1)
- Produces: nada (UI)

**Contexto:** `CarrerasPantalla.kt` ya tiene 693 líneas. Este task NO la agranda mucho porque `TipoCarrera.kt` se llevó la lógica. Si al terminar supera ~750 líneas, extraer `CardCarrera` a su propio archivo es una mejora razonable — pero no es obligatorio.

- [ ] **Step 1: Reemplazar `CardCarrera`**

En `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt`, reemplazar la función `CardCarrera` completa por:

```kotlin
/** Una carrera disponible: el tipo, de dónde a dónde, cuánto gana y el botón que gana el primero. */
@Composable
private fun CardCarrera(
    carrera: CarreraDto,
    aceptando: Boolean,
    habilitado: Boolean,
    onAceptar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Tipo + lo que GANA (el flete). Nunca el total con la compra.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    etiquetaTipo(carrera.tipo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    centavosASoles(carrera.montoOfrecido),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "📍 ${carrera.origenTexto ?: carrera.negocio}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val destino = carrera.destinoTexto ?: carrera.direccion
            if (!destino.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "🏁 $destino",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (carrera.notas.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "📝 ${carrera.notas}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // MANDADO: cuánta plata tiene que llevar encima. En ámbar y
            // SEPARADO de lo que gana — un total combinado (S/8 + S/60 = S/68)
            // se lee como una carrera muy rentable y no lo es.
            if (esMandado(carrera)) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = TokensExtra.espera.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💵 Llevas ${centavosASoles(carrera.montoCompraEstimado ?: 0)} para la compra",
                        style = MaterialTheme.typography.bodySmall,
                        color = TokensExtra.espera,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "El cliente te lo devuelve al entregar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Despacho por proximidad: el feed llega ordenado por cercanía.
            if (carrera.kmAlNegocio != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "🛵 A ${carrera.kmAlNegocio} km de ti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAceptar,
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (aceptando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Aceptar carrera", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Agregar los imports que faltan**

Al bloque de imports de `CarrerasPantalla.kt`, si no están ya:

```kotlin
import androidx.compose.foundation.background
```

(`etiquetaTipo` y `esMandado` viven en el mismo paquete `pe.leadai.rider.ui.carreras`, así que no necesitan import.)

- [ ] **Step 3: Usar el título por tipo en la carrera en curso**

En la misma pantalla, dentro de `ContenidoRider`, reemplazar:

```kotlin
            Text(
                // El título dice en qué tramo va (2026-07-24).
                if (miCarrera.recogido) "🛵 Llevando el pedido" else "📦 Recoge en el local",
```

por:

```kotlin
            Text(
                // El título dice en qué tramo va y con las palabras del tipo:
                // a un pasajero no se lo "recoge", se lo pasa a buscar.
                tituloTramo(miCarrera),
```

Y el botón de acción, reemplazar:

```kotlin
                    Text(
                        if (miCarrera.recogido) "✅ Entregado" else "📦 Ya recogí el pedido",
                        style = MaterialTheme.typography.labelLarge,
                    )
```

por:

```kotlin
                    Text(
                        when {
                            miCarrera.recogido -> "✅ Entregado"
                            miCarrera.tipo == "pasajero" -> "🚕 Ya subió"
                            miCarrera.tipo == "mandado" -> "🛍️ Ya compré"
                            else -> "📦 Ya recogí el pedido"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
```

- [ ] **Step 4: Mostrar el monto de compra también en la carrera en curso**

En la `Card` de datos de `miCarrera` (la que muestra `🍽️ negocio · monto`), después del bloque de `clienteNombre`, agregar:

```kotlin
                    if (esMandado(miCarrera)) {
                        Text(
                            "💵 Llevas ${centavosASoles(miCarrera.montoCompraEstimado ?: 0)} para la compra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TokensExtra.espera,
                        )
                    }
```

- [ ] **Step 5: Compilar y correr toda la suite**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
```

Expected: PASS — toda la suite, sin regresiones.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt
git commit -m "feat: la card del pool muestra los cuatro tipos de carrera"
```

---

### Task 4: `tipoVehiculo` en el alta del rider

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt` (`PerfilMotorizadoDto`)
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/MotorizadosApi.kt:43-64` (`guardarMiPerfil`)
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/alta/AltaRiderViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/alta/AltaRiderPantalla.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/alta/AltaRiderViewModelTest.kt`

**Interfaces:**
- Consumes: nada
- Produces: `AltaRiderUiState.tipoVehiculo: String`, `AltaRiderViewModel.elegirTipoVehiculo(tipo: String)`

**Contexto:** el backend ya tiene el campo (`PerfilMotorizado.tipoVehiculo`, default `"moto"`). La sugerencia de monto depende de él: un auto consume ~3x más que una moto.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/alta/AltaRiderViewModelTest.kt` (seguí el patrón del archivo):

```kotlin
@Test
fun el_tipo_de_vehiculo_arranca_en_moto() = runTest {
    val vm = AltaRiderViewModel(apiDePrueba(), testDispatcher)

    assertEquals("moto", vm.estado.value.tipoVehiculo)
}

@Test
fun se_puede_elegir_auto() = runTest {
    val vm = AltaRiderViewModel(apiDePrueba(), testDispatcher)

    vm.elegirTipoVehiculo("auto")

    assertEquals("auto", vm.estado.value.tipoVehiculo)
}

@Test
fun guardar_manda_el_tipo_de_vehiculo() = runTest {
    var cuerpoEnviado = ""
    val engine = MockEngine { peticion ->
        cuerpoEnviado = (peticion.body as io.ktor.http.content.TextContent).text
        respond(
            content = """{"perfil":{"id":"m1","usuarioId":"u1","distrito":"Tacna, Tacna","estado":"pendiente","creadoEn":"2026-07-28T10:00:00.000Z"}}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val vm = AltaRiderViewModel(
        MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine)),
        testDispatcher,
    )
    vm.elegirDepartamento("Tacna")
    vm.elegirDistrito("Tacna")
    vm.cambiarDni("44247191")
    vm.cambiarTelefono("952123456")
    vm.elegirTipoVehiculo("auto")

    vm.guardar(alExito = {})
    advanceUntilIdle()

    assertTrue(cuerpoEnviado.contains("\"tipoVehiculo\":\"auto\""))
}
```

Si el archivo no tiene un helper `apiDePrueba()`, creá uno que devuelva un `MotorizadosApi` con `MockEngine` que responda `{"perfil":null}`.

- [ ] **Step 2: Correr para verificar que falla**

```bash
./gradlew.bat :composeApp:testDebugUnitTest --tests "*AltaRiderViewModelTest*"
```

Expected: FAIL — `tipoVehiculo` y `elegirTipoVehiculo` no existen.

- [ ] **Step 3: Agregar el campo al DTO**

En `Dtos.kt`, dentro de `PerfilMotorizadoDto`, después de `placa`:

```kotlin
    /** `moto` | `auto` — la sugerencia de monto depende del vehículo. */
    val tipoVehiculo: String = "moto",
```

- [ ] **Step 4: Mandarlo en el upsert**

En `MotorizadosApi.kt`, cambiar la firma de `guardarMiPerfil` para aceptar el tipo y agregarlo al body:

```kotlin
    suspend fun guardarMiPerfil(
        distrito: String,
        telefono: String?,
        placa: String?,
        dni: String? = null,
        tipoVehiculo: String = "moto",
    ): Resultado<PerfilMotorizadoDto> =
```

Y dentro del `buildJsonObject`, después de la línea del `dni`:

```kotlin
                    put("tipoVehiculo", tipoVehiculo)
```

- [ ] **Step 5: Agregarlo al ViewModel**

En `AltaRiderViewModel.kt`, agregar al `AltaRiderUiState` (después de `placa`):

```kotlin
    /** `moto` | `auto` — de qué depende cuánto se sugiere cobrar por km. */
    val tipoVehiculo: String = "moto",
```

Agregar el setter (junto a `cambiarPlaca`):

```kotlin
    fun elegirTipoVehiculo(tipo: String) {
        _estado.update { it.copy(tipoVehiculo = tipo, error = null) }
    }
```

En `prepararEdicion`, agregar `tipoVehiculo = perfil.tipoVehiculo` al `copy` del estado.

Y en `guardar`, pasar el campo:

```kotlin
                    dni = actual.dni,
                    tipoVehiculo = actual.tipoVehiculo,
```

- [ ] **Step 6: Agregar el selector a la pantalla**

En `AltaRiderPantalla.kt`, antes del campo de placa, agregar un selector de dos opciones. Seguí el estilo visual del resto del formulario:

```kotlin
        Text(
            "¿En qué te mueves?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("moto" to "🛵 Moto", "auto" to "🚗 Auto").forEach { (valor, etiqueta) ->
                val elegido = estado.tipoVehiculo == valor
                OutlinedButton(
                    onClick = { viewModel.elegirTipoVehiculo(valor) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (elegido) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Text(etiqueta, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
```

Agregar el import `androidx.compose.material3.ButtonDefaults` si falta.

- [ ] **Step 7: Correr toda la suite**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
```

Expected: PASS — toda la suite.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/Dtos.kt composeApp/src/commonMain/kotlin/pe/leadai/rider/datos/MotorizadosApi.kt composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/alta/ composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/alta/AltaRiderViewModelTest.kt
git commit -m "feat: el rider elige si se mueve en moto o auto"
```

---

### Task 5: Verificación final

- [ ] **Step 1: Suite completa**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest
```

Expected: PASS, sin regresiones.

- [ ] **Step 2: Compila el APK**

```bash
./gradlew.bat :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verificar la regla del dinero a mano**

```bash
grep -rn "montoCompraEstimado" composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/
```

Revisar cada aparición: **ninguna** debe sumar `montoCompraEstimado` con `montoOfrecido` ni con `totalCentavos`. El monto de compra siempre se muestra en su propia línea, con su propia etiqueta.

- [ ] **Step 4: Confirmar la cobertura**

Deben existir y pasar:
- `MotorizadosApiTest` — deserialización de los cuatro tipos + backend viejo sin campos nuevos
- `TipoCarreraTest` — etiquetas, títulos por tramo y tipo, detección de mandado
- `AltaRiderViewModelTest` — tipo de vehículo por defecto, selección y envío

---

## Qué NO cubre este plan

- **Modo pasajero** (elegir origen/destino en un mapa con búsqueda de direcciones, crear la carrera, seguir el viaje) — spec y plan propios. Es la parte más pesada del producto.
- **Una sola app con switch de modo** — el spec lo decidió, pero implementarlo exige reescribir el arranque (`Navegacion.kt`) y va junto con el modo pasajero.
- **Endpoints públicos para crear mandados/encomiendas/pasajeros** — `crearCarreraLibre` existe en el backend pero no está expuesta por HTTP. Va con el modo pasajero.
- **Foreground service para el GPS** — hoy el rider deja de reportar posición con la app en segundo plano. Es un problema real y anterior a este plan; merece su propio trabajo.
- **iOS** — los cuatro `actual` siguen siendo stubs. Requiere una Mac.
