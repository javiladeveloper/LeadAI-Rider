package pe.leadai.rider

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.koin.core.context.startKoin
import pe.leadai.rider.datos.ContextoApp
import pe.leadai.rider.di.moduloApp
import pe.leadai.rider.push.CANAL_LLEGADA
import pe.leadai.rider.push.CANAL_NOTIFICACIONES_PEDIDOS

/**
 * `Application` custom: inicializa [ContextoApp.context] y arranca Koin en
 * `onCreate`, ANTES de que exista cualquier Activity. Así DataStore/DI no
 * dependen del ciclo de vida de `MainActivity` — cualquier pantalla puede
 * resolver dependencias desde el primer frame sin una carrera contra
 * `setContent`.
 *
 * Registrado en AndroidManifest.xml vía `android:name=".LeadAIRiderApp"`.
 */
class LeadAIRiderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // applicationContext (no `this` como Activity) para que crearDataStore()
        // (expect/actual, sin acceso a DI) tenga el Context sin retener nada
        // con ciclo de vida más corto. Ver ContextoApp.kt.
        ContextoApp.context = applicationContext
        startKoin {
            modules(moduloApp)
        }
        crearCanalNotificacionesCarreras()
    }

    /**
     * Canal de notificaciones (importancia alta, sonido default) para el push
     * "nueva carrera en tu zona" que manda el backend cuando un pedido pasa a
     * listo. El rider tiene que enterarse aunque no esté mirando la app: si el
     * aviso llega mudo, la carrera se la lleva otro.
     *
     * Un canal se crea UNA sola vez en la vida de la app — crearlo de nuevo
     * con la misma config es un no-op seguro según la API de Android, así que
     * no hace falta guardar un flag de "ya se creó".
     */
    private fun crearCanalNotificacionesCarreras() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL_NOTIFICACIONES_PEDIDOS,
            "Carreras",
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(canal)
        manager.createNotificationChannel(canalDeLlegada())
    }

    /**
     * "Tu motorizado llegó": el aviso que el cliente espera mirando el
     * teléfono.
     *
     * Va en un canal propio para que VIBRE y SUENE aunque los demás estén
     * silenciados. El patrón de vibración es largo y en tres pulsos a
     * propósito: uno corto se confunde con un WhatsApp y el rider termina
     * esperando en la puerta.
     */
    private fun canalDeLlegada(): NotificationChannel {
        val canal = NotificationChannel(
            CANAL_LLEGADA,
            "Tu motorizado llegó",
            NotificationManager.IMPORTANCE_HIGH,
        )
        canal.description = "Avisa cuando el motorizado está esperándote"
        canal.enableVibration(true)
        canal.vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        canal.enableLights(true)
        // Sonido de alarma, no de notificación: tiene que oírse en la calle.
        val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val atributos = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        canal.setSound(sonido, atributos)
        return canal
    }
}
