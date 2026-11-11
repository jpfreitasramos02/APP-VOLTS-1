package br.com.volts.driver

import android.app.Application
import androidx.room.Room
import br.com.volts.driver.data.Prefs
import br.com.volts.driver.data.VoltsDao
import br.com.volts.driver.data.VoltsDb

class VoltsApp : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val dao: VoltsDao by lazy {
        Room.databaseBuilder(this, VoltsDb::class.java, "volts.db").build().dao()
    }
}
