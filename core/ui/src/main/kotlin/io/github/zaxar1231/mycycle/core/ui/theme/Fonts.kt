@file:OptIn(ExperimentalTextApi::class)

package io.github.zaxar1231.mycycle.core.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.zaxar1231.mycycle.core.domain.model.AppFont
import io.github.zaxar1231.mycycle.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bundled fonts are variable fonts (OFL, with Cyrillic), so one file gives every weight the
 * Material type scale uses. Licences are in `assets/licenses`. Nothing is downloaded.
 */
private fun variable(resId: Int) = FontFamily(
    WEIGHTS.map { weight ->
        Font(resId, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private val WEIGHTS = listOf(300, 400, 500, 600, 700)

internal fun AppFont.family(context: Context, customFile: String?): FontFamily? = when (this) {
    AppFont.SYSTEM -> null
    AppFont.INTER -> variable(R.font.inter)
    AppFont.NUNITO -> variable(R.font.nunito)
    AppFont.COMFORTAA -> variable(R.font.comfortaa)
    AppFont.LORA -> variable(R.font.lora)
    AppFont.CAVEAT -> variable(R.font.caveat)
    // A missing or broken file falls back to the system font instead of failing.
    AppFont.CUSTOM -> customFile?.let { CustomFonts.file(context, it) }?.takeIf { it.exists() }?.let { FontFamily(Font(it)) }
}

/** The Material type scale with every style in [family]. */
internal fun typographyWith(family: FontFamily?): Typography {
    val base = Typography()
    if (family == null) return base
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}

/**
 * The user's own font, copied into app-private storage (excluded from backups like all app
 * files). Each import gets a new file name, so the UI never keeps a cached old typeface.
 */
object CustomFonts {
    private const val DIRECTORY = "fonts"
    private const val MAX_BYTES = 15L * 1024 * 1024

    internal fun file(context: Context, name: String) = File(File(context.filesDir, DIRECTORY), name)

    /** Copies and checks the font. Returns the stored file name, or null if it is not a usable font. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val name = "custom_${System.currentTimeMillis()}.ttf"
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
                        if (total > MAX_BYTES) error("font too large")
                        output.write(buffer, 0, read)
                    }
                }
            } != null
        }.getOrDefault(false)
        // Typeface.Builder returns null for files that are not fonts.
        val valid = copied && runCatching { Typeface.Builder(target).build() }.getOrNull() != null
        if (!valid) {
            target.delete()
            return@withContext null
        }
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        name
    }

    private const val BUFFER = 64 * 1024
}
