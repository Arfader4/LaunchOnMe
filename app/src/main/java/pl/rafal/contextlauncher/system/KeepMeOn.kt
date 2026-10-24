package pl.rafal.contextlauncher.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.ui.MotionDurationScale
import pl.rafal.contextlauncher.MainActivity
import pl.rafal.contextlauncher.data.AppPrefs

// 1.11.0: KeepMeOn — tryb oszczędzania energii. To zwykły OnMode (karta, reguła „bateria poniżej X%”, motyw AMOLED,
// oszczędne ustawienia telefonu), a gdy jest aktywny, launcher dodatkowo: wyłącza animacje, chowa tapetę,
// wstrzymuje widżety i (opcjonalnie) blokuje zmianę trybu do odblokowania odciskiem palca / PIN-em.
// Systemowego oszczędzania baterii aplikacja sama nie włączy (to wymaga uprawnienia z adb) — tylko przypomina.
object KeepMeOn {
    // Intencja do launchera „przełącz na tryb X”, gdy kafelek albo skrót trafią na blokadę (pytanie o odcisk
    // zadaje launcher — kafelek i niewidoczna aktywność skrótu nie mają gdzie go pokazać).
    const val EXTRA_UNLOCK_MODE = "pl.rafal.contextlauncher.UNLOCK_MODE"

    // Czy wyjście z aktywnego trybu do `targetId` wymaga odblokowania.
    fun isLocked(prefs: AppPrefs, activeId: Long?, targetId: Long): Boolean {
        val id = prefs.keepMeOnModeId.value
        return prefs.keepMeOnLock.value && id != -1L && activeId == id && targetId != id
    }

    fun unlockIntent(context: Context, modeId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_UNLOCK_MODE, modeId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    // Telefon bez blokady ekranu nie ma czym potwierdzić — wtedy launcher pyta zwykłym oknem „Wyjść z KeepMeOn?”.
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    // Systemowe okno odcisku palca / twarzy z zapasowym PIN-em, wzorem lub hasłem (BiometricPrompt z Androida,
    // bez dodatkowej biblioteki). onResult(true) = potwierdzone, false = anulowane albo błąd.
    // signal.cancel() zamyka okno (np. gdy ekran launchera znika przy obrocie).
    fun authenticate(context: Context, title: String, subtitle: String, signal: CancellationSignal, onResult: (Boolean) -> Unit) {
        val builder = BiometricPrompt.Builder(context).setTitle(title).setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true) // Android 10: odcisk albo PIN / wzór
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
            // onAuthenticationFailed (nierozpoznany palec) — okno zostaje i pozwala spróbować ponownie, więc nic.
        }
        runCatching {
            builder.build().authenticate(signal, context.mainExecutor, callback)
        }.onFailure { onResult(false) }
    }
}

// Skala czasu animacji dla całego okna launchera (jak systemowe „Skala animacji”, ale tylko u nas):
// 0 = animacje kończą się od razu. Poza KeepMeOn oddajemy systemową skalę (Ułatwienia dostępu → „Usuń animacje”),
// bo własna skala zastępuje domyślną, która ją uwzględniała. MainActivity podpina ją pod Recomposer okna, więc obejmuje wszystkie
// animacje Compose naraz, także w oknach dialogowych (dziedziczą kontekst kompozycji okna).
object LauncherMotion : MotionDurationScale {
    @Volatile var off: Boolean = false
    override val scaleFactor: Float get() = if (off) 0f else android.animation.ValueAnimator.getDurationScale()
}
