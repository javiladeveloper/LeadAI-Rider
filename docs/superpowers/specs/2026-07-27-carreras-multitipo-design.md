# Carreras multi-tipo: delivery, mandado, encomienda y pasajero

**Fecha:** 2026-07-27
**Estado:** backend IMPLEMENTADO (2026-07-27). Pendiente: app y modo pasajero.
**Repos afectados:** `leadia` (backend), `LeadAI-Rider` (app)

---

## 1. Qué se construye

Hoy la app del rider solo sirve un caso: pedidos de restaurante. Una "carrera"
es literalmente un `Pedido` en estado `listo` con `modalidad: 'delivery'`.

Se extiende a cuatro tipos:

| Tipo | Origen | Quién la crea |
|---|---|---|
| `pedido` | Local del negocio | El negocio cliente de LeadAI, al marcar el pedido como listo (ya funciona) |
| `mandado` | Negocio **ajeno** o dirección libre | Un usuario desde la app |
| `encomienda` | Dirección libre | Un usuario desde la app |
| `pasajero` | Dirección libre | Un usuario desde la app |

### El caso `mandado`

"Tráeme un chifa del Salón Cantón." El cliente le pide al rider que le **compre**
algo en un negocio que no usa LeadAI. No existe `Pedido`, no hay `Tenant`, nadie
marca "listo" — la carrera nace del usuario, con el negocio como texto libre.

Es la diferencia clave con `pedido`: ahí LeadAI conoce al negocio y el monto ya
está arreglado. Acá el negocio es ajeno y **el rider adelanta la plata de la
compra**, que después le devuelve el cliente junto con el flete.

Por eso `mandado` necesita dos montos separados:

- `montoOfrecido` — el **flete**: lo que el cliente le paga al rider por ir.
  Sobre este se calcula la comisión.
- `montoCompraEstimado` — lo que **cuesta lo que va a comprar**, declarado por
  el cliente. Es una estimación: el rider paga lo que salga en caja. **La
  comisión NUNCA se calcula sobre este monto** — no es ingreso del rider, es
  plata que adelanta y recupera.

El rider ve los dos por separado antes de aceptar, porque necesita saber cuánta
plata tiene que llevar encima. Una carrera de flete S/8 con una compra de S/60
exige tener S/60 disponibles.

**Riesgo asumido:** si el cliente no le paga, el rider pierde el adelanto.
LeadAI no garantiza ni intermedia esa plata — igual que no intermedia el flete.
La app lo advierte al aceptar un mandado con compra alta.

Y la app pasa de ser solo-rider a tener **dos modos** (pasajero / conductor),
como inDrive.

---

## 2. Modelo de negocio

**Puro enlace.** LeadAI no fija tarifas, no cobra el viaje, no intermedia el
pago. El pasajero y el rider acuerdan el precio; el pago es en efectivo entre
ellos.

**Monto sugerido, editable.** El sistema propone un monto de partida; el
solicitante lo sube o lo baja. Es una *sugerencia*, no una tarifa: si el cobro
final difiere, no es responsabilidad de la plataforma. Esta distinción es
deliberada — sugerir es reversible, tarifar convierte a LeadAI en prestador del
servicio de transporte.

**Ingreso: comisión del monedero prepago del rider**, descontada al aceptar.

| Tipo | Comisión |
|---|---|
| `pedido` | S/1 fijo |
| `encomienda` | S/1 fijo |
| `mandado` | `max(S/1, 10% del flete)` — **nunca sobre el monto de compra** |
| `pasajero` | `max(S/1, 10% del monto ofrecido)` |

Por qué el delivery y la encomienda no llevan porcentaje: en delivery el monto
lo arregla el negocio con el rider por fuera y no pasa por el sistema — un %
sobre un dato que no se puede verificar es ficticio. En encomienda el ticket es
bajo y el piso de S/1 ya domina.

**En `mandado` la comisión se calcula SOLO sobre el flete.** El monto de compra
es plata que el rider adelanta y recupera, no ganancia suya. Cobrarle un % sobre
los S/60 del chifa sería cobrarle por prestarle plata al cliente — lo dejaría
trabajando a pérdida en cualquier compra grande.

Por qué al rider y no al comercio (modelo inDrive, no Rappi): Rappi cobra 18-30%
al restaurante porque le *aporta demanda*. LeadAI no le lleva clientes nuevos al
negocio — el pedido ya entró por su WhatsApp. El valor que LeadAI entrega es
para el rider: una carrera que no tenía. Además el negocio ya paga por la app de
negocios; sumarle un 20% sería un salto de precio sobre un cliente existente.

**Validación de mercado:** en Tacna un rider paga hoy ~S/0.90 de comisión por
un viaje de S/5-6 en otras apps (≈15-18%). El S/1 de LeadAI está en línea con lo
que el mercado ya acepta.

### Configurabilidad

Comisión (`piso` y `porcentaje`) configurable **por tipo de carrera y por
ciudad/zona**, sin deploy. Razones: los competidores mueven sus comisiones con
frecuencia (inDrive pasó de ~10% a ~15-18% en Tacna), las promociones de
lanzamiento son estándar en la industria, y al expandir a mercados de ticket más
alto los valores de Tacna no aplican.

**Comisión cero es un valor válido de primera clase.** inDrive entró a Perú con
6 meses sin comisión, y esa es una jugada que LeadAI quiere poder repetir para
ganar riders al lanzar en una ciudad nueva. El sistema debe soportar
`piso = 0, porcentaje = 0` sin casos especiales: la carrera se acepta, se
registra el movimiento en S/0 y el rider no paga nada. No debe exigir saldo
cuando la comisión es cero — un rider con monedero vacío tiene que poder
trabajar durante la campaña.

**Vigencia temporal.** Cada configuración lleva `desde` y `hasta` (nullable =
sin límite). Una campaña se programa con fecha de fin y se apaga sola; nadie
tiene que acordarse de desactivarla. Al resolver la comisión de una carrera se
toma la config vigente más específica (tipo + zona > tipo > default).

**Congelado al aceptar.** `comisionCentavos` se guarda en la carrera al momento
de aceptarla. Cambiar la configuración nunca altera carreras ya tomadas, y el
historial del rider queda auditable.

---

## 3. Monto sugerido

```
sugerido = base + (km_estimado × factorPorKm[tipoVehiculo])
```

| Vehículo | Base | Factor/km |
|---|---|---|
| `moto` | S/4.00 | S/1.20 |
| `auto` | S/6.00 | S/2.50 |

Constantes configurables, no hardcodeadas.

**Lo que ve el rider: monto ofrecido menos comisión.** Nada más. La app nunca
le muestra costos estimados de combustible ni márgenes calculados — él conoce
sus gastos mejor que el sistema.

| km | Sugerido | Comisión | **Recibe el rider** |
|---|---|---|---|
| 2 | S/6.40 | S/1.00 | **S/5.40** |
| 3 | S/7.60 | S/1.00 | **S/6.60** |
| 5 | S/10.00 | S/1.00 | **S/9.00** |
| 10 | S/16.00 | S/1.60 | **S/14.40** |
| 15 | S/22.00 | S/2.20 | **S/19.80** |

**Fundamento del factor S/1.20/km (uso interno, no se muestra en la app).** Una
moto 150cc rinde 30-45 km/l contra 10-14 km/l de un auto: ~3x más eficiente. Con
gasolina a ~S/4.20/l el combustible sale ~S/0.40/km en moto y ~S/1.30/km en
auto — de ahí que el factor del auto sea ~2x. El factor de la moto se eligió
para que el monto resultante deje al rider un margen holgado sobre sus costos
operativos y reproduzca los precios que ya se pagan en Tacna.

**Validación:** la fórmula da S/6-8 para 2-3 km, que es exactamente lo que se
paga hoy por un delivery del Pollón (Gral. Varela 471) a José Olaya 110 en
Tacna. Reproduce el precio real del mercado sin haber sido forzada a hacerlo.

**Supuesto a validar en campo:** el rendimiento de combustible está
fundamentado en fuentes; el costo operativo total (mantenimiento, llantas,
cadena) es una estimación. Confirmar con 2-3 riders reales antes de fijar las
constantes definitivas.

Se persisten `montoSugerido` y `montoOfrecido` por separado para medir qué tan
bien calibra la fórmula y ajustarla con datos reales.

---

## 4. Modelo de datos (backend `leadia`)

### 4.1 Decisión: tabla `Carrera` nueva

`Pedido` exige `tenantId` (FK NOT NULL), `leadId`, `canal`, `items`,
`modalidad`. Para un taxi no existe ninguno de esos. Hay dos caminos:

- **Extender `Pedido`** haciendo nullable `tenantId`/`leadId`: menos código
  nuevo, pero debilita la integridad de un modelo vivo que la app de negocios
  usa en producción, y `Pedido` pasaría a significar tres cosas distintas.
- **Tabla `Carrera` nueva** ← elegida: `Pedido` queda intacto, la app de
  negocios no se entera. Los pedidos de restaurante se proyectan a `Carrera`
  al marcarse listos.

Costo asumido: los endpoints de pool/aceptar/recoger/entregar hoy escriben sobre
`Pedido` y deben redirigirse a `Carrera`, manteniendo el estado del `Pedido`
sincronizado para que la Cocina siga viendo "en camino". Es la parte más
delicada de la migración.

### 4.2 `Carrera`

```prisma
model Carrera {
  id   String @id @default(cuid())
  tipo String // 'pedido' | 'mandado' | 'encomienda' | 'pasajero'

  // Solo tipo 'pedido': enlaza al Pedido del restaurante.
  pedidoId String? @unique
  // Solo encomienda/pasajero: quién la pidió.
  solicitanteId String?

  origenTexto  String
  origenLat    Float?
  origenLng    Float?
  destinoTexto String
  destinoLat   Float?
  destinoLng   Float?

  kmEstimado     Float?
  montoSugerido  Int?    // centavos — flete propuesto por el sistema
  montoOfrecido  Int?    // centavos — flete definido por el solicitante
  // Solo 'mandado': lo que cuesta lo que el rider va a COMPRAR. Es plata que
  // adelanta y recupera del cliente — la comisión NUNCA se calcula sobre esto.
  montoCompraEstimado Int?
  notas          String  @default("") // "caja mediana", "chifa combinado sin verduras"

  // 'disponible' | 'aceptada' | 'recogida' | 'entregada' | 'cancelada'
  estado           String  @default("disponible")
  riderUsuarioId   String?
  comisionCentavos Int?    // congelada al aceptar — auditable
  kmReal           Float?  // odómetro de pings GPS

  aceptadaEn  DateTime?
  recogidoEn  DateTime?
  entregadoEn DateTime?
  creadoEn    DateTime @default(now())

  @@index([estado, tipo])
  @@index([riderUsuarioId, estado])
}
```

### 4.3 Cambios en modelos existentes

- `PerfilMotorizado`: agregar `tipoVehiculo String @default("moto")` —
  `'moto' | 'auto'`. Necesario desde el inicio aunque solo haya motos:
  agregarlo después obliga a migrar perfiles en producción y recalcular
  sugerencias.
- Nueva tabla de configuración de comisión y tarifas por tipo y zona.
- `Pedido`: **sin cambios**.

---

## 5. App (repo `LeadAI-Rider`)

### 5.1 Una sola app, dos modos

Decisión: **una sola app** con switch pasajero/conductor, como inDrive (no dos
APKs tipo Uber/Uber Driver).

Implica renombrar `applicationId` de `pe.leadai.rider` a uno neutro y
reposicionar la ficha de Play. Verificar que el nuevo id no choque con el de la
app de negocios.

### 5.2 Reescritura del arranque

Hoy [`Navegacion.kt:74-78`](../../../composeApp/src/commonMain/kotlin/pe/leadai/rider/ui/navegacion/Navegacion.kt)
manda al alta de motorizado a cualquiera sin perfil. Un pasajero quedaría
atrapado ahí, pidiéndole DNI y placa.

Nuevo flujo:

```
sesión → ¿tiene perfil de motorizado?
           sí → modo conductor (comportamiento actual, sin pantallas nuevas)
           no → elegir modo
                  "Pido un viaje" → modo pasajero
                  "Manejo"        → alta de motorizado → modo conductor
```

**Requisito de compatibilidad:** un rider con la app ya instalada y perfil
creado no debe ver ninguna pantalla nueva. Su experiencia queda idéntica.

### 5.3 Modo conductor

El pool acepta los cuatro tipos. La card de carrera cambia según el tipo:

- `pedido` — como hoy: `🍽️ Negocio · S/45.00`
- `mandado` — `🛍️ Mandado`, negocio de origen, destino, **flete y monto de
  compra por separado y bien diferenciados**
- `encomienda` — `📦 Encomienda`, origen, destino, monto ofrecido
- `pasajero` — `🚕 Pasajero`, origen, destino, monto ofrecido

**El mandado exige claridad visual sobre el dinero.** El rider tiene que ver de
un vistazo cuánto gana y cuánta plata necesita llevar. Nunca mostrar un total
combinado: un `S/68` donde S/60 es adelanto y S/8 es ganancia se lee como una
carrera muy rentable y no lo es. Antes de aceptar, la card muestra
`Ganas S/8 · Llevas S/60 para la compra`.

Todos muestran distancia al origen (`📍 A 1.2 km de ti`), que ya funciona.

**Qué ve el rider sobre el dinero.** Solo dos números: el monto ofrecido y lo
que recibe después de la comisión. La app **no** muestra costos estimados de
combustible, márgenes ni ganancia neta calculada — el rider conoce sus gastos
mejor que el sistema, y mostrarle una estimación equivocada solo genera
desconfianza. Los supuestos de costo operativo existen únicamente para calibrar
la fórmula de sugerencia, puertas adentro.

**Sin cambios:** dos tramos (recogido → entregado), tracking GPS, mapa,
monedero, historial, push. Ya funcionan y son agnósticos al tipo de carrera.

### 5.4 Modo pasajero

Fuera del alcance de este spec — va en spec propio. Ver sección 7.

---

## 6. Alcance de este spec

**Incluye:**
1. Backend: modelo `Carrera`, tipos, comisión configurable, fórmula de
   sugerencia, migración del pool de `Pedido` a `Carrera`.
2. App: modo conductor aceptando los cuatro tipos; elección de modo en el
   arranque; `tipoVehiculo` en el alta.

**No incluye** (specs propios):
- Modo pasajero completo: elegir origen/destino en mapa con búsqueda de
  direcciones, cotización, seguimiento del viaje. Es la parte más pesada del
  producto y merece su propio diseño.
- Encomiendas por WhatsApp vía el bot de IA existente.
- Soporte real de `auto` (el campo existe; el producto para autos no).

---

## 7. Orden de construcción recomendado

1. **Backend** (este spec) — sin esto nada tiene qué consumir.
2. **App, modo conductor** (este spec) — el trabajo más chico: el pool, el
   mapa, los dos tramos y el monedero ya existen.
3. **Encomiendas por WhatsApp** — el bot ya existe y una encomienda es casi
   idéntica a un pedido. Con esto ya hay riders ganando con carreras que no son
   de restaurante, y se valida la fórmula de sugerencia con dinero real.
4. **Modo pasajero** — recién cuando haya riders activos en el pool.

Razón del orden: si el modo pasajero se construye primero, el día que lance no
habrá riders esperando taxis y los primeros pasajeros verán un pool vacío.
Construir la oferta antes que la demanda evita el arranque en frío.

---

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Migrar el pool de `Pedido` a `Carrera` rompe los deliveries en producción | Proyección con doble escritura y verificación antes de cortar; la Cocina sigue leyendo `Pedido` |
| En un `mandado`, el cliente no le paga al rider lo que adelantó | LeadAI no garantiza esa plata (igual que no garantiza el flete). La app lo advierte al aceptar mandados con compra alta y muestra los montos separados. Si aparece como problema real, evaluar tope de compra o adelanto por la app |
| El rider acepta un mandado sin plata suficiente para la compra | El monto de compra se muestra destacado antes de aceptar, nunca sumado al flete |
| El rider evade declarando montos bajos (pago en efectivo, no verificable) | El piso de S/1 hace que evadir solo rinda hasta ese punto; el 10% recién pesa arriba de S/10 |
| La sugerencia queda mal calibrada para Tacna | `montoSugerido` vs `montoOfrecido` persistidos; constantes configurables sin deploy |
| Nominatim (1 req/s) no aguanta el volumen de geocodificación | Cachear pines por dirección; evaluar proveedor pago si el volumen lo justifica |
| Renombrar el `applicationId` afecta a usuarios instalados | Verificar comportamiento de actualización en Play antes de publicar |

---

## 9. Decisiones registradas

| Decisión | Elegido | Descartado |
|---|---|---|
| Precios | Sugerencia editable (inDrive) | Tarifa fija (Uber/Yango) |
| Quién paga la comisión | El rider | El restaurante (Rappi) |
| Comisión pasajero | `max(S/1, 10%)` | % puro sin piso; S/1 fijo |
| Comisión delivery/encomienda | S/1 fijo | % (el monto no es verificable) |
| Base de comisión en `mandado` | Solo el flete | El total (flete + compra) — cobraría por plata que el rider adelanta |
| Negocios ajenos (`mandado`) | Texto libre, sin `Tenant` | Exigir que el negocio sea cliente de LeadAI |
| Estructura de la app | Una app, dos modos (inDrive) | Dos apps (Uber/Uber Driver) |
| Modelo de datos | Tabla `Carrera` nueva | Extender `Pedido` con nullables |
| Subasta de ofertas | No por ahora | Contraofertas estilo inDrive |
