package io.github.zaxar1231.mycycle.core.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The user's own picture behind every screen. It is copied into app-private storage (excluded
 * from backups like all app files), so the app never needs the photo library permission.
 */
object Wallpapers {
    private const val DIRECTORY = "wallpapers"
    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val TARGET_PX = 1280
    private const val BUFFER = 64 * 1024

    internal fun file(context: Context, name: String) = File(File(context.filesDir, DIRECTORY), name)

    /**
     * Copies and checks the picture. Returns the stored file name, or null if it is not an image.
     * Older pictures are removed, except [keep], the one the settings still point to.
     */
    suspend fun import(context: Context, uri: Uri, keep: String?): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val name = "wallpaper_${System.currentTimeMillis()}.img"
        val target = File(dir, name)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) error("picture too large")
                        output.write(buffer, 0, read)
                    }
                }
            } != null
        }.getOrDefault(false)
        if (!copied || !isPicture(target)) {
            target.delete()
            return@withContext null
        }
        dir.listFiles()?.filter { it.name != name && it.name != keep }?.forEach { it.delete() }
        name
    }

    /** Decodes a picture no wider than about [TARGET_PX], so a large photo does not run out of memory. */
    suspend fun decode(context: Context, name: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val path = file(context, name).takeIf { it.exists() }?.path ?: return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            val sample = Integer.highestOneBit(maxOf(1, bounds.outWidth / TARGET_PX))
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }

    /** Deletes the stored picture. Call it after the settings stop pointing to the file. */
    suspend fun remove(context: Context, name: String) = withContext(Dispatchers.IO) { file(context, name).delete() }

    private fun isPicture(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }
}

/**
 * The picture, cropped to fill, with a scrim on top so text stays readable. Draws nothing until
 * the picture is decoded.
 */
@Composable
fun WallpaperBackdrop(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, name) { value = Wallpapers.decode(context, name) }
    val dark = LocalDarkTheme.current
    Box(modifier.fillMaxSize()) {
        picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) DARK_SCRIM else LIGHT_SCRIM)))
    }
}

private const val DARK_SCRIM = 0.5f
private const val LIGHT_SCRIM = 0.3f
