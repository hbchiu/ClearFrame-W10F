package com.ezhart.clearframe.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ezhart.clearframe.ClearFrameApplication
import com.ezhart.clearframe.R
import com.ezhart.clearframe.data.DisplayMode
import com.ezhart.clearframe.ui.theme.ClearFrameTheme
import java.io.File

@Composable
fun HomeScreen(
    uiState: AppUiState,
    displayMode: DisplayMode,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is AppUiState.Loading -> LoadingScreen(modifier = modifier.fillMaxSize())
        is AppUiState.Running -> SlideScreen(
            uiState,
            displayMode = displayMode,
            modifier = modifier.fillMaxWidth()
        )
        is AppUiState.Error -> ErrorScreen(modifier = modifier.fillMaxSize())
        AppUiState.Empty -> EmptyScreen(modifier = modifier.fillMaxSize())
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Image(
        modifier = modifier.size(200.dp),
        painter = painterResource(R.drawable.loading),
        contentDescription = stringResource(R.string.loading)
    )
}

@Composable
fun ErrorScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.connection_error), contentDescription = ""
        )
        Text(text = stringResource(R.string.loading_failed), modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun EmptyScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            modifier = modifier.size(200.dp),
            painter = painterResource(id = R.drawable.empty), contentDescription = ""
        )
        Text(text = stringResource(R.string.empty), modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun VideoPlayer(filename: String, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(filename) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filename))))
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(filename) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun AdaptiveImage(
    filename: String,
    displayMode: DisplayMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var contentScale by remember { mutableStateOf(ContentScale.Fit) }
    var ready by remember { mutableStateOf(false) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(filename)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    contentScale = when (displayMode) {
                        DisplayMode.FILL -> ContentScale.Crop
                        DisplayMode.MATCH_ORIENTATION -> ContentScale.Crop
                        DisplayMode.ADAPTIVE -> {
                            val photoWidth = result.drawable.intrinsicWidth
                            val photoHeight = result.drawable.intrinsicHeight
                            val frameWidth = context.resources.displayMetrics.widthPixels
                            val frameHeight = context.resources.displayMetrics.heightPixels
                            val photoIsPortrait = photoHeight > photoWidth
                            val frameIsPortrait = frameHeight > frameWidth
                            if (photoIsPortrait == frameIsPortrait) ContentScale.Crop else ContentScale.Fit
                        }
                    }
                    ready = true
                }
            )
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        alpha = if (ready) 1f else 0f,
        error = painterResource(R.drawable.broken_image),
        modifier = modifier
    )
}

@Composable
fun SlideScreen(
    uiState: AppUiState.Running,
    displayMode: DisplayMode,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(colorResource(R.color.black))
            .fillMaxSize(), contentAlignment = Alignment.Center
    ) {

        val nextPhoto = uiState.slideShowViewModel.nextPhoto
        if (!nextPhoto.endsWith("mp4", true) && !nextPhoto.endsWith("mov", true)) {
            val request = ImageRequest.Builder(LocalContext.current)
                .data(nextPhoto)
                .build()
            (LocalContext.current.applicationContext as ClearFrameApplication).imageLoader.enqueue(request)
        }

        AnimatedContent(
            targetState = uiState.slideShowViewModel.currentPhoto,
            label = "animated content",
            transitionSpec = {
                val slideDirection = when (uiState.slideShowViewModel.direction) {
                    SlideDirection.Forward -> AnimatedContentTransitionScope.SlideDirection.Left
                    SlideDirection.Backward -> AnimatedContentTransitionScope.SlideDirection.Right
                }

                val slideDuration = 1500
                val fadeDuration = 1000

                slideIntoContainer(
                    towards = slideDirection,
                    animationSpec = tween(slideDuration)
                ) + fadeIn(animationSpec = tween(fadeDuration)) togetherWith
                        slideOutOfContainer(
                            towards = slideDirection,
                            animationSpec = tween(slideDuration)
                        ) + fadeOut(animationSpec = tween(fadeDuration))
            }
        ) { filename ->
            val photo = uiState.slideShowViewModel.photos.find { it.filename == filename }
            if (photo?.isVideo == true) {
                VideoPlayer(
                    filename = filename,
                    isCurrent = filename == uiState.slideShowViewModel.currentPhoto
                )
            } else {
                if (filename == uiState.slideShowViewModel.currentPhoto) {
                    AdaptiveImage(
                        filename = uiState.slideShowViewModel.currentPhoto,
                        displayMode = displayMode,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AdaptiveImage(
                        filename = uiState.slideShowViewModel.previousPhoto,
                        displayMode = displayMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (uiState.slideShowViewModel.showNewPhotosToast) {
            Text(
                text = "New photos added!",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingScreenPreview() {
    ClearFrameTheme {
        LoadingScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorScreenPreview() {
    ClearFrameTheme {
        ErrorScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyScreenPreview() {
    ClearFrameTheme {
        EmptyScreen()
    }
}