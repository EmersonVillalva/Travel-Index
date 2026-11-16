package com.travelindex.app.ui.viewer

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.travelindex.app.R
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.ViewerSupport
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.categoryLabel
import com.travelindex.app.ui.components.openAttachment
import com.travelindex.app.ui.components.shareAttachment

/**
 * Visor interno: PDFs (todas las páginas, desplazables) e imágenes, con zoom de pellizco y doble toque para
 * volver al tamaño normal. Arriba, compartir y «Abrir con otra app». Los archivos siguen siendo privados de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(onBack: () -> Unit, vm: ViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val att = state.att
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(att?.let { categoryLabel(AttachmentCategory.fromName(it.category)) }.orEmpty(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (att != null) {
                        IconButton(onClick = { shareAttachment(ctx, att) }) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { openAttachment(ctx, att) }) {
                            Icon(Icons.Outlined.OpenInNew, stringResource(R.string.viewer_open_external))
                        }
                    }
                },
            )
        },
    ) { pad ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(pad))

            // Sin archivo (borrado) o PDF que no se pudo abrir (protegido o dañado): se ofrece la app externa.
            att == null || state.file == null || (state.isPdf && state.pageCount == null) -> Box(Modifier.padding(pad)) {
                EmptyState(
                    stringResource(R.string.viewer_error_title), stringResource(R.string.viewer_error_body),
                    icon = Icons.Outlined.InsertDriveFile,
                    actionLabel = if (att != null) stringResource(R.string.viewer_open_external) else null,
                    onAction = if (att != null) ({ openAttachment(ctx, att) }) else null,
                )
            }

            state.isPdf -> PdfViewer(vm, state.pageCount ?: 0, Modifier.padding(pad))

            else -> ZoomablePane(Modifier.padding(pad), panBothAxes = true) {
                AsyncImage(
                    model = state.file, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** Las páginas del PDF, una debajo de otra; solo se dibujan las que se acercan a la pantalla. */
@Composable
private fun PdfViewer(vm: ViewerViewModel, pages: Int, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val renderWidth = with(LocalDensity.current) {
            val px = maxWidth.roundToPx()
            remember(px) { ViewerSupport.pageRenderWidth(px) }
        }
        ZoomablePane(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp),
            ) {
                for (i in 0 until pages) item(key = i) { PdfPageView(vm, i, renderWidth) }
            }
        }
        if (pages > 1) {
            val current by remember { derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(pages) } }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                Text(
                    stringResource(R.string.viewer_page_of, current, pages),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfPageView(vm: ViewerViewModel, index: Int, renderWidth: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, renderWidth) { value = vm.renderPage(index, renderWidth) }
    // Fundido del hueco (proporción A4) a la página ya dibujada, sin saltos ni parpadeo.
    Crossfade(targetState = bitmap, label = "pdfPage") { b ->
        if (b == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1 / 1.414f).background(MaterialTheme.colorScheme.surfaceVariant))
        } else {
            Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
    }
}

/**
 * Marco con zoom: pellizco para acercar (hasta 5x); doble toque acerca hacia el punto tocado y vuelve.
 * Ampliado, se puede arrastrar con un dedo (en el PDF solo en horizontal, para que el dedo siga pasando
 * páginas; en una imagen en ambos ejes). [transformable] solo reacciona a gestos de dos dedos, así que el
 * desplazamiento normal de la lista no se toca.
 */
@Composable
private fun ZoomablePane(modifier: Modifier = Modifier, panBothAxes: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = ViewerSupport.clampScale(scale * zoom)
        offsetX = ViewerSupport.clampPan(offsetX + pan.x, size.width.toFloat(), scale)
        offsetY = ViewerSupport.clampPan(offsetY + pan.y, size.height.toFloat(), scale)
        if (scale == 1f) { offsetX = 0f; offsetY = 0f }
    }
    val zoomed = scale > 1f
    Box(
        modifier.fillMaxSize().onSizeChanged { size = it }.clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else {
                        scale = ViewerSupport.DOUBLE_TAP_SCALE
                        offsetX = ViewerSupport.doubleTapPan(tap.x, size.width.toFloat(), scale)
                        offsetY = ViewerSupport.doubleTapPan(tap.y, size.height.toFloat(), scale)
                    }
                })
            }
            .transformable(transform)
            .then(
                if (!zoomed) Modifier
                else if (panBothAxes) Modifier.pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offsetX = ViewerSupport.clampPan(offsetX + drag.x, size.width.toFloat(), scale)
                        offsetY = ViewerSupport.clampPan(offsetY + drag.y, size.height.toFloat(), scale)
                    }
                }
                else Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        offsetX = ViewerSupport.clampPan(offsetX + dx, size.width.toFloat(), scale)
                    }
                }
            ),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
            },
            content = content,
        )
    }
}
