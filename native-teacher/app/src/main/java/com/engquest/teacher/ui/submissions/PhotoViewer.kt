package com.engquest.teacher.ui.submissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * The photo on a black screen: pinch to zoom (up to 5×), drag to move around, double-tap to zoom
 * in or back out. The close button or Android Back closes it.
 */
@Composable
fun PhotoViewer(image: ImageBitmap, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        /** Keeps the zoomed photo covering the screen: it cannot be dragged off to one side. */
        fun clamp(o: Offset, z: Float): Offset {
            val maxX = box.width * (z - 1) / 2
            val maxY = box.height * (z - 1) / 2
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        Column(Modifier.fillMaxSize().background(Color.Black).testTag("photoViewer")) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { box = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panBy, zoomBy, _ ->
                            val z = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM)
                            zoom = z
                            pan = clamp(pan + panBy, z)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (zoom > 1f) {
                                zoom = 1f
                                pan = Offset.Zero
                            } else {
                                zoom = DOUBLE_TAP_ZOOM
                            }
                        })
                    },
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Submitted work, full size",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .testTag("closeViewer"),
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.semantics { contentDescription = "Close photo" })
                }
            }
            Text(
                "Pinch or double-tap to zoom",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
