package pe.leadai.rider.ui.alta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pe.leadai.rider.ui.comunes.BannerError
import pe.leadai.rider.ui.tema.TokensExtra

/**
 * Alta del motorizado: DNI (con validación en vivo que autocompleta el
 * nombre), ubicación (departamento → distrito del catálogo), teléfono y
 * placa opcional → `POST /motorizados/mi-perfil`. En esta app el usuario
 * SIEMPRE es rider, así que no hay tarjetas de "¿negocio o motorizado?": la
 * pantalla arranca directo en el formulario.
 *
 * La misma pantalla sirve al alta post-registro y al "✏️ Editar mi perfil"
 * de la sala de espera ([modoEditar] pre-llena el form con el perfil actual)
 * — el endpoint es un upsert. [alTerminar] no recibe parámetros: el llamador
 * siempre navega a la sala de espera del rider.
 */
@Composable
fun AltaRiderPantalla(
    alTerminar: () -> Unit,
    /** Modo "✏️ Editar mi perfil": form pre-llenado con el perfil actual y copy de edición. */
    modoEditar: Boolean = false,
    viewModel: AltaRiderViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(modoEditar) {
        if (modoEditar) viewModel.prepararEdicion() else viewModel.cargarDistritos()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (modoEditar) "Tu perfil de motorizado" else "Empecemos, motorizado 🛵",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (modoEditar) "Corrige lo que necesites y guarda" else "Un último paso antes de empezar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                FormularioMotorizado(
                    departamento = estado.departamento,
                    distrito = estado.distrito,
                    distritoLibre = estado.distritoLibre,
                    distritosDisponibles = distritosDe(estado.departamento, estado.distritosDisponibles),
                    dni = estado.dni,
                    validandoDni = estado.validandoDni,
                    nombreOficialDni = estado.nombreOficialDni,
                    telefono = estado.telefono,
                    placa = estado.placa,
                    habilitado = !estado.cargando,
                    onDepartamentoElegido = viewModel::elegirDepartamento,
                    onDistritoElegido = viewModel::elegirDistrito,
                    onDistritoLibreCambia = viewModel::cambiarDistritoLibre,
                    onDniCambia = viewModel::cambiarDni,
                    onTelefonoCambia = viewModel::cambiarTelefono,
                    onPlacaCambia = viewModel::cambiarPlaca,
                )

                val mensajeError = estado.error
                if (mensajeError != null) {
                    Spacer(Modifier.height(16.dp))
                    BannerError(mensajeError)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.guardar(alTerminar) },
                    enabled = !estado.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (estado.cargando) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Guardando…", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            if (modoEditar) "Guardar cambios" else "Empezar a repartir",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * El formulario del rider: ubicación (dropdown del catálogo real de
 * `GET /motorizados/distritos` para Lima), DNI, teléfono (teclado
 * telefónico) y placa (opcional). Dropdown simple (`Box` + `DropdownMenu`)
 * en vez de chips porque la lista de distritos es larga y viene del backend.
 */
@Composable
private fun FormularioMotorizado(
    departamento: String,
    distrito: String?,
    distritoLibre: String,
    distritosDisponibles: List<String>,
    dni: String,
    validandoDni: Boolean,
    nombreOficialDni: String?,
    telefono: String,
    placa: String,
    habilitado: Boolean,
    onDepartamentoElegido: (String) -> Unit,
    onDistritoElegido: (String) -> Unit,
    onDistritoLibreCambia: (String) -> Unit,
    onDniCambia: (String) -> Unit,
    onTelefonoCambia: (String) -> Unit,
    onPlacaCambia: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SelectorUbicacion(
            departamento = departamento,
            distrito = distrito,
            distritoLibre = distritoLibre,
            distritosDisponibles = distritosDisponibles,
            habilitado = habilitado,
            onDepartamentoElegido = onDepartamentoElegido,
            onDistritoElegido = onDistritoElegido,
            onDistritoLibreCambia = onDistritoLibreCambia,
        )

        Spacer(Modifier.height(16.dp))

        // DNI con validación EN VIVO (fila 16): a los 8 dígitos el backend
        // consulta MAXFIND y acá se muestra el nombre oficial — o "lo
        // verificaremos manualmente", sin bloquear (decisión 8).
        OutlinedTextField(
            value = dni,
            onValueChange = onDniCambia,
            label = { Text("DNI") },
            singleLine = true,
            enabled = habilitado,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                when {
                    validandoDni -> Text("Verificando tu DNI…")
                    nombreOficialDni == null -> Text("8 dígitos — para verificar tu identidad")
                    nombreOficialDni.isNotBlank() -> Text(
                        "✓ $nombreOficialDni",
                        color = TokensExtra.exito,
                    )
                    else -> Text("No lo encontramos — lo verificaremos manualmente")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = telefono,
            onValueChange = onTelefonoCambia,
            label = { Text("Teléfono") },
            singleLine = true,
            enabled = habilitado,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = placa,
            onValueChange = onPlacaCambia,
            label = { Text("Placa (opcional)") },
            supportingText = { Text("Si repartes en moto o auto — en bici no hace falta") },
            singleLine = true,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Ubicación estructurada (fila 15 / decisión 8): 🇵🇪 fijo → departamento →
 * distrito del catálogo (backend para Lima, local para el resto) con "Otro…"
 * como escape a texto libre. Hacia el backend viaja con la convención
 * "Distrito, Departamento" ([distritoParaBackend]).
 */
@Composable
private fun SelectorUbicacion(
    departamento: String,
    distrito: String?,
    distritoLibre: String,
    distritosDisponibles: List<String>,
    habilitado: Boolean,
    onDepartamentoElegido: (String) -> Unit,
    onDistritoElegido: (String) -> Unit,
    onDistritoLibreCambia: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "🇵🇪 Perú",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        CampoDropdown(
            etiqueta = "Departamento",
            valor = departamento,
            opciones = DEPARTAMENTOS_PERU,
            habilitado = habilitado,
            onElegir = onDepartamentoElegido,
        )

        Spacer(Modifier.height(16.dp))

        CampoDropdown(
            etiqueta = "Distrito",
            valor = distrito.orEmpty(),
            opciones = distritosDisponibles,
            habilitado = habilitado,
            onElegir = onDistritoElegido,
        )

        if (esOtroDistrito(distrito)) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = distritoLibre,
                onValueChange = onDistritoLibreCambia,
                label = { Text("Escribe tu distrito") },
                singleLine = true,
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Dropdown simple reutilizable (no `ExposedDropdownMenuBox` — su API varía
 * entre versiones de M3 y este proyecto fija `compose-multiplatform 1.7.3`):
 * `OutlinedTextField` de solo lectura + `Box.clickable` transparente encima
 * (captura el tap sin foco de teclado) que abre un `DropdownMenu` M3 normal.
 */
@Composable
private fun CampoDropdown(
    etiqueta: String,
    valor: String,
    opciones: List<String>,
    habilitado: Boolean,
    onElegir: (String) -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            label = { Text(etiqueta) },
            placeholder = { Text("Elige tu ${etiqueta.lowercase()}") },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(enabled = habilitado) { expandido = true },
        )
        DropdownMenu(
            expanded = expandido && habilitado,
            onDismissRequest = { expandido = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        onElegir(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}
