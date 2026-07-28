# Backend — Carreras multi-tipo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el pool del rider sirva cuatro tipos de carrera (delivery, mandado, encomienda, pasajero) con comisión configurable que admite campañas de comisión cero.

**Architecture:** Tabla `Carrera` nueva como fuente del pool, sin tocar `Pedido` (que la app de negocios usa en producción). Los pedidos de restaurante se proyectan a `Carrera` al marcarse listos; `Pedido` sigue siendo la verdad para la Cocina. La comisión se resuelve por configuración en BD con vigencia temporal y se congela en la carrera al aceptar.

**Tech Stack:** Node 20, TypeScript, Fastify, Prisma, PostgreSQL, Vitest, Zod.

**Repo:** `d:\Personal Proyects\leadia` — TODOS los commits de este plan van a ese repo, no a `LeadAI-Rider`.

## Global Constraints

- Todo el código, comentarios, nombres de variables y mensajes de error en **español** (convención del repo).
- Montos siempre en **centavos** (`Int`), nunca decimales flotantes.
- Comisión cero (`piso = 0, porcentaje = 0`) es un valor válido: la carrera se acepta, se registra movimiento en S/0, y **no se exige saldo**.
- En tipo `mandado`, la comisión se calcula **solo sobre el flete** (`montoOfrecido`), nunca sobre `montoCompraEstimado` — esa es plata que el rider adelanta y recupera del cliente.
- `comisionCentavos` se **congela** en la carrera al aceptar; cambiar la config nunca altera carreras ya tomadas.
- `Pedido` **no se modifica** en ningún task de este plan.
- Los deliveries en producción no pueden romperse: `Pedido` sigue actualizándose para la Cocina.
- Tests con `vitest run`, mockeando Prisma con `vi.hoisted` + `vi.mock` (patrón de `tests/monedero.test.ts`).
- Commits en español con prefijo `feat:` / `test:` / `refactor:`.

---

### Task 1: Modelo `Carrera` y configuración de comisión

**Files:**
- Modify: `prisma/schema.prisma` (agregar al final, después de `MovimientoMonedero`)
- Create: `prisma/migrations/<timestamp>_carreras_multitipo/migration.sql` (lo genera Prisma)

**Interfaces:**
- Consumes: nada (primer task)
- Produces: modelos `Carrera` y `ConfigComision`; campo `PerfilMotorizado.tipoVehiculo`

- [ ] **Step 1: Agregar los modelos al schema**

En `prisma/schema.prisma`, al final del archivo:

```prisma
// Una CARRERA es cualquier trabajo que un rider puede tomar del pool: el
// delivery de un restaurante (proyectado desde Pedido), una encomienda o un
// pasajero. Se separa de Pedido porque Pedido exige tenantId/leadId/items —
// cosas que un taxi no tiene — y porque la app de negocios usa Pedido en
// producción: acá no se toca.
model Carrera {
  id   String @id @default(cuid())
  // 'pedido' | 'mandado' | 'encomienda' | 'pasajero'
  tipo String

  // Solo tipo 'pedido': el Pedido del restaurante que originó esta carrera.
  pedidoId String? @unique
  // Solo mandado/encomienda/pasajero: el usuario que la pidió.
  solicitanteId String?
  // Teléfono de contacto del solicitante. Se guarda ACÁ y no se lee de
  // Usuario: el modelo Usuario no tiene teléfono (solo email y nombre), y
  // además quien pide puede querer dar un número distinto al de su cuenta.
  // El rider solo lo ve cuando la carrera ya es suya.
  solicitanteContacto String @default("")

  origenTexto  String  @default("")
  origenLat    Float?
  origenLng    Float?
  destinoTexto String  @default("")
  destinoLat   Float?
  destinoLng   Float?

  kmEstimado    Float?
  // FLETE propuesto por el sistema (centavos) — se guarda para medir qué tan
  // bien calibra la fórmula contra lo que la gente realmente ofrece.
  montoSugerido Int?
  // FLETE definido por el solicitante (centavos). Es el monto sobre el que se
  // calcula la comisión; el pago real es en efectivo entre las partes.
  montoOfrecido Int?
  // Solo 'mandado': lo que cuesta lo que el rider va a COMPRAR ("tráeme un
  // chifa del Salón Cantón"). Plata que ADELANTA y recupera del cliente — la
  // comisión NUNCA se calcula sobre esto, o se le cobraría por prestar plata.
  montoCompraEstimado Int?
  notas         String @default("")

  // 'disponible' | 'aceptada' | 'recogida' | 'entregada' | 'cancelada'
  estado         String  @default("disponible")
  riderUsuarioId String?
  // Comisión CONGELADA al aceptar (centavos). Cambiar ConfigComision nunca
  // altera carreras ya tomadas. 0 es válido (campaña sin comisión).
  comisionCentavos Int?
  kmReal           Float?

  aceptadaEn  DateTime?
  recogidoEn  DateTime?
  entregadoEn DateTime?
  creadoEn    DateTime  @default(now())

  @@index([estado, tipo])
  @@index([riderUsuarioId, estado])
}

// Cuánto cobra LeadAI por carrera, configurable SIN deploy. Permite campañas
// de comisión cero (inDrive entró a Perú con 6 meses gratis) y ajustar por
// ciudad cuando los tickets no son los de Tacna.
model ConfigComision {
  id String @id @default(cuid())
  // null = aplica a todos los tipos; si no: 'pedido'|'encomienda'|'pasajero'
  tipo String?
  // null = aplica a todas las zonas; si no: el departamento ("Tacna").
  zona String?
  // Piso en centavos. 0 es válido (campaña gratis).
  pisoCentavos Int @default(100)
  // Porcentaje sobre montoOfrecido, 0-100. 0 es válido.
  porcentaje Int @default(0)
  // Vigencia: una campaña con `hasta` se apaga sola.
  desde DateTime  @default(now())
  hasta DateTime?
  nota  String    @default("")

  @@index([tipo, zona])
}
```

Y en el modelo `PerfilMotorizado` existente (línea ~196, después de `placa`), agregar:

```prisma
  // 'moto' | 'auto' — la sugerencia de monto depende del vehículo: una moto
  // 150cc rinde ~3x más que un auto, así que su factor por km es menor.
  tipoVehiculo String @default("moto")
```

- [ ] **Step 2: Generar la migración**

```bash
cd "d:/Personal Proyects/leadia"
npx prisma migrate dev --name carreras_multitipo
```

Expected: crea `prisma/migrations/<timestamp>_carreras_multitipo/migration.sql` y regenera el cliente. Sin errores.

- [ ] **Step 3: Verificar que el cliente compila**

```bash
npm run typecheck
```

Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add prisma/schema.prisma prisma/migrations/
git commit -m "feat: modelo Carrera y ConfigComision para carreras multi-tipo"
```

---

### Task 2: Resolver la comisión (`src/core/comision.ts`)

**Files:**
- Create: `src/core/comision.ts`
- Test: `tests/comision.test.ts`

**Interfaces:**
- Consumes: modelo `ConfigComision` (Task 1)
- Produces:
  - `resolverComision(args: { tipo: string; zona?: string | null; montoOfrecidoCentavos?: number | null }): Promise<number>` — devuelve los centavos a cobrar
  - `COMISION_DEFECTO: { pisoCentavos: number; porcentaje: number }`

- [ ] **Step 1: Escribir el test que falla**

Crear `tests/comision.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Cuánto cobra LeadAI por carrera. Es DINERO: los casos que importan son el
// piso, el porcentaje, la especificidad de la config y la campaña gratis.

const { prismaMock } = vi.hoisted(() => ({
  prismaMock: {
    configComision: { findMany: vi.fn() },
  },
}));
vi.mock('../src/lib/prisma.js', () => ({ prisma: prismaMock }));

import { resolverComision, COMISION_DEFECTO } from '../src/core/comision.js';

beforeEach(() => {
  vi.clearAllMocks();
  prismaMock.configComision.findMany.mockResolvedValue([]);
});

describe('resolverComision', () => {
  it('sin config usa el defecto: S/1 de piso', async () => {
    const c = await resolverComision({ tipo: 'pedido', zona: 'Tacna' });
    expect(c).toBe(100);
    expect(COMISION_DEFECTO.pisoCentavos).toBe(100);
  });

  it('delivery cobra el piso aunque haya monto', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pedido', zona: null, pisoCentavos: 100, porcentaje: 0, desde: new Date(0), hasta: null },
    ]);
    const c = await resolverComision({ tipo: 'pedido', zona: 'Tacna', montoOfrecidoCentavos: 5000 });
    expect(c).toBe(100);
  });

  it('pasajero cobra 10% cuando supera el piso', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pasajero', zona: null, pisoCentavos: 100, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    // S/16 → 10% = S/1.60
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna', montoOfrecidoCentavos: 1600 });
    expect(c).toBe(160);
  });

  it('pasajero cobra el PISO cuando el 10% queda por debajo', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pasajero', zona: null, pisoCentavos: 100, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    // S/6 → 10% = S/0.60, menor que el piso de S/1
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna', montoOfrecidoCentavos: 600 });
    expect(c).toBe(100);
  });

  it('CAMPAÑA GRATIS: piso 0 y porcentaje 0 cobran cero', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: null, zona: 'Tacna', pisoCentavos: 0, porcentaje: 0, desde: new Date(0), hasta: null },
    ]);
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna', montoOfrecidoCentavos: 5000 });
    expect(c).toBe(0);
  });

  it('la config MÁS ESPECÍFICA gana (tipo+zona sobre tipo sobre global)', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: null, zona: null, pisoCentavos: 100, porcentaje: 0, desde: new Date(0), hasta: null },
      { tipo: 'pasajero', zona: null, pisoCentavos: 150, porcentaje: 0, desde: new Date(0), hasta: null },
      { tipo: 'pasajero', zona: 'Tacna', pisoCentavos: 200, porcentaje: 0, desde: new Date(0), hasta: null },
    ]);
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna' });
    expect(c).toBe(200);
  });

  it('sin monto ofrecido cae al piso (no explota)', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pasajero', zona: null, pisoCentavos: 100, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna', montoOfrecidoCentavos: null });
    expect(c).toBe(100);
  });

  it('redondea al centavo (nunca fracciones)', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pasajero', zona: null, pisoCentavos: 0, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    // S/7.77 → 10% = 77.7 centavos → 78
    const c = await resolverComision({ tipo: 'pasajero', zona: 'Tacna', montoOfrecidoCentavos: 777 });
    expect(c).toBe(78);
  });

  it('MANDADO: la comision sale del FLETE, jamas del monto de compra', async () => {
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'mandado', zona: null, pisoCentavos: 100, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    // Flete S/8, compra S/60 ("tráeme un chifa"). El 10% es sobre S/8 = S/0.80,
    // que no llega al piso → S/1. Si se calculara sobre S/68 daría S/6.80 y el
    // rider estaría pagando por adelantar plata que no es suya.
    const c = await resolverComision({ tipo: 'mandado', zona: 'Tacna', montoOfrecidoCentavos: 800 });
    expect(c).toBe(100);
  });
});
```

- [ ] **Step 2: Correr el test para verificar que falla**

```bash
cd "d:/Personal Proyects/leadia"
npx vitest run tests/comision.test.ts
```

Expected: FAIL — `Cannot find module '../src/core/comision.js'`

- [ ] **Step 3: Implementar**

Crear `src/core/comision.ts`:

```typescript
import { prisma } from '../lib/prisma.js';

// Cuánto cobra LeadAI por carrera aceptada. Configurable SIN deploy
// (ConfigComision) para poder correr campañas de comisión CERO al entrar a
// una ciudad nueva — la jugada con la que inDrive entró a Perú.
//
// La comisión se CONGELA en la carrera al aceptarla: cambiar la config nunca
// altera lo ya cobrado.

/** Lo que se cobra si nadie configuró nada: S/1, sin porcentaje. */
export const COMISION_DEFECTO = { pisoCentavos: 100, porcentaje: 0 } as const;

interface FilaConfig {
  tipo: string | null;
  zona: string | null;
  pisoCentavos: number;
  porcentaje: number;
}

/**
 * Qué tan específica es una config: tipo+zona (3) gana a tipo (2), que gana a
 * zona (1), que gana a la global (0). Así una campaña de una ciudad puntual
 * pisa la regla general sin borrarla.
 */
function especificidad(c: FilaConfig): number {
  return (c.tipo ? 2 : 0) + (c.zona ? 1 : 0);
}

/**
 * Centavos a cobrar por una carrera. `max(piso, porcentaje × monto)`, con la
 * config vigente más específica. CERO es un resultado válido (campaña gratis).
 */
export async function resolverComision(args: {
  tipo: string;
  zona?: string | null;
  montoOfrecidoCentavos?: number | null;
}): Promise<number> {
  const ahora = new Date();
  const candidatas = await prisma.configComision.findMany({
    where: {
      OR: [{ tipo: args.tipo }, { tipo: null }],
      desde: { lte: ahora },
      AND: [{ OR: [{ hasta: null }, { hasta: { gte: ahora } }] }],
    },
  });

  // La zona no se filtra en SQL porque `null` significa "todas": se resuelve
  // acá junto con la especificidad.
  const aplicables = (candidatas as FilaConfig[]).filter(
    (c) => c.zona == null || c.zona === args.zona,
  );

  const elegida = aplicables.sort((a, b) => especificidad(b) - especificidad(a))[0] ?? COMISION_DEFECTO;
  const piso = 'pisoCentavos' in elegida ? elegida.pisoCentavos : COMISION_DEFECTO.pisoCentavos;
  const porcentaje = 'porcentaje' in elegida ? elegida.porcentaje : COMISION_DEFECTO.porcentaje;

  const monto = args.montoOfrecidoCentavos ?? 0;
  const porPorcentaje = Math.round((monto * porcentaje) / 100);
  return Math.max(piso, porPorcentaje);
}
```

- [ ] **Step 4: Correr el test para verificar que pasa**

```bash
npx vitest run tests/comision.test.ts
```

Expected: PASS — 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/core/comision.ts tests/comision.test.ts
git commit -m "feat: resolver comision configurable con soporte de campana gratis"
```

---

### Task 3: Cobro con comisión variable en el monedero

**Files:**
- Modify: `src/core/monedero.ts:75-115` (función `cobrarCarrera`)
- Modify: `src/core/monedero.ts:121-148` (función `devolverCarrera`)
- Test: `tests/monedero.test.ts` (agregar casos)

**Interfaces:**
- Consumes: `resolverComision` (Task 2)
- Produces:
  - `cobrarCarrera(usuarioId: string, pedidoId: string, centavos?: number): Promise<ResultadoCobro>` — `centavos` opcional; si se omite usa `COSTO_CARRERA_CENTAVOS` (compatibilidad con llamadas existentes)
  - `devolverCarrera(usuarioId: string, pedidoId: string, centavos?: number): Promise<void>`

**Contexto:** hoy `cobrarCarrera` descuenta la constante `COSTO_CARRERA_CENTAVOS`. Ahora el monto lo decide quien llama (resuelto por `resolverComision`). El parámetro es opcional para no romper los llamadores actuales.

- [ ] **Step 1: Escribir los tests que fallan**

Agregar al final de `tests/monedero.test.ts`, dentro del archivo existente:

```typescript
describe('cobrarCarrera con comision variable', () => {
  it('cobra el monto que le pasan, no la constante', async () => {
    prismaMock.monederoRider.update.mockResolvedValue({ ...MONEDERO, saldoCentavos: 1840 });
    prismaMock.movimientoMonedero.create.mockResolvedValue({});

    const r = await cobrarCarrera('u1', 'p1', 160);

    expect(r).toEqual({ ok: true, saldoCentavos: 1840 });
    expect(prismaMock.monederoRider.update).toHaveBeenCalledWith(expect.objectContaining({
      data: { saldoCentavos: { decrement: 160 } },
    }));
    const mov = prismaMock.movimientoMonedero.create.mock.calls[0][0];
    expect(mov.data).toMatchObject({ montoCentavos: -160 });
  });

  it('CAMPAÑA GRATIS: comision 0 acepta sin exigir saldo', async () => {
    prismaMock.monederoRider.upsert.mockResolvedValue({ ...MONEDERO, saldoCentavos: 0 });
    prismaMock.monederoRider.update.mockResolvedValue({ ...MONEDERO, saldoCentavos: 0 });
    prismaMock.movimientoMonedero.create.mockResolvedValue({});

    const r = await cobrarCarrera('u1', 'p1', 0);

    expect(r.ok).toBe(true);
    expect(r).toMatchObject({ saldoCentavos: 0 });
  });

  it('sin monto explícito sigue cobrando S/1 (compatibilidad)', async () => {
    prismaMock.monederoRider.update.mockResolvedValue({ ...MONEDERO, saldoCentavos: 1900 });
    prismaMock.movimientoMonedero.create.mockResolvedValue({});

    await cobrarCarrera('u1', 'p1');

    expect(prismaMock.monederoRider.update).toHaveBeenCalledWith(expect.objectContaining({
      data: { saldoCentavos: { decrement: COSTO_CARRERA_CENTAVOS } },
    }));
  });

  it('sin saldo para una comision ALTA no cobra', async () => {
    prismaMock.monederoRider.upsert.mockResolvedValue({ ...MONEDERO, saldoCentavos: 100 });

    const r = await cobrarCarrera('u1', 'p1', 500);

    expect(r).toEqual({ ok: false, motivo: 'sin_saldo', saldoCentavos: 100 });
    expect(prismaMock.monederoRider.update).not.toHaveBeenCalled();
  });

  it('devuelve el mismo monto que se cobro', async () => {
    prismaMock.movimientoMonedero.findFirst.mockResolvedValue({ id: 'mov1' });
    prismaMock.monederoRider.update.mockResolvedValue({ ...MONEDERO, saldoCentavos: 2160 });
    prismaMock.movimientoMonedero.create.mockResolvedValue({});

    await devolverCarrera('u1', 'p1', 160);

    expect(prismaMock.monederoRider.update).toHaveBeenCalledWith(expect.objectContaining({
      data: { saldoCentavos: { increment: 160 } },
    }));
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/monedero.test.ts
```

Expected: FAIL — los tests nuevos fallan porque `cobrarCarrera` ignora el tercer parámetro (descuenta 100 en vez de 160).

- [ ] **Step 3: Implementar**

En `src/core/monedero.ts`, reemplazar la firma y el cuerpo de `cobrarCarrera` (líneas 75-115) por:

```typescript
export async function cobrarCarrera(
  usuarioId: string,
  pedidoId: string,
  centavos: number = COSTO_CARRERA_CENTAVOS,
): Promise<ResultadoCobro> {
  const monedero = await monederoDe(usuarioId);

  const yaCobrada = await prisma.movimientoMonedero.findFirst({
    where: { monederoId: monedero.id, pedidoId, tipo: 'cobro_carrera' },
    select: { id: true },
  });
  if (yaCobrada) return { ok: true, saldoCentavos: monedero.saldoCentavos };

  // Campaña de comisión CERO: no se exige saldo. Un rider con el monedero
  // vacío tiene que poder trabajar mientras dure la campaña.
  if (centavos > 0 && monedero.saldoCentavos < centavos) {
    return { ok: false, motivo: 'sin_saldo', saldoCentavos: monedero.saldoCentavos };
  }

  try {
    const [actualizado] = await prisma.$transaction([
      prisma.monederoRider.update({
        where: { id: monedero.id },
        data: { saldoCentavos: { decrement: centavos } },
      }),
      prisma.movimientoMonedero.create({
        data: {
          monederoId: monedero.id,
          tipo: 'cobro_carrera',
          montoCentavos: -centavos,
          concepto: centavos === 0 ? 'Carrera aceptada (sin comisión)' : 'Carrera aceptada',
          pedidoId,
        },
      }),
    ]);
    return { ok: true, saldoCentavos: actualizado.saldoCentavos };
  } catch (err) {
    logger.warn({ err, usuarioId, pedidoId }, 'cobrarCarrera: movimiento duplicado (idempotente)');
    const actual = await monederoDe(usuarioId);
    return { ok: true, saldoCentavos: actual.saldoCentavos };
  }
}
```

Y en `devolverCarrera` (líneas 121-148), cambiar la firma y los dos usos de la constante:

```typescript
export async function devolverCarrera(
  usuarioId: string,
  pedidoId: string,
  centavos: number = COSTO_CARRERA_CENTAVOS,
): Promise<void> {
```

Dentro del `$transaction`, reemplazar `COSTO_CARRERA_CENTAVOS` por `centavos` en el `increment` y en `montoCentavos`.

- [ ] **Step 4: Correr los tests**

```bash
npx vitest run tests/monedero.test.ts
```

Expected: PASS — los tests viejos siguen pasando (compatibilidad) y los 5 nuevos también.

- [ ] **Step 5: Commit**

```bash
git add src/core/monedero.ts tests/monedero.test.ts
git commit -m "feat: cobro de carrera con comision variable y campana gratis"
```

---

### Task 4: Fórmula de monto sugerido (`src/core/sugerencia.ts`)

**Files:**
- Create: `src/core/sugerencia.ts`
- Test: `tests/sugerencia.test.ts`

**Interfaces:**
- Consumes: nada
- Produces:
  - `sugerirMontoCentavos(km: number, tipoVehiculo?: string): number`
  - `TARIFAS_SUGERENCIA: Record<'moto' | 'auto', { baseCentavos: number; porKmCentavos: number }>`

- [ ] **Step 1: Escribir el test que falla**

Crear `tests/sugerencia.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { sugerirMontoCentavos, TARIFAS_SUGERENCIA } from '../src/core/sugerencia.js';

// El monto que la app PROPONE al solicitante (editable). No es una tarifa: el
// precio final lo acuerdan las partes. Calibrada contra el mercado de Tacna —
// un delivery de 2-3 km se paga ~S/6.

describe('sugerirMontoCentavos', () => {
  it('moto: base S/4 + S/1.20 por km', () => {
    expect(TARIFAS_SUGERENCIA.moto.baseCentavos).toBe(400);
    expect(TARIFAS_SUGERENCIA.moto.porKmCentavos).toBe(120);
  });

  it('un viaje de 2 km en moto sugiere S/6.40', () => {
    expect(sugerirMontoCentavos(2, 'moto')).toBe(640);
  });

  it('un viaje de 3 km en moto sugiere S/7.60 (el precio real del Pollon)', () => {
    expect(sugerirMontoCentavos(3, 'moto')).toBe(760);
  });

  it('un viaje de 10 km en moto sugiere S/16', () => {
    expect(sugerirMontoCentavos(10, 'moto')).toBe(1600);
  });

  it('el auto cuesta mas por km (consume ~3x)', () => {
    expect(sugerirMontoCentavos(3, 'auto')).toBe(1350);
    expect(sugerirMontoCentavos(3, 'auto')).toBeGreaterThan(sugerirMontoCentavos(3, 'moto'));
  });

  it('vehiculo desconocido usa moto (el caso mayoritario)', () => {
    expect(sugerirMontoCentavos(3, 'bicicleta')).toBe(sugerirMontoCentavos(3, 'moto'));
    expect(sugerirMontoCentavos(3)).toBe(sugerirMontoCentavos(3, 'moto'));
  });

  it('km 0 o negativo devuelve solo la base (nunca menos)', () => {
    expect(sugerirMontoCentavos(0, 'moto')).toBe(400);
    expect(sugerirMontoCentavos(-5, 'moto')).toBe(400);
  });

  it('redondea al centavo', () => {
    // 2.7 km → 400 + 324 = 724
    expect(sugerirMontoCentavos(2.7, 'moto')).toBe(724);
    expect(Number.isInteger(sugerirMontoCentavos(2.7, 'moto'))).toBe(true);
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/sugerencia.test.ts
```

Expected: FAIL — `Cannot find module '../src/core/sugerencia.js'`

- [ ] **Step 3: Implementar**

Crear `src/core/sugerencia.ts`:

```typescript
// El monto que la app PROPONE al solicitante — editable, no una tarifa. El
// precio final lo acuerdan el pasajero y el rider; LeadAI solo enlaza.
//
// Calibración: una moto 150cc rinde 30-45 km/l contra 10-14 km/l de un auto
// (~3x), así que el factor por km del auto es ~2x el de la moto. Los valores
// de la moto reproducen el mercado real de Tacna: 2-3 km ≈ S/6-8, que es lo
// que se paga hoy por un delivery.
//
// NOTA: estas constantes NO se muestran al rider. La app solo le enseña el
// monto ofrecido y lo que recibe tras la comisión — él conoce sus costos
// mejor que el sistema.

export const TARIFAS_SUGERENCIA = {
  moto: { baseCentavos: 400, porKmCentavos: 120 },
  auto: { baseCentavos: 600, porKmCentavos: 250 },
} as const;

/** Monto sugerido en centavos para una distancia dada. Nunca menor que la base. */
export function sugerirMontoCentavos(km: number, tipoVehiculo: string = 'moto'): number {
  const tarifa =
    tipoVehiculo === 'auto' ? TARIFAS_SUGERENCIA.auto : TARIFAS_SUGERENCIA.moto;
  const kmValidos = km > 0 ? km : 0;
  return Math.round(tarifa.baseCentavos + kmValidos * tarifa.porKmCentavos);
}
```

- [ ] **Step 4: Correr el test**

```bash
npx vitest run tests/sugerencia.test.ts
```

Expected: PASS — 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/core/sugerencia.ts tests/sugerencia.test.ts
git commit -m "feat: formula de monto sugerido por tipo de vehiculo"
```

---

### Task 5: Proyectar pedidos a `Carrera`

**Files:**
- Create: `src/core/carreras.ts`
- Test: `tests/carreras-proyeccion.test.ts`

**Interfaces:**
- Consumes: modelo `Carrera` (Task 1)
- Produces:
  - `proyectarPedidoACarrera(pedidoId: string): Promise<void>` — idempotente
  - `carreraDePedido(pedidoId: string): Promise<{ id: string } | null>`

**Contexto:** un delivery nace cuando el negocio marca el pedido como `listo`. Esta función crea la `Carrera` espejo. `Pedido` sigue siendo la verdad para la Cocina; `Carrera` es la verdad para el pool.

- [ ] **Step 1: Escribir el test que falla**

Crear `tests/carreras-proyeccion.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Un delivery entra al pool como Carrera espejo del Pedido. Pedido sigue
// siendo la verdad para la Cocina — acá NO se toca.

const { prismaMock } = vi.hoisted(() => ({
  prismaMock: {
    pedido: { findUnique: vi.fn() },
    carrera: { findUnique: vi.fn(), create: vi.fn() },
  },
}));
vi.mock('../src/lib/prisma.js', () => ({ prisma: prismaMock }));

import { proyectarPedidoACarrera } from '../src/core/carreras.js';

const PEDIDO = {
  id: 'ped1',
  direccion: 'Jose Olaya 110',
  totalCentavos: 4500,
  direccionLat: -18.01,
  direccionLng: -70.25,
  tenant: { nombre: 'El Pollon', distrito: 'Tacna, Tacna', lat: -18.0, lng: -70.24 },
};

beforeEach(() => {
  vi.clearAllMocks();
  prismaMock.carrera.findUnique.mockResolvedValue(null);
  prismaMock.pedido.findUnique.mockResolvedValue(PEDIDO);
  prismaMock.carrera.create.mockResolvedValue({ id: 'car1' });
});

describe('proyectarPedidoACarrera', () => {
  it('crea la carrera tipo pedido con origen en el negocio', async () => {
    await proyectarPedidoACarrera('ped1');

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data).toMatchObject({
      tipo: 'pedido',
      pedidoId: 'ped1',
      estado: 'disponible',
      origenTexto: 'El Pollon',
      destinoTexto: 'Jose Olaya 110',
      montoOfrecido: 4500,
    });
  });

  it('es IDEMPOTENTE: si ya existe no crea otra', async () => {
    prismaMock.carrera.findUnique.mockResolvedValue({ id: 'car1' });

    await proyectarPedidoACarrera('ped1');

    expect(prismaMock.carrera.create).not.toHaveBeenCalled();
  });

  it('un pedido inexistente no explota ni crea nada', async () => {
    prismaMock.pedido.findUnique.mockResolvedValue(null);

    await expect(proyectarPedidoACarrera('fantasma')).resolves.toBeUndefined();
    expect(prismaMock.carrera.create).not.toHaveBeenCalled();
  });

  it('copia las coordenadas del negocio y del cliente', async () => {
    await proyectarPedidoACarrera('ped1');

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data).toMatchObject({
      origenLat: -18.0,
      origenLng: -70.24,
      destinoLat: -18.01,
      destinoLng: -70.25,
    });
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/carreras-proyeccion.test.ts
```

Expected: FAIL — `Cannot find module '../src/core/carreras.js'`

- [ ] **Step 3: Implementar**

Crear `src/core/carreras.ts`:

```typescript
import { prisma } from '../lib/prisma.js';
import { logger } from '../lib/logger.js';

// Los deliveries de restaurante entran al pool como Carrera ESPEJO del
// Pedido. Pedido sigue siendo la verdad para la Cocina (la app de negocios lo
// lee tal cual); Carrera es la verdad para el pool del rider. Nunca se
// modifica Pedido desde acá.

/** La carrera espejo de un pedido, si ya existe. */
export async function carreraDePedido(pedidoId: string): Promise<{ id: string } | null> {
  return prisma.carrera.findUnique({ where: { pedidoId }, select: { id: true } });
}

/**
 * Crea la Carrera de un pedido listo. IDEMPOTENTE: llamarla dos veces (por un
 * reintento o un doble click en la Cocina) no duplica la carrera.
 */
export async function proyectarPedidoACarrera(pedidoId: string): Promise<void> {
  const yaExiste = await carreraDePedido(pedidoId);
  if (yaExiste) return;

  const pedido = await prisma.pedido.findUnique({
    where: { id: pedidoId },
    include: { tenant: { select: { nombre: true, distrito: true, lat: true, lng: true } } },
  });
  if (!pedido) return;

  try {
    await prisma.carrera.create({
      data: {
        tipo: 'pedido',
        pedidoId: pedido.id,
        estado: 'disponible',
        // El origen de un delivery es el LOCAL; el destino, la casa del cliente.
        origenTexto: pedido.tenant.nombre,
        origenLat: pedido.tenant.lat,
        origenLng: pedido.tenant.lng,
        destinoTexto: pedido.direccion ?? '',
        destinoLat: pedido.direccionLat,
        destinoLng: pedido.direccionLng,
        // En delivery el monto lo arregló el negocio con el rider: se copia el
        // total del pedido como referencia, pero NO se le aplica porcentaje.
        montoOfrecido: pedido.totalCentavos,
      },
    });
  } catch (err) {
    // El unique de pedidoId protege contra la carrera de dos proyecciones
    // simultáneas: si otra ganó, no hay nada que hacer.
    logger.warn({ err, pedidoId }, 'proyectarPedidoACarrera: duplicado (idempotente)');
  }
}
```

- [ ] **Step 4: Correr el test**

```bash
npx vitest run tests/carreras-proyeccion.test.ts
```

Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/core/carreras.ts tests/carreras-proyeccion.test.ts
git commit -m "feat: proyeccion de pedidos listos a Carrera"
```

---

### Task 6: Crear carreras de mandado, encomienda y pasajero

**Files:**
- Modify: `src/core/carreras.ts` (agregar función)
- Test: `tests/carreras-proyeccion.test.ts` (agregar describe)

**Interfaces:**
- Consumes: `sugerirMontoCentavos` (Task 4)
- Produces:
  - `crearCarreraLibre(args: { tipo: 'mandado' | 'encomienda' | 'pasajero'; solicitanteId: string; origenTexto: string; origenLat?: number | null; origenLng?: number | null; destinoTexto: string; destinoLat?: number | null; destinoLng?: number | null; kmEstimado?: number | null; montoOfrecidoCentavos?: number | null; montoCompraEstimadoCentavos?: number | null; notas?: string }): Promise<{ id: string; montoSugerido: number; montoOfrecido: number }>`

- [ ] **Step 1: Escribir el test que falla**

Agregar a `tests/carreras-proyeccion.test.ts`. Primero, extender el mock de Prisma al inicio del archivo — reemplazar el bloque `vi.hoisted` existente por:

```typescript
const { prismaMock } = vi.hoisted(() => ({
  prismaMock: {
    pedido: { findUnique: vi.fn() },
    carrera: { findUnique: vi.fn(), create: vi.fn() },
  },
}));
```

(ya es así — no cambia). Y agregar al final del archivo:

```typescript
import { crearCarreraLibre } from '../src/core/carreras.js';

describe('crearCarreraLibre', () => {
  beforeEach(() => {
    prismaMock.carrera.create.mockResolvedValue({
      id: 'car2', montoSugerido: 760, montoOfrecido: 760,
    });
  });

  it('sugiere el monto por km cuando el solicitante no ofrece uno', async () => {
    await crearCarreraLibre({
      tipo: 'pasajero',
      solicitanteId: 'u1',
      origenTexto: 'Av. Grau 240',
      destinoTexto: 'Miraflores',
      kmEstimado: 3,
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    // 3 km en moto → S/7.60
    expect(args.data.montoSugerido).toBe(760);
    expect(args.data.montoOfrecido).toBe(760);
  });

  it('respeta el monto que ofrece el solicitante', async () => {
    await crearCarreraLibre({
      tipo: 'pasajero',
      solicitanteId: 'u1',
      origenTexto: 'A',
      destinoTexto: 'B',
      kmEstimado: 3,
      montoOfrecidoCentavos: 1000,
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data.montoSugerido).toBe(760);
    expect(args.data.montoOfrecido).toBe(1000);
  });

  it('nace disponible y con el solicitante', async () => {
    await crearCarreraLibre({
      tipo: 'encomienda',
      solicitanteId: 'u9',
      origenTexto: 'A',
      destinoTexto: 'B',
      kmEstimado: 2,
      notas: 'caja mediana',
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data).toMatchObject({
      tipo: 'encomienda',
      estado: 'disponible',
      solicitanteId: 'u9',
      notas: 'caja mediana',
    });
  });

  it('sin km estimado sugiere solo la base', async () => {
    await crearCarreraLibre({
      tipo: 'pasajero',
      solicitanteId: 'u1',
      origenTexto: 'A',
      destinoTexto: 'B',
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data.montoSugerido).toBe(400);
  });

  it('MANDADO: guarda el flete y el monto de compra POR SEPARADO', async () => {
    // "Tráeme un chifa del Salón Cantón": flete S/8, compra S/60.
    await crearCarreraLibre({
      tipo: 'mandado',
      solicitanteId: 'u1',
      origenTexto: 'Chifa Salon Canton',
      destinoTexto: 'Jose Olaya 110',
      kmEstimado: 3,
      montoOfrecidoCentavos: 800,
      montoCompraEstimadoCentavos: 6000,
      notas: 'combinado sin verduras',
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data).toMatchObject({
      tipo: 'mandado',
      montoOfrecido: 800,
      montoCompraEstimado: 6000,
      notas: 'combinado sin verduras',
    });
    // El flete NO se contamina con el monto de compra.
    expect(args.data.montoOfrecido).toBe(800);
  });

  it('el monto de compra solo aplica a mandado', async () => {
    await crearCarreraLibre({
      tipo: 'pasajero',
      solicitanteId: 'u1',
      origenTexto: 'A',
      destinoTexto: 'B',
      kmEstimado: 3,
      montoCompraEstimadoCentavos: 6000,
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data.montoCompraEstimado).toBe(null);
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/carreras-proyeccion.test.ts
```

Expected: FAIL — `crearCarreraLibre is not a function`

- [ ] **Step 3: Implementar**

Agregar al final de `src/core/carreras.ts`:

```typescript
import { sugerirMontoCentavos } from './sugerencia.js';

/**
 * Carrera pedida por un usuario, sin negocio cliente de por medio: un mandado
 * ("tráeme un chifa del Salón Cantón"), una encomienda o un pasajero. El
 * sistema SUGIERE el flete por distancia; el solicitante puede ofrecer otro.
 * Se guardan los dos para calibrar la fórmula después.
 *
 * En 'mandado' el `montoCompraEstimado` es plata que el rider ADELANTA y
 * recupera del cliente: se guarda aparte del flete y la comisión jamás lo toca.
 */
export async function crearCarreraLibre(args: {
  tipo: 'mandado' | 'encomienda' | 'pasajero';
  solicitanteId: string;
  origenTexto: string;
  origenLat?: number | null;
  origenLng?: number | null;
  destinoTexto: string;
  destinoLat?: number | null;
  destinoLng?: number | null;
  kmEstimado?: number | null;
  montoOfrecidoCentavos?: number | null;
  montoCompraEstimadoCentavos?: number | null;
  solicitanteContacto?: string;
  notas?: string;
}): Promise<{ id: string; montoSugerido: number; montoOfrecido: number }> {
  const montoSugerido = sugerirMontoCentavos(args.kmEstimado ?? 0);
  const montoOfrecido = args.montoOfrecidoCentavos ?? montoSugerido;

  const carrera = await prisma.carrera.create({
    data: {
      tipo: args.tipo,
      solicitanteId: args.solicitanteId,
      estado: 'disponible',
      origenTexto: args.origenTexto,
      origenLat: args.origenLat ?? null,
      origenLng: args.origenLng ?? null,
      destinoTexto: args.destinoTexto,
      destinoLat: args.destinoLat ?? null,
      destinoLng: args.destinoLng ?? null,
      kmEstimado: args.kmEstimado ?? null,
      montoSugerido,
      montoOfrecido,
      // Solo tiene sentido en un mandado: en los otros tipos el rider no compra
      // nada, así que un monto de compra ahí sería un dato sin significado.
      montoCompraEstimado:
        args.tipo === 'mandado' ? args.montoCompraEstimadoCentavos ?? null : null,
      solicitanteContacto: args.solicitanteContacto ?? '',
      notas: args.notas ?? '',
    },
  });

  return { id: carrera.id, montoSugerido, montoOfrecido };
}
```

Mover el `import { sugerirMontoCentavos }` al bloque de imports del inicio del archivo (junto a `prisma` y `logger`), no dejarlo a mitad.

- [ ] **Step 4: Correr el test**

```bash
npx vitest run tests/carreras-proyeccion.test.ts
```

Expected: PASS — 8 tests (4 de proyección + 4 nuevos).

- [ ] **Step 5: Commit**

```bash
git add src/core/carreras.ts tests/carreras-proyeccion.test.ts
git commit -m "feat: crear carreras de encomienda y pasajero con monto sugerido"
```

---

### Task 7: El pool sirve los cuatro tipos

**Files:**
- Modify: `src/routes/motorizados.ts:151-210` (endpoint `GET /motorizados/carreras`)
- Test: `tests/rutas-motorizados.test.ts` (agregar casos)

**Interfaces:**
- Consumes: modelo `Carrera` (Task 1)
- Produces: el endpoint devuelve carreras con `tipo`, `origenTexto`, `destinoTexto`, `montoOfrecido`, además de los campos actuales

**Contexto:** hoy el endpoint arma el feed desde `Pedido`. Ahora lee `Carrera` (que ya incluye los pedidos proyectados por Task 5). El contrato de respuesta **agrega** campos, no quita: la app vieja sigue funcionando.

- [ ] **Step 1: Leer el test existente para seguir su patrón**

```bash
cd "d:/Personal Proyects/leadia"
head -50 tests/rutas-motorizados.test.ts
```

Usar el mismo estilo de mock y de construcción de la app Fastify que ya emplea ese archivo.

- [ ] **Step 2: Escribir el test que falla**

Agregar a `tests/rutas-motorizados.test.ts` un `describe` nuevo. El mock de Prisma del archivo debe extenderse con `carrera: { findMany: vi.fn(), findFirst: vi.fn() }`.

```typescript
describe('GET /motorizados/carreras — cuatro tipos', () => {
  it('devuelve carreras de pedido, encomienda y pasajero', async () => {
    prismaMock.perfilMotorizado.findUnique.mockResolvedValue({
      usuarioId: 'u1', distrito: 'Tacna, Tacna', lat: null, lng: null, posicionEn: null,
    });
    prismaMock.carrera.findFirst.mockResolvedValue(null); // sin carrera en curso
    prismaMock.carrera.findMany.mockResolvedValue([
      {
        id: 'c1', tipo: 'pedido', pedidoId: 'ped1', estado: 'disponible',
        origenTexto: 'El Pollon', origenLat: -18.0, origenLng: -70.24,
        destinoTexto: 'Jose Olaya 110', destinoLat: null, destinoLng: null,
        montoOfrecido: 4500, kmEstimado: null, notas: '', creadoEn: new Date(),
        recogidoEn: null,
      },
      {
        id: 'c2', tipo: 'pasajero', pedidoId: null, estado: 'disponible',
        origenTexto: 'Av. Grau 240', origenLat: null, origenLng: null,
        destinoTexto: 'Miraflores', destinoLat: null, destinoLng: null,
        montoOfrecido: 760, kmEstimado: 3, notas: '', creadoEn: new Date(),
        recogidoEn: null,
      },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: '/motorizados/carreras',
      headers: { authorization: 'Bearer token-u1' },
    });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.carreras).toHaveLength(2);
    expect(body.carreras.map((c: { tipo: string }) => c.tipo)).toEqual(['pedido', 'pasajero']);
    expect(body.carreras[1]).toMatchObject({
      tipo: 'pasajero',
      origenTexto: 'Av. Grau 240',
      destinoTexto: 'Miraflores',
      montoOfrecido: 760,
    });
  });

  it('solo devuelve carreras disponibles', async () => {
    prismaMock.perfilMotorizado.findUnique.mockResolvedValue({
      usuarioId: 'u1', distrito: 'Tacna, Tacna', lat: null, lng: null, posicionEn: null,
    });
    prismaMock.carrera.findFirst.mockResolvedValue(null);
    prismaMock.carrera.findMany.mockResolvedValue([]);

    await app.inject({
      method: 'GET',
      url: '/motorizados/carreras',
      headers: { authorization: 'Bearer token-u1' },
    });

    const where = prismaMock.carrera.findMany.mock.calls[0][0].where;
    expect(where).toMatchObject({ estado: 'disponible' });
  });
});
```

- [ ] **Step 3: Correr para verificar que falla**

```bash
npx vitest run tests/rutas-motorizados.test.ts
```

Expected: FAIL — el endpoint todavía consulta `pedido.findMany`, no `carrera.findMany`.

- [ ] **Step 4: Implementar**

En `src/routes/motorizados.ts`, agregar el import al inicio:

```typescript
import { carreraDePedido } from '../core/carreras.js';
```

Reemplazar el cuerpo de `app.get('/motorizados/carreras', ...)` (líneas 151-210) por:

```typescript
  app.get('/motorizados/carreras', async (req, reply) => {
    const perfil = await prisma.perfilMotorizado.findUnique({ where: { usuarioId: req.usuarioId! } });
    if (!perfil) return reply.code(403).send({ error: 'No eres motorizado' });
    const zona = departamentoDe(perfil.distrito);

    // La carrera EN CURSO del rider (aceptada o recogida, aún no entregada).
    const enCurso = await prisma.carrera.findFirst({
      where: { riderUsuarioId: req.usuarioId!, estado: { in: ['aceptada', 'recogida'] } },
    });

    const disponibles = await prisma.carrera.findMany({
      where: { estado: 'disponible' },
      orderBy: { creadoEn: 'asc' },
      take: 50,
    });

    const posicionFresca =
      perfil.lat != null && perfil.lng != null && perfil.posicionEn != null &&
      Date.now() - perfil.posicionEn.getTime() < 10 * 60_000
        ? { lat: perfil.lat, lng: perfil.lng }
        : null;

    const salida = (c: {
      id: string; tipo: string; pedidoId: string | null; origenTexto: string;
      origenLat: number | null; origenLng: number | null; destinoTexto: string;
      montoOfrecido: number | null; montoCompraEstimado: number | null;
      kmEstimado: number | null; notas: string;
      creadoEn: Date; recogidoEn: Date | null;
    }) => {
      const km =
        posicionFresca && c.origenLat != null && c.origenLng != null
          ? distanciaKm(posicionFresca, { lat: c.origenLat, lng: c.origenLng })
          : null;
      return {
        // `pedidoId` se mantiene para no romper la app instalada, que hoy lo
        // usa como identificador de la carrera. En encomienda/pasajero no hay
        // Pedido, así que va el id de la carrera.
        pedidoId: c.pedidoId ?? c.id,
        carreraId: c.id,
        tipo: c.tipo,
        negocio: c.origenTexto,
        origenTexto: c.origenTexto,
        destinoTexto: c.destinoTexto,
        direccion: c.destinoTexto,
        totalCentavos: c.montoOfrecido ?? 0,
        // El FLETE: lo que el rider gana. Nunca sumar el monto de compra acá —
        // un total combinado se lee como una carrera muy rentable y no lo es.
        montoOfrecido: c.montoOfrecido ?? 0,
        // Solo en mandado: cuánta plata tiene que llevar encima para comprar.
        montoCompraEstimado: c.montoCompraEstimado,
        kmEstimado: c.kmEstimado,
        notas: c.notas,
        creadoEn: c.creadoEn,
        recogido: c.recogidoEn != null,
        kmAlNegocio: km == null ? null : Math.round(km * 10) / 10,
      };
    };

    const carreras = disponibles
      .map(salida)
      .sort((a, b) => (a.kmAlNegocio ?? Infinity) - (b.kmAlNegocio ?? Infinity));

    // Datos del cliente SOLO en la carrera aceptada (privacidad: el feed
    // abierto no expone el contacto de nadie).
    let miCarrera = null;
    if (enCurso) {
      const base = salida(enCurso);
      if (enCurso.pedidoId) {
        const pedido = await prisma.pedido.findUnique({
          where: { id: enCurso.pedidoId },
          select: { leadId: true },
        });
        const cliente = pedido
          ? await prisma.lead.findUnique({
              where: { id: pedido.leadId },
              select: { nombre: true, contactoExterno: true },
            })
          : null;
        miCarrera = {
          ...base,
          clienteNombre: cliente?.nombre ?? null,
          clienteContacto: cliente?.contactoExterno ?? null,
        };
      } else {
        // Mandado/encomienda/pasajero: el nombre sale de Usuario, pero el
        // teléfono vive en la Carrera (Usuario no tiene campo telefono).
        const solicitante = enCurso.solicitanteId
          ? await prisma.usuario.findUnique({
              where: { id: enCurso.solicitanteId },
              select: { nombre: true },
            })
          : null;
        miCarrera = {
          ...base,
          clienteNombre: solicitante?.nombre ?? null,
          clienteContacto: enCurso.solicitanteContacto || null,
        };
      }
    }

    return { carreras, miCarrera };
  });
```

**Nota sobre `zona`:** queda calculada pero sin usar en el filtro porque `Carrera` no tiene zona propia todavía. El filtro por zona se agrega en un task futuro cuando `Carrera` guarde el departamento del origen. Dejar la variable con un comentario `// TODO(zona)` NO es aceptable — en su lugar, borrar la línea `const zona = ...` si no se usa, para que el typecheck no falle por variable sin usar.

- [ ] **Step 5: Correr los tests**

```bash
npx vitest run tests/rutas-motorizados.test.ts
npm run typecheck
```

Expected: PASS en ambos.

- [ ] **Step 6: Commit**

```bash
git add src/routes/motorizados.ts tests/rutas-motorizados.test.ts
git commit -m "feat: el pool del rider sirve pedidos, encomiendas y pasajeros"
```

---

### Task 8: Aceptar / recoger / entregar sobre `Carrera`

**Files:**
- Modify: `src/routes/motorizados.ts:214-270` (aceptar), `:315-337` (recogido), `:337-362` (entregar)
- Test: `tests/rutas-motorizados.test.ts`

**Interfaces:**
- Consumes: `resolverComision` (Task 2), `cobrarCarrera`/`devolverCarrera` con monto (Task 3)
- Produces: los tres endpoints operan sobre `Carrera` y sincronizan `Pedido` cuando la carrera es de tipo `pedido`

- [ ] **Step 1: Escribir el test que falla**

Agregar a `tests/rutas-motorizados.test.ts`:

```typescript
describe('POST /motorizados/carreras/:id/aceptar — comision por tipo', () => {
  it('cobra la comision resuelta y la CONGELA en la carrera', async () => {
    prismaMock.perfilMotorizado.findUnique.mockResolvedValue({
      usuarioId: 'u1', distrito: 'Tacna, Tacna', telefono: '999',
    });
    prismaMock.usuario.findUnique.mockResolvedValue({ id: 'u1', nombre: 'Ana' });
    prismaMock.carrera.findUnique.mockResolvedValue({
      id: 'c2', tipo: 'pasajero', pedidoId: null, estado: 'disponible', montoOfrecido: 1600,
    });
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: 'pasajero', zona: null, pisoCentavos: 100, porcentaje: 10, desde: new Date(0), hasta: null },
    ]);
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 1 });

    const res = await app.inject({
      method: 'POST',
      url: '/motorizados/carreras/c2/aceptar',
      headers: { authorization: 'Bearer token-u1' },
    });

    expect(res.statusCode).toBe(200);
    // S/16 → 10% = S/1.60
    const update = prismaMock.carrera.updateMany.mock.calls[0][0];
    expect(update.data).toMatchObject({ estado: 'aceptada', comisionCentavos: 160 });
  });

  it('CAMPAÑA GRATIS: acepta con saldo 0 y comision 0', async () => {
    prismaMock.perfilMotorizado.findUnique.mockResolvedValue({
      usuarioId: 'u1', distrito: 'Tacna, Tacna', telefono: '999',
    });
    prismaMock.usuario.findUnique.mockResolvedValue({ id: 'u1', nombre: 'Ana' });
    prismaMock.carrera.findUnique.mockResolvedValue({
      id: 'c2', tipo: 'pasajero', pedidoId: null, estado: 'disponible', montoOfrecido: 1600,
    });
    prismaMock.configComision.findMany.mockResolvedValue([
      { tipo: null, zona: null, pisoCentavos: 0, porcentaje: 0, desde: new Date(0), hasta: null },
    ]);
    prismaMock.monederoRider.upsert.mockResolvedValue({ id: 'm1', usuarioId: 'u1', saldoCentavos: 0 });
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 1 });

    const res = await app.inject({
      method: 'POST',
      url: '/motorizados/carreras/c2/aceptar',
      headers: { authorization: 'Bearer token-u1' },
    });

    expect(res.statusCode).toBe(200);
    const update = prismaMock.carrera.updateMany.mock.calls[0][0];
    expect(update.data).toMatchObject({ comisionCentavos: 0 });
  });

  it('otro rider la tomo primero: 409 y devuelve la comision', async () => {
    prismaMock.perfilMotorizado.findUnique.mockResolvedValue({
      usuarioId: 'u1', distrito: 'Tacna, Tacna', telefono: '999',
    });
    prismaMock.usuario.findUnique.mockResolvedValue({ id: 'u1', nombre: 'Ana' });
    prismaMock.carrera.findUnique.mockResolvedValue({
      id: 'c2', tipo: 'pasajero', pedidoId: null, estado: 'disponible', montoOfrecido: 1600,
    });
    prismaMock.configComision.findMany.mockResolvedValue([]);
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 0 });

    const res = await app.inject({
      method: 'POST',
      url: '/motorizados/carreras/c2/aceptar',
      headers: { authorization: 'Bearer token-u1' },
    });

    expect(res.statusCode).toBe(409);
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/rutas-motorizados.test.ts
```

Expected: FAIL — el endpoint todavía opera sobre `pedido.updateMany`.

- [ ] **Step 3: Implementar aceptar**

En `src/routes/motorizados.ts`, agregar el import:

```typescript
import { resolverComision } from '../core/comision.js';
```

Reemplazar el cuerpo de `app.post('/motorizados/carreras/:pedidoId/aceptar', ...)` por:

```typescript
  app.post('/motorizados/carreras/:pedidoId/aceptar', async (req, reply) => {
    // El param se llama pedidoId por compatibilidad con la app instalada,
    // pero puede ser un id de Carrera (encomienda/pasajero).
    const { pedidoId: id } = req.params as { pedidoId: string };
    const perfil = await prisma.perfilMotorizado.findUnique({ where: { usuarioId: req.usuarioId! } });
    if (!perfil) return reply.code(403).send({ error: 'No eres motorizado' });
    const usuario = await prisma.usuario.findUnique({ where: { id: req.usuarioId! } });

    // Acepta tanto el id de la Carrera como el del Pedido que la originó.
    const carrera =
      (await prisma.carrera.findUnique({ where: { id } })) ??
      (await prisma.carrera.findUnique({ where: { pedidoId: id } }));
    if (!carrera) return reply.code(404).send({ error: 'Carrera no encontrada' });

    const comision = await resolverComision({
      tipo: carrera.tipo,
      zona: departamentoDe(perfil.distrito),
      montoOfrecidoCentavos: carrera.montoOfrecido,
    });

    // Se cobra ANTES de asignar: sin saldo no se toma la carrera. Con comisión
    // 0 (campaña) `cobrarCarrera` no exige saldo.
    const cobro = await cobrarCarrera(req.usuarioId!, carrera.id, comision);
    if (!cobro.ok) {
      return reply.code(402).send({
        error: 'Recarga tu monedero para seguir tomando carreras',
        motivo: 'sin_saldo',
        saldoCentavos: cobro.saldoCentavos,
      });
    }

    // El PRIMERO gana: updateMany condicionado por estado.
    const res = await prisma.carrera.updateMany({
      where: { id: carrera.id, estado: 'disponible', riderUsuarioId: null },
      data: {
        estado: 'aceptada',
        riderUsuarioId: req.usuarioId!,
        comisionCentavos: comision,
        aceptadaEn: new Date(),
        kmReal: 0,
      },
    });
    if (res.count === 0) {
      await devolverCarrera(req.usuarioId!, carrera.id, comision);
      return reply.code(409).send({ error: 'Otro rider tomó esta carrera' });
    }

    // Espejo en Pedido para que la Cocina siga viendo "en camino".
    if (carrera.pedidoId) {
      await prisma.pedido.updateMany({
        where: { id: carrera.pedidoId },
        data: {
          estado: 'en_camino',
          motorizadoUsuarioId: req.usuarioId!,
          motorizadoNombre: usuario?.nombre ?? 'Motorizado',
          motorizadoNumero: perfil.telefono || null,
          kmRider: 0,
        },
      });

      const pedido = await prisma.pedido.findUnique({
        where: { id: carrera.pedidoId },
        select: { tenantId: true, totalCentavos: true, leadId: true, canal: true },
      });
      if (pedido) {
        void notificarPush(
          pedido.tenantId,
          '🛵 Un rider tomó tu pedido',
          `${usuario?.nombre ?? 'Motorizado'} va en camino — S/${(pedido.totalCentavos / 100).toFixed(2)}`,
          { tipo: 'pedido', pedidoId: carrera.pedidoId },
        );
        void avisarClienteCarrera(
          pedido.tenantId, pedido.leadId, pedido.canal,
          `🏍️ ¡Tu pedido va en camino! Lo lleva ${usuario?.nombre ?? 'nuestro motorizado'}. ` +
            `Síguelo en vivo: ${env.TRACK_BASE_URL}/track/${carrera.pedidoId}`,
        );
      }
    }
    return { ok: true };
  });
```

- [ ] **Step 4: Implementar recogido y entregar**

Reemplazar el cuerpo de `/recogido`:

```typescript
  app.post('/motorizados/carreras/:pedidoId/recogido', async (req, reply) => {
    const { pedidoId: id } = req.params as { pedidoId: string };
    const res = await prisma.carrera.updateMany({
      where: {
        OR: [{ id }, { pedidoId: id }],
        riderUsuarioId: req.usuarioId!,
        estado: 'aceptada',
      },
      data: { estado: 'recogida', recogidoEn: new Date() },
    });
    if (res.count === 0) return reply.code(409).send({ error: 'Esa carrera ya no está en ese estado' });

    const carrera = await prisma.carrera.findFirst({
      where: { OR: [{ id }, { pedidoId: id }] },
      select: { pedidoId: true },
    });
    if (carrera?.pedidoId) {
      await prisma.pedido.updateMany({
        where: { id: carrera.pedidoId },
        data: { recogidoEn: new Date() },
      });
    }
    return { ok: true };
  });
```

Y el de `/entregar`:

```typescript
  app.post('/motorizados/carreras/:pedidoId/entregar', async (req, reply) => {
    const { pedidoId: id } = req.params as { pedidoId: string };
    const ahora = new Date();
    const res = await prisma.carrera.updateMany({
      where: {
        OR: [{ id }, { pedidoId: id }],
        riderUsuarioId: req.usuarioId!,
        estado: { in: ['aceptada', 'recogida'] },
      },
      data: { estado: 'entregada', entregadoEn: ahora },
    });
    if (res.count === 0) return reply.code(409).send({ error: 'Esa carrera ya no está en ese estado' });

    const carrera = await prisma.carrera.findFirst({
      where: { OR: [{ id }, { pedidoId: id }] },
      select: { pedidoId: true, kmReal: true },
    });
    if (carrera?.pedidoId) {
      await prisma.pedido.updateMany({
        where: { id: carrera.pedidoId },
        data: { estado: 'entregado', entregadoEn: ahora, kmRider: carrera.kmReal ?? 0 },
      });
    }
    return { ok: true };
  });
```

- [ ] **Step 5: Correr los tests**

```bash
npx vitest run tests/rutas-motorizados.test.ts
npm run typecheck
```

Expected: PASS en ambos.

- [ ] **Step 6: Commit**

```bash
git add src/routes/motorizados.ts tests/rutas-motorizados.test.ts
git commit -m "feat: aceptar/recoger/entregar sobre Carrera con comision congelada"
```

---

### Task 9: Enganchar la proyección al marcar un pedido listo

**Files:**
- Modify: el handler que pasa un pedido a `listo` (localizarlo primero, ver Step 1)
- Test: el test de ese handler

**Interfaces:**
- Consumes: `proyectarPedidoACarrera` (Task 5)
- Produces: nada nuevo — efecto secundario

**Contexto:** sin este task, `Carrera` queda vacía y el pool no muestra deliveries. Es el punto de corte real de la migración.

- [ ] **Step 1: Localizar dónde se marca un pedido como listo**

```bash
cd "d:/Personal Proyects/leadia"
grep -rn "'listo'" src/routes/ src/core/ --include=*.ts | grep -v "estado: 'listo'.*where"
```

Buscar el `update` que **escribe** `estado: 'listo'` (probablemente en `src/routes/pedidos.ts` o similar). Anotar archivo y línea exactos.

- [ ] **Step 2: Escribir el test que falla**

En el archivo de test correspondiente a ese handler, agregar:

```typescript
it('al marcar listo, proyecta la carrera al pool', async () => {
  // ... setup del pedido según el patrón del archivo ...

  await app.inject({
    method: 'POST', // o PATCH, según el handler real
    url: '/pedidos/ped1/estado',
    payload: { estado: 'listo' },
    headers: { authorization: 'Bearer token', 'x-tenant-id': 't1' },
  });

  expect(prismaMock.carrera.create).toHaveBeenCalledWith(
    expect.objectContaining({
      data: expect.objectContaining({ tipo: 'pedido', pedidoId: 'ped1' }),
    }),
  );
});
```

Ajustar método, URL y payload a los del handler real encontrado en Step 1.

- [ ] **Step 3: Correr para verificar que falla**

```bash
npx vitest run tests/<archivo-encontrado>.test.ts
```

Expected: FAIL — `carrera.create` no fue llamado.

- [ ] **Step 4: Implementar**

En el handler, después del `update` que deja el pedido en `listo`, agregar:

```typescript
import { proyectarPedidoACarrera } from '../core/carreras.js';

// ... dentro del handler, tras marcar listo:
// El pedido entra al pool del rider. Fire-and-forget: un fallo acá no debe
// romper el flujo de la Cocina.
void proyectarPedidoACarrera(pedidoId);
```

- [ ] **Step 5: Correr los tests**

```bash
npx vitest run
npm run typecheck
```

Expected: PASS — toda la suite.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: los pedidos listos entran al pool como Carrera"
```

---

### Task 10: Backfill de pedidos activos y seed de comisión

**Files:**
- Create: `scripts/backfill-carreras.ts`
- Create: `scripts/seed-comision.ts`

**Interfaces:**
- Consumes: `proyectarPedidoACarrera` (Task 5)
- Produces: scripts ejecutables una sola vez

**Contexto:** al desplegar, los pedidos que ya están en `listo` o `en_camino` no tienen `Carrera`. Sin backfill, los riders con carrera en curso la pierden de vista.

- [ ] **Step 1: Escribir el script de backfill**

Crear `scripts/backfill-carreras.ts`:

```typescript
import { prisma } from '../src/lib/prisma.js';
import { proyectarPedidoACarrera } from '../src/core/carreras.js';

// Corrida ÚNICA al desplegar carreras multi-tipo: los pedidos que ya están
// listos o en camino necesitan su Carrera espejo, o el rider que va manejando
// pierde la carrera de vista. `proyectarPedidoACarrera` es idempotente, así
// que volver a correrlo no duplica nada.

async function main(): Promise<void> {
  const pedidos = await prisma.pedido.findMany({
    where: { estado: { in: ['listo', 'en_camino'] }, esPrueba: false },
    select: { id: true, estado: true, motorizadoUsuarioId: true, recogidoEn: true },
  });
  console.log(`Pedidos a proyectar: ${pedidos.length}`);

  let creadas = 0;
  for (const p of pedidos) {
    await proyectarPedidoACarrera(p.id);
    // Los que ya tienen rider arrancan como aceptados, no disponibles.
    if (p.motorizadoUsuarioId) {
      await prisma.carrera.updateMany({
        where: { pedidoId: p.id },
        data: {
          estado: p.recogidoEn ? 'recogida' : 'aceptada',
          riderUsuarioId: p.motorizadoUsuarioId,
          recogidoEn: p.recogidoEn,
        },
      });
    }
    creadas++;
  }
  console.log(`Listo: ${creadas} carreras proyectadas.`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
```

- [ ] **Step 2: Escribir el seed de comisión**

Crear `scripts/seed-comision.ts`:

```typescript
import { prisma } from '../src/lib/prisma.js';

// Configuración inicial de comisión (spec 2026-07-27):
//   delivery y encomienda → S/1 fijo
//   pasajero              → max(S/1, 10%)
// Para una campaña de lanzamiento sin comisión, crear una fila extra con
// pisoCentavos: 0, porcentaje: 0, zona: '<Departamento>' y `hasta` con la
// fecha de fin — se apaga sola.

async function main(): Promise<void> {
  const filas = [
    { tipo: 'pedido', zona: null, pisoCentavos: 100, porcentaje: 0, nota: 'Delivery: S/1 fijo' },
    { tipo: 'encomienda', zona: null, pisoCentavos: 100, porcentaje: 0, nota: 'Encomienda: S/1 fijo' },
    // El % del mandado sale del FLETE, nunca del monto de compra.
    { tipo: 'mandado', zona: null, pisoCentavos: 100, porcentaje: 10, nota: 'Mandado: max(S/1, 10% del flete)' },
    { tipo: 'pasajero', zona: null, pisoCentavos: 100, porcentaje: 10, nota: 'Pasajero: max(S/1, 10%)' },
  ];

  for (const f of filas) {
    const existe = await prisma.configComision.findFirst({
      where: { tipo: f.tipo, zona: f.zona },
    });
    if (existe) {
      console.log(`Ya existe config para ${f.tipo} — se deja como está.`);
      continue;
    }
    await prisma.configComision.create({ data: f });
    console.log(`Creada: ${f.nota}`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
```

- [ ] **Step 3: Verificar que compilan**

```bash
cd "d:/Personal Proyects/leadia"
npm run typecheck
```

Expected: sin errores.

- [ ] **Step 4: Documentar el orden de despliegue**

Agregar al final de `README.md` del repo `leadia`:

```markdown
## Despliegue de carreras multi-tipo (2026-07-27)

Orden exacto, una sola vez:

1. `npm run prisma:deploy` — crea las tablas `Carrera` y `ConfigComision`.
2. `npx tsx scripts/seed-comision.ts` — carga la comisión inicial.
3. Desplegar el código.
4. `npx tsx scripts/backfill-carreras.ts` — proyecta los pedidos activos.

El paso 4 va DESPUÉS del deploy: los pedidos que entren a `listo` mientras
tanto ya se proyectan solos. El backfill es idempotente — si algo falla, se
puede volver a correr.
```

- [ ] **Step 5: Commit**

```bash
git add scripts/backfill-carreras.ts scripts/seed-comision.ts README.md
git commit -m "feat: scripts de backfill y seed de comision para carreras"
```

---

### Task 11: Verificación final

- [ ] **Step 1: Correr toda la suite**

```bash
cd "d:/Personal Proyects/leadia"
npm run typecheck && npx vitest run
```

Expected: PASS — sin errores de tipos, todos los tests verdes.

- [ ] **Step 2: Verificar que `Pedido` no se tocó**

```bash
git diff 919cdd7..HEAD -- prisma/schema.prisma | grep -A5 "model Pedido"
```

Expected: sin cambios dentro del bloque `model Pedido`. Si aparecen, revisar — el spec lo prohíbe.

- [ ] **Step 3: Confirmar la cobertura del spec**

Revisar que existan y pasen:
- `tests/comision.test.ts` — piso, porcentaje, especificidad, campaña gratis, **mandado sobre el flete**
- `tests/sugerencia.test.ts` — fórmula por vehículo
- `tests/carreras-proyeccion.test.ts` — proyección e idempotencia, carreras libres, **flete y compra separados**
- `tests/monedero.test.ts` — comisión variable y saldo cero con campaña
- `tests/rutas-motorizados.test.ts` — pool de cuatro tipos, aceptar con comisión congelada

- [ ] **Step 4: Verificar la regla del mandado a mano**

```bash
cd "d:/Personal Proyects/leadia"
grep -rn "montoCompraEstimado" src/core/comision.ts
```

Expected: **cero resultados**. Si `comision.ts` menciona `montoCompraEstimado`,
la comisión estaría tocando plata que el rider adelanta — es un bug de dinero.

---

## Qué NO cubre este plan

- **App `LeadAI-Rider`** — plan propio, después de este. El backend es prerequisito.
- **Modo pasajero** (crear carreras desde la app, mapa con búsqueda de direcciones) — spec propio.
- **Filtro por zona en el pool** — `Carrera` no guarda el departamento del origen todavía. Hoy el pool devuelve todas las disponibles. Task futuro.
- **Cálculo real de `kmEstimado`** — quien crea la carrera lo pasa; el ruteo con OSRM/Nominatim se integra en el plan de la app de pasajero.
- **Endpoints públicos para crear encomiendas/pasajeros** — `crearCarreraLibre` queda lista, pero exponerla por HTTP va con el modo pasajero.
