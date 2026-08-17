package pe.leadai.rider.ui.carreras

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La notificación del servicio tiene que decir lo que PASA.
 *
 * El rider veía un aviso permanente que decía "🛵 Carrera en curso · Rumbo a
 * Esperando carreras". Salía de pegar dos cosas correctas por separado: el
 * título estaba escrito fijo, y cuando no había carrera se mandaba el texto de
 * relleno "Esperando carreras" como si fuera un destino.
 *
 * El resultado no se entendía —"rumbo a esperando carreras"— y encima no se
 * podía borrar. Un aviso que miente es peor que ninguno: enseña a ignorarlos.
 *
 * Sin infraestructura de Android en commonTest, se verifica sobre el FUENTE.
 */
class NotificacionTurnoTest {

    private fun fuente(ruta: String): String {
        val rutas = listOf(ruta, "composeApp/$ruta")
        return rutas.firstNotNullOfOrNull { r ->
            runCatching { java.io.File(r).takeIf { it.exists() }?.readText() }.getOrNull()
        } ?: error("No se encontró $ruta")
    }

    @Test
    fun sin_carrera_no_se_manda_un_destino_de_relleno() {
        // "Esperando carreras" como destino producía "Rumbo a Esperando
        // carreras". La cadena VACÍA es la señal de "en turno, sin viaje".
        val texto = fuente("src/commonMain/kotlin/pe/leadai/rider/ui/carreras/CarrerasPantalla.kt")

        assertFalse(
            texto.contains("?: \"Esperando carreras\""),
            "sin carrera el destino va vacío: el servicio decide qué decir",
        )
    }

    @Test
    fun el_titulo_depende_de_si_hay_carrera() {
        // Estaba escrito fijo, así que decía "Carrera en curso" también cuando
        // el rider solo estaba esperando.
        val texto = fuente("src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ServicioCarreraActiva.kt")

        assertTrue(texto.contains("val enCarrera = destino.isNotBlank()"))
        assertTrue(
            texto.contains("if (enCarrera) \"🛵 Carrera en curso\" else \"🟢 Estás en turno\""),
            "el título tiene que distinguir los dos estados",
        )
    }

    @Test
    fun hay_una_salida_visible_desde_la_notificacion() {
        // No se puede deslizar para borrarla —si se pudiera, el rider la
        // sacaría sin querer y dejaría de aparecer en el radar sin enterarse—
        // así que tiene que haber un botón.
        val texto = fuente("src/androidMain/kotlin/pe/leadai/rider/ui/carreras/ServicioCarreraActiva.kt")

        assertTrue(texto.contains("\"Salir de turno\""))
        // Y solo sin carrera: cortar el rastreo a mitad de viaje dejaría al
        // cliente sin ver la moto que espera.
        assertTrue(texto.contains("if (!enCarrera)"))
    }
}
