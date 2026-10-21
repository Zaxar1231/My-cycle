package io.github.zaxar1231.mycycle.core.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.repository.PhaseContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Reads `assets/phase_content/<language>.json`. Editing those files changes the texts, no code needed. */
@Singleton
class AssetPhaseContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhaseContentRepository {
    private val cache = mutableMapOf<String, Map<CyclePhase, PhaseContent>>()
    private val lock = Mutex()

    override suspend fun content(phase: CyclePhase, language: String): PhaseContent {
        val lang = if (language in SUPPORTED) language else DEFAULT_LANGUAGE
        val all = lock.withLock { cache.getOrPut(lang) { load(lang) } }
        return all[phase] ?: PhaseContent(phase, emptyList(), emptyList(), emptyList(), emptyList())
    }

    private suspend fun load(language: String): Map<CyclePhase, PhaseContent> = withContext(Dispatchers.IO) {
        val json = context.assets.open("$DIRECTORY/$language.json").bufferedReader().use { JSONObject(it.readText()) }
        CyclePhase.entries.associateWith { phase ->
            val obj = json.optJSONObject(phase.name)
            PhaseContent(
                phase = phase,
                symptoms = obj.strings("symptoms"),
                pros = obj.strings("pros"),
                cons = obj.strings("cons"),
                tips = obj.strings("tips"),
                about = obj?.optString("about").orEmpty(),
            )
        }
    }

    private fun JSONObject?.strings(key: String): List<String> {
        val array = this?.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getString(it) }
    }

    private companion object {
        const val DIRECTORY = "phase_content"
        const val DEFAULT_LANGUAGE = "ru"
        val SUPPORTED = setOf("ru", "en")
    }
}
