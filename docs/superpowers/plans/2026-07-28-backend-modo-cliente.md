# Backend — Modo cliente Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un cliente pueda pedir una encomienda o un pasajero desde la app, y seguir su carrera hasta que la entreguen.

**Architecture:** Un archivo de rutas nuevo (`src/routes/carreras.ts`) que expone `crearCarreraLibre` —que ya existe y está testeada— por HTTP, más geocodificación del origen/destino y un job que expira las carreras que nadie tomó. No se toca nada del lado del rider: el pool ya sirve los tres tipos.

**Tech Stack:** Node 20, TypeScript, Fastify, Prisma, PostgreSQL, Vitest, Zod.

**Repo:** `d:\Personal Proyects\leadia` — TODOS los commits van a ese repo.

## Global Constraints

- Todo el código, comentarios y mensajes de error en **español**.
- Montos en **centavos** (`Int`), nunca decimales.
- **La comisión se calcula solo sobre el flete** (`montoOfrecido`), jamás sobre `montoCompraEstimado` — esa es plata que el rider adelanta y recupera del cliente.
- Autenticación con `autenticarUsuario` (identidad de plataforma, **sin `X-Tenant-Id`**) — mismo patrón que `rutasMotorizados`.
- **Un cliente solo puede tener UNA carrera activa a la vez.** Evita que pida cinco motos por error y que el pool se llene de basura.
- Tipos válidos desde el cliente: `encomienda` y `pasajero`. **`pedido` NO** — ese nace del negocio.
- Tests con `vitest run`, mockeando Prisma con `vi.hoisted` + `vi.mock` (patrón de `tests/rutas-motorizados.test.ts`).
- Commits en español con prefijo `feat:` / `test:` / `fix:`.

## Contexto: lo que YA existe y se reusa

| Pieza | Dónde | Qué hace |
|---|---|---|
| `crearCarreraLibre` | `src/core/carreras.ts:88` | Crea la carrera con monto sugerido. Ya testeada. |
| `sugerirMontoCentavos` | `src/core/sugerencia.ts` | `base + km × factor`, por tipo de vehículo. |
| `geocodificar` | `src/core/geocodificar.ts:46` | Texto → lat/lng vía Nominatim (**1 req/s**). |
| `distanciaKm` | `src/core/geocodificar.ts:110` | Distancia entre dos puntos. |
| `departamentoDe` | `src/core/carreras.ts` | "Pocollay, Tacna" → "Tacna". |
| `/track/:id` | `src/routes/track.ts:425` | Mapa público de seguimiento. |

**Restricción crítica:** Nominatim admite **1 request por segundo**. Geocodificar origen y destino son 2 requests. Nunca geocodificar en un loop ni por cada tecla — solo al crear la carrera.

---

### Task 1: Estado `expirada` y campo de expiración

**Files:**
- Modify: `prisma/schema.prisma` (modelo `Carrera`)
- Create: `prisma/migrations/20260728_carrera_expira/migration.sql`

**Interfaces:**
- Consumes: nada
- Produces: `Carrera.expiraEn: DateTime?`

**Contexto:** una carrera que nadie tomó en 15 minutos debe morir sola. Una carrera de hace dos horas que un rider acepta de golpe es peor que ninguna: el cliente ya se fue en otra cosa.

- [ ] **Step 1: Agregar el campo al schema**

En `prisma/schema.prisma`, dentro de `model Carrera`, después de `creadoEn`:

```prisma
  // Cuándo deja de ofrecerse si nadie la tomó. Una carrera vieja que un rider
  // acepta de golpe es peor que ninguna: el cliente ya resolvió por otro lado.
  expiraEn    DateTime?
```

Y agregar el índice, junto a los que ya están:

```prisma
  @@index([estado, expiraEn])
```

- [ ] **Step 2: Escribir la migración a mano**

`prisma migrate dev` NO funciona en este repo (no puede reconstruir la shadow DB por una migración vieja). Se escribe a mano, **idempotente** — si no lleva `IF NOT EXISTS`, el CD falla y bloquea todas las migraciones siguientes (P3009).

Crear `prisma/migrations/20260728_carrera_expira/migration.sql`:

```sql
-- Expiración de carreras que nadie tomó (modo cliente). IDEMPOTENTE a
-- propósito: si la columna ya se aplicó a mano, el deploy no debe fallar.

-- AlterTable
ALTER TABLE "Carrera" ADD COLUMN IF NOT EXISTS "expiraEn" TIMESTAMP(3);

-- CreateIndex
CREATE INDEX IF NOT EXISTS "Carrera_estado_expiraEn_idx" ON "Carrera"("estado", "expiraEn");
```

- [ ] **Step 3: Verificar que el SQL coincide con el schema**

```bash
cd "d:/Personal Proyects/leadia"
npx prisma migrate diff --from-schema-datasource prisma/schema.prisma --to-schema-datamodel prisma/schema.prisma --script
```

Expected: la salida debe ser equivalente al SQL escrito (salvo los `IF NOT EXISTS`, que Prisma no genera). Si aparece algo más, el schema y el SQL divergieron — corregir antes de seguir.

- [ ] **Step 4: Aplicar a la base**

```bash
npx prisma migrate deploy
```

Expected: `All migrations have been successfully applied.`

Después verificar que no quedó drift:

```bash
npx prisma migrate diff --from-schema-datasource prisma/schema.prisma --to-schema-datamodel prisma/schema.prisma --script
```

Expected: `-- This is an empty migration.`

- [ ] **Step 5: Commit**

```bash
git add prisma/schema.prisma prisma/migrations/20260728_carrera_expira/
git commit -m "feat: las carreras que nadie toma expiran"
```

---

### Task 2: Resolver una dirección a coordenadas

**Files:**
- Modify: `src/core/carreras.ts` (agregar función)
- Test: `tests/carreras-ubicacion.test.ts`

**Interfaces:**
- Consumes: `geocodificar`, `distanciaKm` (`src/core/geocodificar.ts`)
- Produces:
  - `resolverUbicacion(args: { texto: string; lat?: number | null; lng?: number | null; zona?: string | null }): Promise<{ texto: string; lat: number | null; lng: number | null }>`
  - `kmEntre(origen: {lat: number|null, lng: number|null}, destino: {lat: number|null, lng: number|null}): number | null`

**Contexto (diseño híbrido):** el cliente manda su GPS cuando lo tiene ("estoy acá") y texto cuando no. Si vienen coordenadas, se usan tal cual — son más precisas que geocodificar texto. Si no, se geocodifica.

- [ ] **Step 1: Escribir el test que falla**

Crear `tests/carreras-ubicacion.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Origen y destino del modo cliente: si el cliente manda su GPS se usa tal
// cual (más preciso); si manda solo texto, se geocodifica.

const { geoMock } = vi.hoisted(() => ({ geoMock: vi.fn() }));
vi.mock('../src/core/geocodificar.js', async (original) => {
  const real = (await original()) as Record<string, unknown>;
  return { ...real, geocodificar: geoMock };
});

const { prismaMock } = vi.hoisted(() => ({
  prismaMock: { carrera: { create: vi.fn(), findFirst: vi.fn() }, pedido: { findUnique: vi.fn() } },
}));
vi.mock('../src/lib/prisma.js', () => ({ prisma: prismaMock }));

import { resolverUbicacion, kmEntre } from '../src/core/carreras.js';

beforeEach(() => {
  vi.clearAllMocks();
  geoMock.mockResolvedValue({ lat: -18.01, lng: -70.25 });
});

describe('resolverUbicacion', () => {
  it('con GPS del cliente NO geocodifica (el GPS es mas preciso)', async () => {
    const r = await resolverUbicacion({ texto: 'Estoy acá', lat: -18.0, lng: -70.24 });

    expect(r).toEqual({ texto: 'Estoy acá', lat: -18.0, lng: -70.24 });
    expect(geoMock).not.toHaveBeenCalled();
  });

  it('sin GPS geocodifica el texto', async () => {
    const r = await resolverUbicacion({ texto: 'Av. Grau 240', zona: 'Tacna' });

    expect(r).toEqual({ texto: 'Av. Grau 240', lat: -18.01, lng: -70.25 });
    expect(geoMock).toHaveBeenCalledOnce();
  });

  it('si la geocodificacion falla la carrera igual se crea sin pin', async () => {
    // Nominatim caído no debe impedir pedir una moto: el rider navega por el
    // texto, como hacía antes de que existieran los pines.
    geoMock.mockResolvedValue(null);

    const r = await resolverUbicacion({ texto: 'Calle inexistente 999', zona: 'Tacna' });

    expect(r).toEqual({ texto: 'Calle inexistente 999', lat: null, lng: null });
  });

  it('si geocodificar EXPLOTA tampoco rompe', async () => {
    geoMock.mockRejectedValue(new Error('timeout de Nominatim'));

    const r = await resolverUbicacion({ texto: 'Av. Grau 240', zona: 'Tacna' });

    expect(r.lat).toBe(null);
  });
});

describe('kmEntre', () => {
  it('calcula la distancia cuando hay dos pines', () => {
    const km = kmEntre({ lat: -18.0, lng: -70.24 }, { lat: -18.01, lng: -70.25 });

    expect(km).toBeGreaterThan(0);
    expect(km).toBeLessThan(5);
  });

  it('sin alguno de los pines devuelve null (no inventa distancia)', () => {
    expect(kmEntre({ lat: null, lng: null }, { lat: -18.01, lng: -70.25 })).toBe(null);
    expect(kmEntre({ lat: -18.0, lng: -70.24 }, { lat: null, lng: null })).toBe(null);
  });
});
```

- [ ] **Step 2: Correr para verificar que falla**

```bash
npx vitest run tests/carreras-ubicacion.test.ts
```

Expected: FAIL — `resolverUbicacion is not a function`.

- [ ] **Step 3: Implementar**

Agregar a `src/core/carreras.ts` (con el import de `geocodificar` y `distanciaKm` en el bloque de imports del inicio):

```typescript
/**
 * Convierte lo que el cliente escribió en un punto del mapa. Si mandó su GPS
 * se usa tal cual — es más preciso que geocodificar texto. Si no, se
 * geocodifica.
 *
 * NUNCA lanza: Nominatim caído no debe impedir pedir una moto. Sin pin, el
 * rider navega por el texto como se hacía antes de que existieran los pines.
 */
export async function resolverUbicacion(args: {
  texto: string;
  lat?: number | null;
  lng?: number | null;
  zona?: string | null;
}): Promise<{ texto: string; lat: number | null; lng: number | null }> {
  if (args.lat != null && args.lng != null) {
    return { texto: args.texto, lat: args.lat, lng: args.lng };
  }
  try {
    const pin = await geocodificar(args.texto, args.zona ?? null);
    return { texto: args.texto, lat: pin?.lat ?? null, lng: pin?.lng ?? null };
  } catch {
    return { texto: args.texto, lat: null, lng: null };
  }
}

/** Distancia entre origen y destino, o `null` si falta alguno de los dos pines. */
export function kmEntre(
  origen: { lat: number | null; lng: number | null },
  destino: { lat: number | null; lng: number | null },
): number | null {
  if (origen.lat == null || origen.lng == null || destino.lat == null || destino.lng == null) {
    return null;
  }
  return distanciaKm(
    { lat: origen.lat, lng: origen.lng },
    { lat: destino.lat, lng: destino.lng },
  );
}
```

**Verificar la firma real de `geocodificar`** antes de escribir esto: abrí `src/core/geocodificar.ts:46` y confirmá qué parámetros toma y qué devuelve. Si no coincide con `(texto, departamento)` → `{lat, lng} | null`, adaptá la llamada.

- [ ] **Step 4: Correr el test**

```bash
npx vitest run tests/carreras-ubicacion.test.ts
```

Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/core/carreras.ts tests/carreras-ubicacion.test.ts
git commit -m "feat: resolver origen y destino del cliente a coordenadas"
```

---

### Task 3: Endpoints del cliente

**Files:**
- Create: `src/routes/carreras.ts`
- Modify: `src/server.ts:147` (registrar la ruta)
- Test: `tests/rutas-carreras.test.ts`

**Interfaces:**
- Consumes: `crearCarreraLibre`, `resolverUbicacion`, `kmEntre`, `departamentoDe` (`src/core/carreras.ts`); `sugerirMontoCentavos` (`src/core/sugerencia.ts`)
- Produces: cuatro endpoints HTTP

**Endpoints:**

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/carreras/sugerir` | Devuelve el monto sugerido y los km, sin crear nada |
| `POST` | `/carreras` | Crea la carrera |
| `GET` | `/carreras/mia` | La carrera activa del cliente y su estado |
| `POST` | `/carreras/:id/cancelar` | La cancela |

- [ ] **Step 1: Leer el patrón de tests de rutas**

```bash
cd "d:/Personal Proyects/leadia"
head -60 tests/rutas-motorizados.test.ts
```

Mirá cómo construye la app (`appDePrueba()`), cómo mockea la autenticación y cómo mockea Prisma. Seguí ESE patrón — no inventes uno nuevo.

- [ ] **Step 2: Escribir el test que falla**

Crear `tests/rutas-carreras.test.ts`, adaptando el patrón del archivo anterior:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

// El cliente pide una moto: encomienda ("tráeme un chifa") o pasajero.
// Los pedidos de restaurante NO nacen acá — esos los crea el negocio.

// (mocks de prisma y auth siguiendo el patrón de rutas-motorizados.test.ts,
//  incluyendo carrera: { create, findFirst, updateMany })

describe('POST /carreras', () => {
  it('crea una encomienda con su monto sugerido', async () => {
    // sin carrera activa previa
    prismaMock.carrera.findFirst.mockResolvedValue(null);
    prismaMock.carrera.create.mockResolvedValue({ id: 'c1', montoSugerido: 760, montoOfrecido: 760 });

    const app = await appDePrueba();
    const res = await app.inject({
      method: 'POST',
      url: '/carreras',
      payload: {
        tipo: 'encomienda',
        origenTexto: 'Chifa Salon Canton',
        destinoTexto: 'Jose Olaya 110',
        origenLat: -18.0,
        origenLng: -70.24,
        destinoLat: -18.01,
        destinoLng: -70.25,
        montoCompraEstimadoCentavos: 6000,
      },
    });

    expect(res.statusCode).toBe(200);
    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data).toMatchObject({ tipo: 'encomienda', estado: 'disponible' });
    // El flete y la compra van SEPARADOS.
    expect(args.data.montoCompraEstimado).toBe(6000);
  });

  it('un cliente NO puede tener dos carreras activas', async () => {
    prismaMock.carrera.findFirst.mockResolvedValue({ id: 'c-vieja', estado: 'disponible' });

    const app = await appDePrueba();
    const res = await app.inject({
      method: 'POST',
      url: '/carreras',
      payload: { tipo: 'pasajero', origenTexto: 'A', destinoTexto: 'B' },
    });

    expect(res.statusCode).toBe(409);
    expect(prismaMock.carrera.create).not.toHaveBeenCalled();
  });

  it('NO se puede crear un pedido de restaurante desde acá', async () => {
    prismaMock.carrera.findFirst.mockResolvedValue(null);

    const app = await appDePrueba();
    const res = await app.inject({
      method: 'POST',
      url: '/carreras',
      payload: { tipo: 'pedido', origenTexto: 'A', destinoTexto: 'B' },
    });

    expect(res.statusCode).toBe(400);
    expect(prismaMock.carrera.create).not.toHaveBeenCalled();
  });

  it('la carrera nace con fecha de expiracion', async () => {
    prismaMock.carrera.findFirst.mockResolvedValue(null);
    prismaMock.carrera.create.mockResolvedValue({ id: 'c1', montoSugerido: 760, montoOfrecido: 760 });

    const app = await appDePrueba();
    await app.inject({
      method: 'POST',
      url: '/carreras',
      payload: { tipo: 'pasajero', origenTexto: 'A', destinoTexto: 'B' },
    });

    const args = prismaMock.carrera.create.mock.calls[0][0];
    expect(args.data.expiraEn).toBeInstanceOf(Date);
  });
});

describe('GET /carreras/mia', () => {
  it('devuelve la carrera activa del cliente', async () => {
    prismaMock.carrera.findFirst.mockResolvedValue({
      id: 'c1', tipo: 'pasajero', estado: 'aceptada', riderUsuarioId: 'r1',
      origenTexto: 'A', destinoTexto: 'B', montoOfrecido: 760,
      montoCompraEstimado: null, recogidoEn: null, creadoEn: new Date(), expiraEn: null,
    });

    const app = await appDePrueba();
    const res = await app.inject({ method: 'GET', url: '/carreras/mia' });

    expect(res.statusCode).toBe(200);
    expect(res.json().carrera).toMatchObject({ estado: 'aceptada' });
  });

  it('sin carrera activa devuelve null, no un error', async () => {
    prismaMock.carrera.findFirst.mockResolvedValue(null);

    const app = await appDePrueba();
    const res = await app.inject({ method: 'GET', url: '/carreras/mia' });

    expect(res.statusCode).toBe(200);
    expect(res.json().carrera).toBe(null);
  });
});

describe('POST /carreras/:id/cancelar', () => {
  it('cancela una carrera que nadie tomo', async () => {
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 1 });

    const app = await appDePrueba();
    const res = await app.inject({ method: 'POST', url: '/carreras/c1/cancelar' });

    expect(res.statusCode).toBe(200);
    const where = prismaMock.carrera.updateMany.mock.calls[0][0].where;
    // Solo se cancela lo propio y lo que todavía nadie tomó.
    expect(where).toMatchObject({ id: 'c1', estado: 'disponible' });
  });

  it('una carrera YA aceptada no se cancela desde acá', async () => {
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 0 });

    const app = await appDePrueba();
    const res = await app.inject({ method: 'POST', url: '/carreras/c1/cancelar' });

    expect(res.statusCode).toBe(409);
  });
});
```

- [ ] **Step 3: Correr para verificar que falla**

```bash
npx vitest run tests/rutas-carreras.test.ts
```

Expected: FAIL — las rutas no existen (404).

- [ ] **Step 4: Implementar**

Crear `src/routes/carreras.ts`:

```typescript
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { autenticarUsuario } from '../middleware/auth.js';
import { prisma } from '../lib/prisma.js';
import {
  crearCarreraLibre, resolverUbicacion, kmEntre, departamentoDe,
} from '../core/carreras.js';
import { sugerirMontoCentavos } from '../core/sugerencia.js';

// El CLIENTE pide una moto: una encomienda ("tráeme un chifa del Salón
// Cantón") o un pasajero. Identidad de PLATAFORMA (sin X-Tenant-Id), igual
// que el rider — pedir una moto no tiene nada que ver con ser dueño de un
// negocio.
//
// Los pedidos de restaurante NO nacen acá: los crea el negocio al marcar un
// pedido como listo en su propia app.

/** Cuánto vive una carrera que nadie tomó. Vieja y aceptada de golpe es peor que ninguna. */
const MINUTOS_HASTA_EXPIRAR = 15;

/** Estados en los que la carrera todavía le importa al cliente. */
const ESTADOS_ACTIVOS = ['disponible', 'aceptada', 'recogida'];

const ubicacionSchema = z.object({
  origenTexto: z.string().min(2).max(200),
  origenLat: z.number().min(-90).max(90).nullable().optional(),
  origenLng: z.number().min(-180).max(180).nullable().optional(),
  destinoTexto: z.string().min(2).max(200),
  destinoLat: z.number().min(-90).max(90).nullable().optional(),
  destinoLng: z.number().min(-180).max(180).nullable().optional(),
});

const crearSchema = ubicacionSchema.extend({
  // 'pedido' NO: ese lo crea el negocio, no el cliente.
  tipo: z.enum(['encomienda', 'pasajero']),
  montoOfrecidoCentavos: z.number().int().min(0).max(100_000).nullable().optional(),
  montoCompraEstimadoCentavos: z.number().int().min(0).max(100_000).nullable().optional(),
  notas: z.string().max(300).optional(),
  contacto: z.string().max(20).optional(),
});

const sugerirSchema = ubicacionSchema.extend({
  tipoVehiculo: z.enum(['moto', 'auto']).optional(),
});

export async function rutasCarreras(app: FastifyInstance): Promise<void> {
  app.addHook('preHandler', autenticarUsuario);

  /** La zona del cliente sale de su perfil de rider si lo tiene; si no, Lima. */
  const zonaDelUsuario = async (usuarioId: string): Promise<string> => {
    const perfil = await prisma.perfilMotorizado.findUnique({ where: { usuarioId } });
    return departamentoDe(perfil?.distrito) ?? 'Lima';
  };

  // Cuánto se sugiere cobrar, ANTES de crear nada. La app lo muestra como
  // punto de partida editable — no es una tarifa.
  app.post('/carreras/sugerir', async (req) => {
    const b = sugerirSchema.parse(req.body);
    const zona = await zonaDelUsuario(req.usuarioId!);

    const origen = await resolverUbicacion({ texto: b.origenTexto, lat: b.origenLat, lng: b.origenLng, zona });
    const destino = await resolverUbicacion({ texto: b.destinoTexto, lat: b.destinoLat, lng: b.destinoLng, zona });
    const km = kmEntre(origen, destino);

    return {
      kmEstimado: km == null ? null : Math.round(km * 10) / 10,
      montoSugerido: sugerirMontoCentavos(km ?? 0, b.tipoVehiculo ?? 'moto'),
      origen,
      destino,
    };
  });

  // Pedir una moto.
  app.post('/carreras', async (req, reply) => {
    const b = crearSchema.parse(req.body);

    // UNA carrera activa por cliente: si no, se pide cinco motos por error y
    // el pool se llena de basura que nadie va a atender.
    const activa = await prisma.carrera.findFirst({
      where: { solicitanteId: req.usuarioId!, estado: { in: ESTADOS_ACTIVOS } },
      select: { id: true },
    });
    if (activa) {
      return reply.code(409).send({
        error: 'Ya tienes una carrera en curso',
        carreraId: activa.id,
      });
    }

    const zona = await zonaDelUsuario(req.usuarioId!);
    const origen = await resolverUbicacion({ texto: b.origenTexto, lat: b.origenLat, lng: b.origenLng, zona });
    const destino = await resolverUbicacion({ texto: b.destinoTexto, lat: b.destinoLat, lng: b.destinoLng, zona });
    const km = kmEntre(origen, destino);

    const creada = await crearCarreraLibre({
      tipo: b.tipo,
      solicitanteId: req.usuarioId!,
      solicitanteContacto: b.contacto ?? '',
      origenTexto: origen.texto,
      origenLat: origen.lat,
      origenLng: origen.lng,
      destinoTexto: destino.texto,
      destinoLat: destino.lat,
      destinoLng: destino.lng,
      kmEstimado: km,
      montoOfrecidoCentavos: b.montoOfrecidoCentavos,
      montoCompraEstimadoCentavos: b.montoCompraEstimadoCentavos,
      notas: b.notas,
      zona,
      expiraEn: new Date(Date.now() + MINUTOS_HASTA_EXPIRAR * 60_000),
    });

    return { ok: true, ...creada, expiraEnMinutos: MINUTOS_HASTA_EXPIRAR };
  });

  // Mi carrera activa y en qué anda.
  app.get('/carreras/mia', async (req) => {
    const carrera = await prisma.carrera.findFirst({
      where: { solicitanteId: req.usuarioId!, estado: { in: ESTADOS_ACTIVOS } },
      orderBy: { creadoEn: 'desc' },
    });
    if (!carrera) return { carrera: null };

    // Quién la lleva — solo cuando ya hay rider asignado.
    const rider = carrera.riderUsuarioId
      ? await prisma.usuario.findUnique({
          where: { id: carrera.riderUsuarioId },
          select: { nombre: true },
        })
      : null;
    const perfilRider = carrera.riderUsuarioId
      ? await prisma.perfilMotorizado.findUnique({
          where: { usuarioId: carrera.riderUsuarioId },
          select: { telefono: true, placa: true, tipoVehiculo: true },
        })
      : null;

    return {
      carrera: {
        id: carrera.id,
        tipo: carrera.tipo,
        estado: carrera.estado,
        origenTexto: carrera.origenTexto,
        destinoTexto: carrera.destinoTexto,
        montoOfrecido: carrera.montoOfrecido,
        montoCompraEstimado: carrera.montoCompraEstimado,
        kmEstimado: carrera.kmEstimado,
        notas: carrera.notas,
        recogido: carrera.recogidoEn != null,
        creadoEn: carrera.creadoEn,
        expiraEn: carrera.expiraEn,
        riderNombre: rider?.nombre ?? null,
        riderTelefono: perfilRider?.telefono ?? null,
        riderPlaca: perfilRider?.placa ?? null,
        riderVehiculo: perfilRider?.tipoVehiculo ?? null,
      },
    };
  });

  // Arrepentirse, mientras nadie la haya tomado.
  app.post('/carreras/:id/cancelar', async (req, reply) => {
    const { id } = req.params as { id: string };
    const res = await prisma.carrera.updateMany({
      // Solo lo propio, y solo lo que nadie tomó: si un rider ya está en
      // camino, cancelar por app lo dejaría manejando al vacío.
      where: { id, solicitanteId: req.usuarioId!, estado: 'disponible' },
      data: { estado: 'cancelada' },
    });
    if (res.count === 0) {
      return reply.code(409).send({ error: 'Esa carrera ya no se puede cancelar' });
    }
    return { ok: true };
  });
}
```

- [ ] **Step 5: Extender `crearCarreraLibre` con los campos nuevos**

`crearCarreraLibre` todavía no acepta `expiraEn` ni `solicitanteContacto`. En `src/core/carreras.ts`, agregarlos al objeto de args y al `data` del `create`:

```typescript
  solicitanteContacto?: string;
  expiraEn?: Date | null;
```

y en el `data`:

```typescript
      solicitanteContacto: args.solicitanteContacto ?? '',
      expiraEn: args.expiraEn ?? null,
```

(Si `solicitanteContacto` ya está, dejarlo como está.)

- [ ] **Step 6: Registrar la ruta**

En `src/server.ts`, junto a los otros imports de rutas:

```typescript
import { rutasCarreras } from './routes/carreras.js';
```

Y después de `await app.register(rutasMotorizados);` (línea ~146):

```typescript
  await app.register(rutasCarreras);
```

- [ ] **Step 7: Correr los tests**

```bash
npx vitest run tests/rutas-carreras.test.ts
npm run typecheck
```

Expected: PASS en ambos.

- [ ] **Step 8: Correr la suite completa**

```bash
npx vitest run
```

Expected: PASS — sin regresiones.

- [ ] **Step 9: Commit**

```bash
git add src/routes/carreras.ts src/core/carreras.ts src/server.ts tests/rutas-carreras.test.ts
git commit -m "feat: el cliente puede pedir encomiendas y pasajeros"
```

---

### Task 4: Expirar las carreras que nadie tomó

**Files:**
- Modify: `src/core/carreras.ts` (agregar función)
- Test: `tests/carreras-ubicacion.test.ts` (agregar describe)
- Modify: donde vive el scheduler de tareas periódicas (ver Step 1)

**Interfaces:**
- Consumes: modelo `Carrera` (Task 1)
- Produces: `expirarCarrerasVencidas(): Promise<number>` — devuelve cuántas expiró

- [ ] **Step 1: Localizar el scheduler existente**

```bash
cd "d:/Personal Proyects/leadia"
grep -rn "setInterval\|cron\|barrido" src/ --include=*.ts | grep -v test | head -10
```

El repo ya tiene barridos periódicos (por ejemplo el de autocierre — hay un `tests/barrido-autocierre.test.ts`). Encontrá dónde se registran y agregá el de expiración **siguiendo ese mismo patrón**. Si no existe ninguno, dejá la función escrita y testeada, y anotá en el reporte que falta engancharla.

- [ ] **Step 2: Escribir el test que falla**

Agregar a `tests/carreras-ubicacion.test.ts`:

```typescript
import { expirarCarrerasVencidas } from '../src/core/carreras.js';

describe('expirarCarrerasVencidas', () => {
  it('expira solo las disponibles que ya vencieron', async () => {
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 3 });

    const cuantas = await expirarCarrerasVencidas();

    expect(cuantas).toBe(3);
    const where = prismaMock.carrera.updateMany.mock.calls[0][0].where;
    expect(where.estado).toBe('disponible');
    expect(where.expiraEn.lte).toBeInstanceOf(Date);
  });

  it('NO toca las que un rider ya acepto', async () => {
    prismaMock.carrera.updateMany.mockResolvedValue({ count: 0 });

    await expirarCarrerasVencidas();

    const where = prismaMock.carrera.updateMany.mock.calls[0][0].where;
    // Si ya la aceptaron, el rider está yendo: expirarla sería sacarle el
    // trabajo de las manos.
    expect(where.estado).toBe('disponible');
  });
});
```

Agregar `updateMany: vi.fn()` al mock de `carrera` al inicio del archivo.

- [ ] **Step 3: Correr para verificar que falla**

```bash
npx vitest run tests/carreras-ubicacion.test.ts
```

Expected: FAIL — `expirarCarrerasVencidas is not a function`.

- [ ] **Step 4: Implementar**

Agregar a `src/core/carreras.ts`:

```typescript
/**
 * Mata las carreras que nadie tomó a tiempo. Solo las `disponible`: si un
 * rider ya aceptó, está yendo — expirarla sería sacarle el trabajo de las
 * manos. Devuelve cuántas expiró.
 */
export async function expirarCarrerasVencidas(): Promise<number> {
  const res = await prisma.carrera.updateMany({
    where: { estado: 'disponible', expiraEn: { lte: new Date() } },
    data: { estado: 'expirada' },
  });
  return res.count;
}
```

- [ ] **Step 5: Engancharlo al scheduler**

Según lo que hayas encontrado en el Step 1, registrar la función para que corra cada minuto. Si el repo usa `setInterval` en el arranque, seguí ese patrón; si usa otra cosa, seguí esa.

- [ ] **Step 6: Correr los tests**

```bash
npx vitest run
npm run typecheck
```

Expected: PASS — toda la suite.

- [ ] **Step 7: Commit**

```bash
git add -u src/core/carreras.ts tests/carreras-ubicacion.test.ts
git commit -m "feat: las carreras vencidas se expiran solas"
```

---

### Task 5: Verificación final

- [ ] **Step 1: Suite completa y tipos**

```bash
cd "d:/Personal Proyects/leadia"
npm run typecheck && npx vitest run
```

Expected: PASS, sin regresiones.

- [ ] **Step 2: Verificar la regla del dinero**

```bash
grep -n "montoCompraEstimado" src/routes/carreras.ts src/core/comision.ts
```

Expected: en `carreras.ts` solo se pasa a `crearCarreraLibre`, nunca sumado a `montoOfrecido`. En `comision.ts`, **cero resultados** — la comisión jamás toca el adelanto de compra.

- [ ] **Step 3: Verificar que el cliente no puede crear pedidos de restaurante**

```bash
grep -n "z.enum" src/routes/carreras.ts
```

Expected: `z.enum(['encomienda', 'pasajero'])` — sin `'pedido'`.

- [ ] **Step 4: Probar contra la base real**

Escribir un script temporal en `scripts/` (borrarlo después) que cree una carrera de prueba con `crearCarreraLibre`, la lea, y la borre. Correr con `node --env-file=.env`. Sirve para confirmar que el campo `expiraEn` existe de verdad en Supabase y que el cliente Prisma está regenerado.

---

## Qué NO cubre este plan

- **La app del cliente** — plan propio, después de este. Este backend es su prerequisito.
- **Push al rider cuando nace una carrera de cliente** — el push existe para pedidos de negocio; falta el gemelo para carreras del cliente. Task futuro.
- **Contraofertas de riders** (la subasta de inDrive) — el pool sigue siendo "el primero gana".
- **Calificaciones** entre cliente y rider.
- **Historial de carreras del cliente** — `GET /carreras/mia` solo devuelve la activa.
- **Direcciones favoritas** ("Casa", "Trabajo").
