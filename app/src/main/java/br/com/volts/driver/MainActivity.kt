package br.com.volts.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import br.com.volts.driver.ui.VoltsRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Como tela inicial do tablet, "voltar" não deve fechar o painel.
        onBackPressedDispatcher.addCallback(this) { }
        setContent { VoltsRoot() }
    }
}
