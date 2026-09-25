package com.mrjackspade.kairo98

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Full library detail page. Artwork loads from approved app assets. */
class GameDetailPage(
    context: Context,
    private val catalog: GameCatalog,
    private val play: (LibraryEntry) -> Unit,
    private val viewScreenshot: (LibraryEntry) -> Unit,
    private val back: () -> Unit
) : FrameLayout(context) {
    private val imagePanel = FrameLayout(context)
    private val image = ImageView(context)
    private val imageFallback = TextView(context)
    private val backButton = TextView(context)
    private val title = TextView(context)
    private val description = TextView(context)
    private val playButton = TextView(context)
    private val scroll = ScrollView(context)
    private val hero = LinearLayout(context)
    private val right = LinearLayout(context)
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private var currentEntry: LibraryEntry? = null
    private var imageGeneration = 0

    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        setBackgroundColor(Color.BLACK)
        isClickable = true

        scroll.isFillViewport = true
        addView(scroll, LayoutParams(-1, -1))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(body)

        backButton.apply {
            text = "‹  LIBRARY"
            textSize = 16f
            setTextColor(0xffb8e9ec.toInt())
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "Back to game library"
            setPadding(dp(4), 0, dp(16), 0)
            setOnClickListener { back() }
        }
        body.addView(backButton, LinearLayout.LayoutParams(-1, dp(48)))

        hero.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(220)).apply {
            topMargin = dp(16)
        })

        imagePanel.apply {
            setBackgroundColor(0xff171d27.toInt())
            contentDescription = "View screenshot"
            setOnClickListener { currentEntry?.let(viewScreenshot) }
        }
        hero.addView(imagePanel, LinearLayout.LayoutParams(0, -1, 1.15f))
        image.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = "Game screenshot"
        }
        imagePanel.addView(image, LayoutParams(-1, -1))
        imageFallback.apply {
            text = "No screenshot available"
            textSize = 14f
            setTextColor(0xff9ba9b8.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        imagePanel.addView(imageFallback, LayoutParams(-1, -1))

        right.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, 0, 0)
        }
        hero.addView(right, LinearLayout.LayoutParams(0, -1, 0.85f))
        title.apply {
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        right.addView(title, LinearLayout.LayoutParams(-1, 0, 1f))
        playButton.apply {
            text = "PLAY"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setBackgroundColor(0xff66d6df.toInt())
            isClickable = true
            isFocusable = true
            contentDescription = "Play game"
            setOnClickListener { playSelected() }
            setOnFocusChangeListener { _, focused ->
                alpha = if (!isEnabled) 0.4f else if (focused) 1f else 0.85f
            }
        }
        right.addView(playButton, LinearLayout.LayoutParams(-1, dp(56)))

        description.apply {
            textSize = 16f
            setTextColor(0xffd1d8e0.toInt())
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        body.addView(description, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        updateHeroLayout(resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }

    private fun updateHeroLayout(portrait: Boolean) {
        hero.orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        hero.layoutParams = (hero.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (portrait) -2 else dp(220)
        }
        imagePanel.layoutParams = if (portrait) LinearLayout.LayoutParams(-1, dp(220))
            else LinearLayout.LayoutParams(0, -1, 1.15f)
        right.setPadding(if (portrait) 0 else dp(18), if (portrait) dp(18) else 0, 0, 0)
        right.layoutParams = if (portrait) LinearLayout.LayoutParams(-1, -2)
            else LinearLayout.LayoutParams(0, -1, 0.85f)
        title.layoutParams = LinearLayout.LayoutParams(-1, if (portrait) -2 else 0,
            if (portrait) 0f else 1f)
        playButton.layoutParams = LinearLayout.LayoutParams(
            if (portrait) dp(180) else -1, dp(56)).apply {
            if (portrait) topMargin = dp(18)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateHeroLayout(newConfig.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT)
    }

    fun show(entry: LibraryEntry) {
        currentEntry = entry
        val game = catalog.resolve(entry.contentId ?: "", entry.displayName)
        title.text = game.title
        description.text = game.description ?: "No description available yet."
        playButton.isEnabled = entry.playable
        playButton.alpha = if (entry.playable) 1f else 0.4f
        image.setImageDrawable(null)
        image.visibility = View.GONE
        imageFallback.visibility = View.VISIBLE
        imageFallback.text = if (game.preview == null) "No screenshot available" else "Loading screenshot…"
        imagePanel.isEnabled = game.preview != null
        imagePanel.isFocusable = game.preview != null
        visibility = View.VISIBLE
        scroll.scrollTo(0, 0)
        if (entry.playable) playButton.requestFocus()
        else backButton.requestFocus()

        val generation = ++imageGeneration
        val path = game.preview ?: return
        imageExecutor.execute {
            val bitmap = try {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
            post {
                if (generation == imageGeneration && isOpen) {
                    if (bitmap == null) imageFallback.text = "Screenshot unavailable"
                    else {
                        image.setImageBitmap(bitmap)
                        image.visibility = View.VISIBLE
                        imageFallback.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun close(): Boolean {
        if (!isOpen) return false
        imageGeneration++
        currentEntry = null
        visibility = View.GONE
        return true
    }

    fun playSelected() { currentEntry?.takeIf { it.playable }?.let(play) }

    fun activateFocused() {
        when {
            backButton.hasFocus() -> back()
            imagePanel.hasFocus() -> currentEntry?.let(viewScreenshot)
            else -> playSelected()
        }
    }

    override fun onDetachedFromWindow() {
        imageExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
