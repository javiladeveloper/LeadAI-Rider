package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * DataStore real (createWithPath) apuntando a un archivo temporal por test —
 * no hay fake in-memory oficial para la variante multiplatform de
 * datastore-preferences-core, y usar el real contra un archivo temp es rápido
 * y evita acoplar el test a detalles de implementación internos. Se usa
 * `okio.FileSystem` (multiplatform) en vez de `java.io.File`/`kotlin.io.path`
 * para no atarse a la JVM.
 */
private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "sesion_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

class SesionRepositorioTest {

    @Test
    fun observar_devuelve_null_cuando_no_hay_sesion_guardada() = runTest {
        val repo = SesionRepositorio(dataStoreDePrueba())

        assertNull(repo.observar().first())
    }

    @Test
    fun guardar_y_observar_devuelve_la_misma_sesion() = runTest {
        val repo = SesionRepositorio(dataStoreDePrueba())
        val sesion = SesionGuardada(
            token = "hilo_u_abc123",
            usuarioNombre = "Guisella",
            usuarioEmail = "guisella@leadai-pe.com",
            empresas = listOf(EmpresaResumen(tenantId = "t1", nombre = "Pollería Doña Rosa", rol = "dueño")),
            tenantIdActivo = "t1",
        )

        repo.guardar(sesion)

        assertEquals(sesion, repo.observar().first())
    }

    @Test
    fun cerrar_borra_la_sesion() = runTest {
        val repo = SesionRepositorio(dataStoreDePrueba())
        repo.guardar(
            SesionGuardada(
                token = "hilo_u_abc123",
                usuarioNombre = "Guisella",
                usuarioEmail = "guisella@leadai-pe.com",
            ),
        )

        repo.cerrar()

        assertNull(repo.observar().first())
    }
}
