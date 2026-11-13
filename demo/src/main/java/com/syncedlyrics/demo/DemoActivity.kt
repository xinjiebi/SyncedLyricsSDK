package com.syncedlyrics.demo

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.GridLayout
import android.graphics.drawable.GradientDrawable
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.syncedlyrics.sdk.LyricLine
import com.syncedlyrics.sdk.LyricWord
import com.syncedlyrics.sdk.LyricStyle
import com.syncedlyrics.sdk.EmptyLyricsCoverStyle
import com.syncedlyrics.sdk.BackgroundStyle
import com.syncedlyrics.sdk.SyncedLyricsView

class DemoActivity : Activity() {
    private val duration = 68000L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lyrics: SyncedLyricsView
    private lateinit var seek: SeekBar
    private lateinit var timeLabel: TextView
    private lateinit var playButton: ImageButton
    private var position = 0L
    private var started = 0L
    private var playing = true
    private var dragging = false
    private var hasCover = true
    private var previewEmptyLyrics = false
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun currentPosition() = (position + if (playing) SystemClock.elapsedRealtime() - started else 0L).coerceAtMost(duration)
    private val tick = object : Runnable {
        override fun run() {
            var now = currentPosition()
            if (playing && now >= duration) {
                val clock = SystemClock.elapsedRealtime()
                position = (position + (clock - started)) % duration
                started = clock
                lyrics.setPlaybackState(position, true)
                now = position
            }
            if (!dragging) seek.progress = now.toInt()
            timeLabel.text = "${format(now)} / ${format(duration)}"
            handler.postDelayed(this, 100)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        started = SystemClock.elapsedRealtime()
        if (savedInstanceState != null) {
            position = savedInstanceState.getLong("position", 0L)
            playing = savedInstanceState.getBoolean("playing", false)
            started = savedInstanceState.getLong("started", SystemClock.elapsedRealtime())
        }
        hasCover = savedInstanceState?.getBoolean("demoHasCover", true) ?: true
        previewEmptyLyrics = savedInstanceState?.getBoolean("demoEmptyLyrics", false) ?: false
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(16, 18, 27)) }
        lyrics = SyncedLyricsView(this).apply {
            setLyrics(if (previewEmptyLyrics) emptyList() else demoLyrics())
            setCover(if (hasCover) BitmapFactory.decodeResource(resources, com.syncedlyrics.sdk.R.drawable.synced_lyrics_default_cover) else null)
            lyricStyle = LyricStyle.values().getOrElse(savedInstanceState?.getInt("style", LyricStyle.DIORAMA.ordinal) ?: LyricStyle.DIORAMA.ordinal) { LyricStyle.DIORAMA }
            emptyLyricsCoverStyle = EmptyLyricsCoverStyle.values().firstOrNull { it.name == savedInstanceState?.getString("emptyCoverStyle") } ?: EmptyLyricsCoverStyle.SQUARE_RECORD
            backgroundStyle = BackgroundStyle.values().firstOrNull { it.name == savedInstanceState?.getString("backgroundStyle") } ?: BackgroundStyle.LIQUID
            activeColor = savedInstanceState?.getInt("activeColor", 0xFF72DA9F.toInt()) ?: 0xFF72DA9F.toInt()
            setPlaybackState(currentPosition(), playing)
        }
        root.addView(lyrics, FrameLayout.LayoutParams(-1, -1))
        val watermark = TextView(this).apply { text = "SYNCED LYRICS · DEMO"; textSize = 9f; letterSpacing = .12f; setTextColor(0x88FFFFFF.toInt()) }
        root.addView(watermark, FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP or Gravity.START).apply { leftMargin = dp(20); topMargin = dp(14) })
        val settings = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences); setColorFilter(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "歌词样式和背景设置"; setOnClickListener { showSettings() }
        }
        root.addView(settings, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { rightMargin = dp(12); topMargin = dp(4) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(16), 0); setBackgroundColor(0x55000000) }
        playButton = ImageButton(this).apply { setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE); setOnClickListener { toggle() } }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        seek = SeekBar(this).apply { max = duration.toInt(); contentDescription = "演示进度" }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) { dragging = false; setPosition(seekBar.progress.toLong()) }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) timeLabel.text = "${format(progress.toLong())} / ${format(duration)}" }
        })
        controls.addView(seek, LinearLayout.LayoutParams(0, dp(48), 1f))
        timeLabel = TextView(this).apply { textSize = 10f; setTextColor(0xFFCBC7D6.toInt()); gravity = Gravity.CENTER }
        controls.addView(timeLabel)
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM))
        updatePlayButton()
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION") val systemTop = insets.systemWindowInsetTop
            @Suppress("DEPRECATION") val systemBottom = insets.systemWindowInsetBottom
            @Suppress("DEPRECATION") val systemLeft = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION") val systemRight = insets.systemWindowInsetRight
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout else null
            val top = maxOf(systemTop, cutout?.safeInsetTop ?: 0)
            val bottom = maxOf(systemBottom, cutout?.safeInsetBottom ?: 0)
            val left = maxOf(systemLeft, cutout?.safeInsetLeft ?: 0)
            val right = maxOf(systemRight, cutout?.safeInsetRight ?: 0)
            // The view paints its background outside padding while clipping lyric content inside it.
            lyrics.setPadding(left, 0, right, 0)
            (watermark.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top + dp(14); leftMargin = left + dp(20) }.also { watermark.layoutParams = it }
            (settings.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top + dp(4); rightMargin = right + dp(12) }.also { settings.layoutParams = it }
            (controls.layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = bottom; leftMargin = left; rightMargin = right }.also { controls.layoutParams = it }
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        handler.post(tick)
    }
    private fun setPosition(value: Long) {
        position = value.coerceIn(0, duration)
        started = SystemClock.elapsedRealtime()
        lyrics.setPlaybackState(position, playing)
    }
    private fun toggle() {
        if (playing) { position = currentPosition(); playing = false }
        else { if (position >= duration) position = 0; started = SystemClock.elapsedRealtime(); playing = true }
        updatePlayButton()
        lyrics.setPlaybackState(position, playing)
    }
    private fun updatePlayButton() {
        playButton.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playButton.contentDescription = if (playing) "暂停演示" else "播放演示"
    }
    private fun showSettings() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(4), dp(24), dp(4)) }
        container.addView(Switch(this).apply {
            text = "无歌词预览"; isChecked = previewEmptyLyrics
            setOnCheckedChangeListener { _, checked ->
                previewEmptyLyrics = checked
                lyrics.setLyrics(if (checked) emptyList() else demoLyrics())
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        container.addView(Switch(this).apply {
            text = "有歌曲封面"; isChecked = hasCover
            setOnCheckedChangeListener { _, checked ->
                hasCover = checked
                lyrics.setCover(if (checked) BitmapFactory.decodeResource(resources, com.syncedlyrics.sdk.R.drawable.synced_lyrics_default_cover) else null)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        container.addView(TextView(this).apply { text = "无歌词封面样式"; textSize = 14f; setPadding(0, dp(8), 0, dp(4)) })
        val covers = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        EmptyLyricsCoverStyle.values().forEach { style ->
            covers.addView(RadioButton(this).apply { id = View.generateViewId(); text = style.label; isChecked = style == lyrics.emptyLyricsCoverStyle; setOnClickListener { lyrics.emptyLyricsCoverStyle = style } }, RadioGroup.LayoutParams(-1, dp(44)))
        }
        covers.check(covers.getChildAt(lyrics.emptyLyricsCoverStyle.ordinal).id)
        container.addView(covers)
        container.addView(TextView(this).apply { text = "背景样式"; textSize = 14f; setPadding(0, dp(8), 0, dp(4)) })
        val backgrounds = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        BackgroundStyle.values().forEach { style ->
            backgrounds.addView(RadioButton(this).apply { id = View.generateViewId(); text = style.label; isChecked = style == lyrics.backgroundStyle; setOnClickListener { lyrics.backgroundStyle = style } }, RadioGroup.LayoutParams(-1, dp(44)))
        }
        backgrounds.check(backgrounds.getChildAt(lyrics.backgroundStyle.ordinal).id)
        container.addView(backgrounds)
        val title = TextView(this).apply { text = "歌词样式"; textSize = 14f; setPadding(0, dp(8), 0, dp(4)) }
        container.addView(title)
        val choices = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        LyricStyle.values().forEach { style ->
            choices.addView(RadioButton(this).apply { id = View.generateViewId(); text = style.label; isChecked = style == lyrics.lyricStyle; setOnClickListener { lyrics.lyricStyle = style } }, RadioGroup.LayoutParams(-1, dp(46)))
        }
        choices.check(choices.getChildAt(lyrics.lyricStyle.ordinal).id)
        container.addView(choices)
        container.addView(TextView(this).apply { text = "选中颜色"; textSize = 14f; setPadding(0, dp(12), 0, dp(6)) })
        val palette = listOf(
            "紫色" to 0xFFBE8BFF.toInt(), "白色" to Color.WHITE, "粉色" to 0xFFFF91BC.toInt(), "红色" to 0xFFFF7070.toInt(),
            "橙色" to 0xFFFFA65C.toInt(), "金色" to 0xFFFFD26A.toInt(), "黄绿" to 0xFFC4EB78.toInt(), "绿色" to 0xFF72DA9F.toInt(),
            "青色" to 0xFF69DDDC.toInt(), "浅蓝" to 0xFF75C9FF.toInt(), "蓝色" to 0xFF819CFF.toInt(), "玫瑰" to 0xFFE89AF2.toInt()
        )
        val grid = GridLayout(this).apply { columnCount = 6 }
        val buttons = mutableListOf<TextView>()
        fun refreshPalette() {
            buttons.forEachIndexed { index, button ->
                button.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette[index].second); setStroke(dp(if (lyrics.activeColor == palette[index].second) 3 else 1), if (lyrics.activeColor == palette[index].second) Color.WHITE else 0xFF777777.toInt()) }
                button.text = if (lyrics.activeColor == palette[index].second) "✓" else ""
            }
        }
        palette.forEach { (name, color) ->
            val button = TextView(this).apply {
                gravity = Gravity.CENTER; textSize = 18f; setTextColor(if (color == Color.WHITE || color == 0xFFFFD26A.toInt()) Color.DKGRAY else Color.WHITE)
                contentDescription = name; setOnClickListener { lyrics.activeColor = color; refreshPalette() }
            }
            buttons.add(button)
            grid.addView(button, GridLayout.LayoutParams().apply { width = dp(38); height = dp(38); setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        refreshPalette(); container.addView(grid)
        val scroller = ScrollView(this).apply { addView(container); isFillViewport = false }
        AlertDialog.Builder(this).setTitle("显示设置").setView(scroller).setPositiveButton("完成", null).show()
    }
    private fun format(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("demoHasCover", hasCover)
        outState.putBoolean("demoEmptyLyrics", previewEmptyLyrics)
        outState.putLong("position", position)
        outState.putLong("started", started)
        outState.putBoolean("playing", playing)
        outState.putInt("style", lyrics.lyricStyle.ordinal)
        outState.putString("emptyCoverStyle", lyrics.emptyLyricsCoverStyle.name)
        outState.putString("backgroundStyle", lyrics.backgroundStyle.name)
        outState.putInt("activeColor", lyrics.activeColor)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        super.onStop()
        if (playing && !isChangingConfigurations) { position = currentPosition(); playing = false; updatePlayButton(); lyrics.setPlaybackState(position, false) }
        handler.removeCallbacks(tick)
    }
    override fun onStart() { super.onStart(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun demoLyrics(): List<LyricLine> {
        fun line(start: Long, end: Long, text: String, translation: String? = null): LyricLine {
            val units = text.map { it.toString() }
            val step = (end - start) / units.size.coerceAtLeast(1)
            return LyricLine(start, text, translation, units.mapIndexed { i, word -> LyricWord(word, start + i * step, if (i == units.lastIndex) end else start + (i + 1) * step) })
        }
        return listOf(
            line(0, 3500, "把此刻轻轻交给旋律"),
            line(4000, 7500, "让微光落进每一句诗里"),
            line(8000, 11500, "黄昏沿着街角慢慢褪去"),
            line(12000, 15500, "晚风捎来远方的声音"),
            line(16000, 19500, "我们穿过城市的灯影"),
            line(20000, 23500, "把未说完的心事藏进流星"),
            line(24000, 27500, "有些相遇，像一场温柔的雨"),
            line(28000, 31500, "落在心上，便有了回音"),
            line(32000, 35500, "当世界渐渐安静"),
            line(36000, 39500, "请听见心底那片海的呼吸"),
            LyricLine(40000, "Let the music carry us home"),
            line(44000, 47500, "让音乐带我们回到心之所向"),
            LyricLine(48000, "Every little light will find its way"),
            line(52000, 55500, "每一束微光，都会找到归途"),
            line(56000, 59500, "愿所有平凡的瞬间"),
            line(60000, 63500, "都能被温柔收藏"),
            LyricLine(64000, "Design by BiBi")
        )
    }
}
