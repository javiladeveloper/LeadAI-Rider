package pe.leadai.rider.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import pe.leadai.rider.ui.comunes.BotonPrincipal
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Primera pantalla real de la app. Estética "Brand Harmony": fondo arena
 * (`colorScheme.background`), card blanca centrada con radius 16 y
 * `imePadding` para que el teclado no la tape, logo "LeadAI Rider" (Lead en tinta,
 * AI en teal), banner de error suave y botón primario full-width con spinner
 * integrado mientras `cargando`.
 *
 * [alExito] solo avisa que la sesión ya quedó guardada — la decisión de A
 * DÓNDE navegar vive en `ui/navegacion/Navegacion.kt`.
 */
@Composable
fun LoginPantalla(
    alExito: () -> Unit,
    alRegistrarse: () -> Unit = {},
    viewModel: LoginViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    // Deshabilita TODOS los controles (campos + ambos botones) mientras
    // cualquiera de los dos flujos de login esté en vuelo (ver LoginUiState).
    val algoCargando = estado.cargando || estado.cargandoGoogle

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
                LogoLeadAI()

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Ingresa a tu panel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = estado.email,
                    onValueChange = viewModel::cambiarEmail,
                    label = { Text("Correo") },
                    singleLine = true,
                    enabled = !algoCargando,
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
                    enabled = !algoCargando,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            // Toggle de visibilidad con emoji (sin agregar la dependencia
                            // material-icons-extended solo para un ícono).
                            Text(
                                text = if (passwordVisible) "🙈" else "👁",
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

                BotonPrincipal(
                    texto = "Entrar",
                    onClick = { viewModel.entrar(alExito) },
                    habilitado = !algoCargando,
                    cargando = estado.cargando,
                )

                Spacer(Modifier.height(20.dp))

                // Divisor "— o —" (Task B6): separa el login por contraseña
                // del alterno con Google, mismo patrón visual que cualquier
                // pantalla de login con proveedores múltiples.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  o  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))

                // Botón secundario outline "Continuar con Google" (Task B6):
                // ícono "G" simple con Text estilizado — nada de assets
                // externos (brief explícito). Credential Manager (androidMain)
                // resuelve el idToken; iOS es stub null hasta Fase D.
                OutlinedButton(
                    onClick = { viewModel.entrarConGoogle(alExito) },
                    enabled = !algoCargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (estado.cargandoGoogle) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Conectando…", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "G",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Continuar con Google", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Link real de alta in-app (Fase B.5, Task T1): reemplaza el
                // caption estático "Créala desde leadai-pe.com" — el registro
                // ahora vive DENTRO de la app, no en el sitio web.
                TextoConLinkRegistro(onClick = alRegistrarse, habilitado = !algoCargando)
            }
        }
    }
}

@Composable
private fun TextoConLinkRegistro(onClick: () -> Unit, habilitado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "¿No tienes cuenta? ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick, enabled = habilitado, contentPadding = PaddingValues(0.dp)) {
            Text(text = "Créala aquí", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * "Jala" con las flechas del logo: `»»` en amarillo de marca y el nombre en
 * carbón, igual que el logotipo.
 *
 * NO dice "LeadAI" a propósito — esa es la marca B2B que le vendemos a los
 * negocios, y no le dice nada a un motorizado ni a alguien pidiendo una moto
 * en Tacna. Tampoco dice "Rider": la misma app la usan los clientes.
 */
@Composable
private fun LogoLeadAI() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = ColoresJala.actuales.marcaAmarillo)) {
                append("»» ")
            }
            withStyle(SpanStyle(color = ColoresJala.actuales.marcaCarbon)) {
                append("Jala")
            }
        },
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    )
}
