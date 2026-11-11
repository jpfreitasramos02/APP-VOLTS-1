package br.com.volts.driver

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.volts.driver.apps.KnownApps
import br.com.volts.driver.data.Offer
import br.com.volts.driver.data.Payment
import br.com.volts.driver.data.Shift
import br.com.volts.driver.ui.VoltsRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renderiza as telas do app em formato de tablet e salva em app/build/screenshots.
 * Os números do relatório são dados de exemplo inseridos pelo próprio teste.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun captureScreens() {
        val app = ApplicationProvider.getApplicationContext<VoltsApp>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        KnownApps.all.forEach { known ->
            shadowOf(app.packageManager).addResolveInfoForIntent(launcher, ResolveInfo().apply {
                nonLocalizedLabel = known.label
                activityInfo = ActivityInfo().apply {
                    packageName = known.pkg
                    name = "Main"
                    applicationInfo = ApplicationInfo().apply { packageName = known.pkg }
                }
            })
        }
        val now = System.currentTimeMillis()
        runBlocking {
            app.dao.insertShift(Shift(startTime = now - 5 * 3_600_000L, endTime = now - 60_000, distanceMeters = 142_300.0))
            listOf(1890L to 7.4, 2450L to 11.2, 1230L to 4.1, 3320L to 15.8, 990L to 3.0).forEachIndexed { i, (v, km) ->
                app.dao.insertOffer(Offer(time = now - i * 600_000L, pkg = "com.ubercab.driver", valueCents = v, km = km, minutes = 20))
            }
            listOf(1650L to 6.9, 2780L to 12.5, 1410L to 6.2).forEachIndexed { i, (v, km) ->
                app.dao.insertOffer(Offer(time = now - i * 900_000L, pkg = "com.app99.driver", valueCents = v, km = km, minutes = 18))
            }
            app.dao.insertPayment(Payment(time = now - 7_200_000, amountCents = 2450, method = Payment.PIX))
            app.dao.insertPayment(Payment(time = now - 3_600_000, amountCents = 1890, method = Payment.NFC))
        }
        app.prefs.update { it.copy(pixKey = "motorista@exemplo.com", pixName = "Motorista Exemplo", pixCity = "São Paulo") }

        rule.setContent { VoltsRoot() }
        shot("1-painel")

        rule.onNodeWithText("Cobrar").performClick()
        listOf("2", "3", "5", "0").forEach { rule.onNodeWithText(it).performClick() }
        shot("2-cobrar")

        rule.onNodeWithText("Relatório").performClick()
        shot("3-relatorio")

        rule.onNodeWithText("Ajustes").performClick()
        shot("4-ajustes")
    }

    private fun shot(name: String) {
        // dá tempo para o banco e a lista de apps carregarem fora da thread principal
        repeat(5) { Thread.sleep(150); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }
}
