# Bloqueantes para operar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la plataforma funcione en la calle y no solo en demo: que el GPS siga reportando con el teléfono en el bolsillo, y que rider y cliente se enteren de las cosas sin tener la app abierta.

**Architecture:** Tres frentes independientes. (1) Un foreground service de Android que mantiene el GPS vivo mientras hay carrera. (2) Dos pushes nuevos en el backend, gemelos del que ya existe para pedidos de negocio. (3) Los permisos que Android exige para que (1) y (2) funcionen de verdad, con deep link a Configuración.

**Tech Stack:** Kotlin Multiplatform, Compose, Android Services, FCM; Node/TypeScript, Fastify, Prisma en el backend.

**Repos:** `d:\Personal Proyects\LeadAI-Rider` (app) y `d:\Personal Proyects\leadia` (backend). **Cada task dice a cuál va.**

## Global Constraints

- Todo el código, comentarios y textos de UI en **español**.
- **El push nunca rompe el flujo principal.** Fire-and-forget con `try/catch`, igual que `avisarRidersCarreraNueva`: un aviso fallido jamás debe impedir que se acepte o se cree una carrera.
- **Los permisos no se piden de golpe al abrir.** Se piden cuando hacen falta y explicando para qué — pedir "ubicación todo el tiempo" en el primer arranque es la forma más rápida de que lo nieguen.
- El foreground service **solo corre mientras hay carrera activa**. Un service que vive siempre drena batería y Play lo cuestiona en revisión.
- Backend: tests con `vitest run`. App: `./gradlew.bat :composeApp:testDebugUnitTest`.
- Commits en español con prefijo `feat:` / `fix:`.

## Lo que YA existe y se reusa

| Pieza | Dónde | Qué hace |
|---|---|---|
| `notificarPushUsuarios` | `leadia/src/core/push.ts:100` | Push a una lista de `usuarioId`. Ya maneja FCM ausente y tokens muertos. |
| `avisarRidersCarreraNueva` | `leadia/src/core/pool.ts:14` | Avisa a los riders de la zona cuando un pedido queda listo. **El molde para los pushes nuevos.** |
| `LeadAIFirebaseService` | `app/androidMain/push/` | Recibe el push y arma el `PendingIntent`. |
| `obtenerUbicacionActual` | `app/androidMain/ui/carreras/` | Lee el GPS. Hoy se llama desde la pantalla. |
| `DispositivoPush` | schema Prisma | `tenantId` ya es nullable para riders. Sirve igual para clientes. |

---

### Task 1: Push al rider cuando un cliente pide (backend)

**Repo:** `d:\Personal Proyects\leadia`

**Files:**
- Modify: `src/core/pool.ts` (agregar función)
- Modify: `src/routes/carreras.ts` (llamarla al crear)
- Test: `tests/pool-avisos.test.ts` (crear)

**Interfaces:**
- Consumes: `notificarPushUsuarios`, `departamentoDe`
- Produces: `avisarRidersCarreraLibre(carreraId: string): Promise<void>`

**Contexto:** hoy el rider solo recibe push de pedidos de negocio. Si un cliente pide una encomienda o un pasajero, nadie se entera hasta que abre la app.

- [ ] **Step 1: Escribir el test que falla**

Crear `tests/pool-avisos.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Avisos a los riders cuando aparece trabajo. Fire-and-forget: un push que
// falla jamás debe romper la creación de la carrera.

const { prismaMock } = vi.hoisted(() => ({
  prismaMock: {
    carrera: { findUnique: vi.fn() },
    perfilMotorizado: { findMany: vi.fn() },
  },
}));
vi.mock('../src/lib/prisma.js', () => ({ prisma: prismaMock }));

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }));
vi.mock('../src/core/push.js', () => ({
  notificarPushUsuarios: pushMock,
  notificarPush: vi.fn(),
}));

import { avisarRidersCarreraLibre } from '../src/core/pool.js';

const CARRERA = {
  id: 'c1',
  tipo: 'pasajero',
  estado: 'disponible',
  riderUsuarioId: null,
  zona: 'Tacna',
  origenTexto: 'Av. Grau 240',
  destinoTexto: 'Miraflores',
  montoOfrecido: 760,
  montoCompraEstimado: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  prismaMock.carrera.findUnique.mockResolvedValue(CARRERA);
  prismaMock.perfilMotorizado.findMany.mockResolvedValue([
    { usuarioId: 'r1', distrito: 'Tacna, Tacna' },
    { usuarioId: 'r2', distrito: 'Pocollay, Tacna' },
    { usuarioId: 'r3', distrito: 'Miraflores' }, // Lima: no le toca
  ]);
});

describe('avisarRidersCarreraLibre', () => {
  it('avisa SOLO a los riders de la zona de la carrera', async () => {
    await avisarRidersCarreraLibre('c1');

    const [ids] = pushMock.mock.calls[0];
    expect(ids).toEqual(['r1', 'r2']);
  });

  it('el aviso dice de donde a donde y cuanto', async () => {
    await avisarRidersCarreraLibre('c1');

    const [, titulo, cuerpo] = pushMock.mock.calls[0];
    expect(titulo).toContain('carrera');
    expect(cuerpo).toContain('Av. Grau 240');
    expect(cuerpo).toContain('S/7.60');
  });

  it('en una encomienda con compra avisa cuanta plata llevar', async () => {
    prismaMock.carrera.findUnique.mockResolvedValue({
      ...CARRERA, tipo: 'encomienda', montoOfrecido: 800, montoCompraEstimado: 6000,
    });

    await avisarRidersCarreraLibre('c1');

    const [, , cuerpo] = pushMock.mock.calls[0];
    // El flete y la compra NUNCA se suman: S/68 se leería como carrera muy
    // rentable y no lo es.
    expect(cuerpo).toContain('S/8.00');
    expect(cuerpo).toContain('S/60.00');
    expect(cuerpo).not.toContain('S/68');
  });

  it('una carrera que ya tomaron NO se avisa', async () => {
    prismaMock.carrera.findUnique.mockResolvedValue({
      ...CARRERA, estado: 'aceptada', riderUsuarioId: 'r9',
    });

    await avisarRidersCarreraLibre('c1');

    expect(pushMock).not.toHaveBeenCalled();
  });

  it('si el push explota NO propaga el error', async () => {
    pushMock.mockRejectedValue(new Error('FCM caido'));

    await expect(avisarRidersCarreraLibre('c1')).resolves.toBeUndefined();
  });

  it('una carrera inexistente no explota', async () => {
    prismaMock.carrera.findUnique.mockResolvedValue(null);

    await expect(avisarRidersCarreraLibre('fantasma')).resolves.toBeUndefined();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
cd "d:/Personal Proyects/leadia"
npx vitest run tests/pool-avisos.test.ts
```

Expected: FAIL — `avisarRidersCarreraLibre is not a function`.

- [ ] **Step 3: Implementar**

Agregar a `src/core/pool.ts`:

```typescript
/**
 * "🛵 Nueva carrera en tu zona": push a los riders del departamento cuando un
 * CLIENTE pide una encomienda o un pasajero. Gemelo de
 * [avisarRidersCarreraNueva], que cubre los pedidos de negocio.
 *
 * El flete y el monto de compra van SEPARADOS en el texto: sumarlos haría
 * que una encomienda de S/8 con S/60 de compra se lea como una carrera de
 * S/68, que sería mentira.
 *
 * Fire-and-forget: un aviso fallido jamás rompe la creación de la carrera.
 */
export async function avisarRidersCarreraLibre(carreraId: string): Promise<void> {
  try {
    const carrera = await prisma.carrera.findUnique({ where: { id: carreraId } });
    if (!carrera || carrera.estado !== 'disponible' || carrera.riderUsuarioId) return;

    const perfiles = await prisma.perfilMotorizado.findMany({
      select: { usuarioId: true, distrito: true },
    });
    const riders = perfiles
      .filter((p) => departamentoDe(p.distrito) === carrera.zona)
      .map((p) => p.usuarioId);
    if (riders.length === 0) return;

    const soles = (centavos: number): string => `S/${(centavos / 100).toFixed(2)}`;
    const etiqueta = carrera.tipo === 'pasajero' ? '🚕 Pasajero' : '📦 Encomienda';
    const compra = carrera.montoCompraEstimado
      ? ` · Llevá ${soles(carrera.montoCompraEstimado)} para la compra`
      : '';

    await notificarPushUsuarios(
      riders,
      `${etiqueta} en tu zona`,
      `${carrera.origenTexto} → ${carrera.destinoTexto} · ${soles(carrera.montoOfrecido ?? 0)}${compra}`,
      { tipo: 'carrera', carreraId },
    );
  } catch {
    // fire-and-forget: un aviso fallido jamás rompe el despacho
  }
}
```

- [ ] **Step 4: Llamarla al crear la carrera**

En `src/routes/carreras.ts`, importar:

```typescript
import { avisarRidersCarreraLibre } from '../core/pool.js';
```

Y en el handler de `POST /carreras`, justo antes del `return`:

```typescript
    // Que los riders de la zona se enteren aunque tengan la app cerrada.
    void avisarRidersCarreraLibre(creada.id);
```

- [ ] **Step 5: Correr los tests**

```bash
npx vitest run tests/pool-avisos.test.ts tests/rutas-carreras.test.ts
npm run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/core/pool.ts src/routes/carreras.ts tests/pool-avisos.test.ts
git commit -m "feat: los riders se enteran cuando un cliente pide"
```

---

### Task 2: Push al cliente cuando un rider acepta (backend)

**Repo:** `d:\Personal Proyects\leadia`

**Files:**
- Modify: `src/core/pool.ts` (agregar función)
- Modify: `src/routes/motorizados.ts` (llamarla en aceptar, recogido y entregar)
- Test: `tests/pool-avisos.test.ts` (agregar describe)

**Interfaces:**
- Consumes: `notificarPushUsuarios`
- Produces: `avisarClienteCarrera(carreraId: string, hito: 'aceptada' | 'recogida' | 'entregada'): Promise<void>`

**Contexto:** hoy el cliente se entera por el polling de 10s, y solo con la app abierta. Si la cierra, no sabe que un rider aceptó.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `tests/pool-avisos.test.ts`:

```typescript
import { avisarClienteCarrera } from '../src/core/pool.js';

describe('avisarClienteCarrera', () => {
  beforeEach(() => {
    prismaMock.carrera.findUnique.mockResolvedValue({
      ...CARRERA, estado: 'aceptada', riderUsuarioId: 'r1', solicitanteId: 'u9',
    });
    prismaMock.usuario = { findUnique: vi.fn().mockResolvedValue({ nombre: 'Ana' }) };
  });

  it('avisa al cliente que un rider la tomo, con el nombre', async () => {
    await avisarClienteCarrera('c1', 'aceptada');

    const [ids, titulo, cuerpo] = pushMock.mock.calls[0];
    expect(ids).toEqual(['u9']);
    expect(titulo).toContain('camino');
    expect(cuerpo).toContain('Ana');
  });

  it('avisa cuando ya lo recogieron', async () => {
    await avisarClienteCarrera('c1', 'recogida');

    const [, titulo] = pushMock.mock.calls[0];
    expect(titulo.length).toBeGreaterThan(0);
  });

  it('una carrera SIN solicitante (pedido de negocio) no avisa por aca', async () => {
    // Los pedidos de restaurante avisan al cliente por su chat, no por push.
    prismaMock.carrera.findUnique.mockResolvedValue({
      ...CARRERA, solicitanteId: null, pedidoId: 'ped1',
    });

    await avisarClienteCarrera('c1', 'aceptada');

    expect(pushMock).not.toHaveBeenCalled();
  });

  it('si el push explota NO propaga', async () => {
    pushMock.mockRejectedValue(new Error('FCM caido'));

    await expect(avisarClienteCarrera('c1', 'aceptada')).resolves.toBeUndefined();
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/pool-avisos.test.ts
```

Expected: FAIL — `avisarClienteCarrera is not a function`.

- [ ] **Step 3: Implementar**

Agregar a `src/core/pool.ts`:

```typescript
/**
 * Avisa al CLIENTE que pidió la carrera cómo va: la tomaron, la recogieron,
 * la entregaron. Sin esto solo se entera si tiene la app abierta.
 *
 * Solo aplica a carreras pedidas desde la app (`solicitanteId`). Los pedidos
 * de restaurante avisan al cliente por su chat de WhatsApp, que es donde ese
 * cliente vive — no tiene la app instalada.
 */
export async function avisarClienteCarrera(
  carreraId: string,
  hito: 'aceptada' | 'recogida' | 'entregada',
): Promise<void> {
  try {
    const carrera = await prisma.carrera.findUnique({ where: { id: carreraId } });
    if (!carrera?.solicitanteId) return;

    const rider = carrera.riderUsuarioId
      ? await prisma.usuario.findUnique({
          where: { id: carrera.riderUsuarioId },
          select: { nombre: true },
        })
      : null;
    const nombre = rider?.nombre ?? 'Tu motorizado';

    const esPasajero = carrera.tipo === 'pasajero';
    const avisos = {
      aceptada: {
        titulo: '🛵 ¡Va en camino!',
        cuerpo: `${nombre} tomó tu ${esPasajero ? 'viaje' : 'encomienda'} y va hacia ${carrera.origenTexto}`,
      },
      recogida: {
        titulo: esPasajero ? '🚕 ¡Arrancaron!' : '📦 ¡Ya lo recogió!',
        cuerpo: `${nombre} va rumbo a ${carrera.destinoTexto}`,
      },
      entregada: {
        titulo: '✅ Listo',
        cuerpo: esPasajero ? '¡Buen viaje!' : 'Tu encomienda llegó',
      },
    };

    await notificarPushUsuarios(
      [carrera.solicitanteId],
      avisos[hito].titulo,
      avisos[hito].cuerpo,
      { tipo: 'mi_carrera', carreraId },
    );
  } catch {
    // fire-and-forget
  }
}
```

- [ ] **Step 4: Llamarla en los tres hitos**

En `src/routes/motorizados.ts`, importar `avisarClienteCarrera` desde `../core/pool.js` (ojo: **ya hay** una función local con nombre parecido, `avisarClienteCarrera`, que manda mensajes de chat para pedidos de negocio. Si el nombre choca, importá con alias: `import { avisarClienteCarrera as avisarClientePush } from '../core/pool.js'` y usá el alias).

En el handler de **aceptar**, después del `updateMany` exitoso:

```typescript
    void avisarClientePush(carrera.id, 'aceptada');
```

En **recogido**, después del `updateMany` exitoso:

```typescript
    void avisarClientePush(carrera?.id ?? id, 'recogida');
```

En **entregar**, después del `updateMany` exitoso:

```typescript
    void avisarClientePush(carrera?.id ?? id, 'entregada');
```

- [ ] **Step 5: Correr toda la suite**

```bash
npx vitest run
npm run typecheck
```

Expected: PASS — sin regresiones.

- [ ] **Step 6: Commit**

```bash
git add src/core/pool.ts src/routes/motorizados.ts tests/pool-avisos.test.ts
git commit -m "feat: el cliente se entera cuando su carrera avanza"
```

---

### Task 3: Registro de push para el cliente (app)

**Repo:** `d:\Personal Proyects\LeadAI-Rider`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModelTest.kt`

**Interfaces:**
- Consumes: `tokenPushActual()` (`push/RegistroPush.kt`), `MotorizadosApi.registrarDispositivo`
- Produces: nada nuevo — efecto en `cargar()`

**Contexto:** el backend ya manda push al cliente (Task 2), pero **el cliente nunca registró su token FCM**. Sin registro, el push no llega a ningún lado. El endpoint `POST /motorizados/dispositivo` sirve igual: registra `usuarioId` + token, sin exigir perfil de motorizado.

- [ ] **Step 1: Escribir el test que falla**

Agregar a `ClienteViewModelTest.kt`:

```kotlin
@Test
fun al_cargar_registra_el_token_de_push() = runTest {
    var rutasLlamadas = mutableListOf<String>()
    val engine = MockEngine { peticion ->
        rutasLlamadas.add(peticion.url.encodedPath)
        respond(
            content = """{"carrera":null}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val vm = vmDePrueba(engine, tokenPush = "token-fcm-123")

    vm.cargar()
    advanceUntilIdle()

    assertTrue(rutasLlamadas.any { it.contains("dispositivo") })
}

@Test
fun sin_token_de_push_no_llama_al_backend() = runTest {
    var rutasLlamadas = mutableListOf<String>()
    val engine = MockEngine { peticion ->
        rutasLlamadas.add(peticion.url.encodedPath)
        respond(
            content = """{"carrera":null}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val vm = vmDePrueba(engine, tokenPush = null)

    vm.cargar()
    advanceUntilIdle()

    assertTrue(rutasLlamadas.none { it.contains("dispositivo") })
}
```

Extender el helper `vmDePrueba` con el parámetro `tokenPush: String? = null`.

- [ ] **Step 2: Correr para verificar que falla**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest --tests "*ClienteViewModelTest*"
```

Expected: FAIL — no se llama a `/dispositivo`.

- [ ] **Step 3: Implementar**

En `ClienteViewModel`, agregar al constructor:

```kotlin
    private val motorizadosApi: MotorizadosApi,
    /** Token FCM inyectable (mismo patrón que CarrerasViewModel). */
    private val obtenerTokenPush: suspend () -> String? = { tokenPushActual() },
```

Agregar el flag y el registro en `cargar()`:

```kotlin
    private var pushRegistrado = false
```

Dentro de `cargar()`, después del `when`:

```kotlin
        // Push del cliente ("un rider tomó tu carrera"): una vez por sesión de
        // pantalla, silencioso. Sin token no pasa nada — el endpoint es el
        // mismo del rider, que solo asocia usuarioId + token.
        if (!pushRegistrado) {
            pushRegistrado = true
            viewModelScope.launch(dispatcher) {
                obtenerTokenPush()?.let { token -> motorizadosApi.registrarDispositivo(token) }
            }
        }
```

Actualizar el registro en Koin (`Modulos.kt`):

```kotlin
    viewModel { ClienteViewModel(get(), get(), get()) }
```

(el tercer `get()` es `MotorizadosApi` — verificá el orden real de los parámetros).

- [ ] **Step 4: Correr los tests**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
```

Expected: PASS — toda la suite.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModel.kt composeApp/src/commonMain/kotlin/pe/leadai/rider/di/Modulos.kt composeApp/src/commonTest/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModelTest.kt
git commit -m "feat: el cliente registra su telefono para recibir avisos"
```

---

### Task 4: Foreground service del GPS (app)

**Repo:** `d:\Personal Proyects\LeadAI-Rider`

**Files:**
- Create: `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ServicioCarreraActiva.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.kt` (expect)
- Create: `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.android.kt` (actual)
- Create: `composeApp/src/iosMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.ios.kt` (stub)
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt`

**Interfaces:**
- Consumes: `MotorizadosApi.reportarPosicion`, `obtenerUbicacionActual`
- Produces: `expect fun iniciarServicioCarrera()` / `expect fun detenerServicioCarrera()`

**Contexto — el problema:** los dos `LaunchedEffect` de `CarrerasPantalla` se suspenden cuando el rider bloquea el teléfono. El cliente ve la motito congelada mientras el rider maneja.

**Reglas del service:**
- Vive **solo mientras hay carrera activa**. Se arranca al aceptar, se para al entregar.
- Notificación persistente con el destino: es obligatorio en Android y además le sirve al rider.
- `foregroundServiceType="location"` — sin eso, Android 14+ lo mata.

- [ ] **Step 1: Declarar permisos y el service en el manifest**

En `composeApp/src/androidMain/AndroidManifest.xml`, junto a los permisos existentes:

```xml
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Y dentro de `<application>`, junto al `<service>` de Firebase:

```xml
        <service
            android:name=".ui.carreras.ServicioCarreraActiva"
            android:exported="false"
            android:foregroundServiceType="location" />
```

- [ ] **Step 2: Implementar el service**

Crear `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ServicioCarreraActiva.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import pe.leadai.rider.datos.MotorizadosApi

/**
 * Mantiene vivo el reporte de GPS mientras el rider tiene una carrera.
 *
 * Sin esto, al bloquear el teléfono los `LaunchedEffect` de la pantalla se
 * suspenden y el cliente ve la moto CONGELADA en el mapa mientras el rider
 * maneja con el celular en el bolsillo. Un foreground service con
 * notificación persistente es la única forma que Android permite de seguir
 * leyendo ubicación con la app en segundo plano.
 *
 * Vive SOLO mientras hay carrera: se arranca al aceptar y se para al
 * entregar. Un service permanente drena batería y Play lo cuestiona.
 */
class ServicioCarreraActiva : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destino = intent?.getStringExtra(EXTRA_DESTINO).orEmpty()
        startForeground(ID_NOTIFICACION, construirNotificacion(destino))

        scope.launch {
            val api = GlobalContext.get().get<MotorizadosApi>()
            while (isActive) {
                // Silencioso: sin GPS o sin red, se reintenta a la vuelta.
                runCatching {
                    obtenerUbicacionActual()?.let { api.reportarPosicion(it.lat, it.lng) }
                }
                delay(INTERVALO_MS)
            }
        }
        // START_STICKY: si Android lo mata por memoria, que vuelva solo — el
        // rider sigue manejando aunque el sistema haya hecho limpieza.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun construirNotificacion(destino: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Carrera en curso",
                // BAJA a propósito: no debe sonar ni vibrar, solo estar ahí.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Mantiene tu ubicación en vivo mientras llevas una carrera" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("🛵 Carrera en curso")
            .setContentText(if (destino.isBlank()) "Compartiendo tu ubicación" else "Rumbo a $destino")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CANAL_ID = "carrera_activa"
        private const val ID_NOTIFICACION = 1001
        private const val EXTRA_DESTINO = "destino"

        /** Mismo pulso que tenía la pantalla: la moto se mueve fluida en el mapa del cliente. */
        private const val INTERVALO_MS = 5_000L

        fun iniciar(contexto: Context, destino: String) {
            val intent = Intent(contexto, ServicioCarreraActiva::class.java)
                .putExtra(EXTRA_DESTINO, destino)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexto.startForegroundService(intent)
            } else {
                contexto.startService(intent)
            }
        }

        fun detener(contexto: Context) {
            contexto.stopService(Intent(contexto, ServicioCarreraActiva::class.java))
        }
    }
}
```

- [ ] **Step 3: El puente multiplataforma**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

/**
 * Arranca y para el reporte de GPS en segundo plano. En Android es un
 * foreground service; en iOS todavía no hace nada (Fase D).
 */
expect fun iniciarServicioCarrera(destino: String)

expect fun detenerServicioCarrera()
```

Crear `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.android.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.contextoApp

actual fun iniciarServicioCarrera(destino: String) {
    ServicioCarreraActiva.iniciar(contextoApp, destino)
}

actual fun detenerServicioCarrera() {
    ServicioCarreraActiva.detener(contextoApp)
}
```

**Verificá primero cómo se accede al `Context` de aplicación** — hay un `datos/ContextoApp.kt` en `androidMain`. Usá lo que ese archivo exponga; si el nombre no es `contextoApp`, adaptá.

Crear `composeApp/src/iosMain/kotlin/pe/leadai/rider/ui/carreras/ControlServicioCarrera.ios.kt`:

```kotlin
package pe.leadai.rider.ui.carreras

// Stub: el tracking en segundo plano en iOS llega con Fase D (CoreLocation
// con allowsBackgroundLocationUpdates).
actual fun iniciarServicioCarrera(destino: String) = Unit

actual fun detenerServicioCarrera() = Unit
```

- [ ] **Step 4: Engancharlo a la carrera activa**

En `CarrerasPantalla.kt`, reemplazar el `LaunchedEffect` del pulso de GPS (el de `INTERVALO_GPS_MS`) por el control del servicio:

```kotlin
    // El GPS en segundo plano lo lleva un foreground service: si lo hiciera
    // la pantalla, al bloquear el teléfono se congelaría la moto en el mapa
    // del cliente. Vive solo mientras hay carrera.
    val carreraEnCurso = estado.miCarrera
    LaunchedEffect(carreraEnCurso?.pedidoId) {
        if (carreraEnCurso != null) {
            iniciarServicioCarrera(carreraEnCurso.destinoTexto ?: carreraEnCurso.direccion.orEmpty())
        } else {
            detenerServicioCarrera()
        }
    }
    DisposableEffect(Unit) {
        onDispose { detenerServicioCarrera() }
    }
```

Agregar el import de `androidx.compose.runtime.DisposableEffect`.

- [ ] **Step 5: Compilar y correr la suite**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
./gradlew.bat :composeApp:assembleDebug
```

Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/androidMain/ composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/ composeApp/src/iosMain/kotlin/pe/leadai/rider/ui/carreras/
git commit -m "feat: el GPS sigue reportando con el telefono bloqueado"
```

---

### Task 5: Pantalla de permisos con deep link (app)

**Repo:** `d:\Personal Proyects\LeadAI-Rider`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/permisos/AbrirAjustes.kt` (expect)
- Create: `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/permisos/AbrirAjustes.android.kt` (actual)
- Create: `composeApp/src/iosMain/kotlin/pe/leadai/rider/ui/permisos/AbrirAjustes.ios.kt` (stub)
- Create: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/permisos/PermisosPantalla.kt`
- Modify: `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt`

**Interfaces:**
- Produces: `expect fun abrirAjustesDeLaApp()`, `expect fun abrirAjustesDeBateria()`, `PermisosPantalla`

**Contexto:** desde Android 11 el permiso de ubicación "todo el tiempo" **no se puede pedir por diálogo** — hay que mandar al usuario a Configuración. El detalle que hace la diferencia: `ACTION_APPLICATION_DETAILS_SETTINGS` con `package:<applicationId>` abre **directo la pantalla de la app**, sin que tenga que buscarla en una lista de cien apps.

- [ ] **Step 1: El deep link a Configuración**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/permisos/AbrirAjustes.kt`:

```kotlin
package pe.leadai.rider.ui.permisos

/**
 * Abre la pantalla de ajustes DE ESTA APP, no la lista general: el usuario no
 * tiene que buscarla entre cien apps. Desde Android 11 el permiso de
 * ubicación "todo el tiempo" solo se puede dar desde acá.
 */
expect fun abrirAjustesDeLaApp()

/** Ajustes de optimización de batería — en Xiaomi/Oppo el sistema mata la app sin esto. */
expect fun abrirAjustesDeBateria()
```

Crear `composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/permisos/AbrirAjustes.android.kt`:

```kotlin
package pe.leadai.rider.ui.permisos

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import pe.leadai.rider.datos.contextoApp

actual fun abrirAjustesDeLaApp() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", contextoApp.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { contextoApp.startActivity(intent) }
}

actual fun abrirAjustesDeBateria() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    // Algunos fabricantes no exponen esa pantalla: si falla, al menos que
    // llegue a los ajustes de la app.
    if (runCatching { contextoApp.startActivity(intent) }.isFailure) abrirAjustesDeLaApp()
}
```

Crear el stub de iOS:

```kotlin
package pe.leadai.rider.ui.permisos

// Stub: los ajustes de iOS llegan con Fase D (UIApplication.openSettingsURLString).
actual fun abrirAjustesDeLaApp() = Unit

actual fun abrirAjustesDeBateria() = Unit
```

- [ ] **Step 2: La pantalla**

Crear `composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/permisos/PermisosPantalla.kt` con una `Card` por permiso, cada una con su explicación de **para qué sirve** (no "damos permisos" sino "para que te lleguen las carreras"):

- **Ubicación en todo momento** → "Para seguir compartiendo tu ubicación cuando guardas el teléfono" → botón "Configurar" → `abrirAjustesDeLaApp()`
- **Batería sin restricciones** → "Para que el teléfono no cierre la app mientras trabajas" → `abrirAjustesDeBateria()`
- **Notificaciones** → "Para avisarte cuando aparezca una carrera cerca" → `abrirAjustesDeLaApp()`

Cada card con un check ✅ cuando el permiso ya está dado (o sin check, si detectarlo resulta complejo — es aceptable en la primera versión).

Estilo: seguí `ElegirModoPantalla.kt`.

- [ ] **Step 3: Ofrecerla en el momento correcto**

**No al abrir la app.** El mejor momento es cuando el rider acepta su primera carrera — ahí el permiso tiene sentido evidente.

En `CarrerasPantalla.kt`, agregar un estado local que muestre la pantalla de permisos como diálogo la primera vez que `miCarrera` deja de ser null. Guardar en DataStore que ya se mostró para no repetirlo.

Alternativa más simple y también válida: un botón "⚙️ Permisos" en la pantalla del rider, junto a "Editar mi perfil". Si la detección del "primera vez" resulta enredada, hacé esto.

- [ ] **Step 4: Compilar**

```bash
./gradlew.bat :composeApp:testDebugUnitTest
./gradlew.bat :composeApp:assembleDebug
```

Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/permisos/ composeApp/src/androidMain/kotlin/pe/leadai/rider/ui/permisos/ composeApp/src/iosMain/kotlin/pe/leadai/rider/ui/permisos/ composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt
git commit -m "feat: pantalla de permisos que lleva directo a los ajustes"
```

---

### Task 6: Verificación final

- [ ] **Step 1: Backend**

```bash
cd "d:/Personal Proyects/leadia"
npm run typecheck && npx vitest run
```

Expected: PASS, sin regresiones.

- [ ] **Step 2: App**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
./gradlew.bat :composeApp:testDebugUnitTest && ./gradlew.bat :composeApp:assembleDebug
```

Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 3: Verificar que el push no rompe nada**

```bash
cd "d:/Personal Proyects/leadia"
grep -n "void avisar" src/routes/carreras.ts src/routes/motorizados.ts
```

Expected: todas las llamadas con `void` (fire-and-forget). Si alguna tiene `await`, un FCM caído bloquearía la respuesta al rider.

- [ ] **Step 4: Verificar el service**

```bash
cd "d:/Personal Proyects/LeadAI-Rider"
grep -n "foregroundServiceType\|ACCESS_BACKGROUND_LOCATION" composeApp/src/androidMain/AndroidManifest.xml
```

Expected: ambos presentes. Sin `foregroundServiceType="location"`, Android 14+ mata el service al arrancar.

- [ ] **Step 5: Desplegar el backend**

```bash
cd "d:/Personal Proyects/leadia"
git push origin main
```

Verificar que el deploy pasa:

```bash
gh run list --workflow=deploy.yml --limit 2
```

---

## Qué NO cubre este plan

Del resto de `PENDIENTES.md`, queda para después:

- **Verificación de riders** (punto 3) — SOAT, SUNARP, selfie contra DNI. Es un producto en sí mismo y merece spec propio.
- **Elegir origen/destino tocando el mapa** (4), **historial y favoritas** (5), **contraofertas** (6), **calificaciones** (7) — producto, cuando haya uso real.
- **iOS** (8) — requiere una Mac.
- **Filtro de preferencias del rider** (9), **soporte real de auto** (10), **cobro al restaurante** (11).
- **`prisma migrate dev` roto** (12) — el workaround (SQL a mano idempotente) funciona.
- **Password del keystore en el repo** (13).
- **Tests de UI** (14).
