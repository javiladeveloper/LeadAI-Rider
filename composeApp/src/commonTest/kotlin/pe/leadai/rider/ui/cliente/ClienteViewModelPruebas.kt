package pe.leadai.rider.ui.cliente

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.PerfilApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.ui.carreras.UbicacionRider
import pe.leadai.rider.ui.comunes.AvisosGlobales

/**
 * Arma el `ClienteViewModel` para tests, con el GPS bajo control.
 *
 * El VM necesita cinco colaboradores y un DataStore; repetir eso en cada test
 * hace que agregar uno nuevo cueste más de lo que debería.
 */
object ClienteViewModelPruebas {

    private var contador = 0

    private fun dataStore(): DataStore<Preferences> {
        // Un archivo por VM: si dos tests comparten el DataStore, la sesión de
        // uno se filtra al otro y fallan según el orden en que corran.
        contador += 1
        val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/pruebas_cliente_$contador.preferences_pb").toPath()
        return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
    }

    private fun armar(
        engine: MockEngine,
        dispatcher: CoroutineDispatcher,
        ubicacion: UbicacionRider?,
    ): ClienteViewModel {
        val apiCliente = ApiCliente(sesion = SesionRepositorio(dataStore()), engine = engine)
        return ClienteViewModel(
            api = CarrerasClienteApi(apiCliente),
            avisos = AvisosGlobales(),
            motorizadosApi = MotorizadosApi(apiCliente),
            perfilApi = PerfilApi(apiCliente),
            dispatcher = dispatcher,
            obtenerUbicacion = { ubicacion },
            obtenerTokenPush = { null },
        )
    }

    /** Con GPS: el teléfono está en Tacna. */
    fun conGps(
        engine: MockEngine,
        dispatcher: CoroutineDispatcher,
        ubicacion: UbicacionRider = UbicacionRider(-17.99, -70.23),
    ): ClienteViewModel = armar(engine, dispatcher, ubicacion)

    /** Sin GPS: el emulador recién arrancado, que no da nada. */
    fun sinGps(engine: MockEngine, dispatcher: CoroutineDispatcher): ClienteViewModel =
        armar(engine, dispatcher, null)
}
