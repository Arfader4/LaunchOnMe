package pl.rafal.contextlauncher.data.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.SizeF
import android.view.MotionEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.rafal.contextlauncher.layout.CardGrid
import java.text.Collator
import kotlin.math.ceil
import kotlin.math.min

// Widżet dostępny do dodania (pozycja w wyborze widżetów).
data class WidgetProvider(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val preview: ImageBitmap?,
)

// Wszystko, co dotyczy hostowania widżetów innych aplikacji, w jednym miejscu.
//
// Jak to działa w Androidzie:
// 1. Launcher jest "hostem" (AppWidgetHost) i prosi system o nowy identyfikator widżetu.
// 2. System musi zgodzić się na powiązanie tego id z konkretnym widżetem (bind).
// 3. Niektóre widżety mają ekran konfiguracji (np. wybór miasta w pogodzie).
// 4. Host tworzy widok (AppWidgetHostView), a aplikacja widżetu zdalnie go aktualizuje.
class LauncherWidgets private constructor(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host: AppWidgetHost = LauncherAppWidgetHost(context, HOST_ID)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    // Próba cichego powiązania. Zwraca false, gdy trzeba zapytać użytkownika o zgodę.
    fun tryBind(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)

    // Czy przed dodaniem trzeba pokazać ekran konfiguracji widżetu?
    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        // Od Androida 12 widżet może oznaczyć konfigurację jako opcjonalną.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        ) return false
        return true
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    // Widok widżetu. Kontekst aktywności, żeby widżet dostał właściwy motyw.
    fun createView(activityContext: Context, appWidgetId: Int): LauncherWidgetHostView =
        (host.createView(activityContext, appWidgetId, info(appWidgetId)) as LauncherWidgetHostView)
            .also { views[appWidgetId] = java.lang.ref.WeakReference(it) }

    // Ostatnio utworzone widoki widżetów (słabe referencje — nie trzymamy ich w pamięci dłużej niż ekran).
    private val views = mutableMapOf<Int, java.lang.ref.WeakReference<android.view.View>>()

    // Czy widżet ma przewijaną w pionie treść (lista albo siatka — w widżetach to ListView / GridView).
    // Stos widżetów po tym poznaje, że przesunięcie w pionie ma przewijać treść, a nie zmieniać widżet.
    fun scrollsVertically(appWidgetId: Int): Boolean {
        val root = views[appWidgetId]?.get() ?: return false
        fun check(view: android.view.View): Boolean =
            view is android.widget.AbsListView || // ListView / GridView (StackView i "przerzucane" zdjęcia się nie liczą)
                (view is android.widget.ScrollView && (view.canScrollVertically(1) || view.canScrollVertically(-1))) ||
                (view is android.view.ViewGroup && (0 until view.childCount).any { check(view.getChildAt(it)) })
        return check(root)
    }

    // Ile komórek naszej siatki 8×12 powinien zająć widżet.
    fun cellSize(info: AppWidgetProviderInfo): Pair<Int, Int> {
        // Od Androida 12 widżet podaje rozmiar w komórkach typowego launchera (4 kolumny),
        // a nasza komórka to pół takiej, więc mnożymy przez 2.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return (info.targetCellWidth * 2).coerceIn(2, CardGrid.COLUMNS) to
                (info.targetCellHeight * 2).coerceIn(2, CardGrid.ROWS)
        }
        // Starsze widżety podają minimalny rozmiar w pikselach.
        val density = context.resources.displayMetrics.density
        val w = ceil(info.minWidth / density / APPROX_CELL_DP).toInt().coerceIn(2, CardGrid.COLUMNS)
        val h = ceil(info.minHeight / density / APPROX_CELL_DP).toInt().coerceIn(2, CardGrid.ROWS)
        return w to h // "to" tworzy parę (Pair), jak krotka (w, h) w C#
    }

    // Lista widżetów do wyboru, z podglądami. Ładowanie grafik jest ciężkie, więc poza wątkiem UI.
    suspend fun loadProviders(): List<WidgetProvider> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val collator = Collator.getInstance()
        manager.installedProviders.map { info ->
            // runCatching ≈ try/catch zwracający wynik albo wartość domyślną.
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
            WidgetProvider(
                info = info,
                label = info.loadLabel(pm),
                appLabel = appLabel,
                preview = drawable?.toPreview(),
            )
        }.sortedWith(compareBy<WidgetProvider, String>(collator) { it.appLabel }.thenBy(collator) { it.label })
    }

    private fun Drawable.toPreview(maxSide: Int = 360): ImageBitmap {
        // Zmniejszamy z zachowaniem proporcji; -1 oznacza "rozmiar nieznany".
        val iw = intrinsicWidth.takeIf { it > 0 } ?: maxSide
        val ih = intrinsicHeight.takeIf { it > 0 } ?: maxSide
        val scale = min(1f, maxSide.toFloat() / maxOf(iw, ih))
        return toBitmap(
            (iw * scale).toInt().coerceAtLeast(1),
            (ih * scale).toInt().coerceAtLeast(1),
        ).asImageBitmap()
    }

    companion object {
        const val HOST_ID = 1024 // dowolna stała; system rozpoznaje po niej nasze widżety
        private const val APPROX_CELL_DP = 45f

        @Volatile
        private var instance: LauncherWidgets? = null

        fun get(context: Context): LauncherWidgets =
            instance ?: synchronized(this) {
                instance ?: LauncherWidgets(context.applicationContext).also { instance = it }
            }
    }
}

// Własny host, żeby system tworzył NASZĄ klasę widoku widżetu.
private class LauncherAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetHostView(context)
}

// Widok widżetu z dwoma dodatkami: blokadą dotyku w trybie edycji i aktualizacją rozmiaru.
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    // W trybie edycji widżet nie może łapać dotyku, bo wtedy nie dałoby się go przeciągnąć.
    var blockTouches = false

    private var lastSize: Pair<Float, Float>? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        blockTouches || super.onInterceptTouchEvent(ev)

    // false = "nie obsłużyłem", więc gest trafia do Compose, który przeciąga element.
    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (blockTouches) false else super.onTouchEvent(event)

    // Informujemy widżet, ile ma miejsca (w dp), żeby dobrał swój układ.
    fun updateSizeDp(widthDp: Float, heightDp: Float) {
        val size = widthDp to heightDp
        if (size == lastSize) return
        lastSize = size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }
}
