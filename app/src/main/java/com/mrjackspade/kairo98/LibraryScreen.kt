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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** One settings row, shown the same way in the game menu and the library menu. */
data class SettingsEntry(val title: String, val value: () -> String, val action: () -> Unit)

/**
 * The part of a file name that tells revisions of one game apart: its bracketed and
 * parenthesized tags, such as a translation or bug-fix credit.
 */
internal fun variantLabel(name: String): String? {
    val tags = Regex("""[\[(]([^\])]+)[\])]""").findAll(name.substringAfterLast('/'))
        .map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
    return tags.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

/** Library landing page. A short tap or A opens the selected game's page. */
class LibraryScreen(
    context: Context,
    private val catalog: GameCatalog,
    private val chooseFolder: () -> Unit,
    private val refresh: () -> Unit,
    private val rehash: () -> Unit,
    private val downloadMissingImages: (() -> Unit)?,
    private val cancelArtworkDownload: () -> Unit,
    private val settings: List<SettingsEntry>,
    private val lastPlayedId: () -> String?,
    private val play: (LibraryEntry) -> Unit,
    private val preview: (LibraryEntry) -> Unit,
    private val details: (LibraryEntry) -> Unit
) : FrameLayout(context) {
    private val status = TextView(context)
    private val folder = TextView(context)
    private val list = ListView(context)
    private val emptyState = TextView(context)
    private val scanProgress = ProgressBar(context)
    private val artworkBanner = LinearLayout(context)
    private val artworkStatus = TextView(context)
    private val scrim = View(context)
    private val actionsScroll = ScrollView(context)
    private val actionsDrawer = LinearLayout(context)
    private val actionItems = ArrayList<View>()
    private val detailPage = GameDetailPage(context, catalog, play, preview, details) { closeDetail() }
    private val settingValues = ArrayList<Pair<TextView, () -> String>>()
    private val search = android.widget.EditText(context)
    private var allEntries = emptyList<LibraryEntry>()
    private var pinnedId: String? = null
    private var detailEntryId: String? = null
    private var selectedAction = 0
    var actionsOpen = false
        private set
    val detailOpen: Boolean get() = detailPage.isOpen
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
            val source = entry.zipEntry ?: entry.path
            val variant = variantLabel(entry.path) ?: variantLabel(source)
                ?: source.substringAfterLast('/').substringBeforeLast('.')
            holder.detail.text = when {
                entry.error != null -> "${entry.error}. Fix the source, then Refresh."
                entry.id == pinnedId -> "Last played  ·  $variant"
                else -> variant
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
        header.addView(headerButton("☰", "Library menu") { openActions() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
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
        header.addView(headerButton("⌕", "Search games") { toggleSearch() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(header)
        search.apply {
            visibility = View.GONE
            hint = "Search by title"
            textSize = 16f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(0xff8794a3.toInt())
            setBackgroundColor(0xff171d27.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(text: android.text.Editable?) { applyFilter() }
            })
        }
        body.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        artworkBanner.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(0xff263748.toInt())
            setPadding(dp(12), dp(7), dp(8), dp(7))
        }
        artworkStatus.apply {
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        artworkBanner.addView(artworkStatus, LinearLayout.LayoutParams(0, -2, 1f))
        artworkBanner.addView(TextView(context).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xff66d6df.toInt())
            setPadding(dp(12), dp(6), dp(6), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                artworkStatus.text = "Stopping image download…"
                cancelArtworkDownload()
            }
        })
        body.addView(artworkBanner, LinearLayout.LayoutParams(-1, -2))
        val gameArea = FrameLayout(context)
        list.apply {
            divider = null
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@LibraryScreen.adapter
            setOnItemClickListener { _, _, position, _ ->
                selectedIndex = position
                this@LibraryScreen.adapter.notifyDataSetChanged()
                openDetail(entries[position])
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
            setBackgroundColor(0xff171d27.toInt())
            setPadding(dp(16), dp(22), dp(16), dp(16))
        }
        actionsScroll.apply {
            visibility = View.GONE
            elevation = dp(16).toFloat()
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(0xff171d27.toInt())
            addView(actionsDrawer, FrameLayout.LayoutParams(-1, -2))
        }
        val drawerWidth = minOf(dp(320), resources.displayMetrics.widthPixels - dp(40))
        addView(actionsScroll, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
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
        drawerAction("Select ROM folder", "Choose where disk images and ZIP games are stored", chooseFolder)
        drawerAction("Refresh", "Scan for added, changed, or removed games", refresh)
        drawerAction("Rehash", "Recheck every game image", rehash)
        if (downloadMissingImages != null) drawerAction("Download missing images",
            "Fetch artwork for games in this library", downloadMissingImages)
        actionsDrawer.addView(TextView(context).apply {
            text = "SETTINGS"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(0xff66d6df.toInt())
            setPadding(dp(12), dp(18), dp(12), dp(5))
        })
        settings.forEach { entry ->
            settingValues.add(drawerAction(entry.title, entry.value(), entry.action) to entry.value)
        }
        addView(detailPage, FrameLayout.LayoutParams(-1, -1))
    }

    fun showFolder(label: String?) { folder.text = label ?: "No ROM folder selected" }
    fun showArtworkProgress(completed: Int, total: Int, failures: Int) {
        artworkBanner.visibility = View.VISIBLE
        artworkStatus.text = if (total == 0) "Checking library artwork…"
            else "Downloading images $completed/$total" +
                (if (failures == 0) "" else " · $failures failed")
    }
    fun finishArtworkDownload(message: String) {
        artworkBanner.visibility = View.GONE
        showStatus(message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun refreshArtwork() {
        missingArt.clear()
        adapter.notifyDataSetChanged()
        detailPage.refreshArtwork()
    }
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
        allEntries = items
        pinnedId = lastPlayedId()?.takeIf { id -> items.any { it.id == id } }
        applyFilter()
        if (entries.isNotEmpty()) list.setSelection(selectedIndex)
        if (detailOpen) {
            val refreshed = items.firstOrNull { it.id == detailEntryId }
            if (refreshed == null) closeDetail() else detailPage.show(refreshed)
        }
    }
    /** Used by the debug ADB selector after cached entries or a scan arrive. */
    fun selectGame(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return false
        if (search.text.isNotEmpty()) search.setText("")
        val index = entries.indexOfFirst { it.displayName.equals(needle, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: entries.indexOfFirst {
                catalog.resolve(it.contentId ?: "", it.displayName).title.equals(needle, ignoreCase = true)
            }.takeIf { it >= 0 }
            ?: entries.indices.filter { entries[it].displayName.contains(needle, ignoreCase = true) }
                .singleOrNull() ?: -1
        if (index < 0) return false
        selectedIndex = index
        list.setSelection(index)
        adapter.notifyDataSetChanged()
        openDetail(entries[index])
        return true
    }

    /** Debug launch requires an unambiguous library entry. A full content ID
     * can select a particular patch revision when names overlap. */
    fun findGameForDebugLaunch(query: String): LibraryEntry? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        val playable = allEntries.filter { it.playable }
        fun unique(matches: List<LibraryEntry>): LibraryEntry? =
            matches.firstOrNull()?.takeIf {
                matches.all { other -> other.contentId == it.contentId }
            }
        return unique(playable.filter { it.contentId?.equals(needle, ignoreCase = true) == true })
            ?: unique(playable.filter { it.displayName.equals(needle, ignoreCase = true) })
            ?: unique(playable.filter {
                catalog.resolve(it.contentId ?: "", it.displayName).title.equals(needle, ignoreCase = true)
            })
            ?: unique(playable.filter { it.displayName.contains(needle, ignoreCase = true) })
    }
    fun moveSelection(delta: Int) {
        if (entries.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        this@LibraryScreen.adapter.notifyDataSetChanged()
        keepSelectionVisible()
    }

    private fun keepSelectionVisible() {
        if (list.height == 0) return
        val first = list.firstVisiblePosition
        val last = list.lastVisiblePosition
        val viewportBottom = list.height - list.paddingBottom
        val child = list.getChildAt(selectedIndex - first)
        when {
            selectedIndex < first -> list.setSelectionFromTop(selectedIndex, list.paddingTop)
            selectedIndex > last -> {
                val rowHeight = list.getChildAt(last - first)?.height ?: dp(75)
                list.setSelectionFromTop(selectedIndex,
                    (viewportBottom - rowHeight).coerceAtLeast(list.paddingTop))
            }
            child != null && child.top < list.paddingTop ->
                list.setSelectionFromTop(selectedIndex, list.paddingTop)
            child != null && child.bottom > viewportBottom ->
                list.setSelectionFromTop(selectedIndex,
                    (viewportBottom - child.height).coerceAtLeast(list.paddingTop))
        }
    }
    fun activateSelection() {
        entries.getOrNull(selectedIndex)?.let(::openDetail)
    }
    fun detailsSelection() { entries.getOrNull(selectedIndex)?.let(details) }

    fun openDetail(entry: LibraryEntry) {
        closeActions()
        detailEntryId = entry.id
        detailPage.show(entry)
    }

    fun closeDetail(): Boolean {
        if (!detailPage.close()) return false
        detailEntryId = null
        list.requestFocus()
        return true
    }

    fun activateDetail() { detailPage.activateFocused() }

    fun openActions() {
        if (actionsOpen) return
        actionsOpen = true
        scrim.animate().cancel()
        actionsScroll.animate().cancel()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        actionsScroll.visibility = View.VISIBLE
        actionsScroll.translationX = -actionsScroll.layoutParams.width.toFloat()
        refreshSettingValues()
        focusAction(0)
        scrim.animate().alpha(1f).setDuration(180).start()
        actionsScroll.animate().translationX(0f).setDuration(180).start()
    }

    fun closeActions(): Boolean {
        if (!actionsOpen) return false
        actionsOpen = false
        scrim.animate().cancel()
        actionsScroll.animate().cancel()
        scrim.animate().alpha(0f).setDuration(160).withEndAction {
            if (!actionsOpen) scrim.visibility = View.GONE
        }.start()
        actionsScroll.animate().translationX(-actionsScroll.layoutParams.width.toFloat())
            .setDuration(160).withEndAction {
                if (!actionsOpen) actionsScroll.visibility = View.GONE
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

    private fun drawerAction(title: String, description: String, action: () -> Unit): TextView {
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
        val detail = TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(0xff9ba9b8.toInt())
        }
        item.addView(detail)
        actionsDrawer.addView(item, LinearLayout.LayoutParams(-1, -2))
        item.minimumHeight = dp(66)
        actionItems.add(item)
        return detail
    }

    fun refreshSettingValues() {
        settingValues.forEach { (view, value) -> view.text = value() }
    }

    private fun headerButton(label: String, description: String, action: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = description
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
        }

    private fun toggleSearch() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        if (search.visibility == View.VISIBLE) {
            search.setText("")
            search.visibility = View.GONE
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        } else {
            search.visibility = View.VISIBLE
            search.requestFocus()
            imm.showSoftInput(search, 0)
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        val ordered = pinnedId?.let { id ->
            allEntries.firstOrNull { it.id == id }?.let { pinned -> listOf(pinned) + (allEntries - pinned) }
        } ?: allEntries
        entries = if (query.isEmpty()) ordered else ordered.filter { entry ->
            catalog.resolve(entry.contentId ?: "", entry.displayName).title.contains(query, ignoreCase = true) ||
                entry.displayName.contains(query, ignoreCase = true)
        }
        emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        if (entries.isEmpty() && allEntries.isNotEmpty()) emptyState.text = "No games match \"$query\""
        selectedIndex = selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        adapter.notifyDataSetChanged()
    }

    private fun loadArt(path: String): Bitmap? {
        artCache.get(path)?.let { return it }
        if (path in missingArt || !pendingArt.add(path)) return null
        artExecutor.execute {
            val bitmap = try {
                catalog.openArtwork(path).use { stream ->
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
