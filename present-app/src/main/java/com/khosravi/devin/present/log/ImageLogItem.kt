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
import com.khosravi.devin.write.api.DevinImageFlagsApi
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
                        tvInfo.text = buildImageInfo(false,null,null)
                    }
                })
        }
    }

    private fun ItemImageLogBinding.buildImageInfo(isLoading:Boolean, resource: Bitmap? = null, fileLength: Long? = null) = spantastic {
        if (isLoading) {
            text("Loading") {
                bold()
            }
            return@spantastic
        }
        text("Status: ") {
            bold()
        }

        val (text: String, textColor: Int) = getTextAndItsColor()
        text(text.plus("\n")) {
            bold()
            foreground(textColor)
        }

        text("Name: ") {
            bold()
        }
        text(data.data.name.plus("\n"))

        text("Date: ") {
            bold()
        }
        text(calendar.initIfNeed(data.datePresent).getFormatted().plus("\n"))

        text("Time: ") {
            bold()
        }
        text(calendar.initIfNeed(data.timePresent).getFormatted().plus("\n"))

        if (resource!=null && fileLength!=null){
            text("Image size: ") {
                bold()
            }
            text("${resource.width}x${resource.height} \n")

            text("File size: ") {
                bold()
            }
            text(humanReadableByteCountSI(fileLength))
        }
    }

    private fun ItemImageLogBinding.getTextAndItsColor(): Pair<String, Int> {
        return when (data.data.status) {
            DevinImageFlagsApi.Status.SUCCEED -> {
                "Succeed" to getColor(R.color.colorStatusSuccess)
            }

            DevinImageFlagsApi.Status.FAILED -> {
                "Failed" to getColor(R.color.colorStatusError)
            }

            else -> {
                "" to Color.BLACK
            }
        }
    }

}