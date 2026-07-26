package pe.leadai.rider.ui.login

/**
 * Web client ID del proyecto GCP "leadai-501802" (Task B6, Google Sign-In).
 *
 * Google Identity exige el Web client ID (no el Android client ID) como
 * `serverClientId` en `GetGoogleIdOption` — es el mismo client ID que el
 * backend usa para verificar el `idToken` en `POST /auth/google`.
 *
 * Se leyó DIRECTO de `composeApp/google-services.json` →
 * `client[0].oauth_client[]` con `client_type == 3` (3 = WEB, ver
 * https://developers.google.com/android/reference/com/google/android/gms/common/api/internal/ClientSettings
 * o el schema de `google-services.json` de Firebase) — el archivo YA lo
 * trae, así que NO hizo falta un placeholder pegado a mano por Jonathan.
 */
const val GOOGLE_WEB_CLIENT_ID =
    "508875005300-p6fsb40ebjpdtij6r284d8ii0uje2btc.apps.googleusercontent.com"
