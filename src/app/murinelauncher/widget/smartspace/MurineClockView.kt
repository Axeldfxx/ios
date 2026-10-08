package app.murinelauncher.widget.smartspace

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextClock
import com.android.launcher3.R
import com.android.launcher3.Utilities
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
    private var minuteView: TextClock? = null
    private var attached = false
    private var currentLocale: Locale? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        hourView = findViewById(R.id.murine_clock_hour)
        minuteView = findViewById(R.id.murine_clock_minute)
        dateText = findViewById(R.id.murine_clock_date)
        applyLocaleDateFormat()

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
        listOfNotNull(hourView, minuteView).forEach { tc ->
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
            // Custom logic
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (attached) {
            attached = false
            // Custom logic
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLocaleDateFormat()
    }

    /**
     * Sets the date [TextClock]'s pattern using the locale's best weekday + month + day-of-month format
     */
    private fun applyLocaleDateFormat() {
        val clock = dateText ?: return
        val locale: Locale = resources.configuration.locales.get(0)
        if (locale == currentLocale) return
        currentLocale = locale
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, DATE_SKELETON)
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

    /** Colors the hour and date. Minutes keep the red accent. */
    fun setTextColor(color: Int) {
        hourView?.setTextColor(color)
        dateText?.setTextColor(color)
    }

    /** Colors the minutes (default: Infinity X red). */
    fun setAccentColor(color: Int) {
        minuteView?.setTextColor(color)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    companion object {
        // Order-independent skeleton: weekday + month + day-of-month
        private const val DATE_SKELETON = "EEEEMMMMd"

        private const val ROW_FRACTION = 11f / 16f
        private const val LINE_HEIGHT_FACTOR = 1.1f
        private const val DIGITS_WIDTH_FACTOR = 2.8f
    }
}
