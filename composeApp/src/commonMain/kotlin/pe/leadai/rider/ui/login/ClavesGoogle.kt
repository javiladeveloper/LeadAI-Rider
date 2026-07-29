package pe.leadai.rider.ui.login

/**
 * Web client ID de la app `pe.leadai.rider` en el proyecto Firebase "leadai"
 * (GCP leadai-501802).
 *
 * Google Identity exige el Web client ID (no el Android client ID) como
 * `serverClientId` en `GetGoogleIdOption` — es el mismo client ID que el
 * backend usa para verificar el `idToken` en `POST /auth/google`.
 *
 * Sale de `composeApp/google-services.json` → el bloque `client[]` cuyo
 * `package_name` es `pe.leadai.rider` → `oauth_client[]` con
 * `client_type == 3` (3 = WEB).
 *
 * OJO: cada app de Firebase tiene el SUYO. Este valor NO es el de
 * `pe.leadai.app` (la app de negocios): usar el de la otra app hace que
 * Google rechace el token y el login falle sin un error claro. Si algún día
 * se regenera `google-services.json`, verificar que siga coincidiendo.
 */
const val GOOGLE_WEB_CLIENT_ID =
    "508875005300-6i1fbv85q7efn46p6j0fl8v3ls48je7g.apps.googleusercontent.com"
