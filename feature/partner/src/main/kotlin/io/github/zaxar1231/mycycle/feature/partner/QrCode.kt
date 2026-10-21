package io.github.zaxar1231.mycycle.feature.partner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** [text] as a QR code: black on white with a quiet zone, whatever the theme, so cameras read it. */
@Composable
internal fun QrCode(text: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { runCatching { Encoder.encode(text, ErrorCorrectionLevel.M).matrix }.getOrNull() } ?: return
    Canvas(
        modifier
            .size(QR_SIZE)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(QUIET_ZONE)
            .semantics { contentDescription = description },
    ) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y).toInt() == 1) {
                    // A hair larger than the cell, so neighbouring modules join without seams.
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private val QR_SIZE = 240.dp
private val QUIET_ZONE = 20.dp
