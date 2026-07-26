package pe.leadai.rider.ui.alta

/**
 * Catálogo LOCAL de distritos por departamento (decisión 8 + "los
 * departamentos tienen distritos eh", Jonathan 2026-07-23): los distritos
 * del área urbana principal de cada departamento — donde de verdad opera un
 * delivery — más "Otro…" como escape (el ubigeo completo con provincia
 * llega con las filas 15/16 del handoff; mientras tanto el backend acepta
 * el string libre "Distrito, Departamento").
 *
 * Lima NO está acá: su catálogo viene del backend (`GET
 * /motorizados/distritos`), la fuente que ya usaba el alta.
 */
internal val DISTRITOS_POR_DEPARTAMENTO: Map<String, List<String>> = mapOf(
    "Amazonas" to listOf("Chachapoyas", "Bagua", "Bagua Grande"),
    "Áncash" to listOf("Huaraz", "Independencia", "Chimbote", "Nuevo Chimbote"),
    "Apurímac" to listOf("Abancay", "Tamburco", "Andahuaylas"),
    "Arequipa" to listOf(
        "Arequipa (Cercado)", "Alto Selva Alegre", "Cayma", "Cerro Colorado",
        "Characato", "Jacobo Hunter", "José Luis Bustamante y Rivero",
        "Mariano Melgar", "Miraflores", "Paucarpata", "Sabandía", "Sachaca",
        "Socabaya", "Tiabaya", "Uchumayo", "Yanahuara", "Yura",
    ),
    "Ayacucho" to listOf(
        "Ayacucho", "San Juan Bautista", "Carmen Alto", "Jesús Nazareno",
        "Andrés Avelino Cáceres Dorregaray",
    ),
    "Cajamarca" to listOf("Cajamarca", "Baños del Inca"),
    "Callao" to listOf(
        "Callao", "Bellavista", "Carmen de la Legua Reynoso", "La Perla",
        "La Punta", "Ventanilla", "Mi Perú",
    ),
    "Cusco" to listOf(
        "Cusco", "San Jerónimo", "San Sebastián", "Santiago", "Wanchaq",
        "Saylla", "Poroy",
    ),
    "Huancavelica" to listOf("Huancavelica", "Ascensión"),
    "Huánuco" to listOf("Huánuco", "Amarilis", "Pillco Marca"),
    "Ica" to listOf(
        "Ica", "Parcona", "La Tinguiña", "Subtanjalla", "Pisco",
        "Chincha Alta", "Nazca",
    ),
    "Junín" to listOf(
        "Huancayo", "El Tambo", "Chilca", "Pilcomayo", "Huancán",
        "San Agustín de Cajas",
    ),
    "La Libertad" to listOf(
        "Trujillo", "El Porvenir", "Florencia de Mora", "Huanchaco",
        "La Esperanza", "Laredo", "Moche", "Salaverry", "Víctor Larco Herrera",
    ),
    "Lambayeque" to listOf(
        "Chiclayo", "José Leonardo Ortiz", "La Victoria", "Pimentel",
        "Monsefú", "Reque", "Pomalca", "Lambayeque",
    ),
    "Loreto" to listOf("Iquitos", "Belén", "Punchana", "San Juan Bautista"),
    "Madre de Dios" to listOf("Puerto Maldonado (Tambopata)", "Las Piedras"),
    "Moquegua" to listOf("Moquegua", "Samegua", "Ilo"),
    "Pasco" to listOf("Chaupimarca (Cerro de Pasco)", "Yanacancha"),
    "Piura" to listOf(
        "Piura", "Castilla", "Veintiséis de Octubre", "Catacaos",
        "Sullana", "Talara", "Paita",
    ),
    "Puno" to listOf("Puno", "Juliaca"),
    "San Martín" to listOf("Tarapoto", "Morales", "La Banda de Shilcayo", "Moyobamba"),
    "Tacna" to listOf(
        "Tacna", "Alto de la Alianza", "Ciudad Nueva", "Pocollay",
        "Coronel Gregorio Albarracín Lanchipa", "Calana",
    ),
    "Tumbes" to listOf("Tumbes", "Corrales", "La Cruz", "Zorritos"),
    "Ucayali" to listOf("Callería (Pucallpa)", "Yarinacocha", "Manantay", "Campoverde"),
)

/** La opción de escape de todos los catálogos — habilita el campo de texto libre. */
internal const val OTRO_DISTRITO = "Otro…"

/** ¿La opción elegida es el escape "Otro"? (el catálogo de Lima del backend usa "Otro" a secas.) */
internal fun esOtroDistrito(distrito: String?): Boolean =
    distrito != null && distrito.startsWith("Otro")

/** Opciones del dropdown de distrito para [departamento]: Lima usa [catalogoLima] (backend); el resto, el catálogo local + "Otro…". */
internal fun distritosDe(departamento: String, catalogoLima: List<String>): List<String> =
    if (departamento == "Lima") {
        catalogoLima
    } else {
        (DISTRITOS_POR_DEPARTAMENTO[departamento] ?: emptyList()) + OTRO_DISTRITO
    }
