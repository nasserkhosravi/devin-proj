package com.khosravi.devin.present.log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemImageLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.humanReadableByteCountSI
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.tool.adapter.FastBindingItem
import com.khosravi.devin.read.DevinImageFlagsApi
import com.wcabral.spantastic.bold
import com.wcabral.spantastic.foreground
import com.wcabral.spantastic.spantastic
import java.io.File


open class ImageLogItem(
    val calendar: CalendarProxy,
    val data: ImageLogItemData
) : FastBindingItem<ItemImageLogBinding>() {

    override val type: Int = R.id.vh_item_image_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemImageLogBinding {
        return ItemImageLogBinding.inflate(inflater)
    }

    override fun bindView(binding: ItemImageLogBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.apply {
            tvInfo.text = buildImageInfo(true)

            tvUrlCopy.text = spantastic {
                text("URL: ") { bold() }
                text(data.data.url)
            }
            tvUrlCopy.setOnClickListener {
                Toast.makeText(it.context, "URL Copied", Toast.LENGTH_SHORT).show()
                it.context.setClipboard(data.data.url)
            }

            if (data.data.isFailed()) {
                imgView.setImageBitmap(null)
            } else {
                Glide.with(context).asFile().load(data.data.url)
                    .into(object : CustomTarget<File>() {
                        override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                            val filePath: String = resource.path
                            val bitmap = BitmapFactory.decodeFile(filePath)
                            imgView.setImageBitmap(bitmap)
                            tvInfo.text = buildImageInfo(false, bitmap, resource.length())
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)
                            tvInfo.text = buildImageInfo(false, null, null)
                            imgView.setImageBitmap(null)
                        }
                    })
            }
        }
    }

    private fun ItemImageLogBinding.buildImageInfo(isLoading:Boolean, resource: Bitmap? = null, fileLength: Long? = null) = spantastic {
        if (isLoading) {
            text(getString(R.string.image_tag_loading)) {
                bold()
            }
            return@spantastic
        }
        text(getString(R.string.image_tag_status_prefix)) {
            bold()
        }

        val (text: String, textColor: Int) = getTextAndItsColor()
        text(text.plus("\n")) {
            bold()
            foreground(textColor)
        }

        text(getString(R.string.image_tag_name_prefix)) {
            bold()
        }
        text(data.data.name.plus("\n"))

        text(getString(R.string.image_tag_date_prefix)) {
            bold()
        }
        text(calendar.initIfNeed(data.datePresent).getFormatted().plus("\n"))

        text(getString(R.string.image_tag_time_prefix)) {
            bold()
        }
        text(calendar.initIfNeed(data.timePresent).getFormatted().plus("\n"))

        if (resource!=null && fileLength!=null){
            text(getString(R.string.image_tag_image_size_prefix)) {
                bold()
            }
            text("${resource.width}x${resource.height} \n")

            text(getString(R.string.image_tag_file_size_prefix)) {
                bold()
            }
            text(humanReadableByteCountSI(fileLength))
        }
    }

    private fun ItemImageLogBinding.getTextAndItsColor(): Pair<String, Int> {
        return when (data.data.status) {
            DevinImageFlagsApi.Status.SUCCEED -> {
                getString(R.string.image_status_success) to getColor(R.color.status_success)
            }

            DevinImageFlagsApi.Status.FAILED -> {
                getString(R.string.image_status_failed) to getColor(R.color.status_error)
            }

            else -> {
                "" to Color.BLACK
            }
        }
    }

}