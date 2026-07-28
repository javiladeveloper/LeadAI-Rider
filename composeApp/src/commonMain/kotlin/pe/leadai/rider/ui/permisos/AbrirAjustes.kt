package pe.leadai.rider.ui.permisos

/**
 * Abre la pantalla de ajustes DE ESTA APP, no la lista general: el rider no
 * tiene que buscarla entre cien apps instaladas.
 *
 * Desde Android 11 el permiso de ubicación "todo el tiempo" NO se puede pedir
 * por diálogo — el sistema obliga a que el usuario lo conceda a mano desde
 * Configuración. Sin ese permiso el foreground service del GPS
 * (`ServicioCarreraActiva`) no puede leer la ubicación con la pantalla
 * bloqueada, que es justo cuando el rider está manejando.
 *
 * `expect/actual` — mismo patrón que `iniciarServicioCarrera()`.
 */
expect fun abrirAjustesDeLaApp()

/**
 * Ajustes de optimización de batería. En Xiaomi, Oppo y compañía el sistema
 * mata la app "para ahorrar batería" en plena carrera; desde acá el rider
 * puede eximirla.
 */
expect fun abrirAjustesDeBateria()
