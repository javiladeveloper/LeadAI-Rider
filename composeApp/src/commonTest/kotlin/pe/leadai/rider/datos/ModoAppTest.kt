package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "modo_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

class ModoAppTest {

    @Test
    fun sin_elegir_el_modo_es_null() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        assertNull(repo.observar().first())
    }

    @Test
    fun el_modo_elegido_se_recuerda() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        repo.guardar(ModoRepositorio.CLIENTE)

        assertEquals(ModoRepositorio.CLIENTE, repo.observar().first())
    }

    @Test
    fun se_puede_cambiar_de_modo() = runTest {
        val repo = ModoRepositorio(dataStoreDePrueba())

        repo.guardar(ModoRepositorio.CLIENTE)
        repo.guardar(ModoRepositorio.CONDUCTOR)

        assertEquals(ModoRepositorio.CONDUCTOR, repo.observar().first())
    }
}
