package com.mrjackspade.kairo98

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Library landing page. A short tap or A activates the selected game. */
class LibraryScreen(
    context: Context,
    private val catalog: GameCatalog,
    private val chooseFolder: () -> Unit,
    private val refresh: () -> Unit,
    private val rehash: () -> Unit,
    private val play: (LibraryEntry) -> Unit,
    private val details: (LibraryEntry) -> Unit
) : FrameLayout(context) {
    private val status = TextView(context)
    private val folder = TextView(context)
    private val list = ListView(context)
    private val emptyState = TextView(context)
    private val scanProgress = ProgressBar(context)
    private val scrim = View(context)
    private val actionsDrawer = LinearLayout(context)
    private val actionItems = ArrayList<View>()
    private var selectedAction = 0
    var actionsOpen = false
        private set
    private val artCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val artExecutor = Executors.newSingleThreadExecutor()
    private val pendingArt = HashSet<String>()
    private val missingArt = HashSet<String>()
    private var entries = emptyList<LibraryEntry>()
    private var selectedIndex = 0

    private data class Row(val art: ImageView, val mark: TextView,
                           val title: TextView, val detail: TextView)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = entries[position].id.hashCode().toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: LinearLayout
            val holder: Row
            if (convertView is LinearLayout && convertView.tag is Row) {
                view = convertView
                holder = view.tag as Row
            } else {
                view = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    minimumHeight = dp(75)
                }
                val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                val mark = TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(0xffb8e9ec.toInt())
                    textSize = 24f
                    setBackgroundColor(0xff263748.toInt())
                }
                val coverSize = dp(58)
                view.addView(art, LinearLayout.LayoutParams(coverSize, coverSize))
                view.addView(mark, LinearLayout.LayoutParams(coverSize, coverSize))
                val text = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                val title = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 17f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val detail = TextView(context).apply {
                    setTextColor(0xff9ba9b8.toInt())
                    textSize = 12f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                }
                text.addView(title)
                text.addView(detail)
                view.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                holder = Row(art, mark, title, detail)
                view.tag = holder
            }
            val entry = entries[position]
            val game = catalog.resolve(entry.contentId ?: "", entry.displayName)
            holder.title.text = game.title
            holder.detail.text = when {
                entry.error != null -> "${entry.path}  ·  ${entry.error}. Fix the source, then Refresh."
                entry.zipEntry != null -> "${entry.path}  ·  ${entry.zipEntry}"
                else -> entry.path
            }
            val artwork = game.boxArt ?: game.preview
            val bitmap = artwork?.let(::loadArt)
            holder.art.visibility = if (bitmap == null) View.GONE else View.VISIBLE
            holder.mark.visibility = if (bitmap == null) View.VISIBLE else View.GONE
            holder.art.setImageBitmap(bitmap)
            holder.mark.text = game.title.firstOrNull()?.uppercase() ?: "?"
            view.setBackgroundColor(if (position == selectedIndex) 0xff30475b.toInt()
                else if (position % 2 == 0) 0xff171d27.toInt() else 0xff1b2430.toInt())
            view.alpha = if (entry.playable) 1f else 0.68f
            return view
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        addView(body, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "KAIRO98"
            textSize = 28f
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        scanProgress.apply { visibility = View.GONE }
        header.addView(scanProgress, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginEnd = dp(12)
        })
        header.addView(TextView(context).apply {
            text = "☰"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            contentDescription = "Library actions"
            isFocusable = true
            isClickable = true
            setOnClickListener { openActions() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header)
        val gameArea = FrameLayout(context)
        list.apply {
            divider = null
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@LibraryScreen.adapter
            setOnItemClickListener { _, _, position, _ ->
                selectedIndex = position
                this@LibraryScreen.adapter.notifyDataSetChanged()
                play(entries[position])
            }
            setOnItemLongClickListener { _, _, position, _ ->
                selectedIndex = position
                this@LibraryScreen.adapter.notifyDataSetChanged()
                details(entries[position])
                true
            }
        }
        gameArea.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState.apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xff9ba9b8.toInt())
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        gameArea.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        body.addView(gameArea, LinearLayout.LayoutParams(-1, 0, 1f))

        scrim.apply {
            visibility = View.GONE
            alpha = 0f
            setBackgroundColor(0xb8000000.toInt())
            setOnClickListener { closeActions() }
        }
        addView(scrim, FrameLayout.LayoutParams(-1, -1))
        actionsDrawer.apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            elevation = dp(16).toFloat()
            setBackgroundColor(0xff171d27.toInt())
            setPadding(dp(16), dp(22), dp(16), dp(16))
        }
        val drawerWidth = minOf(dp(320), resources.displayMetrics.widthPixels - dp(40))
        addView(actionsDrawer, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.END))
        actionsDrawer.addView(TextView(context).apply {
            text = "LIBRARY"
            textSize = 24f
            letterSpacing = 0.08f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), dp(4))
        })
        actionsDrawer.addView(TextView(context).apply {
            text = "MANAGE GAMES"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(0xff66d6df.toInt())
            setPadding(dp(12), 0, dp(12), dp(12))
        })
        folder.apply {
            textSize = 13f
            setTextColor(0xffc8d0da.toInt())
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        actionsDrawer.addView(folder)
        status.apply {
            textSize = 12f
            setTextColor(0xff9ba9b8.toInt())
            setPadding(dp(12), 0, dp(12), dp(18))
            maxLines = 2
        }
        actionsDrawer.addView(status)
        drawerAction("Select ROM folder", "Choose where HDI and ZIP games are stored", chooseFolder)
        drawerAction("Refresh", "Scan for added, changed, or removed games", refresh)
        drawerAction("Rehash", "Recheck every game image", rehash)
    }

    fun showFolder(label: String?) { folder.text = label ?: "No ROM folder selected" }
    fun showStatus(message: String) {
        status.text = message
        scanProgress.visibility = if (message.startsWith("Scanning") ||
            message.startsWith("Rehashing") || message.startsWith("Hashing") ||
            message.startsWith("Found") || message.startsWith("Skipping") ||
            message.startsWith("Preparing"))
            View.VISIBLE else View.GONE
        if (entries.isEmpty()) emptyState.text = message
        if (entries.isNotEmpty() && (message.startsWith("Scan failed") ||
            message.startsWith("Launch failed") || message.startsWith("Cannot keep folder access"))) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    fun showEntries(items: List<LibraryEntry>) {
        entries = items
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        this@LibraryScreen.adapter.notifyDataSetChanged()
        if (items.isNotEmpty()) list.setSelection(selectedIndex)
    }
    fun moveSelection(delta: Int) {
        if (entries.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        list.setSelection(selectedIndex)
        this@LibraryScreen.adapter.notifyDataSetChanged()
    }
    fun activateSelection() {
        entries.getOrNull(selectedIndex)?.let(play)
    }
    fun detailsSelection() { entries.getOrNull(selectedIndex)?.let(details) }

    fun openActions() {
        if (actionsOpen) return
        actionsOpen = true
        scrim.animate().cancel()
        actionsDrawer.animate().cancel()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        actionsDrawer.visibility = View.VISIBLE
        actionsDrawer.translationX = actionsDrawer.layoutParams.width.toFloat()
        focusAction(0)
        scrim.animate().alpha(1f).setDuration(180).start()
        actionsDrawer.animate().translationX(0f).setDuration(180).start()
    }

    fun closeActions(): Boolean {
        if (!actionsOpen) return false
        actionsOpen = false
        scrim.animate().cancel()
        actionsDrawer.animate().cancel()
        scrim.animate().alpha(0f).setDuration(160).withEndAction {
            if (!actionsOpen) scrim.visibility = View.GONE
        }.start()
        actionsDrawer.animate().translationX(actionsDrawer.layoutParams.width.toFloat())
            .setDuration(160).withEndAction {
                if (!actionsOpen) actionsDrawer.visibility = View.GONE
            }.start()
        return true
    }

    fun moveActionSelection(delta: Int) {
        focusAction((selectedAction + delta).coerceIn(0, actionItems.lastIndex))
    }

    fun activateAction() { actionItems.getOrNull(selectedAction)?.performClick() }

    private fun focusAction(index: Int) {
        selectedAction = index
        actionItems.forEachIndexed { position, item ->
            item.setBackgroundColor(if (position == index) 0xff30475b.toInt()
                else Color.TRANSPARENT)
        }
        actionItems.getOrNull(index)?.requestFocus()
    }

    private fun drawerAction(title: String, description: String, action: () -> Unit) {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true
            isClickable = true
            contentDescription = "$title. $description"
            setOnClickListener {
                closeActions()
                action()
            }
        }
        item.addView(TextView(context).apply {
            text = title
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        item.addView(TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(0xff9ba9b8.toInt())
        })
        actionsDrawer.addView(item, LinearLayout.LayoutParams(-1, dp(66)))
        actionItems.add(item)
    }

    private fun loadArt(path: String): Bitmap? {
        artCache.get(path)?.let { return it }
        if (path in missingArt || !pendingArt.add(path)) return null
        artExecutor.execute {
            val bitmap = try {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inSampleSize = 4
                    })
                }
            } catch (_: Exception) { null }
            post {
                pendingArt.remove(path)
                if (bitmap == null) missingArt.add(path) else artCache.put(path, bitmap)
                adapter.notifyDataSetChanged()
            }
        }
        return null
    }

    override fun onDetachedFromWindow() {
        artExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
