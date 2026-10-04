package com.wakeup.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import com.wakeup.dna.AppRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(val label: String, val ref: AppRef) {
    val lower = label.lowercase(Locale.getDefault())
    val letter: Char = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
}

/**
 * App discovery through LauncherApps (no QUERY_ALL_PACKAGES). The list is scanned once, kept in memory,
 * and updated incrementally when LauncherApps reports a package change, so the drawer never rescans
 * during normal use.
 */
class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private val um = ctx.getSystemService(UserManager::class.java)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> get() = _apps
    private var infos = HashMap<String, LauncherActivityInfo>()
    private var scanJob: Job? = null
    private val iconCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val collator = Collator.getInstance()
    private var registered = false

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String, u: UserHandle) = changed()
        override fun onPackageAdded(p: String, u: UserHandle) = changed()
        override fun onPackageChanged(p: String, u: UserHandle) = changed()
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = changed()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = changed()
    }

    fun start() {
        if (!registered) { la.registerCallback(callback, Handler(Looper.getMainLooper())); registered = true }
        if (_apps.value.isEmpty()) scan()
    }

    fun stop() { if (registered) { la.unregisterCallback(callback); registered = false } }

    private fun changed() {
        // debounce bursts (an update fires several callbacks)
        scanJob?.cancel()
        scanJob = scope.launch { delay(350); scan() }
    }

    private fun scan() {
        scope.launch(Dispatchers.Default) {
            val map = HashMap<String, LauncherActivityInfo>()
            val list = ArrayList<AppEntry>()
            for (user in la.profiles) {
                val serial = um.getSerialNumberForUser(user)
                for (info in la.getActivityList(null, user)) {
                    val ref = AppRef(info.componentName.packageName, info.componentName.className, serial)
                    if (map.put(ref.key, info) == null) list += AppEntry(info.label?.toString().orEmpty().ifBlank { ref.pkg }, ref)
                }
            }
            list.sortWith { a, b -> collator.compare(a.label, b.label) }
            withContext(Dispatchers.Main.immediate) { infos = map; iconCache.evictAll(); _apps.value = list }
        }
    }

    fun find(ref: AppRef): AppEntry? = _apps.value.firstOrNull { it.ref == ref }
    fun installedKeys(): Set<String> = _apps.value.mapTo(HashSet()) { it.ref.key }

    /** Starts the app immediately. Visual transitions run alongside this call and never gate it. */
    fun launch(ref: AppRef, bounds: Rect? = null): Boolean = try {
        la.startMainActivity(ComponentName(ref.pkg, ref.cls), userFor(ref), bounds, null); true
    } catch (e: Exception) { false }

    fun openAppInfo(ref: AppRef, bounds: Rect? = null) {
        runCatching { la.startAppDetailsActivity(ComponentName(ref.pkg, ref.cls), userFor(ref), bounds, null) }
    }

    private fun userFor(ref: AppRef): UserHandle = um.getUserForSerialNumber(ref.user) ?: android.os.Process.myUserHandle()

    /** Synchronous; call from a background dispatcher. */
    fun icon(ref: AppRef, size: Int, treatment: String, accent: Int): Bitmap? {
        val key = "${ref.key}|$size|$treatment|${if (treatment == "mono") accent else 0}"
        iconCache.get(key)?.let { return it }
        val info = infos[ref.key] ?: return null
        val d = runCatching { info.getIcon(0) }.getOrNull() ?: return null
        val b = runCatching { IconRenderer.render(d, size, treatment, accent) }.getOrNull() ?: return null
        iconCache.put(key, b); return b
    }

    fun trim() = iconCache.evictAll()

    /** Role-based defaults for first run: whichever app the user's device uses for each job. */
    fun defaults(): List<AppRef> {
        val pm = ctx.packageManager
        fun pick(i: android.content.Intent): AppRef? {
            val r = pm.resolveActivity(i, 0)?.activityInfo ?: return null
            if (r.packageName == "android" || r.packageName == ctx.packageName) return null
            return _apps.value.firstOrNull { it.ref.pkg == r.packageName }?.ref
        }
        fun cat(c: String) = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(c)
        val out = ArrayList<AppRef>()
        listOf(
            android.content.Intent(android.content.Intent.ACTION_DIAL),
            cat(android.content.Intent.CATEGORY_APP_MESSAGING),
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")),
            android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            cat(android.content.Intent.CATEGORY_APP_GALLERY), cat(android.content.Intent.CATEGORY_APP_MAPS),
            cat(android.content.Intent.CATEGORY_APP_EMAIL), cat(android.content.Intent.CATEGORY_APP_CALENDAR),
            cat(android.content.Intent.CATEGORY_APP_MUSIC), android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
        ).forEach { i -> pick(i)?.let { if (it !in out) out += it } }
        return out
    }
}
