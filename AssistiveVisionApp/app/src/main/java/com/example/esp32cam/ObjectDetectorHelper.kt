package com.example.esp32cam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FINAL BUILD-READY ObjectDetectorHelper.
 * Removed all Java reflection and resolved naming conflicts.
 */
class ObjectDetectorHelper(
    val context: Context,
    val modelName: String = "ssd_mobilenet_v2_coco_quant_postprocess.tflite",
    var threshold: Float = 0.3f
) {

    private var interpreter: Interpreter? = null
    private val inputSize = 300 

    // Output buffers for TFLite (matches shape [1, 20, X])
    private val outputLocations = Array(1) { Array(20) { FloatArray(4) } }
    private val outputClasses = Array(1) { FloatArray(20) }
    private val outputScores = Array(1) { FloatArray(20) }
    private val numDetections = FloatArray(1)

    init {
        try {
            val model = FileUtil.loadMappedFile(context, modelName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detect(bitmap: Bitmap): DetectionOutput {
        if (interpreter == null) return DetectionOutput(emptyList(), 0)

        val startTime = SystemClock.uptimeMillis()

        // 1. Pre-process Image
        val tensorImage = TensorImage(org.tensorflow.lite.DataType.UINT8)
        tensorImage.load(bitmap)

        // Using ResizeMethod alias to avoid ambiguity with java.lang.reflect.Method
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val processedImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = outputLocations
        outputs[1] = outputClasses
        outputs[2] = outputScores
        outputs[3] = numDetections

        interpreter?.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputs)

        val inferenceTime = SystemClock.uptimeMillis() - startTime

        // 3. Post-process Results
        val results = mutableListOf<DetectionResult>()
        var personDetected = false

        for (i in 0 until numDetections[0].toInt()) {
            val confidence = outputScores[0][i]
            if (confidence < threshold) continue

            // Index 0 is background, Index 1 is Person in COCO
            val classId = outputClasses[0][i].toInt() + 1
            
            val box = outputLocations[0][i]
            val rect = RectF(box[1], box[0], box[3], box[2])

            if (classId == 1) { 
                results.add(DetectionResult(rect, confidence, classId, false))
                personDetected = true
            }
        }

        // Wall / Obstacle Heuristic
        if (!personDetected) {
            for (i in 0 until numDetections[0].toInt()) {
                val confidence = outputScores[0][i]
                if (confidence < threshold) continue

                val box = outputLocations[0][i]
                val rect = RectF(box[1], box[0], box[3], box[2])
                
                val width = rect.right - rect.left
                val height = rect.bottom - rect.top
                val area = width * height

                if (area > 0.40f) {
                    results.add(DetectionResult(rect, confidence, -1, true))
                    break 
                }
            }
        }

        return DetectionOutput(results, inferenceTime)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

/**
 * Single source of truth for detection models in this project.
 */
data class DetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val classIndex: Int,
    val isWall: Boolean = false
)

data class DetectionOutput(
    val results: List<DetectionResult>,
    val inferenceTime: Long
)
