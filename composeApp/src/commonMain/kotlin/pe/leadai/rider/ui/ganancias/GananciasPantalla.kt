package pe.leadai.rider.ui.ganancias

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraEntregadaDto
import pe.leadai.rider.datos.DiaDeGananciasDto
import pe.leadai.rider.datos.HistorialRiderResponseDto
import pe.leadai.rider.datos.ResumenHoyRiderDto
import pe.leadai.rider.ui.carreras.etiquetaTipo
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Cuánto ganó el rider. Es la pantalla que lo mantiene motivado, así que el
 * número grande manda.
 *
 * Lo que se muestra es lo que GANÓ —monto menos comisión—, no lo que movió:
 * el adelanto de una encomienda con compra no es ingreso suyo y nunca entra
 * en estos totales.
 */
@Composable
fun GananciasPantalla(
    historial: HistorialRiderResponseDto?,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    if (historial == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando tus ganancias…", color = colores.tintaSecundaria)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "titulo") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Mis ganancias",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item(key = "hoy") {
            TotalDelDia(historial.hoy)
        }

        item(key = "grafico") {
            GraficoSemana(historial.porDia, historial.semana)
        }

        item(key = "mes") {
            ResumenDelMes(historial.mes)
        }

        val entregas = historial.carreras
        if (entregas.isNotEmpty()) {
            item(key = "titulo-viajes") {
                Text(
                    "Últimas carreras",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(entregas, key = { it.carreraId ?: it.pedidoId }) { entrega ->
                FilaEntrega(entrega)
            }
        }

        item(key = "fin") { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * El total del día — la "hero card" del diseño: verde claro con borde, el
 * monto en display, y los stats separados por una línea abajo.
 *
 * El verde es propio del diseño, no un rol de Material 3: comunica "esto es
 * plata que ya ganaste" sin confundirse con el amarillo de marca, que se usa
 * para acciones.
 */
@Composable
private fun TotalDelDia(hoy: ResumenHoyRiderDto) {
    val colores = ColoresJala.actuales

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 160.dp)
            .background(colores.gananciaFondo, RoundedCornerShape(24.dp))
            .border(1.dp, colores.gananciaBorde, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "TOTAL DEL DÍA 💸",
                style = MaterialTheme.typography.labelLarge,
                color = colores.gananciaTexto.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            // "S/" en título y el número en display: el diseño pide que el
            // símbolo pese menos que la cifra.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "S/",
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.gananciaTexto,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    solesSinSimbolo(hoy.totalCentavos),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colores.gananciaTexto,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Línea divisoria + los dos datos, como en el diseño.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colores.gananciaBorde),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DatoChico("Viajes completados", "${hoy.carreras} 🛵", Modifier.weight(1f))
            DatoChico("Distancia (km)", "${hoy.km} 🛣️", Modifier.weight(1f))
        }
    }
}

/** "845" → "8.45" sin el "S/", que va aparte en la hero card. */
private fun solesSinSimbolo(centavos: Long): String {
    val entero = centavos / 100
    val resto = (centavos % 100).toString().padStart(2, '0')
    return "$entero.$resto"
}

@Composable
private fun DatoChico(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Column(modifier) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = colores.gananciaTexto.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium,
            color = colores.gananciaTexto,
        )
    }
}

/**
 * Barras de los últimos 7 días.
 *
 * Dibujado con `Box` y alturas proporcionales en vez de una librería de
 * gráficos: son 7 barras, y sumar una dependencia entera para eso engorda el
 * APK sin ganar nada.
 */
@Composable
private fun GraficoSemana(porDia: List<DiaDeGananciasDto>, semana: ResumenHoyRiderDto) {
    val colores = ColoresJala.actuales
    // El día más alto define la escala; si todo está en cero, no se divide
    // por cero y las barras quedan vacías.
    val maximo = porDia.maxOfOrNull { it.totalCentavos }?.takeIf { it > 0 } ?: 1L

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Últimos 7 días",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                centavosASoles(semana.totalCentavos),
                style = MaterialTheme.typography.titleMedium,
                color = colores.espera,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            porDia.forEach { dia ->
                BarraDelDia(
                    dia = dia,
                    proporcion = dia.totalCentavos.toFloat() / maximo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarraDelDia(
    dia: DiaDeGananciasDto,
    proporcion: Float,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Mínimo visible aunque el día esté en cero: una columna
                // vacía se lee como "falta un dato", no como "no trabajé".
                .fillMaxHeight(proporcion.coerceAtLeast(0.04f))
                .background(
                    color = if (dia.totalCentavos > 0) colores.marcaAmarillo
                    else colores.tintaSecundaria.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            inicialDelDia(dia.fecha),
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResumenDelMes(mes: ResumenHoyRiderDto) {
    val colores = ColoresJala.actuales
    CardJala(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Este mes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DatoDelMes(centavosASoles(mes.totalCentavos), "ganado", Modifier.weight(1f))
            DatoDelMes("${mes.carreras}", "carreras", Modifier.weight(1f))
            DatoDelMes("${mes.km} km", "recorridos", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DatoDelMes(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = colores.tintaSecundaria)
    }
}

/** Una carrera ya entregada: qué era, a dónde, y cuánto quedó. */
@Composable
private fun FilaEntrega(entrega: CarreraEntregadaDto) {
    val colores = ColoresJala.actuales
    // Lo que quedó en el bolsillo del rider, no lo que cobró el cliente.
    val neto = entrega.totalCentavos - entrega.comisionCentavos

    CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    etiquetaTipo(entrega.tipo),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entrega.negocio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!entrega.direccion.isNullOrBlank()) {
                    Text(
                        entrega.direccion,
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    centavosASoles(neto),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entrega.km != null) {
                    Text(
                        "${entrega.km} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }
        }
    }
}

/**
 * "2026-08-06" → "M" (miércoles).
 *
 * Se calcula a mano con la congruencia de Zeller en vez de traer
 * kotlinx-datetime: es la única fecha que la app necesita formatear, y la
 * dependencia pesa más que estas diez líneas.
 */
internal fun inicialDelDia(fechaIso: String): String {
    val partes = fechaIso.split("-")
    if (partes.size != 3) return ""
    val anio = partes[0].toIntOrNull() ?: return ""
    val mes = partes[1].toIntOrNull() ?: return ""
    val dia = partes[2].toIntOrNull() ?: return ""

    // Zeller trata enero y febrero como meses 13 y 14 del año anterior.
    val m = if (mes < 3) mes + 12 else mes
    val a = if (mes < 3) anio - 1 else anio
    val k = a % 100
    val j = a / 100
    val h = (dia + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
    // h: 0=sábado, 1=domingo, 2=lunes…
    return listOf("S", "D", "L", "M", "M", "J", "V")[h]
}
