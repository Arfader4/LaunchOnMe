package pl.rafal.contextlauncher

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import pl.rafal.contextlauncher.data.widgets.LauncherWidgets
import pl.rafal.contextlauncher.data.widgets.WidgetProvider
import pl.rafal.contextlauncher.ui.LauncherApp
import pl.rafal.contextlauncher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {

    // "by viewModels()" to delegat: ViewModel powstaje przy pierwszym użyciu i przeżywa obrót ekranu.
    private val viewModel: LauncherViewModel by viewModels()

    // "by lazy" ≈ Lazy<T> w C#: obiekt powstaje przy pierwszym odczycie.
    private val widgets by lazy { LauncherWidgets.get(this) }

    // Id widżetu w trakcie dodawania (między zgodą a konfiguracją). -1 = nic się nie dzieje.
    private var pendingWidgetId = NO_WIDGET

    // Nowy sposób na "uruchom ekran i poczekaj na wynik": rejestrujemy callback z góry.
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) configureOrPlaceWidget() else cancelPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING_WIDGET, NO_WIDGET) ?: NO_WIDGET
        enableEdgeToEdge()
        pl.rafal.contextlauncher.ui.LaunchOrigin.attach(window.decorView)
        setContent {
            // Motyw nakłada sam LauncherApp, bo zależy od aktywnego trybu.
            LauncherApp(
                viewModel = viewModel,
                widgets = widgets,
                onAddWidget = ::startAddingWidget,
            )
        }
    }

    // Widżety dostają aktualizacje tylko wtedy, gdy host "słucha", czyli gdy launcher jest widoczny.
    override fun onStart() {
        super.onStart()
        widgets.host.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.host.stopListening()
    }

    // Każde dotknięcie zapamiętujemy (bez przejmowania go): z tego miejsca "wyrośnie" otwierana aplikacja.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) pl.rafal.contextlauncher.ui.LaunchOrigin.touched(ev.rawX, ev.rawY)
        return super.dispatchTouchEvent(ev)
    }

    // Wracamy na ekran główny (np. po odinstalowaniu aplikacji), więc odświeżamy listę.
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    // Home naciśnięty, gdy launcher już jest na wierzchu.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onHomePressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_WIDGET, pendingWidgetId)
    }

    // --- Dodawanie widżetu: id → zgoda → (konfiguracja) → położenie na karcie ---

    private fun startAddingWidget(provider: WidgetProvider) {
        val id = widgets.allocateId()
        pendingWidgetId = id
        if (widgets.tryBind(id, provider.info)) {
            configureOrPlaceWidget()
        } else {
            // System pyta użytkownika: "Zezwolić aplikacji na tworzenie widżetów?"
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.info.profile)
            bindWidget.launch(intent)
        }
    }

    private fun configureOrPlaceWidget() {
        val id = pendingWidgetId
        val info = widgets.info(id)
        if (info != null && widgets.needsConfiguration(info)) {
            try {
                // Ekran konfiguracji widżetu (np. wybór miasta). Wynik wraca do onActivityResult.
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
            } catch (e: ActivityNotFoundException) {
                finishAddingWidget()
            } catch (e: SecurityException) {
                finishAddingWidget()
            }
        } else {
            finishAddingWidget()
        }
    }

    private fun finishAddingWidget() {
        if (pendingWidgetId != NO_WIDGET) viewModel.placeWidget(pendingWidgetId)
        pendingWidgetId = NO_WIDGET
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != NO_WIDGET) widgets.deleteId(pendingWidgetId)
        pendingWidgetId = NO_WIDGET
    }

    // Stary mechanizm wyników, ale startAppWidgetConfigureActivityForResult z niego korzysta.
    @Deprecated("Wymagane przez AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK) finishAddingWidget() else cancelPendingWidget()
        }
    }

    companion object {
        private const val NO_WIDGET = -1
        private const val REQUEST_CONFIGURE_WIDGET = 42
        private const val KEY_PENDING_WIDGET = "pending_widget"
    }
}
