package pl.rafal.contextlauncher.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Kropki powiadomień: system wysyła tu każde nowe i usunięte powiadomienie (gdy użytkownik da dostęp
// w Ustawieniach → Dostęp do powiadomień). Treści NIE czytamy — tylko nazwę pakietu, żeby narysować kropkę.
class NotificationDotsService : NotificationListenerService() {

    override fun onListenerConnected() = recompute()
    override fun onNotificationPosted(sbn: StatusBarNotification) = recompute()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = recompute()

    override fun onListenerDisconnected() {
        _packages.value = emptySet()
    }

    // Liczymy od nowa z pełnej listy: prościej i odporne na pogubione zdarzenia.
    private fun recompute() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        _packages.value = active
            .filter { sbn ->
                // Pomijamy stałe powiadomienia (muzyka, nawigacja, usługi w tle) i "podsumowania" grup.
                sbn.isClearable && !sbn.isOngoing && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .map { it.packageName }
            .toSet()
    }

    companion object {
        // Wspólny stan dla launchera (ten sam proces). StateFlow ≈ obserwowalna właściwość.
        private val _packages = MutableStateFlow<Set<String>>(emptySet())
        val packages: StateFlow<Set<String>> = _packages.asStateFlow()
    }
}
