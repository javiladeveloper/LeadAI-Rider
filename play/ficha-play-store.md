# Ficha de Play Store — Light Drive

Todo listo para copiar y pegar en Play Console.
**Crecer → Presencia en Play Store → Ficha principal de Play Store**

> El `applicationId` sigue siendo `pe.leadai.rider` y NO se toca: cambiarlo
> crea una app nueva en Play, sin la firma ni los testers. Solo cambia el
> nombre visible.

---

## Nombre de la aplicación (máx. 30)

```
Light Drive
```

## Descripción breve (máx. 80)

```
Moto, encomiendas y delivery en Tacna. Vos ponés el precio.
```
*(58 caracteres)*

## Descripción completa (máx. 4000)

```
Light Drive conecta a quien necesita algo con el motorizado que lo puede
hacer. En Tacna, al toque.

QUÉ PODÉS PEDIR

🚕 Pasajero — que te lleven a donde vas.
🍔 Delivery — que te traigan comida del local que quieras.
📦 Envío — que lleven o traigan un paquete.

VOS PONÉS EL PRECIO

Acá no hay tarifa impuesta. La app sugiere un monto según la distancia, pero
lo podés cambiar: ofrecés lo que te parece justo y lo acordás con el
motorizado. El pago es en efectivo, entre ustedes dos.

SEGUÍ TU VIAJE

Mirá en el mapa dónde viene tu motorizado, en vivo. Escribile por WhatsApp o
llamalo si necesitás darle una indicación — el timbre que no anda, el color de
la casa, si va con ají o sin ají.

MOTORIZADOS VERIFICADOS

Antes de tomar carreras, cada motorizado pasa por una verificación de
identidad: documento, foto y licencia de conducir, revisados por una persona.

¿SOS MOTORIZADO?

La misma app. Tocás "Quiero manejar" y empezás a recibir carreras de tu zona.
Ves cuánto vas a ganar ANTES de aceptar, sin sorpresas ni descuentos raros.
Tus ganancias del día, la semana y el mes, siempre a la vista.

Hecho en Tacna.
```

---

## Gráficos

Están en `play/graficos/`:

| Archivo | Dónde va |
|---|---|
| `icono-512.png` | Ícono de la app (512×512) |
| `destacado-1024x500.png` | Gráfico destacado |
| `captura-1-pedir.png` | Captura de teléfono |
| `captura-2-viajes.png` | Captura de teléfono |
| `captura-3-perfil.png` | Captura de teléfono |

Play pide **mínimo 2** capturas de teléfono; van 3.

---

## Las otras tareas de Play

Estas son formularios legales — **las tiene que responder el titular de la
cuenta**, no se pueden automatizar. Acá va la respuesta correcta para esta app,
para no dudar al completarlas:

### Política de privacidad
Necesita una URL pública. Ver `play/politica-privacidad.md` — hay que
publicarla (GitHub Pages sirve) y pegar el link.

### Datos de inicio de sesión
La app exige cuenta. Hay que darle al revisor de Google un usuario de prueba
**ya verificado**, o no va a poder ver el flujo del motorizado.

### Anuncios
**No**, la app no tiene publicidad.

### Clasificación de contenido
Categoría: **Utilidad / Productividad / Comunicación**. Sin violencia, sin
contenido sexual, sin apuestas, sin lenguaje ofensivo, sin sustancias.
Comparte ubicación: **sí**. Permite comunicación entre usuarios: **sí**
(WhatsApp y llamada).

### Audiencia objetivo
**18 años en adelante.** No está dirigida a menores.

### Seguridad de los datos
Lo que la app recolecta hoy:

| Dato | Se recolecta | Se comparte | Para qué |
|---|---|---|---|
| Nombre | Sí | Sí, con la otra parte | Identificar a quién le pedís / llevás |
| Email | Sí | No | Cuenta |
| Teléfono | Sí | Sí, con la otra parte | Coordinar la entrega |
| Ubicación precisa | Sí | Sí, mientras hay carrera | Mostrar la moto en el mapa |
| Documento de identidad | Sí | No | Verificación de identidad |
| Fotos (documentos) | Sí | No | Verificación de identidad |

Todo viaja **cifrado (HTTPS)**. El usuario **puede pedir que se borre su
cuenta**. La ubicación se comparte **solo mientras hay una carrera en curso**.

### Aplicaciones gubernamentales
**No.**
