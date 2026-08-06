# Publicar Jala en Play Store

Publicar es **crear un tag**. El prefijo decide el canal:

| Tag | Canal | Quién la recibe |
|---|---|---|
| `interna-v0.1.5` | Prueba interna | El equipo (hasta 100), en minutos |
| `cerrada-v0.1.5` | Prueba cerrada | Motorizados y clientes invitados |
| `produccion-v0.1.5` | **PRODUCCIÓN** | Todo el mundo, al instante |

```bash
git tag interna-v0.1.5
git push origin interna-v0.1.5
```

Un push a `main` **no publica nada**. Eso es a propósito: publicar tiene que
ser una decisión, no un efecto secundario de commitear.

También se puede lanzar a mano desde **Actions → Android · Play Store → Run
workflow**, eligiendo el canal.

⚠️ **`produccion-*` publica LIVE al instante y no hay "deshacer"** — lo máximo
es subir otra versión encima. Usalo solo después de validar en interna y
cerrada.

## El versionCode se maneja solo

Sale de `GITHUB_RUN_NUMBER + 10` (ver `composeApp/build.gradle.kts`). Ya no hay
que acordarse de subirlo, y nunca choca con Play, que rechaza duplicados.

En builds locales queda fijo en `5`.

**Si relanzás una corrida que ya subió a Play**, el `versionCode` es el mismo y
Play la rechaza por duplicada. La salida es crear un tag nuevo, no relanzar.

## Secretos que hay que configurar

En **Settings → Secrets and variables → Actions** del repo `LeadAI-Rider`:

| Secreto | Qué es |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | El `release.keystore` en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | La contraseña del keystore |
| `ANDROID_KEY_ALIAS` | El alias (`leadai`) |
| `ANDROID_KEY_PASSWORD` | La contraseña de la clave |
| `GOOGLE_SERVICES_JSON_BASE64` | El `google-services.json` en base64 |
| `PLAY_STORE_JSON_KEY_B64` | La cuenta de servicio de Play, en base64 |

### Sacar los valores en base64

```bash
base64 -w0 composeApp/release.keystore
base64 -w0 composeApp/google-services.json
base64 -w0 play-store-key.json
```

**Van en base64, no en texto plano**, y no es capricho: el JSON de la cuenta de
servicio tiene una `private_key` con `\n` que GitHub corrompe al guardarla como
secreto normal. El síntoma es `Invalid JWT Signature` al subir, que no dice
nada sobre la causa real.

### La cuenta de servicio de Google Play

> **Empezar por Google Cloud, no por Play Console.** "Acceso a la API" es
> configuración de la CUENTA de desarrollador, no de la app, y en algunas
> cuentas ni aparece en el menú. Esto ya pasó montando el CI de sania y de
> controlgym — el camino de abajo es el que funcionó.

1. **Google Cloud Console** → usar `leadai-501802` (el de Firebase) o crear uno.
2. Habilitar la **"Google Play Android Developer API"** en ese proyecto.
   ⚠️ **Este paso se olvida y sin él nada funciona.**
3. **IAM → Cuentas de servicio** → crear una (los roles se pueden saltar) →
   pestaña **Claves** → **Agregar clave → JSON** → se descarga.
4. **Play Console → Usuarios y permisos** (NO "Acceso a la API") → **Invitar
   usuario** → pegar el **email de la cuenta de servicio**
   (`...@leadai-501802.iam.gserviceaccount.com`) → permiso **"Lanzar en
   canales de prueba"**, y **"Lanzar a producción"** si se va a publicar.
5. `base64 -w0 el-archivo.json` → pegarlo en `PLAY_STORE_JSON_KEY_B64`.

La propagación del permiso tarda unos minutos: si el primer intento falla por
permisos, esperar y relanzar antes de suponer que está mal configurado.

Sin esto el workflow falla en el último paso: compila y firma bien, pero no
tiene con qué autenticarse contra Play.

## Qué hace el workflow

1. Deduce el canal del prefijo del tag
2. Reconstruye el keystore y el `google-services.json` desde los secretos
3. **Corre los tests** — si fallan, no publica
4. Compila el AAB firmado, con el `versionCode` de la corrida
5. Lo sube al canal que corresponde vía Fastlane
6. Borra los secretos del runner, falle o no

## Las notas de versión

Salen de `fastlane/metadata/android/es-419/changelogs/default.txt`. Editá ese
archivo antes de tagear si querés decir algo específico; si no, va el texto
genérico que ya está.

El resto de la ficha —título, descripción, capturas— **no se toca desde acá**:
se administra en Play Console y el workflow la deja como está.

## iOS

No hay workflow todavía: los `actual` de `iosMain` son stubs y la app no
funciona en iPhone. Cuando haya una Mac, el de `controlgym-app`
(`ios-release.yml`) sirve de molde — usa `ios-v*` para TestFlight y
`ios-prod-*` para App Store.

Ojo con una diferencia: en Android `produccion-*` deja la app en manos de los
usuarios en minutos; en iOS lo máximo automatizable es **enviarla a revisión**.
Apple tarda de horas a días y puede rechazarla.
