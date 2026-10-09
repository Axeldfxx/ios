package app.murinelauncher.widget.smartspace

import android.content.Context
import com.android.launcher3.LauncherPrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Small weather helper for the clock widget ("25°C • Clear").
 *
 * Uses Open-Meteo (https://open-meteo.com, free, no API key, no account) and a location the user
 * typed in settings, so no GPS / location permission is needed. The only data sent is the city
 * name (once, when checking it) and the resulting coordinates. The result is cached in
 * [LauncherPrefs] and refreshed at most every 30 minutes.
 */
object ClockWeather {
    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private const val RETRY_MS = 2 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastAttempt = 0L

    @Volatile
    private var running = false

    /** A location found by [geocode]. [label] looks like "Tasikmalaya, ID". */
    data class Place(val label: String, val lat: String, val lon: String)

    /**
     * Looks up "City" or "City, CC" (CC = 2-letter country code). Blocking: call it from a
     * background thread. Returns null when nothing was found; throws on network errors.
     */
    fun geocode(query: String): Place? {
        val parts = query.split(",").map { it.trim() }
        val name = parts.firstOrNull().orEmpty()
        if (name.isEmpty()) return null
        val country = parts.getOrNull(1)?.takeIf { it.length == 2 }
        var url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(name, "UTF-8") + "&count=1&language=en&format=json"
        if (country != null) url += "&countryCode=" + country.uppercase(Locale.US)
        val first = JSONObject(httpGet(url)).optJSONArray("results")?.optJSONObject(0)
            ?: return null
        val code = first.optString("country_code", "")
        val label = first.optString("name", name) + if (code.isEmpty()) "" else ", $code"
        return Place(
            label,
            String.format(Locale.US, "%.4f", first.getDouble("latitude")),
            String.format(Locale.US, "%.4f", first.getDouble("longitude"))
        )
    }

    private fun unitOf(context: Context): String =
        if (LauncherPrefs.CLOCK_WEATHER_UNIT.get(context) == "f") "f" else "c"

    private fun cacheKey(context: Context): String =
        LauncherPrefs.CLOCK_WEATHER_PLACE.get(context) + "|" + unitOf(context)

    /** Cached line to show right now, or "" when weather is off, unset or not loaded yet. */
    fun currentText(context: Context): String {
        if (!LauncherPrefs.CLOCK_WEATHER_ENABLED.get(context)) return ""
        if (LauncherPrefs.CLOCK_WEATHER_PLACE.get(context).isEmpty()) return ""
        if (LauncherPrefs.CLOCK_WEATHER_CACHE_KEY.get(context) != cacheKey(context)) return ""
        return LauncherPrefs.CLOCK_WEATHER_TEXT.get(context)
    }

    /**
     * Fetches fresh data in the background when the cache is stale (or the location / unit
     * changed). [onUpdated] is called from the background thread after the cache was written.
     */
    fun refreshIfNeeded(context: Context, onUpdated: () -> Unit) {
        val app = context.applicationContext
        if (!LauncherPrefs.CLOCK_WEATHER_ENABLED.get(app)) return
        val place = LauncherPrefs.CLOCK_WEATHER_PLACE.get(app)
        val coords = place.split(",")
        if (coords.size != 2) return
        val unit = unitOf(app)
        val key = cacheKey(app)

        val now = System.currentTimeMillis()
        val sameKey = LauncherPrefs.CLOCK_WEATHER_CACHE_KEY.get(app) == key
        val fetchedAt = LauncherPrefs.CLOCK_WEATHER_TIME.get(app).toLongOrNull() ?: 0L
        val fresh = sameKey && now - fetchedAt in 0 until MAX_AGE_MS
        if (fresh) return
        if (running) return
        if (sameKey && now - lastAttempt < RETRY_MS) return

        running = true
        lastAttempt = now
        executor.execute {
            try {
                val weather = JSONObject(
                    httpGet(
                        "https://api.open-meteo.com/v1/forecast?latitude=${coords[0]}" +
                            "&longitude=${coords[1]}&current=temperature_2m,weather_code" +
                            "&temperature_unit=" + if (unit == "f") "fahrenheit" else "celsius"
                    )
                )
                val current = weather.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").roundToInt()
                val text = "$temp°${unit.uppercase(Locale.US)} • " +
                    describe(current.getInt("weather_code"))

                LauncherPrefs.get(app).put(
                    LauncherPrefs.CLOCK_WEATHER_CACHE_KEY to key,
                    LauncherPrefs.CLOCK_WEATHER_TEXT to text,
                    LauncherPrefs.CLOCK_WEATHER_TIME to now.toString(),
                )
                running = false
                onUpdated()
            } catch (_: Exception) {
                // No network / bad response: keep showing the cached text, retry later.
                running = false
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "MurineLauncher-ClockWeather")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** WMO weather interpretation codes -> short label. */
    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}
