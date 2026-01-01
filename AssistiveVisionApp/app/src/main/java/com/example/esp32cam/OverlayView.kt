package com.example.esp32cam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom View to draw bounding boxes and labels over the camera frame.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val personPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val obstaclePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    private var detections: List<DetectionResult> = emptyList()

    fun setResults(results: List<DetectionResult>) {
        this.detections = results
        invalidate() // Redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (detection in detections) {
            val paint = if (detection.isWall) obstaclePaint else personPaint
            val label = if (detection.isWall) "Wall / Obstacle" else "Person"

            // Scale bounding box to view coordinates
            val rect = RectF(
                detection.boundingBox.left * width,
                detection.boundingBox.top * height,
                detection.boundingBox.right * width,
                detection.boundingBox.bottom * height
            )

            canvas.drawRect(rect, paint)
            canvas.drawText("${label} (${(detection.confidence * 100).toInt()}%)", rect.left, rect.top - 10f, textPaint)
        }
    }
}
