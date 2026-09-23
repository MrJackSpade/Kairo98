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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Library landing page. A short tap or A activates the selected game. */
class LibraryScreen(
    context: Context,
    private val catalog: GameCatalog,
    private val chooseFolder: () -> Unit,
    private val refresh: () -> Unit,
    private val rehash: () -> Unit,
    private val play: (LibraryEntry) -> Unit,
    private val details: (LibraryEntry) -> Unit
) : LinearLayout(context) {
    private val status = TextView(context)
    private val folder = TextView(context)
    private val list = ListView(context)
    private val artCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
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
                    orientation = HORIZONTAL
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
                view.addView(art, LayoutParams(coverSize, coverSize))
                view.addView(mark, LayoutParams(coverSize, coverSize))
                val text = LinearLayout(context).apply {
                    orientation = VERTICAL
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
                view.addView(text, LayoutParams(0, -2, 1f))
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
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        setPadding(dp(18), dp(18), dp(18), dp(12))
        addView(TextView(context).apply {
            text = "KAIRO98"
            textSize = 28f
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        addView(TextView(context).apply {
            text = "GAME LIBRARY"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(0xff66d6df.toInt())
        })
        folder.apply {
            textSize = 13f
            setTextColor(0xffc8d0da.toInt())
            setPadding(0, dp(12), 0, dp(4))
        }
        addView(folder)
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(actionButton("Select ROM folder", chooseFolder), LayoutParams(0, dp(44), 1f))
        actions.addView(actionButton("Refresh", refresh), LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(8)
        })
        actions.addView(actionButton("Rehash", rehash), LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(8)
        })
        addView(actions)
        status.apply {
            textSize = 13f
            setTextColor(0xff9ba9b8.toInt())
            setPadding(0, dp(9), 0, dp(8))
            maxLines = 2
        }
        addView(status)
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
        addView(list, LayoutParams(-1, 0, 1f))
    }

    fun showFolder(label: String?) { folder.text = label ?: "No ROM folder selected" }
    fun showStatus(message: String) { status.text = message }
    fun showEntries(items: List<LibraryEntry>) {
        entries = items
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

    private fun actionButton(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(0xff30475b.toInt())
        isFocusable = true
        isClickable = true
        setOnClickListener { action() }
    }

    private fun loadArt(path: String): Bitmap? {
        artCache.get(path)?.let { return it }
        return try {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inSampleSize = 4
                })?.also { artCache.put(path, it) }
            }
        } catch (_: Exception) { null }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
