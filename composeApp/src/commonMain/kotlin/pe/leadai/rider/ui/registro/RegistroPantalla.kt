package pe.leadai.rider.ui.registro

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pe.leadai.rider.ui.comunes.BannerError

/**
 * Pantalla de alta de cuenta. Mismo estilo Brand Harmony que `LoginPantalla`
 * (fondo arena, card blanca centrada, banner de error) — a propósito NO reusa
 * el composable de Login: los campos son distintos (nombre + confirmar
 * contraseña) y esta pantalla no tiene el botón de Google ni el divisor "o"
 * (el registro con Google ya existe desde `LoginPantalla` — este formulario
 * es solo el alta por contraseña).
 *
 * [alExito] navega al alta de motorizado: una cuenta recién creada nunca tiene
 * perfil de rider todavía, así que no hace falta el chequeo que sí hace Login.
 */
@Composable
fun RegistroPantalla(
    alExito: () -> Unit,
    alVolverALogin: () -> Unit,
    viewModel: RegistroViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
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
                LogoRegistro()

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Crea tu cuenta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = viewModel::cambiarNombre,
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = estado.email,
                    onValueChange = viewModel::cambiarEmail,
                    label = { Text("Correo") },
                    singleLine = true,
                    enabled = !estado.cargando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = estado.password,
                    onValueChange = viewModel::cambiarPassword,
                    label = { Text("Contraseña") },
                    singleLine = true,
                    enabled = !estado.cargando,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                text = if (passwordVisible) "🙈" else "👁",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                var confirmarVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = estado.confirmarPassword,
                    onValueChange = viewModel::cambiarConfirmarPassword,
                    label = { Text("Confirmar contraseña") },
                    singleLine = true,
                    enabled = !estado.cargando,
                    visualTransformation = if (confirmarVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { confirmarVisible = !confirmarVisible }) {
                            Text(
                                text = if (confirmarVisible) "🙈" else "👁",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                val mensajeError = estado.error
                if (mensajeError != null) {
                    Spacer(Modifier.height(16.dp))
                    BannerError(mensajeError)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.registrar(alExito) },
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
                            Text("Creando cuenta…", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text("Crear cuenta", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = alVolverALogin, enabled = !estado.cargando) {
                    Text(
                        text = "¿Ya tienes cuenta? Inicia sesión",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoRegistro() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append("Lead")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append("AI")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(" Rider")
            }
        },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    )
}
