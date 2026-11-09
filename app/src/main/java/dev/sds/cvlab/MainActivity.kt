package dev.sds.cvlab

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.setPadding
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var preview: DetectionCanvas
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var scanButton: Button
    private var selectedVideo: Uri? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            status.text = "Video selected · ready for offline analysis"
            scanButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 14, 14)
        window.navigationBarColor = android.graphics.Color.rgb(12, 14, 14)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20)
            setBackgroundColor(android.graphics.Color.rgb(12, 14, 14))
        }

        val heading = TextView(this).apply {
            text = "CV LAB"
            textSize = 25f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "ON-DEVICE VIDEO OBJECT DETECTION"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(android.graphics.Color.rgb(32, 211, 151))
            setPadding(0, 4, 0, 18)
        }

        preview = DetectionCanvas(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(18, 20, 20))
        }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        status = TextView(this).apply {
            text = "Choose a prerecorded video to begin"
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 16, 0, 12)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = ProgressBar.GONE
        }
        val chooseButton = Button(this).apply {
            text = "Choose video"
            setOnClickListener { picker.launch(arrayOf("video/*")) }
        }
        scanButton = Button(this).apply {
            text = "Analyze offline"
            isEnabled = false
            setOnClickListener { selectedVideo?.let(::analyzeVideo) }
        }

        root.addView(heading)
        root.addView(subtitle)
        root.addView(status)
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(chooseButton)
        root.addView(scanButton)
        setContentView(root)
    }

    private fun analyzeVideo(uri: Uri) {
        scanButton.isEnabled = false
        progress.visibility = ProgressBar.VISIBLE
        progress.progress = 0
        status.text = "Preparing video…"

        worker.execute {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                if (durationMs <= 0L) error("Could not read video duration")

                val options = ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
                val detector = ObjectDetection.getClient(options)
                val sampleCount = minOf(24, max(1, (durationMs / 750L).toInt() + 1))
                var totalObjects = 0

                for (index in 0 until sampleCount) {
                    val timeMs = if (sampleCount == 1) 0L
                        else (durationMs * index / sampleCount).coerceAtMost(durationMs - 1)
                    val source = retriever.getFrameAtTime(
                        timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue
                    val bitmap = resizeForInference(source)
                    if (bitmap !== source) source.recycle()

                    val results = try {
                        Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                    } catch (exception: Exception) {
                        bitmap.recycle()
                        throw exception
                    }
                    totalObjects += results.size
                    val current = index + 1
                    runOnUiThread {
                        preview.showFrame(bitmap, results)
                        progress.progress = current * 100 / sampleCount
                        status.text = "Frame $current/$sampleCount · $totalObjects objects detected"
                    }
                    Thread.sleep(90)
                }

                detector.close()
                runOnUiThread {
                    status.text = "Finished · $totalObjects detections across sampled frames"
                    scanButton.isEnabled = true
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    status.text = "Analysis failed: ${exception.localizedMessage ?: "unknown error"}"
                    scanButton.isEnabled = true
                }
            } finally {
                retriever.release()
                runOnUiThread { progress.visibility = ProgressBar.GONE }
            }
        }
    }

    private fun resizeForInference(source: Bitmap): Bitmap {
        val maxDimension = 960f
        val factor = minOf(1f, maxDimension / max(source.width, source.height).toFloat())
        if (factor >= 1f) return source
        val matrix = Matrix().apply { postScale(factor, factor) }
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
