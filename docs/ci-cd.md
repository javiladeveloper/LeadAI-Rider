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

Si todavía no existe:

1. **Play Console → Configuración → Acceso a API**
2. Crear un proyecto de Google Cloud o vincular uno existente
3. Crear una **cuenta de servicio** y descargar su JSON
4. Volver a Play Console y darle permiso de **"Administrar versiones"** sobre
   la app

Sin esto, el workflow falla en el último paso: compila y firma bien, pero no
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
