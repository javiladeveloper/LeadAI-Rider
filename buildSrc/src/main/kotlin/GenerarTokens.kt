import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Convierte `design/jala-design-tokens.json` en Kotlin.
 *
 * POR QUÉ SE GENERA Y NO SE LEE EN RUNTIME: así el compilador verifica los
 * nombres —un token que no existe rompe el build, no el teléfono del rider— y
 * no hay que parsear JSON en cada arranque. La fuente única sigue siendo el
 * JSON; esto solo lo traduce.
 *
 * Corre solo antes de compilar (ver composeApp/build.gradle.kts), así que
 * editar el JSON y compilar es todo lo que hace falta.
 */
abstract class GenerarTokensTask : DefaultTask() {

    @get:InputFile
    abstract val archivoTokens: RegularFileProperty

    @get:OutputDirectory
    abstract val directorioSalida: DirectoryProperty

    @TaskAction
    fun generar() {
        @Suppress("UNCHECKED_CAST")
        val raiz = JsonSlurper().parse(archivoTokens.get().asFile) as Map<String, Any>

        val destino = directorioSalida.get().asFile
        destino.mkdirs()

        val paquete = File(destino, "pe/leadai/rider/ui/tema/generado").apply { mkdirs() }
        File(paquete, "TokensGenerados.kt").writeText(construirArchivo(raiz))

        logger.lifecycle("Tokens de diseño generados desde ${archivoTokens.get().asFile.name}")
    }

    /**
     * Los `$comment` del JSON son documentación para quien lo edita, no
     * tokens. Se filtran acá para que el resto del generador no tenga que
     * preguntarse por ellos en cada bucle.
     */
    private fun sinComentarios(m: Map<*, *>): Map<String, Any> =
        m.entries
            .filter { (k, _) -> (k as? String)?.startsWith("$") == false }
            .associate { (k, v) -> k as String to v as Any }

    @Suppress("UNCHECKED_CAST")
    private fun construirArchivo(raiz: Map<String, Any>): String {
        val colores = sinComentarios(raiz["colores"] as Map<*, *>)
            .mapValues { (_, v) -> sinComentarios(v as Map<*, *>).mapValues { it.value as String } }
        val marca = sinComentarios(raiz["coloresMarca"] as Map<*, *>)
            .mapValues { (_, v) -> sinComentarios(v as Map<*, *>).mapValues { it.value as String } }
        val formas = raiz["formas"] as Map<String, Any>
        val espaciado = raiz["espaciado"] as Map<String, Any>
        val tipografia = raiz["tipografia"] as Map<String, Any>
        val app = raiz["app"] as Map<String, Any>

        return buildString {
            appendLine("package pe.leadai.rider.ui.tema.generado")
            appendLine()
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine("import androidx.compose.ui.unit.sp")
            appendLine()
            appendLine("// ARCHIVO GENERADO — NO EDITAR A MANO.")
            appendLine("// Sale de design/jala-design-tokens.json vía la tarea `generarTokens`.")
            appendLine("// Para cambiar un valor, editá el JSON y recompilá.")
            appendLine()

            appendLine("/** Colores del esquema Material 3, por modo. */")
            appendLine("object ColoresTokens {")
            for ((modo, valores) in colores) {
                if (modo.startsWith("$")) continue
                appendLine("    object ${modo.replaceFirstChar { it.uppercase() }} {")
                for ((clave, hex) in valores) {
                    if (clave.startsWith("$")) continue
                    appendLine("        val $clave = Color(0x${normalizarHex(hex)})")
                }
                appendLine("    }")
            }
            appendLine("}")
            appendLine()

            appendLine("/** Colores de marca sin slot en Material 3. */")
            appendLine("object MarcaTokens {")
            for ((modo, valores) in marca) {
                if (modo.startsWith("$")) continue
                appendLine("    object ${modo.replaceFirstChar { it.uppercase() }} {")
                for ((clave, hex) in valores) {
                    if (clave.startsWith("$")) continue
                    appendLine("        val $clave = Color(0x${normalizarHex(hex)})")
                }
                appendLine("    }")
            }
            appendLine("}")
            appendLine()

            appendLine("/** Radios de esquina, en dp. */")
            appendLine("object FormaTokens {")
            for ((clave, valor) in formas) {
                if (clave.startsWith("$")) continue
                appendLine("    val $clave = ${valor}.dp")
            }
            appendLine("}")
            appendLine()

            appendLine("/** Espaciado y alturas de toque, en dp. */")
            appendLine("object EspacioTokens {")
            for ((clave, valor) in espaciado) {
                if (clave.startsWith("$")) continue
                appendLine("    val $clave = ${valor}.dp")
            }
            appendLine("}")
            appendLine()

            val escala = tipografia["escala"] as Map<String, Map<String, Any>>
            appendLine("/** Escala tipográfica: tamaño, peso, interlineado y tracking. */")
            appendLine("object TipografiaTokens {")
            appendLine("    const val familia = \"${tipografia["familia"]}\"")
            for ((nivel, props) in escala) {
                appendLine("    object ${nivel.replaceFirstChar { it.uppercase() }} {")
                appendLine("        val size = ${props["size"]}.sp")
                appendLine("        val weight = ${props["weight"]}")
                appendLine("        val lineHeight = ${props["lineHeight"]}.sp")
                appendLine("        val letterSpacing = ${props["letterSpacing"]}.sp")
                appendLine("    }")
            }
            appendLine("}")
            appendLine()

            appendLine("/** Configuración de comportamiento (URLs, ritmos de refresco). */")
            appendLine("object AppTokens {")
            for ((clave, valor) in app) {
                if (clave.startsWith("$")) continue
                when (valor) {
                    is String -> appendLine("    const val $clave = \"$valor\"")
                    else -> appendLine("    const val $clave = ${valor}L")
                }
            }
            appendLine("}")
        }
    }

    /** "#FDBF35" → "FFFDBF35" (ARGB con alfa opaco, como espera Compose). */
    private fun normalizarHex(hex: String): String =
        "FF" + hex.removePrefix("#").uppercase()
}
