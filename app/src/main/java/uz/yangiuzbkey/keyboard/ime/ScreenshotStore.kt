package uz.yangiuzbkey.keyboard.ime

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/**
 * Recent screenshots read from MediaStore (on the device only). Nothing is uploaded;
 * a screenshot is copied into the app cache only when the user pastes it.
 */
class ScreenshotStore(private val context: Context) {
    class Shot(val id: Long, val uri: Uri, val time: Long)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = LruCache<Long, Bitmap>(24)

    fun hasPermission(): Boolean = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Screenshots taken within [WINDOW_MS], newest first. */
    fun recent(): List<Shot> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<Shot>()
        val since = (System.currentTimeMillis() - WINDOW_MS) / 1000
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, pathCol)
        val sel = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ($pathCol LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        val args = arrayOf(since.toString(), "%creenshot%", "%creenshot%")
        try {
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && out.size < MAX) {
                    val id = c.getLong(0)
                    out += Shot(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), c.getLong(1) * 1000)
                }
            }
        } catch (_: Exception) { }
        return out
    }

    /** Loads a thumbnail off the main thread; [done] runs on the main thread. */
    fun thumbnail(shot: Shot, px: Int, done: (Bitmap?) -> Unit) {
        thumbs.get(shot.id)?.let { done(it); return }
        io.execute {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(shot.uri, Size(px, px), null)
                else @Suppress("DEPRECATION") MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, shot.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            } catch (_: Exception) { null }
            if (bmp != null) thumbs.put(shot.id, bmp)
            main.post { done(bmp) }
        }
    }

    /** Copies the screenshot into the cache and returns a shareable FileProvider uri. */
    fun share(shot: Shot): Uri? = try {
        val dir = File(context.cacheDir, "shots").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > WINDOW_MS) it.delete() }
        val f = File(dir, "screenshot_${shot.id}.png")
        if (!f.exists()) context.contentResolver.openInputStream(shot.uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
        FileProvider.getUriForFile(context, "${context.packageName}.shots", f)
    } catch (_: Exception) { null }

    companion object {
        const val WINDOW_MS = 60 * 60 * 1000L
        private const val MAX = 12
        val PERMISSION: String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
