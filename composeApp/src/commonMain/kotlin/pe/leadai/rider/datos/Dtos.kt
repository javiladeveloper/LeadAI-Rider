package pe.leadai.rider.datos

import kotlinx.serialization.Serializable

/**
 * DTOs del contrato real del backend (`https://api.leadai-pe.com`) — la MISMA
 * API que consume la app de negocios. Aquí solo viven los que necesita el
 * rider: sesión, alta de motorizado, carreras, monedero, historial y push.
 *
 * El cliente Ktor usa `ignoreUnknownKeys = true`, así que los campos que el
 * backend manda de más (todo lo del lado del restaurante) se ignoran solos.
 */

@Serializable
data class UsuarioDto(
    val id: String,
    val email: String,
    val nombre: String,
)

@Serializable
data class EmpresaResumen(
    val tenantId: String,
    val nombre: String,
    val rol: String,
    /**
     * Ubicación declarada "Distrito, Departamento" (fila 15) — `null` si el
     * negocio aún no la registró. Default `null` también mantiene
     * compatible la sesión guardada en DataStore de versiones previas.
     */
    val distrito: String? = null,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val usuario: UsuarioDto,
    val empresas: List<EmpresaResumen> = emptyList(),
    val esSuperAdmin: Boolean = false,
)

@Serializable
data class ErrorResponseDto(
    val error: String,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class LoginGoogleRequestDto(
    val idToken: String,
)

/**
 * `POST /auth/registro`: `{email, password, nombre?}` → MISMO shape que
 * `/auth/login` (`LoginResponseDto`), porque el backend arma la sesión con
 * la misma función en ambos casos.
 *
 * `nombreEmpresa` (que el schema del backend acepta) NO se manda desde el
 * rider a propósito: acá nadie crea negocios. El usuario queda con
 * `empresas: []` y la app lo lleva directo al alta de motorizado.
 */
@Serializable
data class RegistroRequestDto(
    val email: String,
    val password: String,
    val nombre: String? = null,
)

@Serializable
data class AvanzarEstadoResponseDto(
    val ok: Boolean = false,
    /**
     * Solo al ACEPTAR: la carrera con los datos del cliente ya resueltos.
     *
     * Sin esto la app se quedaba con el objeto del feed, que no trae nombre
     * ni contacto: al aceptar se veía "Tu cliente" sin botones de WhatsApp
     * hasta la siguiente vuelta del polling, 15 segundos después.
     */
    val carrera: CarreraDto? = null,
)

/** `POST /dispositivos-push` (Task 7, backend): registra el token FCM del dispositivo. */
@Serializable
data class RegistrarDispositivoRequestDto(
    val token: String,
    val plataforma: String,
)

/** `DELETE /dispositivos-push` (Task 7, backend): da de baja el token FCM (logout). */
@Serializable
data class DesregistrarDispositivoRequestDto(
    val token: String,
)

@Serializable
data class DispositivoPushResponseDto(
    val ok: Boolean = false,
)

/** Un paquete de saldo que el rider puede comprar (un solo pago). */
@Serializable
data class PaqueteMonederoDto(
    val id: String,
    val soles: Int = 0,
    val centavos: Long = 0,
)

/** Un movimiento del monedero: recarga (+) o cobro de carrera (−). */
@Serializable
data class MovimientoMonederoDto(
    val tipo: String,
    val montoCentavos: Long = 0,
    val concepto: String = "",
    val creadoEn: String? = null,
)

/** `GET /motorizados/monedero` → saldo del rider y con qué cuenta. */
@Serializable
data class MonederoDto(
    val saldoCentavos: Long = 0,
    /** Cuántas carreras le alcanzan con el saldo actual. */
    val carrerasDisponibles: Int = 0,
    val costoCarreraCentavos: Long = 100,
    val movimientos: List<MovimientoMonederoDto> = emptyList(),
    val paquetes: List<PaqueteMonederoDto> = emptyList(),
)

/** `POST /motorizados/monedero/recargar` → saldo tras acreditar el paquete. */
@Serializable
data class RecargaResponseDto(
    val ok: Boolean = false,
    val saldoCentavos: Long = 0,
)

/**
 * Una carrera del POOL: puede ser el delivery de un negocio cliente, una
 * ENCOMIENDA (llevar o traer algo, comprándolo o solo transportándolo) o un
 * pasajero.
 *
 * Todos los campos nuevos llevan default: si el backend los omite, la app
 * sigue funcionando en vez de reventar la deserialización.
 */
@Serializable
data class CarreraDto(
    /** Identificador que usa la app. Es el id del Pedido, o el de la Carrera si no hay Pedido. */
    val pedidoId: String,
    /** Id real de la Carrera en el backend. */
    val carreraId: String? = null,
    /** `pedido` | `encomienda` | `pasajero`. */
    val tipo: String = "pedido",
    val negocio: String = "",
    val negocioDistrito: String? = null,
    /** De dónde sale: el local del negocio, o una dirección libre. */
    val origenTexto: String? = null,
    /** A dónde va. */
    val destinoTexto: String? = null,
    val direccion: String? = null,
    val totalCentavos: Long = 0,
    /**
     * El FLETE: lo que el rider gana por hacer la carrera. Sobre este monto
     * se calcula la comisión.
     */
    val montoOfrecido: Long = 0,
    /**
     * Solo en `encomienda` con compra: lo que cuesta lo que va a COMPRAR. Es
     * plata que el rider adelanta y recupera del cliente — NUNCA se suma al
     * flete, porque un total combinado se lee como una carrera muy rentable y
     * no lo es. Si viene null la encomienda es de solo transporte.
     */
    val montoCompraEstimado: Long? = null,
    val kmEstimado: Double? = null,
    /** Detalle del pedido: "combinado sin verduras", "caja mediana". */
    val notas: String = "",
    val creadoEn: String,
    /** Datos del CLIENTE — solo vienen en `miCarrera` (la aceptada), nunca en el feed abierto. */
    val clienteNombre: String? = null,
    val clienteContacto: String? = null,
    /** Distancia del rider al ORIGEN — null sin GPS fresco. */
    val kmAlNegocio: Double? = null,
    /** Dos tramos: `false` = va al origen a recoger; `true` = ya recogió, va al destino. */
    val recogido: Boolean = false,
    /**
     * Lo que le queda al rider TRAS la comisión.
     *
     * Lo calcula el backend, que es donde vive la config: el rider no debería
     * restar de memoria para decidir si le conviene. `null` si no vino.
     */
    val gananciaCentavos: Long? = null,
)

/** Resumen de HOY del rider (`GET /motorizados/historial`): carreras, km reales y total entregado. */
@Serializable
data class ResumenHoyRiderDto(
    val carreras: Int = 0,
    val km: Double = 0.0,
    val totalCentavos: Long = 0,
)

/** Una entrega pasada del rider, con sus km reales (odómetro de pings GPS). */
@Serializable
data class CarreraEntregadaDto(
    val pedidoId: String,
    val carreraId: String? = null,
    /** `pedido` | `encomienda` | `pasajero`. */
    val tipo: String = "pedido",
    /** Lo que se le descontó del monedero por esta carrera. */
    val comisionCentavos: Long = 0,
    val negocio: String,
    val direccion: String? = null,
    val totalCentavos: Long = 0,
    val km: Double? = null,
    val entregadoEn: String? = null,
)

/** Una barra del gráfico de ganancias: cuánto se hizo ese día. */
@Serializable
data class DiaDeGananciasDto(
    /** ISO corto: "2026-08-06". */
    val fecha: String = "",
    val totalCentavos: Long = 0,
    val carreras: Int = 0,
)

/**
 * `GET /motorizados/historial` → lo que el rider ganó, por período, más sus
 * últimas entregas.
 *
 * Los totales son lo que GANÓ (monto menos comisión), no lo que movió: el
 * adelanto de una encomienda con compra nunca entra acá.
 */
@Serializable
data class HistorialRiderResponseDto(
    val hoy: ResumenHoyRiderDto = ResumenHoyRiderDto(),
    val semana: ResumenHoyRiderDto = ResumenHoyRiderDto(),
    val mes: ResumenHoyRiderDto = ResumenHoyRiderDto(),
    /** Los últimos 7 días, para el gráfico de barras. Siempre 7, con ceros. */
    val porDia: List<DiaDeGananciasDto> = emptyList(),
    val carreras: List<CarreraEntregadaDto> = emptyList(),
)

/** `GET /motorizados/carreras` → disponibles de la zona + la carrera en curso del rider (si tiene). */
@Serializable
data class CarrerasResponseDto(
    val carreras: List<CarreraDto> = emptyList(),
    val miCarrera: CarreraDto? = null,
)

/** Respuesta de `POST /motorizados/validar-dni` (fila 16 — MAXFIND vía backend): `{encontrado, nombreCompleto}`. */
@Serializable
data class ValidarDniResponseDto(
    val encontrado: Boolean = false,
    val nombreCompleto: String? = null,
)

/** `GET /motorizados/distritos` (Fase B.5, Task T3-doble): catálogo de distritos que acepta el backend para el alta de motorizado. */
@Serializable
data class DistritosMotorizadosDto(
    val distritos: List<String> = emptyList(),
)

/**
 * Perfil de motorizado (rol PLATAFORMA, no membresía a un tenant — ver
 * ARQUITECTURA.md Fase E). Espejo literal de `GET|POST /motorizados/mi-perfil`.
 * `estado`: `pendiente|verificado|bloqueado`.
 */
@Serializable
data class PerfilMotorizadoDto(
    val id: String,
    val usuarioId: String,
    val distrito: String,
    val telefono: String? = null,
    val placa: String? = null,
    /** `moto` | `auto` — la sugerencia de monto depende del vehículo. */
    val tipoVehiculo: String = "moto",
    /** DNI del rider (fila 16) — puede ser null en perfiles de antes del campo. */
    val dni: String? = null,
    /**
     * Si el rider está EN TURNO.
     *
     * El backend solo le manda el push de carrera nueva a quien lo tiene en
     * `true`. Arranca en `false`, y como la app nunca lo cambiaba, ningún
     * rider recibía avisos.
     */
    val disponible: Boolean = false,
    val estado: String,
    val creadoEn: String,
)

/** `GET /motorizados/mi-perfil` → `{"perfil": null | PerfilMotorizadoDto}`. */
@Serializable
data class MiPerfilMotorizadoDto(
    val perfil: PerfilMotorizadoDto? = null,
)

// ── Modo CLIENTE: pedir una moto ────────────────────────────────────────

/** Un punto del mapa resuelto por el backend (GPS del cliente o geocodificado). */
@Serializable
data class UbicacionDto(
    val texto: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

/** `POST /carreras/sugerir` → cuánto conviene ofrecer, antes de crear nada. */
@Serializable
data class SugerenciaDto(
    val kmEstimado: Double? = null,
    val montoSugerido: Long = 0,
    val origen: UbicacionDto = UbicacionDto(),
    val destino: UbicacionDto = UbicacionDto(),
)

/** `POST /carreras` → la carrera recién pedida. */
@Serializable
data class CarreraCreadaDto(
    val ok: Boolean = false,
    val id: String = "",
    val montoSugerido: Long = 0,
    val montoOfrecido: Long = 0,
    val expiraEnMinutos: Int = 15,
)

/**
 * `GET /carreras/mia` → la carrera activa del cliente. Los campos `rider*`
 * llegan `null` mientras nadie la tomó.
 */
@Serializable
data class CarreraClienteDto(
    val id: String,
    val tipo: String = "pasajero",
    /** `disponible` | `aceptada` | `recogida` | `entregada` | `cancelada` | `expirada`. */
    val estado: String = "disponible",
    val origenTexto: String = "",
    val destinoTexto: String = "",
    /**
     * Los pines del recorrido, para dibujarlo mientras espera.
     *
     * Sin esto la pantalla de "buscando motorizado" quedaba vacía hasta que
     * llegara la primera oferta: el cliente ya pidió y lo único que veía era
     * un texto.
     */
    val origenLat: Double? = null,
    val origenLng: Double? = null,
    val destinoLat: Double? = null,
    val destinoLng: Double? = null,
    /** El FLETE: lo que el cliente paga por el servicio. */
    val montoOfrecido: Long = 0,
    /**
     * Solo cuando el rider tiene que comprar algo: lo que le devuelve el
     * cliente además del flete. NUNCA se suma al monto ofrecido.
     */
    val montoCompraEstimado: Long? = null,
    val kmEstimado: Double? = null,
    val notas: String = "",
    val recogido: Boolean = false,
    val creadoEn: String = "",
    /** Cuándo se cerró. Solo lo manda el historial; en la carrera activa es `null`. */
    val entregadoEn: String? = null,
    val expiraEn: String? = null,
    val riderNombre: String? = null,
    val riderTelefono: String? = null,
    val riderPlaca: String? = null,
    val riderVehiculo: String? = null,
)

/** `GET /carreras/mia` → `{"carrera": null | {...}}`. */
@Serializable
data class MiCarreraClienteDto(
    val carrera: CarreraClienteDto? = null,
)

/**
 * `GET /carreras/historial` → las carreras ya cerradas del cliente.
 *
 * Reusa [CarreraClienteDto]: el backend manda menos campos (sin datos del
 * rider — una carrera terminada ya no necesita a quién llamar) y los que
 * faltan caen en sus valores por defecto.
 */
@Serializable
data class HistorialClienteDto(
    val carreras: List<CarreraClienteDto> = emptyList(),
)

/**
 * `GET /mi-perfil` → los datos de la persona (cliente o rider).
 *
 * El celular vive ACÁ y no en cada pedido: antes el cliente lo escribía cada
 * vez y el rider a veces se quedaba sin a quién llamar.
 */
@Serializable
data class PerfilPersonaDto(
    val id: String = "",
    val email: String = "",
    val nombre: String? = null,
    val telefono: String? = null,
    /** "Mi casa": pre-llena el destino, que es el 80% de los pedidos. */
    val direccionHabitual: String? = null,
    val direccionLat: Double? = null,
    val direccionLng: Double? = null,
    val dni: String? = null,
    /** `dni` (peruano) | `ce` (carné de extranjería). */
    val tipoDocumento: String = "dni",
    /** El nombre según RENIEC, traído por MAXFIND. */
    val nombreOficial: String? = null,
    /** `sin_verificar` | `en_revision` | `verificado` | `rechazado`. */
    val estadoVerificacion: String = "sin_verificar",
)

/** Un documento subido para verificarse. */
@Serializable
data class DocumentoVerificacionDto(
    val id: String = "",
    /** `dni_frente` | `dni_dorso` | `selfie` | `brevete` | `tarjeta_propiedad` | `soat`. */
    val tipo: String = "",
    /** `pendiente` | `aprobado` | `rechazado`. */
    val estado: String = "pendiente",
    /** Por qué se rechazó — para que la persona sepa qué corregir. */
    val motivoRechazo: String? = null,
    val creadoEn: String = "",
)

/**
 * `GET /mi-perfil` completo. `faltantes` viene RESUELTO del backend: la app no
 * replica la regla de qué documentos son obligatorios.
 */
@Serializable
data class MiPerfilDto(
    val perfil: PerfilPersonaDto? = null,
    val documentos: List<DocumentoVerificacionDto> = emptyList(),
    val faltantes: List<String> = emptyList(),
)

/** `PUT /mi-perfil` → confirma lo guardado. */
@Serializable
data class GuardarPerfilResponseDto(
    val ok: Boolean = false,
    val perfil: PerfilPersonaDto? = null,
)

/** `POST /verificacion/documentos` → el documento subido y el estado global. */
@Serializable
data class SubirDocumentoResponseDto(
    val ok: Boolean = false,
    val documento: DocumentoVerificacionDto? = null,
    val estadoVerificacion: String = "sin_verificar",
)

/** Una dirección sugerida mientras el cliente escribe. */
@Serializable
data class SugerenciaDireccionDto(
    val texto: String = "",
    /**
     * La calle, el barrio y la ciudad: lo que separa esta opción de las
     * demás con el mismo nombre. Sin esto la lista mostraba cinco "Jose
     * Olaya" iguales y no había forma de elegir.
     */
    val detalle: String = "",
    /** "Supermercado", "Paradero"… vacío en una calle común. */
    val categoria: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

/** `GET /carreras/direcciones?q=` → hasta 5 coincidencias de SU ciudad. */
@Serializable
data class SugerenciasDireccionDto(
    val sugerencias: List<SugerenciaDireccionDto> = emptyList(),
)

/**
 * `GET /carreras/direccion-en?lat&lng` → qué dirección hay en ese punto.
 *
 * El "georreverse": al mover el pin en el mapa o tocar "mi ubicación", esto
 * convierte las coordenadas en algo que el rider pueda leer.
 */
@Serializable
data class DireccionEnPuntoDto(
    val direccion: String? = null,
)

/**
 * Quién es el rider que ofertó: lo que el cliente mira antes de elegir.
 *
 * `estrellas` llega `null` cuando todavía nadie lo calificó — la app muestra
 * "Nuevo", no 0, porque cero se lee como "pésimo" y no es lo mismo que "aún
 * no sabemos".
 */
@Serializable
data class RiderDeOfertaDto(
    val usuarioId: String = "",
    val nombre: String? = null,
    val fotoUrl: String? = null,
    val placa: String = "",
    val marcaModelo: String = "",
    val color: String = "",
    val tipoVehiculo: String = "moto",
    val estrellas: Double? = null,
    val totalCalificaciones: Int = 0,
    val viajesCompletados: Int = 0,
)

/** Lo que un rider ofrece por la carrera. */
@Serializable
data class OfertaDto(
    val id: String = "",
    val montoCentavos: Long = 0,
    /** Cuánto dice que tarda en llegar al punto de recojo. */
    val minutosLlegada: Int? = null,
    val creadoEn: String = "",
    val rider: RiderDeOfertaDto = RiderDeOfertaDto(),
)

/** `GET /carreras/:id/ofertas` → las propuestas vivas, de la más barata a la más cara. */
@Serializable
data class OfertasResponseDto(
    val montoOfrecido: Long = 0,
    val estado: String = "disponible",
    val ofertas: List<OfertaDto> = emptyList(),
)
