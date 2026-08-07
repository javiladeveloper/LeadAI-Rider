---
name: lanzar-release
description: Usar al publicar una versión nueva de Jala en Play Store (interna, cerrada o producción). Cubre el bump del versionName, el tag por canal, el anuncio al backend que dispara el modal de "hay versión nueva", y qué hacer cuando GitHub Actions no arranca.
---

# Publicar una versión de Jala

El **prefijo del tag** decide a dónde va. Un push a `master` NO publica nada:
publicar es siempre crear un tag.

| Tag | Destino |
|---|---|
| `interna-vX.Y.Z` | Play — **prueba interna** (el equipo, minutos) |
| `cerrada-vX.Y.Z` | Play — **prueba cerrada** (motorizados invitados) |
| `produccion-vX.Y.Z` | Play — **PRODUCCIÓN**, publicación completa |

> Escalera: `interna` → `cerrada` → `produccion`. Un `produccion-*` deja el AAB
> live para todos; usalo solo tras validar en los otros canales.

iOS todavía no se publica (no hay lane ni tag). Cuando llegue, va en un tag
aparte para que una plataforma no arrastre a la otra.

## Pasos

1. **Subir `VERSION_NAME_LOCAL`** en `composeApp/build.gradle.kts` para que
   coincida con el tag (`interna-v0.1.7` → `"0.1.7"`).

   El **`versionCode` no se toca**: sale de `GITHUB_RUN_NUMBER + VERSION_CODE_OFFSET`.

   > **Esta es LA trampa de este repo.** El `versionName` sale del tag
   > (`GITHUB_REF_NAME`), y `VERSION_NAME_LOCAL` es solo el respaldo de cuando
   > se compila sin tag. Si generás el AAB a mano, toma el respaldo — y si no lo
   > subiste, publicás una versión nueva con el nombre de la anterior.
   >
   > Pasó el 2026-08-06: se subió el versionCode 19 con `versionName` 0.1.6, el
   > mismo que ya estaba publicado. Play lo aceptó (compara el code), pero en
   > Play Console quedaron dos versiones distintas llamadas igual.

2. **Compilar antes de etiquetar** — un tag con el build roto deja un release a
   medias:
   ```bash
   ./gradlew.bat :composeApp:assembleDebug
   ./gradlew.bat :composeApp:allTests
   ```

3. **Commit y push a `master`.**

4. **Etiquetar y empujar:**
   ```bash
   git tag interna-v0.1.7
   git push origin interna-v0.1.7
   ```

   **Nunca borres y recrees un tag ya empujado.** Cuenta como dos pushes y lanza
   dos builds con el mismo tag; el del commit viejo puede terminar después del
   bueno y dejar publicada la versión equivocada. Si te equivocaste, **subí el
   número** — no reutilices el tag.

5. **Verificar que termine verde:**
   ```bash
   gh run list --limit 3
   ```

6. **Verificar que de verdad subió** (verde ≠ le llegó al usuario):
   ```bash
   gh run view <run-id> --log | grep -E "Canal de despliegue|Successfully finished"
   ```

7. **Verificar el anuncio de versión** — es lo que dispara el modal de "hay una
   versión nueva". El CI lo hace solo (paso final del workflow, con
   `ADMIN_API_KEY`), pero se comprueba en el backend, no en el log:
   ```bash
   curl -s https://api.leadai-pe.com/app/version
   ```
   El `versionCode` debe ser el que acabás de publicar.

   > **Sin este paso el modal NO aparece.** La app compara su `versionCode`
   > contra el que anuncia el backend; si son iguales, no ofrece nada. El
   > 2026-08-06 el AAB 19 estaba en Play pero el backend seguía anunciando 18, y
   > el celular no pedía actualizar por más que se cerrara y abriera la app.

## Publicar a mano (cuando Actions no dispara)

Pasó el 2026-08-06: los tags v0.5.0 a v0.6.1 se empujaron y **ninguno** lanzó
una corrida. El workflow figuraba `active` y los tags apuntaban al commit
correcto. Sospecha principal: minutos de Actions agotados
([Settings → Billing](https://github.com/settings/billing)).

`gh workflow run` no es alternativa desde esta cuenta: devuelve
`HTTP 403: Must have admin rights`.

El camino manual:

```bash
# El GITHUB_RUN_NUMBER simula la corrida: debe dar un versionCode MAYOR
# al último publicado (mirá Play Console). code = RUN_NUMBER + OFFSET.
GITHUB_RUN_NUMBER=10 ./gradlew.bat :composeApp:bundleRelease
```

El AAB queda en `composeApp/build/outputs/bundle/release/composeApp-release.aab`
y se sube a mano en Play Console → el canal → Crear nueva versión.

**Verificá el `versionCode` REAL antes de subir**, no lo deduzcas de la cuenta:
```bash
python3 -c "
import zipfile
z = zipfile.ZipFile('composeApp/build/outputs/bundle/release/composeApp-release.aab')
m = z.read('base/manifest/AndroidManifest.xml')
i = m.find(b'versionCode')
print(m[i:i+40])"
```
El manifest del bundle es protobuf (`aapt2 dump badging` NO lo lee: da
`could not identify format of APK`). El `versionCode` aparece como string justo
después del literal — `\x1a\x0225` es 25.

> Play rechaza duplicados: *"El código de versión 20 ya se ha usado"*. Las
> corridas viejas del CI ya consumieron números, así que elegí un
> `GITHUB_RUN_NUMBER` con margen — mirá el mayor de Play Console (incluidas las
> versiones descartadas) y saltá por encima. Un hueco en la numeración no
> molesta a nadie; un número repetido te bloquea la subida.

Antes de subirlo, verificá la firma (debe ser el keystore del repo):
```bash
keytool -printcert -jarfile composeApp/build/outputs/bundle/release/composeApp-release.aab | grep SHA1
# SHA1: 0E:56:35:69:E4:96:72:50:A7:72:70:9B:14:D7:B7:E4:EE:A9:2D:AC
```

Y **acordate del paso 7**, que en manual no lo hace nadie:
```bash
CLAVE=$(grep "^ADMIN_API_KEY=" "d:/Personal Proyects/leadia/.env" | cut -d= -f2-)
curl -s -X POST https://api.leadai-pe.com/app/version \
  -H "Content-Type: application/json" -H "x-admin-key: $CLAVE" \
  -d '{"plataforma":"android","versionCode":20,"versionName":"0.1.7","obligatoria":false,"notas":"..."}'
```

> El AAB **no se commitea**. El código ya está en el repo y el binario se
> regenera; son 11 MB que solo engordan el historial.

## Si dicen "no me sale actualizar"

En orden, del más probable al menos:

1. **El backend sigue anunciando la versión vieja** (paso 7). Lo más común
   cuando se publicó a mano. `curl` y comparás.
2. **El `versionName` no cambió** — sí se instaló, pero se ve idéntica.
3. **Hay que cerrar la app del todo** (deslizarla de recientes). Minimizar no
   alcanza: el chequeo corre al arrancar. Ya confundió una vez.
4. No está en la lista de testers de *ese* canal (interna ≠ cerrada).
5. Play tarda en propagar — minutos a horas.

## Al reportar: separá lo verificado de lo que no

- ✅ Verificado en la fuente: "el backend anuncia `versionCode` 20", "firma OK".
- ⚠️ Sin verificar: "compila, pero **no lo probé en un dispositivo**".

**Compilar no es probar.** El crash al aceptar una carrera sin permiso de
ubicación (2026-08-06) compilaba perfecto y tumbaba la app en el celular.

## Antes de desplegar el BACKEND, arrancalo

El typecheck no detecta una ruta duplicada: Fastify explota al REGISTRARLAS, en
runtime. Registrar `GET /perfil` cuando `perfil.ts` ya lo tenía dejó el backend
entero en 502 — hasta `/health` (2026-08-06).

```bash
cd "d:/Personal Proyects/leadia"
npx tsc -p tsconfig.json --outDir dist
(PORT=3997 node --env-file=.env dist/index.js > /tmp/arranque.log 2>&1 &) ; sleep 15
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3997/health   # tiene que dar 200
```

Y antes de agregar una ruta, mirá si el path ya existe:
```bash
grep -rn "'/tu-ruta'" src/routes/*.ts
```

## Trampas que ya costaron una tarde

- **`startForeground` sin permiso de ubicación mata la app.** Con
  `foregroundServiceType="location"` y targetSdk 34+, Android lanza
  `SecurityException` si el permiso no está concedido. Protegerlo en
  `startForegroundService` NO sirve: ese arranque es asíncrono y devuelve bien;
  la excepción explota después, dentro del proceso del servicio.
- **Google Sign-In error 10.** Cuatro capas distintas, todas necesarias: el
  Web client ID en el código, el backend aceptando varias audiencias, la huella
  de **Play App Signing** (no la del keystore local — son certificados
  distintos), y recrear el contenedor del VPS (`docker restart` NO recarga el
  `.env`, hace falta `up -d --force-recreate`).
- **La comisión se configura en la BD, sin deploy** (`ConfigComision`). Ojo con
  `especificidad()`: una fila global (`tipo = null`) puntúa 0 y PIERDE contra
  las que tienen tipo. Para una campaña gratis hay que apagar las viejas con
  `hasta` y crear una por tipo.
- **La zona del rider sale del texto del distrito.** `departamentoDe()` parte
  por `", "` — un distrito sin coma cae en `'Lima'` por defecto y el rider no ve
  ninguna carrera de su ciudad. Guardá `"Tacna, Tacna"`, no `"Tacna"`.
