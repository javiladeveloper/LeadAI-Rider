package pe.leadai.rider.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.AuthApi
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.ModoRepositorio
import pe.leadai.rider.datos.MonederoApi
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.datos.crearDataStore
import pe.leadai.rider.push.RegistroPushRepositorio
import pe.leadai.rider.ui.alta.AltaRiderViewModel
import pe.leadai.rider.ui.carreras.CarrerasViewModel
import pe.leadai.rider.ui.cliente.ClienteViewModel
import pe.leadai.rider.ui.comunes.AvisosGlobales
import pe.leadai.rider.ui.login.LoginViewModel
import pe.leadai.rider.ui.registro.RegistroViewModel

/**
 * Único módulo Koin de la app (commonMain: todo lo de acá es multiplataforma).
 * Se arranca desde `LeadAIRiderApp.onCreate` (androidMain) — nunca desde
 * `MainActivity`, para que DataStore/DI estén listos antes del primer frame.
 *
 * `ApiCliente` no recibe `engine` (queda `null`): en producción Ktor resuelve
 * el motor de la plataforma vía la dependencia declarada en
 * `androidMain`/`iosMain` (`ktor-client-okhttp` / `ktor-client-darwin`).
 */
val moduloApp = module {
    single { crearDataStore() }
    single { SesionRepositorio(get()) }
    single { ModoRepositorio(get()) }
    single { ApiCliente(sesion = get()) }
    single { AuthApi(get(), get()) }
    single { MotorizadosApi(get()) }
    single { MonederoApi(get()) }
    single { CarrerasClienteApi(get()) }
    single { RegistroPushRepositorio(get()) }
    single { AvisosGlobales() }

    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { RegistroViewModel(get()) }
    viewModel { AltaRiderViewModel(get()) }
    viewModel { CarrerasViewModel(get(), get(), monederoApi = get()) }
    viewModel { ClienteViewModel(get(), get()) }
}
