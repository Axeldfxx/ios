package app.murinelauncher.widget.smartspace

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextClock
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes
import java.util.Locale

/**
 * Simple digital clock + date widget for the first home screen.
 * Shows a large clock and a formatted date below it.
 */
class MurineClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var dateText: TextClock? = null
    private var hourView: TextClock? = null
    private var colonView: TextView? = null
    private var minuteView: TextClock? = null
    private var attached = false
    private var currentLocale: Locale? = null
    private var infinityTypeface: Typeface? = null
    private var originalTypeface: Typeface? = null
    private var infinityStyle = true

    /** Re-applies the clock style when the "Infinity X clock style" switch is toggled. */
    private val prefListener = LauncherPrefChangeListener { key ->
        if (key == LauncherPrefs.CLOCK_STYLE_INFINITYX.sharedPrefKey) {
            post { applyClockStyle(LauncherPrefs.CLOCK_STYLE_INFINITYX.get(context)) }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        hourView = findViewById(R.id.murine_clock_hour)
        colonView = findViewById(R.id.murine_clock_colon)
        minuteView = findViewById(R.id.murine_clock_minute)
        dateText = findViewById(R.id.murine_clock_date)
        originalTypeface = loadFont(R.font.murine_gantari_medium)
        infinityTypeface = loadFont(R.font.murine_clock_font)
        applyClockStyle(LauncherPrefs.CLOCK_STYLE_INFINITYX.get(context))

        // Uncomment to show alarms when clicked
        /*setOnClickListener {
            try {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // No clock app available
            }
        }*/
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateClockTextSize(w, h)
    }

    /**
     * Digits scale with the widget: limited by the row height (11/16 of the widget)
     * and by the width so "HHmm" never overflows.
     */
    private fun updateClockTextSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val byHeight = h * ROW_FRACTION / LINE_HEIGHT_FACTOR
        val byWidth = w * 0.9f / DIGITS_WIDTH_FACTOR
        val size = minOf(byHeight, byWidth)
        var changed = false
        listOfNotNull<TextView>(hourView, colonView, minuteView).forEach { tc ->
            if (kotlin.math.abs(tc.textSize - size) > 0.5f) {
                tc.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                changed = true
            }
        }
        // requestLayout() fired from inside a layout pass can be dropped by the widget host,
        // leaving the hour/minute boxes at their old width (uneven gap). Re-request next frame.
        if (changed) post { requestLayout() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!attached) {
            attached = true
            LauncherPrefs.get(context).addListener(prefListener, LauncherPrefs.CLOCK_STYLE_INFINITYX)
            val wanted = LauncherPrefs.CLOCK_STYLE_INFINITYX.get(context)
            if (wanted != infinityStyle) applyClockStyle(wanted)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (attached) {
            attached = false
            LauncherPrefs.get(context).removeListener(prefListener, LauncherPrefs.CLOCK_STYLE_INFINITYX)
        }
    }

    private fun loadFont(id: Int): Typeface? = try {
        resources.getFont(id)
    } catch (_: Exception) {
        null
    }

    /**
     * Switches between the Infinity X look (red hour, own font, short date) and the
     * original Murine look (all [R.attr.workspaceTextColor], stock font, long date).
     */
    private fun applyClockStyle(infinity: Boolean) {
        infinityStyle = infinity
        val textColor = if (infinity) {
            resources.getColor(R.color.murine_clock_text, context.theme)
        } else {
            Themes.getAttrColor(context, R.attr.workspaceTextColor)
        }
        val hourColor = if (infinity) {
            resources.getColor(R.color.murine_clock_hour, context.theme)
        } else {
            textColor
        }
        hourView?.setTextColor(hourColor)
        colonView?.setTextColor(textColor)
        minuteView?.setTextColor(textColor)
        dateText?.setTextColor(textColor)
        dateText?.alpha = if (infinity) 0.9f else 0.85f

        // Infinity X mode uses its own font file (res/font/murine_clock_font.ttf, weight comes
        // from the file itself); original mode keeps the stock Gantari. The date stays Gantari.
        (if (infinity) infinityTypeface else originalTypeface)?.let { tf ->
            listOfNotNull<TextView>(hourView, colonView, minuteView).forEach { it.typeface = tf }
        }

        // Date pattern depends on the style, so force it to be recomputed.
        currentLocale = null
        applyLocaleDateFormat()
        requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLocaleDateFormat()
    }

    /**
     * Sets the date [TextClock]'s pattern using the locale's best short weekday + short month + day-of-month format
     */
    private fun applyLocaleDateFormat() {
        val clock = dateText ?: return
        val locale: Locale = resources.configuration.locales.get(0)
        if (locale == currentLocale) return
        currentLocale = locale
        val skeleton = if (infinityStyle) DATE_SKELETON else DATE_SKELETON_LONG
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        clock.format12Hour = pattern
        clock.format24Hour = pattern
    }

    /**
     * Refreshes the displayed time (useful for showing widget preview).
     *
     * A detached TextClock gets no onVisibilityAggregated, which below Android 10
     * suppresses its own setText, so there the text is written directly instead.
     */
    fun refreshClockFormat() {
        if (Utilities.ATLEAST_Q || isAttachedToWindow) {
            listOfNotNull(hourView, minuteView, dateText).forEach { tc ->
                tc.format12Hour = tc.format12Hour
                tc.format24Hour = tc.format24Hour
            }
            return
        }
        val now = System.currentTimeMillis()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        listOfNotNull(hourView, minuteView, dateText).forEach { tc ->
            val pattern = (if (is24Hour) tc.format24Hour else tc.format12Hour)
                ?: tc.format24Hour ?: tc.format12Hour ?: return@forEach
            tc.text = android.text.format.DateFormat.format(pattern, now)
        }
    }

    /** Colors the colon, minutes and date. The hour keeps the red accent. */
    fun setTextColor(color: Int) {
        colonView?.setTextColor(color)
        minuteView?.setTextColor(color)
        dateText?.setTextColor(color)
    }

    /** Colors the hour (default: Infinity X red). */
    fun setAccentColor(color: Int) {
        hourView?.setTextColor(color)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    companion object {
        // Order-independent skeleton: short weekday + short month + day-of-month
        private const val DATE_SKELETON = "EEEMMMd"
        private const val DATE_SKELETON_LONG = "EEEEMMMMd"

        private const val ROW_FRACTION = 11f / 16f
        private const val LINE_HEIGHT_FACTOR = 1.1f
        private const val DIGITS_WIDTH_FACTOR = 3.1f
    }
}
