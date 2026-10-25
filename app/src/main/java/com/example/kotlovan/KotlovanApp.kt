package com.example.kotlovan

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory

class KotlovanApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            // Отключаем ImageDecoder, используем BitmapFactory
//            .imageDecoderEnabled(false) 
            .allowHardware(false) // Отключаем Hardware Bitmap
            .crossfade(false)
            .build()
    }
}