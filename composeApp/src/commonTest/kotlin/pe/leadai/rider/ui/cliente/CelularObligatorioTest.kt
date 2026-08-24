package pe.leadai.rider.ui.cliente

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No se puede pedir sin celular, en ninguno de los tres servicios.
 *
 * Es lo único que tiene el motorizado para coordinar: dónde esperar, qué
 * timbre tocar, "ya estoy afuera". Sin número termina dando vueltas en la
 * cuadra y cancelando.
 *
 * El backend también lo rechaza; avisar acá evita que el cliente llene todo
 * el formulario para recién enterarse al final.
 */
class CelularObligatorioTest {

    private fun fuente(): String {
        val ruta = "src/commonMain/kotlin/pe/leadai/rider/ui/cliente/ClienteViewModel.kt"
        return listOf(ruta, "composeApp/$ruta")
            .firstNotNullOfOrNull { r ->
                runCatching { java.io.File(r).takeIf { it.exists() }?.readText() }.getOrNull()
            } ?: error("No se encontró $ruta")
    }

    @Test
    fun pedir_valida_que_haya_celular() {
        val texto = fuente()
        val revisar = texto.substringAfter("fun revisarPrecio()").substringBefore("\n    fun ")

        assertTrue(
            revisar.contains("contacto.isBlank()"),
            "revisarPrecio tiene que cortar si no hay celular",
        )
    }

    @Test
    fun el_mensaje_dice_donde_cargarlo() {
        // "Falta tu celular" a secas deja al cliente buscando dónde ponerlo.
        val texto = fuente()
        assertTrue(
            texto.contains("MENSAJE_SIN_CELULAR"),
            "tiene que haber un mensaje propio para el celular faltante",
        )
        assertTrue(
            texto.substringAfter("MENSAJE_SIN_CELULAR =").take(120).contains("Perfil"),
            "el mensaje tiene que decir dónde cargarlo",
        )
    }

    @Test
    fun la_validacion_va_ANTES_de_calcular_el_precio() {
        // Si fuera después, el cliente ve el popup de precio y recién ahí se
        // entera de que no puede pedir.
        val texto = fuente()
        val revisar = texto.substringAfter("fun revisarPrecio()").substringBefore("\n    fun ")
        val corte = revisar.indexOf("contacto.isBlank()")
        val calculo = revisar.indexOf("ajustandoPrecio = true")

        assertTrue(corte in 1 until calculo, "el corte va antes del popup de precio")
    }
}
