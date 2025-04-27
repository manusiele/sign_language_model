// ModelManager.kt - Handles downloading, caching, and loading the TFLite model

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_FILENAME = "model.tflite"
        private const val MODEL_VERSION = "1.0" // For version tracking
        private const val MODEL_URL = "https://raw.githubusercontent.com/username/repo/main/model.tflite"
    }
    
    private var interpreter: Interpreter? = null
    
    /**
     * Main function to ensure model is loaded - downloads if needed
     */
    suspend fun loadModel(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if model exists and load it
                if (isModelCached()) {
                    Log.d(TAG, "Model exists locally, loading from cache")
                    loadCachedModel()
                } else {
                    Log.d(TAG, "Model not found locally, downloading from GitHub")
                    if (!downloadAndCacheModel()) {
                        Log.e(TAG, "Failed to download model")
                        return@withContext false
                    }
                    loadCachedModel()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Check if the model is already cached
     */
    private fun isModelCached(): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Download model from GitHub and cache it locally
     */
    private fun downloadAndCacheModel(): Boolean {
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val modelFile = File(context.filesDir, MODEL_FILENAME)
                
                FileOutputStream(modelFile).use { output ->
                    inputStream.copyTo(output)
                }
                
                // Save version info if needed
                saveModelVersion()
                
                Log.d(TAG, "Model downloaded and cached successfully")
                return true
            } else {
                Log.e(TAG, "Server returned error code: $responseCode")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model: ${e.message}")
            return false
        }
    }
    
    /**
     * Save the current model version
     */
    private fun saveModelVersion() {
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE).edit()
            .putString("model_version", MODEL_VERSION)
            .putLong("model_timestamp", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Load the model from the local cache
     */
    private fun loadCachedModel() {
        try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            interpreter = Interpreter(loadModelFile(modelFile))
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached model: ${e.message}")
            throw e
        }
    }
    
    /**
     * Load model file into a MappedByteBuffer
     */
    private fun loadModelFile(modelFile: File): MappedByteBuffer {
        val fileDescriptor = FileInputStream(modelFile).fd
        val fileChannel = FileInputStream(fileDescriptor).channel
        val startOffset = 0L
        val declaredLength = modelFile.length()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Run inference with the loaded model
     * (Example for image classification)
     */
    fun runInference(inputBuffer: ByteBuffer): FloatArray {
        if (interpreter == null) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }
        
        // Example for image classification model with 1000 classes
        val outputBuffer = FloatArray(1000)
        
        interpreter?.run(inputBuffer, outputBuffer)
        
        return outputBuffer
    }
    
    /**
     * Check if a model update is available
     * (This is a simplified example - you might want to check GitHub API for version info)
     */
    suspend fun checkForModelUpdate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                val currentVersion = prefs.getString("model_version", "") ?: ""
                val lastUpdate = prefs.getLong("model_timestamp", 0)
                
                // Simple check: force update if more than 7 days old
                val forceUpdate = System.currentTimeMillis() - lastUpdate > 7 * 24 * 60 * 60 * 1000
                
                // In a real app, you'd check with a server for the latest version
                // This is just a placeholder
                val newVersionAvailable = currentVersion != MODEL_VERSION || forceUpdate
                
                if (newVersionAvailable) {
                    Log.d(TAG, "Model update available, downloading new version")
                    downloadAndCacheModel()
                    loadCachedModel()
                    return@withContext true
                }
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for model update: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

// Example usage in an Activity or ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(context: Context) : ViewModel() {
    
    private val modelManager = ModelManager(context)
    
    init {
        // Load the model when ViewModel is created
        viewModelScope.launch {
            val success = modelManager.loadModel()
            if (success) {
                // Model loaded successfully, ready for inference
            } else {
                // Handle error
            }
        }
    }
    
    // Example function for image analysis
    fun analyzeImage(inputBuffer: ByteBuffer) {
        try {
            val results = modelManager.runInference(inputBuffer)
            // Process results
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // Check for model updates periodically
    fun checkForModelUpdates() {
        viewModelScope.launch {
            val updated = modelManager.checkForModelUpdate()
            if (updated) {
                // Model was updated
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        modelManager.close()
    }
}
