package io.github.zaxar1231.mycycle.core.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.wellness.AffirmationContext
import io.github.zaxar1231.mycycle.core.domain.wellness.AffirmationGenerator
import io.github.zaxar1231.mycycle.core.domain.wellness.AffirmationTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Affirmations from local templates in `assets/affirmations/<language>.json`: no network, no data
 * leaves the device. Editing the files changes the texts without code.
 */
@Singleton
class TemplateAffirmationGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) : AffirmationGenerator {
    private val cache = mutableMapOf<String, AffirmationTemplates>()
    private val lock = Mutex()

    override suspend fun generate(context: AffirmationContext, seed: Long): String {
        val lang = if (context.language in SUPPORTED) context.language else DEFAULT_LANGUAGE
        val templates = lock.withLock { cache.getOrPut(lang) { load(lang) } }
        return checkNotNull(templates.fill(context, Random(seed))) { "no affirmation templates for $lang" }
    }

    private suspend fun load(language: String): AffirmationTemplates = withContext(Dispatchers.IO) {
        val json = this@TemplateAffirmationGenerator.context.assets.open("$DIRECTORY/$language.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        AffirmationTemplates(templates = json.getJSONObject("templates").lists(), words = json.getJSONObject("words").lists())
    }

    private fun JSONObject.lists(): Map<String, List<String>> = keys().asSequence().associateWith { key ->
        val array = getJSONArray(key)
        List(array.length()) { array.getString(it) }
    }

    private companion object {
        const val DIRECTORY = "affirmations"
        const val DEFAULT_LANGUAGE = "ru"
        val SUPPORTED = setOf("ru", "en")
    }
}
