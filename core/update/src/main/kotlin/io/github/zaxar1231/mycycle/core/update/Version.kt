package io.github.zaxar1231.mycycle.core.update

/** Semantic version comparison for tags like "v1.2.3" or "1.2.3-beta". */
object Version {
    fun parse(version: String): List<Int> =
        version.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (diff != 0) return diff.coerceIn(-1, 1)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
