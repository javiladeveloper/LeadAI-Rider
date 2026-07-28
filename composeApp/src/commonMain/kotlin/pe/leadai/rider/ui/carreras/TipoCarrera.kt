package pe.leadai.rider.ui.carreras

import pe.leadai.rider.datos.CarreraDto

/**
 * Cómo se le habla al rider de cada tipo de carrera. Funciones PURAS (sin
 * Compose) para poder testearlas sin levantar UI.
 *
 * Un tipo desconocido nunca rompe la pantalla: el backend puede agregar tipos
 * antes de que la app se actualice en los teléfonos.
 */

/** La etiqueta que identifica el tipo en la card del pool. */
fun etiquetaTipo(tipo: String): String = when (tipo) {
    "pedido" -> "🍽️ Delivery"
    "mandado" -> "🛍️ Mandado"
    "encomienda" -> "📦 Encomienda"
    "pasajero" -> "🚕 Pasajero"
    else -> "🛵 Carrera"
}

/**
 * El título de la pantalla cuando la carrera está en curso: dice en qué tramo
 * va y usa las palabras del tipo — a un pasajero no se lo "recoge", se lo pasa
 * a buscar.
 */
fun tituloTramo(carrera: CarreraDto): String = when (carrera.tipo) {
    "pasajero" -> if (carrera.recogido) "🚕 Llevando al pasajero" else "🚕 Pasa a buscarlo"
    "mandado" -> if (carrera.recogido) "🛵 Llevando la compra" else "🛍️ Ve a comprar"
    "encomienda" -> if (carrera.recogido) "🛵 Llevando la encomienda" else "📦 Recoge la encomienda"
    else -> if (carrera.recogido) "🛵 Llevando el pedido" else "📦 Recoge en el local"
}

/**
 * Si esta carrera exige que el rider ADELANTE plata para comprar. Un mandado
 * sin monto declarado no cuenta: mostrar "llevas S/0" confunde más que ayuda.
 */
fun esMandado(carrera: CarreraDto): Boolean =
    carrera.tipo == "mandado" && (carrera.montoCompraEstimado ?: 0) > 0
