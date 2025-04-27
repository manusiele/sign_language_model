// ImagePreprocessor.kt - Example for processing camera input for a TFLite model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImagePreprocessor {
    
    companion object {
        // Example values - adjust based on your model's requirements
        private const val IMAGE_WIDTH = 224
        private const val IMAGE_HEIGHT = 224
        private const val PIXEL_SIZE = 3 // RGB channels
        private const val BATCH_SIZE = 1
        private const val QUANTIZED = false // Set to true for quantized models
    }
    
    /**
     * Convert bitmap to ByteBuffer for model input
     */
    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Resize the bitmap to the required dimensions
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, true)
        
        // Calculate buffer size based on dimensions and pixel size
        val bufferSize = if (QUANTIZED) {
            BATCH_SIZE * IMAGE_WIDTH * IMAGE_HEIGHT * PIXEL_SIZE
        } else {
            BATCH_SIZE * IMAGE_WIDTH * IMAGE_HEIGHT * PIXEL_SIZE * 4 // float is 4 bytes
        }
        
        // Initialize ByteBuffer
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Fill ByteBuffer with pixel values
        val pixels = IntArray(IMAGE_WIDTH * IMAGE_HEIGHT)
        resizedBitmap.getPixels(pixels, 0, IMAGE_WIDTH, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
        
        var pixel = 0
        for (i in 0 until IMAGE_WIDTH) {
            for (j in 0 until IMAGE_HEIGHT) {
                val pixelValue = pixels[pixel++]
                
                if (QUANTIZED) {
                    // Quantized model (0-255)
                    inputBuffer.put((pixelValue shr 16 and 0xFF).toByte())
                    inputBuffer.put((pixelValue shr 8 and 0xFF).toByte())
                    inputBuffer.put((pixelValue and 0xFF).toByte())
                } else {
                    // Float model (0-1)
                    inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
                }
            }
        }
        
        // Reset position to start
        inputBuffer.rewind()
        return inputBuffer
    }
    
    /**
     * Process camera frame for model input
     */
    fun processCameraFrame(data: ByteArray, width: Int, height: Int, rotation: Int): ByteBuffer {
        // Convert YUV to bitmap
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        
        // Rotate if needed
        val rotatedBitmap = if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        
        // Convert to ByteBuffer
        return bitmapToByteBuffer(rotatedBitmap)
    }
    
    /**
     * Normalize a bitmap for model input (alternative approach)
     */
    fun normalizeImage(bitmap: Bitmap): FloatArray {
        // Resize the bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, true)
        
        // Initialize result array
        val result = FloatArray(IMAGE_WIDTH * IMAGE_HEIGHT * PIXEL_SIZE)
        
        // Extract RGB values
        val pixels = IntArray(IMAGE_WIDTH * IMAGE_HEIGHT)
        resizedBitmap.getPixels(pixels, 0, IMAGE_WIDTH, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
        
        // For each pixel
        var index = 0
        pixels.forEach { pixel ->
            // Extract RGB channels
            result[index++] = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f  // R normalized to [-1,1]
            result[index++] = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f   // G normalized to [-1,1]
            result[index++] = ((pixel and 0xFF) - 127.5f) / 127.5f         // B normalized to [-1,1]
        }
        
        return result
    }
}

// PostProcessor.kt - Example for processing model output

class PostProcessor {
    
    companion object {
        // Example for a classification model with labels
        private val LABELS = arrayOf(
            "label1", "label2", "label3", "label4", "label5"
            // Add more labels based on your model
        )
    }
    
    /**
     * Process classification results
     */
    fun processClassificationResult(outputBuffer: FloatArray): Pair<String, Float> {
        // Find the class with highest probability
        var maxIndex = 0
        var maxProb = outputBuffer[0]
        
        for (i in 1 until outputBuffer.size) {
            if (outputBuffer[i] > maxProb) {
                maxProb = outputBuffer[i]
                maxIndex = i
            }
        }
        
        // Return label and confidence
        return if (maxIndex < LABELS.size) {
            Pair(LABELS[maxIndex], maxProb)
        } else {
            Pair("Unknown", maxProb)
        }
    }
    
    /**
     * Process detection results (for object detection models)
     */
    fun processDetectionResult(
        locations: Array<Array<FloatArray>>,
        classes: Array<FloatArray>,
        scores: Array<FloatArray>,
        numDetections: FloatArray
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val detectionsCount = numDetections[0].toInt()
        
        // Process each detection
        for (i in 0 until detectionsCount) {
            // Get detection details
            val score = scores[0][i]
            val classId = classes[0][i].toInt()
            val label = if (classId < LABELS.size) LABELS[classId] else "Unknown"
            
            // Bounding box coordinates (normalized [0,1])
            val top = locations[0][i][0]
            val left = locations[0][i][1]
            val bottom = locations[0][i][2]
            val right = locations[0][i][3]
            
            // Create detection object
            if (score > 0.5f) { // Score threshold
                detections.add(
                    Detection(
                        label = label,
                        confidence = score,
                        boundingBox = RectF(left, top, right, bottom)
                    )
                )
            }
        }
        
        return detections
    }
    
    // Helper data classes
    data class Detection(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )
    
    data class RectF(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
