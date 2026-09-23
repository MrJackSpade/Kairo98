package com.mrjackspade.kairo98

/** One PC-98 scan can be held by several independent input sources. */
class InputRouter(private val send: (Int, Boolean) -> Unit) {
    private val owners = LinkedHashMap<String, List<Int>>()
    private val counts = IntArray(128)

    @Synchronized fun hold(owner: String, scans: List<Int>) {
        require(owner.isNotBlank() && scans.isNotEmpty() && scans.size <= 8)
        require(scans.distinct().size == scans.size && scans.all { it in 0..127 })
        if (owners[owner] == scans) return
        release(owner)
        owners[owner] = scans.toList()
        for (scan in scans) {
            if (counts[scan]++ == 0) send(scan, true)
        }
    }

    @Synchronized fun release(owner: String) {
        val scans = owners.remove(owner) ?: return
        for (scan in scans.asReversed()) {
            if (--counts[scan] == 0) send(scan, false)
        }
    }

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach(::release)
    }

    @Synchronized fun releaseAll() {
        owners.keys.toList().forEach(::release)
    }
}
