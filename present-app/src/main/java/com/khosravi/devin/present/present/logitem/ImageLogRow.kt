package com.khosravi.devin.present.present.logitem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.humanReadableByteCountSI
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.uikit.theme.spacing
import com.khosravi.devin.read.DevinImageFlagsApi
import java.io.File

private sealed interface ImageLoadState {
    data object Loading : ImageLoadState
    data class Loaded(val bitmap: Bitmap, val fileLength: Long) : ImageLoadState
    data object Failed : ImageLoadState
}

@Composable
fun ImageLogRow(calendar: CalendarProxy, data: ImageLogItemData) {
    val context = LocalContext.current
    var loadState by remember(data.data.url) {
        mutableStateOf<ImageLoadState>(if (data.data.isFailed()) ImageLoadState.Failed else ImageLoadState.Loading)
    }

    DisposableEffect(data.data.url) {
        if (data.data.isFailed()) {
            return@DisposableEffect onDispose {}
        }
        val target = object : CustomTarget<File>() {
            override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                val bitmap = BitmapFactory.decodeFile(resource.path)
                loadState = if (bitmap != null) ImageLoadState.Loaded(bitmap, resource.length()) else ImageLoadState.Failed
            }

            override fun onLoadCleared(placeholder: Drawable?) {}

            override fun onLoadFailed(errorDrawable: Drawable?) {
                loadState = ImageLoadState.Failed
            }
        }
        Glide.with(context).asFile().load(data.data.url).into(target)
        onDispose {
            Glide.with(context).clear(target)
        }
    }

    val (statusText, statusColorRes) = when (data.data.status) {
        DevinImageFlagsApi.Status.SUCCEED -> stringResource(R.string.image_status_success) to R.color.status_success
        DevinImageFlagsApi.Status.FAILED -> stringResource(R.string.image_status_failed) to R.color.status_error
        else -> "" to android.R.color.black
    }

    // stringResource() is @Composable and can't be called inside the buildAnnotatedString{} lambda below
    // (that lambda's receiver type isn't @Composable) — resolve every string here first.
    val statusPrefix = stringResource(R.string.image_tag_status_prefix)
    val loadingText = stringResource(R.string.image_tag_loading)
    val namePrefix = stringResource(R.string.image_tag_name_prefix)
    val datePrefix = stringResource(R.string.image_tag_date_prefix)
    val timePrefix = stringResource(R.string.image_tag_time_prefix)
    val imageSizePrefix = stringResource(R.string.image_tag_image_size_prefix)
    val fileSizePrefix = stringResource(R.string.image_tag_file_size_prefix)

    Column(modifier = Modifier.fillMaxWidth()) {
        Divider()
        val state = loadState
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small)
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(statusPrefix) }
                    if (state is ImageLoadState.Loading) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(loadingText) }
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorResource(statusColorRes))) { append(statusText) }
                        append("\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(namePrefix) }
                        append(data.data.name + "\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(datePrefix) }
                        append(calendar.initIfNeed(data.datePresent).getFormatted() + "\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(timePrefix) }
                        append(calendar.initIfNeed(data.timePresent).getFormatted())
                        if (state is ImageLoadState.Loaded) {
                            append("\n")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(imageSizePrefix) }
                            append("${state.bitmap.width}x${state.bitmap.height} \n")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fileSizePrefix) }
                            append(humanReadableByteCountSI(state.fileLength))
                        }
                    }
                }
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("URL: ") }
                    append(data.data.url)
                },
                modifier = Modifier.clickable {
                    Toast.makeText(context, "URL Copied", Toast.LENGTH_SHORT).show()
                    context.setClipboard(data.data.url)
                }
            )

            if (state is ImageLoadState.Loaded) {
                Image(bitmap = state.bitmap.asImageBitmap(), contentDescription = data.data.name)
            }
        }
    }
}
