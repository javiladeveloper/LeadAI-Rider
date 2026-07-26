package pe.leadai.rider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Espera una condición sobre un Flow en tests, con un WATCHDOG de tiempo REAL.
// Por qué no withTimeout a secas: dentro de runTest el reloj es virtual y el
// timeout vencería al instante mientras el I/O real (DataStore/MockEngine)
// sigue corriendo. Acá la espera (first) corre en el contexto del test —
// exactamente como la versión que pasaba — y el perro guardián vive en
// Dispatchers.Default con delay REAL: si la condición no llega en timeoutMs
// de reloj de pared, el test FALLA con un mensaje claro en vez de colgarse.
suspend fun <T> Flow<T>.esperarCondicion(
    timeoutMs: Long = 5_000,
    predicado: (T) -> Boolean,
): T = coroutineScope {
    val perroGuardian = launch(Dispatchers.Default) {
        delay(timeoutMs)
        throw AssertionError("esperarCondicion: la condición no se cumplió en ${timeoutMs}ms (reloj real)")
    }
    try {
        first(predicado)
    } finally {
        perroGuardian.cancel()
    }
}
