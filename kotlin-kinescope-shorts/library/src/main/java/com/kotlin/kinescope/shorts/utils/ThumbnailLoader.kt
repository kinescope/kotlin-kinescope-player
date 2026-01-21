package com.kotlin.kinescope.shorts.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object ThumbnailLoader {

    fun createPlaceholder(width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#1a1a1a"))

        val paint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }

        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawCircle(centerX, centerY, 30f, paint.apply { 
            color = Color.parseColor("#888888")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        })
        
        return bitmap
    }
}

