package io.github.zaxar1231.mycycle.core.domain.model

/** Blocks of the home screen the user can reorder, hide and restyle. */
enum class HomeBlock { HERO, KEY_DATES, CALENDAR, TODAY, AFFIRMATION }

/** How a block's container looks. [NONE] draws the content without a container. */
enum class BlockLook { NONE, FILLED, TONAL, OUTLINED, GLASS }

/** Padding and text scale of a block. */
enum class BlockSize { COMPACT, NORMAL, LARGE }

data class BlockStyle(
    val visible: Boolean = true,
    val look: BlockLook = BlockLook.FILLED,
    val size: BlockSize = BlockSize.NORMAL,
    val cornerDp: Int = DEFAULT_CORNER,
    /** ARGB of the container, or null to take the colour from the theme. */
    val color: Int? = null,
) {
    companion object {
        const val DEFAULT_CORNER = 28
        const val MAX_CORNER = 40
    }
}

/** Order and style of the home screen blocks. Always holds every block exactly once. */
data class HomeLayout(val order: List<HomeBlock>, val styles: Map<HomeBlock, BlockStyle>) {
    fun style(block: HomeBlock): BlockStyle = styles[block] ?: defaultStyle(block)

    fun withStyle(block: HomeBlock, style: BlockStyle) = copy(styles = styles + (block to style))

    /** Moves the block at [from] to [to]; out-of-range indices change nothing. */
    fun moved(from: Int, to: Int): HomeLayout {
        if (from !in order.indices || to !in order.indices || from == to) return this
        val list = order.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(order = list)
    }

    /** Drops unknown or repeated blocks and appends missing ones, so old saved layouts keep working. */
    fun normalized(): HomeLayout {
        val known = order.distinct()
        return copy(order = known + HomeBlock.entries.filter { it !in known })
    }

    companion object {
        val DEFAULT = HomeLayout(HomeBlock.entries, emptyMap())

        /** The picture and the calendar sit on the background; the rest are cards. */
        fun defaultStyle(block: HomeBlock) = when (block) {
            HomeBlock.HERO, HomeBlock.CALENDAR -> BlockStyle(look = BlockLook.NONE)
            HomeBlock.KEY_DATES, HomeBlock.TODAY, HomeBlock.AFFIRMATION -> BlockStyle()
        }
    }
}

/**
 * A compact text form for settings storage: `BLOCK,visible,LOOK,SIZE,corner,color` per block in
 * order, separated by `;`, colour as hex or `-`. Anything unreadable falls back to defaults.
 */
object HomeLayoutCodec {
    private const val BLOCKS = ";"
    private const val FIELDS = ","
    private const val NO_COLOR = "-"
    private const val FIELD_COUNT = 6
    private const val HEX = 16

    fun encode(layout: HomeLayout): String = layout.normalized().order.joinToString(BLOCKS) { block ->
        val s = layout.style(block)
        listOf(
            block.name,
            if (s.visible) "1" else "0",
            s.look.name,
            s.size.name,
            s.cornerDp.toString(),
            s.color?.let { Integer.toHexString(it) } ?: NO_COLOR,
        ).joinToString(FIELDS)
    }

    fun decode(text: String?): HomeLayout {
        if (text.isNullOrBlank()) return HomeLayout.DEFAULT
        val order = mutableListOf<HomeBlock>()
        val styles = mutableMapOf<HomeBlock, BlockStyle>()
        text.split(BLOCKS).forEach { entry ->
            val f = entry.split(FIELDS)
            if (f.size != FIELD_COUNT) return@forEach
            // The first entry of a block wins; unknown and repeated ones are skipped.
            val block = HomeBlock.entries.firstOrNull { it.name == f[0] }?.takeIf { it !in order } ?: return@forEach
            val default = HomeLayout.defaultStyle(block)
            order += block
            styles[block] = BlockStyle(
                visible = f[1] != "0",
                look = BlockLook.entries.firstOrNull { it.name == f[2] } ?: default.look,
                size = BlockSize.entries.firstOrNull { it.name == f[3] } ?: default.size,
                cornerDp = f[4].toIntOrNull()?.coerceIn(0, BlockStyle.MAX_CORNER) ?: default.cornerDp,
                color = f[5].takeIf { it != NO_COLOR }?.toLongOrNull(HEX)?.toInt(),
            )
        }
        return HomeLayout(order, styles).normalized()
    }
}
