package pe.leadai.rider.datos

/**
 * Login por email/password o Google — ambos devuelven la misma forma de
 * sesión. Un login exitoso persiste la sesión en [sesion] automáticamente
 * (la UI no tiene que acordarse de llamar a `guardar` por separado).
 */
class AuthApi(private val api: ApiCliente, private val sesion: SesionRepositorio) {

    suspend fun login(email: String, password: String): Resultado<SesionGuardada> {
        val respuesta = api.post<LoginRequestDto, LoginResponseDto>(
            path = "/auth/login",
            body = LoginRequestDto(email = email, password = password),
        )
        return respuesta.aSesionGuardada()
    }

    suspend fun loginGoogle(idToken: String): Resultado<SesionGuardada> {
        val respuesta = api.post<LoginGoogleRequestDto, LoginResponseDto>(
            path = "/auth/google",
            body = LoginGoogleRequestDto(idToken = idToken),
        )
        return respuesta.aSesionGuardada()
    }

    /**
     * `POST /auth/registro` (Fase B.5, Task T1): crea una cuenta nueva y
     * devuelve la MISMA forma de sesión que [login] (ver
     * `leadia/src/routes/auth.ts`: `registrar()` reusa `sesionDe()`) — reusa
     * [aSesionGuardada] tal cual, sin duplicar el mapeo. Un usuario recién
     * registrado siempre tiene `empresas: []` (el alta segmentada crea el
     * negocio en un paso aparte, T2), así que `tenantIdActivo` queda `null` —
     * `aSesionGuardada` ya lo resuelve con `firstOrNull()` sin necesitar un
     * caso especial acá.
     */
    suspend fun registrar(email: String, password: String, nombre: String): Resultado<SesionGuardada> {
        val respuesta = api.post<RegistroRequestDto, LoginResponseDto>(
            path = "/auth/registro",
            body = RegistroRequestDto(email = email, password = password, nombre = nombre),
        )
        return respuesta.aSesionGuardada()
    }

    private suspend fun Resultado<LoginResponseDto>.aSesionGuardada(): Resultado<SesionGuardada> =
        when (this) {
            is Resultado.Ok -> {
                val sesionGuardada = SesionGuardada(
                    token = valor.token,
                    usuarioNombre = valor.usuario.nombre,
                    usuarioEmail = valor.usuario.email,
                    empresas = valor.empresas,
                    tenantIdActivo = valor.empresas.firstOrNull()?.tenantId,
                )
                sesion.guardar(sesionGuardada)
                Resultado.Ok(sesionGuardada)
            }
            is Resultado.Error -> this
        }
}
