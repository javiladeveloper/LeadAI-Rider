package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Fabrica el DataStore de preferencias de la app (un único archivo,
 * usado hoy solo para la sesión). La ubicación del archivo depende de la
 * plataforma (en Android, el `filesDir` del `Context`), de ahí el
 * expect/actual — ver `crearDataStore.android.kt` para la decisión de cómo
 * se obtiene el `Context` sin pasar por una Activity.
 */
expect fun crearDataStore(): DataStore<Preferences>
