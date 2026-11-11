package br.com.volts.driver.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.volts.driver.MainActivity
import br.com.volts.driver.R
import br.com.volts.driver.VoltsApp
import br.com.volts.driver.data.Shift
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Serviço em primeiro plano que mede a distância percorrida durante o turno. */
class ShiftService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = DistanceTracker()
    private val dao by lazy { (application as VoltsApp).dao }
    @Volatile private var shiftId: Long? = null
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        scope.launch {
            // Retoma o turno aberto se o serviço foi reiniciado pelo sistema.
            shiftId = dao.openShift()?.id ?: dao.insertShift(Shift(startTime = System.currentTimeMillis()))
        }
        if (!listening) {
            listening = true
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 5f, this, mainLooper)
            } catch (e: SecurityException) {
                stopTracking()
            } catch (e: IllegalArgumentException) {
                stopTracking() // aparelho sem GPS
            }
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val id = shiftId ?: return
        val meters = tracker.onFix(location.latitude, location.longitude, location.accuracy, location.time)
        if (meters > 0) scope.launch { dao.addDistance(id, meters) }
    }

    @Deprecated("Necessário em versões antigas do Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun stopTracking() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        listening = false
        shiftId = null
        scope.launch {
            dao.closeOpenShifts(System.currentTimeMillis())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Turno em andamento", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Turno em andamento")
            .setContentText("Medindo a distância percorrida")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "shift"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "br.com.volts.driver.STOP_SHIFT"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, ShiftService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, ShiftService::class.java).setAction(ACTION_STOP))
        }
    }
}
