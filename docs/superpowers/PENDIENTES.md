# Pendientes

Lo que quedó fuera de los planes ejecutados, con el porqué. **Nada de esto está
descartado** — se difirió para llegar antes a algo funcionando.

Actualizado: 2026-07-28

---

## Bloqueantes para operar de verdad

Sin esto la plataforma funciona en demo pero falla en la calle.

### 1. El GPS se apaga con la app en segundo plano

Los dos `LaunchedEffect` de `CarrerasPantalla` (feed 15s, GPS 5s) se suspenden
cuando el rider bloquea el teléfono o cambia de app. **El cliente ve la motito
congelada mientras el rider maneja con el celular en el bolsillo.**

Hace falta un **foreground service** de Android con notificación persistente.
Y con él, los permisos que inDrive pide y hoy no pedimos:

- Ubicación **"todo el tiempo"** — desde Android 11 no se puede pedir por
  diálogo, hay que mandar al usuario a Configuración con
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + `package:<applicationId>`,
  que abre directo la pantalla de la app (el detalle que a Jonathan le gustó de
  inDrive: no tener que buscarla en la lista).
- **Uso de batería sin restricciones** — en Xiaomi/Oppo/Huawei, muy comunes en
  Perú, el sistema mata la app sin esto.
- Notificaciones.

Es anterior a todo el trabajo de carreras multi-tipo. Arrastra desde el inicio.

### 2. Push cuando algo pasa

Hoy el push existe solo para "nueva carrera en tu zona" de pedidos de negocio.
Faltan los gemelos:

- **Al rider**, cuando un cliente pide una encomienda o un pasajero cerca.
- **Al cliente**, cuando un rider acepta su carrera. Hoy se entera por el
  polling de 10s: si tiene la app cerrada, no se entera.

Requiere trabajo del backend además de la app.

### 3. Verificación de riders

Hoy el alta pide DNI (validado contra MAXFIND, que ya funciona) y placa
(solo formato: 5-7 alfanuméricos). **Nadie verifica que la placa exista, ni
que sea del rider, ni que el vehículo tenga SOAT.** El perfil queda
`pendiente` y alguien tendría que revisarlo a mano — pero no hay panel para
eso.

Lo que se puede automatizar, en orden de costo/beneficio:

- **Cruzar el nombre del DNI (que MAXFIND ya devuelve) contra la foto del
  documento** — rechazo automático si no coincide, sin humano.
- **Validaciones básicas de imagen**: que no esté borrosa, que no sea una
  captura de pantalla. (Jonathan probó inDrive subiendo fotos de su escritorio
  y las aceptó — el rechazo llega después, si llega.)
- **Selfie contra foto del DNI** — el paso que más fraude corta.
- **SOAT por placa** — la APESEG tiene consulta de pólizas vigentes. Un
  vehículo sin SOAT no debería transportar gente.
- **SUNARP** para titularidad de la placa — el acceso programático es por
  convenio, no API pública. Vale preguntarle a MAXFIND si ofrece consulta
  vehicular: sería la misma integración que ya existe.

Recomendación: **híbrido**. Automatizar los rechazos obvios y mandar a revisión
manual solo lo que pasa esos filtros. Con volumen bajo en Tacna, revisar a mano
lo que sobrevive es viable.

Merece spec propio: la verificación de identidad es un producto en sí mismo.

---

## Producto, cuando haya uso real

### 4. Elegir origen y destino tocando el mapa

Hoy el cliente escribe texto y el backend geocodifica (o usa su GPS). Funciona,
pero "Jose Olaya 110" puede caer en cualquier lado.

El diseño acordado era **confirmación visual**: mostrar el pin geocodificado y
preguntar "¿es acá?", con opción de arrastrarlo. Se difirió porque el mapa hoy
es un WebView que carga una página del backend — habría que construir una
página de selección nueva.

Precedente en el código: `Pedido.direccionLat/direccionLng` existe justamente
porque *"la dirección escrita es imprecisa y el CLIENTE confirma en el mapa"*.

### 5. Historial y direcciones favoritas del cliente

`GET /carreras/mia` solo devuelve la carrera activa. No hay "pedir de nuevo",
ni "Casa" / "Trabajo". Es lo primero que va a pedir alguien que use la app dos
veces.

### 6. Contraofertas (la subasta de inDrive)

El pool es "el primero que acepta gana". inDrive permite que varios riders
contraoferten y el cliente elija.

Decisión tomada: **no ahora**. Agregarlo después es fácil; sacarlo sería romper
el modelo mental de todos. Si los riders empiezan a pedir negociar, es señal de
que llegó el momento.

### 7. Calificaciones

No existen en ningún lado. Sin ellas no hay forma de sacar a un rider malo del
sistema salvo bloquearlo a mano.

---

## Diferidos con razón técnica

### 8. iOS

Los cuatro `actual` de `iosMain` son stubs que devuelven `null`: mapa, GPS,
push y login con Google. La app compila para iOS pero un rider en iPhone no
puede trabajar.

**Requiere una Mac.** No se destraba desde acá.

### 9. Filtro de preferencias del rider

Un rider podría querer "solo deliveries de negocios, no pasajeros". Hoy ve
todo lo de su zona. Quedó fuera del spec de carreras multi-tipo; se conversó
pero no se decidió.

### 10. Soporte real de `auto`

`PerfilMotorizado.tipoVehiculo` acepta `moto | auto` y la fórmula de sugerencia
ya diferencia (el auto consume ~3x más, así que su factor por km es ~2x). Pero
no hay nada más pensado para autos: ni capacidad, ni categorías, ni filtros.
El campo existe para no tener que migrar perfiles después.

### 11. Cobro al restaurante (modelo Rappi)

Hoy el rider paga la comisión (modelo inDrive). Rappi cobra 18-30% al
restaurante, pero puede hacerlo porque le *aporta demanda*.

El momento natural para evaluarlo sería si LeadAI llega a llevarle clientes
nuevos al negocio — por ejemplo con un catálogo público donde la gente
descubra el local. Ahí el cobro se justificaría solo. Hoy no.

---

## Operativos

### 12. `prisma migrate dev` está roto en `leadia`

No puede reconstruir la shadow DB: la migración vieja
`1783989649845_perfil_vendedor_contacto` no replica limpio (P3006/P1014). Es
preexistente, no lo causó este trabajo.

**Workaround en uso:** escribir el `migration.sql` a mano, siempre con
`IF NOT EXISTS` — sin eso el CD falla y bloquea todas las migraciones
siguientes (P3009, ya pasó una vez).

**Arreglo real:** configurar `shadowDatabaseUrl`, o resolver la migración rota.

### 13. Credenciales del keystore en el repo

`composeApp/build.gradle.kts:112-114` tiene la password del keystore de release
hardcodeada. El `.keystore` no está en el repo, pero la password sí.

### 14. Sin tests de UI

Los tests cubren ViewModels y APIs. Ningún test toca Compose.
