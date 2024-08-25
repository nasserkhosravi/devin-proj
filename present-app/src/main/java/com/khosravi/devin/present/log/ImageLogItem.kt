package com.khosravi.devin.present.log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.tool.adapter.FastBindingItem
import com.wcabral.spantastic.bold
import com.wcabral.spantastic.spantastic
import java.io.File
import java.text.CharacterIterator
import java.text.StringCharacterIterator


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
            tvInfo.text = buildImageInfo()

            tvUrlCopy.text = spantastic {
                text("URL: ") { bold() }
                text(data.data.url)
            }
            tvUrlCopy.setOnClickListener {
                Toast.makeText(it.context, "URL Copied", Toast.LENGTH_SHORT).show()
                it.context.setClipboard(data.data.url)
            }
//            root.setOnClickListener {
//                Toast.makeText(it.context, "Item Copied", Toast.LENGTH_SHORT).show()
//                it.context.setClipboard(data.data)
//            }
//            Glide.with(context).asBitmap().load(data.data.url).into(object : CustomTarget<Bitmap>() {
//                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
//                    tvInfo.text = buildImageInfo(resource)
//                    imgView.setImageBitmap(resource)
//                }
//
//                override fun onLoadCleared(placeholder: Drawable?) {}
//
//                override fun onLoadFailed(errorDrawable: Drawable?) {
//                    super.onLoadFailed(errorDrawable)
//                }
//            })
            Glide.with(context).asFile().load(data.data.url)
//                .addListener()
//                .preload()
                .into(object : CustomTarget<File>() {
                    override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                        val filePath: String = resource.path
                        val bitmap = BitmapFactory.decodeFile(filePath)
                        imgView.setImageBitmap(bitmap)
                        tvInfo.text = buildImageInfo(bitmap, resource.length())
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                })

//            imgView.load(data.data.url) {
//                listener(onError = { _, r ->
//                    r.throwable.printStackTrace()
//                }, onSuccess = { _, r ->
//                    r.request.sizeResolver.size()
//                })
//            }

        }
    }

    private fun ItemImageLogBinding.buildImageInfo(resource: Bitmap? = null, fileLength: Long? = null) = spantastic {
        val isLoading = resource == null
        if (isLoading) {
            text("Loading") {
                bold()
            }
            return@spantastic
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

        text("Image size: ") {
            bold()
        }
        text("${resource!!.width}x${resource.height} \n")

        text("File size: ") {
            bold()
        }
        text(humanReadableByteCountSI(fileLength!!))
    }

//    fun byteSizeOf(bitmap: Bitmap): Int {
//        return bitmap.getAllocationByteCount()
//    }

    private fun sizeOf(data: Bitmap): Int {
        return data.getByteCount()
    }

    private fun humanReadableByteCountSI(bytes: Long): String {
        var mBytes = bytes
        if (-1000 < mBytes && mBytes < 1000) {
            return "$mBytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (mBytes <= -999950 || mBytes >= 999950) {
            mBytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", mBytes / 1000.0, ci.current())
    }
}