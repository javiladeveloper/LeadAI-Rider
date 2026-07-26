# LeadAI Rider

La app del **motorizado**: ve las carreras de su zona, acepta la que quiere,
navega hasta el local y hasta el cliente, y cobra. Separada de la app del
negocio ([`leadai-mobile`](https://github.com/javiladeveloper/leadai-mobile))
como Uber y Uber Driver: dos apps en Play, mismo backend, misma cuenta.

## Qué hace hoy

| Pieza | Detalle |
|---|---|
| **Alta de rider** | Distrito (Perú completo, no solo Lima), DNI validado en vivo (MAXFIND vía backend, que autocompleta el nombre oficial), teléfono y placa. El estado `pendiente` se ve en la app hasta que se verifica. |
| **Pool de carreras** | Pedidos "listos" de negocios de su zona, ordenados por cercanía real ("A 1.2 km de ti"). El primero que acepta se la lleva; a los demás les avisa un 409. |
| **Dos tramos** | Primero al LOCAL a recoger ("📦 Ya recogí el pedido"), después al cliente ("✅ Entregado"). |
| **Mapa con navegación** | Mapa embebido heading-up estilo Maps: la moto, la mejor ruta (OSRM), el negocio y la casa del cliente. GPS local cada ~1s, la ruta se recorta sola al avanzar. |
| **Monedero prepago** | Paquetes de S/20, S/50 y S/100 en un solo pago con tarjeta (Culqi). Cada carrera aceptada descuenta S/1. Sin saldo no se puede aceptar. |
| **Historial** | Carreras entregadas con los km REALES recorridos (odómetro de pings GPS) y el resumen de hoy. |
| **Push** | "Nueva carrera en tu zona" cuando un negocio cercano marca un pedido como listo. |

## Stack

Kotlin Multiplatform + Compose Multiplatform (Android e iOS desde el mismo
`composeApp/`), Koin, Ktor, DataStore. Mismo stack y mismo design system
("Brand Harmony": teal `#006b5d` acción, coral `#f0704f` urgencia, arena
`#f7f9fb`, Plus Jakarta Sans) que la app de negocios.

## Backend

`https://api.leadai-pe.com` — repo [`leadia`](https://github.com/javiladeveloper/leadia).
La MISMA API que consume la app de negocios: mismo `/auth/login`, misma sesión
(`Authorization: Bearer hilo_u...`).

La diferencia es el ROL: el motorizado es un rol de **plataforma**
(`usuarioId` único, endpoints `/motorizados/*`), no una membresía a un tenant,
así que sus requests NO llevan `X-Tenant-Id`. Un mismo usuario puede ser rider
acá y dueño de un restaurante en la otra app; la sesión trae las `empresas`
igual, pero esta app las ignora.

## Correr el proyecto

```bash
./gradlew.bat :composeApp:testDebugUnitTest   # tests
./gradlew.bat :composeApp:assembleDebug       # APK debug
./gradlew.bat :composeApp:bundleRelease       # AAB para Play (necesita release.keystore)
```

Dos archivos NO están en el repo y se agregan a mano:

- `composeApp/google-services.json` — sin él la app compila y corre; lo único
  que no funciona es el push (el plugin de Google Services se aplica solo si el
  archivo existe, ver `composeApp/build.gradle.kts`).
- `composeApp/release.keystore` — igual: la firma de release se configura solo
  si el archivo está presente.
