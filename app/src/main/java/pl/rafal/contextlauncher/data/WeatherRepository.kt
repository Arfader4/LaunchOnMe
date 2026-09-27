package pl.rafal.contextlauncher.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.rafal.contextlauncher.R
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

data class DailyForecast(val date: LocalDate, val min: Int, val max: Int, val code: Int)

data class Weather(
    val temperature: Int,
    val code: Int,          // kod pogody WMO (0 = bezchmurnie, 61 = deszcz itd.)
    val isDay: Boolean,
    val place: String?,
    val daily: List<DailyForecast>,
    val fetchedAt: Long,
)

// Pogoda z darmowego Open-Meteo (bez konta i klucza). Ostatni wynik trzymamy w pamięci podręcznej,
// żeby widżet pokazywał coś od razu, a sieć odpytujemy najwyżej co 30 minut.
class WeatherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val _weather = MutableStateFlow(loadCached())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    suspend fun refreshIfStale(location: Location?, maxAgeMs: Long = 30 * 60_000L) {
        if (location == null) return
        val cached = _weather.value
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < maxAgeMs) return
        fetch(location)
    }

    private suspend fun fetch(location: Location) = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${location.latitude}&longitude=${location.longitude}" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                    "&timezone=auto&forecast_days=4",
            )
            // HttpURLConnection ≈ HttpWebRequest z .NET: najprostszy klient HTTP bez dodatkowych bibliotek.
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val place = placeName(location)
            val now = System.currentTimeMillis()
            prefs.edit().putString(KEY_BODY, body).putString(KEY_PLACE, place).putLong(KEY_TIME, now).apply()
            _weather.value = parse(body, place, now)
        }
        // Błąd sieci = zostaje poprzednia pogoda z pamięci podręcznej.
    }

    // Nazwa miejscowości z współrzędnych (Geocoder systemu; potrzebuje internetu, bywa niedostępny).
    @Suppress("DEPRECATION")
    private fun placeName(location: Location): String? = runCatching {
        Geocoder(context, Locale("pl")).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()?.locality
    }.getOrNull()

    private fun loadCached(): Weather? {
        val body = prefs.getString(KEY_BODY, null) ?: return null
        return runCatching { parse(body, prefs.getString(KEY_PLACE, null), prefs.getLong(KEY_TIME, 0)) }.getOrNull()
    }

    private fun parse(body: String, place: String?, fetchedAt: Long): Weather {
        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        // (0 until n).map ≈ Enumerable.Range(0, n).Select(...)
        val forecast = (0 until dates.length()).map { i ->
            DailyForecast(
                date = LocalDate.parse(dates.getString(i)),
                min = daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(),
                code = daily.getJSONArray("weather_code").getInt(i),
            )
        }
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            place = place,
            daily = forecast,
            fetchedAt = fetchedAt,
        )
    }

    private companion object {
        const val KEY_BODY = "body"
        const val KEY_PLACE = "place"
        const val KEY_TIME = "time"
    }
}

// Kody pogody WMO → polski opis i ikona.
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Bezchmurnie"
        1 -> "Przeważnie słonecznie"
        2 -> "Częściowe zachmurzenie"
        3 -> "Pochmurno"
        45, 48 -> "Mgła"
        in 51..57 -> "Mżawka"
        in 61..67 -> "Deszcz"
        in 71..77 -> "Śnieg"
        in 80..82 -> "Przelotne opady"
        85, 86 -> "Przelotny śnieg"
        in 95..99 -> "Burza"
        else -> "—"
    }

    @DrawableRes
    fun icon(code: Int, isDay: Boolean = true): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_weather_clear else R.drawable.ic_weather_night
        2 -> R.drawable.ic_weather_partly
        3 -> R.drawable.ic_weather_cloud
        45, 48 -> R.drawable.ic_weather_fog
        in 51..67, in 80..82 -> R.drawable.ic_weather_rain
        in 71..77, 85, 86 -> R.drawable.ic_weather_snow
        in 95..99 -> R.drawable.ic_weather_storm
        else -> R.drawable.ic_weather_cloud
    }
}
