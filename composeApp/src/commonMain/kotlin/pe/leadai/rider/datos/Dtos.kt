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
 * Una carrera del POOL: puede ser el delivery de un negocio cliente, un
 * MANDADO (comprar en un negocio ajeno), una encomienda o un pasajero.
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
    val negocio: String,
    val direccion: String? = null,
    val totalCentavos: Long = 0,
    val km: Double? = null,
    val entregadoEn: String? = null,
)

/** `GET /motorizados/historial` → resumen de hoy + últimas entregas del rider. */
@Serializable
data class HistorialRiderResponseDto(
    val hoy: ResumenHoyRiderDto = ResumenHoyRiderDto(),
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
    val estado: String,
    val creadoEn: String,
)

/** `GET /motorizados/mi-perfil` → `{"perfil": null | PerfilMotorizadoDto}`. */
@Serializable
data class MiPerfilMotorizadoDto(
    val perfil: PerfilMotorizadoDto? = null,
)
