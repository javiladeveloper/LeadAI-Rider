package pe.leadai.rider.datos

import android.content.Context

/**
 * Decisión (Task 3): en vez de pasar el `Context` por constructor a través de
 * Koin/Activity (lo cual complica el grafo de DI y el expect/actual de
 * `crearDataStore`), se usa un holder estático mínimo inicializado UNA vez en
 * `MainActivity.onCreate` con `applicationContext`. Es el approach más simple
 * que evita fugas de memoria (guarda el Application context, no la Activity)
 * y no depende de androidx.startup, que sería sobreingeniería para un solo
 * uso (DataStore). Cualquier acceso a `crearDataStore()` antes de que
 * `MainActivity` inicialice esto es un error de programación (falla rápido
 * con `lateinit` no inicializado).
 */
object ContextoApp {
    lateinit var context: Context
}
