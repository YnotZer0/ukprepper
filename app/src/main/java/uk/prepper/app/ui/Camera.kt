package uk.prepper.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import uk.prepper.app.Nav
import uk.prepper.app.Screen
import java.io.File
import java.util.concurrent.Executors

/** Offline barcode decoding with ZXing on the camera's luminance (Y) plane. */
private class BarcodeAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                ),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }
    @Volatile private var done = false

    override fun analyze(image: ImageProxy) {
        try {
            if (done) return
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val rowStride = plane.rowStride
            val buf = plane.buffer
            buf.rewind()
            val y = ByteArray(w * h)
            if (rowStride == w) {
                buf.get(y, 0, w * h)
            } else {
                for (row in 0 until h) {
                    buf.position(row * rowStride)
                    buf.get(y, row * w, w)
                }
            }
            // Try as delivered, then rotated 90° (phone held upright = sensor sideways).
            val text = decode(y, w, h) ?: decode(rotate90(y, w, h), h, w)
            if (text != null) {
                done = true
                onResult(text)
            }
        } catch (e: Exception) {
            // ignore a bad frame
        } finally {
            image.close()
        }
    }

    private fun decode(data: ByteArray, w: Int, h: Int): String? = try {
        val src = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        var i = 0
        for (x in 0 until w) for (yy in h - 1 downTo 0) out[i++] = src[yy * w + x]
        return out
    }
}

@Composable
private fun CameraPermission(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) content() else Column(Modifier.padding(16.dp)) {
        Text("Camera permission is needed for scanning and photos.")
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
    }
}

@Composable
private fun CameraView(modifier: Modifier, analyzer: ImageAnalysis.Analyzer?, capture: ImageCapture?) {
    val ctx = LocalContext.current
    val owner = remember { ctx.findActivity() }
    val previewView = remember { PreviewView(ctx) }
    DisposableEffect(Unit) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cases = mutableListOf<UseCase>(preview)
            if (analyzer != null) {
                val ia = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                ia.setAnalyzer(analysisExecutor, analyzer)
                cases += ia
            }
            if (capture != null) cases += capture
            if (owner != null) {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray())
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun ScanScreen(nav: Nav) {
    val ctx = LocalContext.current
    var manual by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Header("Scan barcode", onBack = { nav.back() })
        CameraPermission {
            val main = remember { ContextCompat.getMainExecutor(ctx) }
            val analyzer = remember {
                BarcodeAnalyzer { code -> main.execute { nav.replace(Screen.EditItem(barcode = code)) } }
            }
            Box(Modifier.fillMaxWidth().height(380.dp)) {
                CameraView(Modifier.fillMaxSize(), analyzer, null)
            }
            Text(
                "Hold the barcode flat and steady, about 15 cm away. Works offline: the first scan of a product " +
                    "asks you for its details once; later scans fill them in.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            Field("Or type the barcode number", manual, { manual = it.filter { c -> c.isDigit() } }, number = true)
            Button(onClick = { if (manual.isNotBlank()) nav.replace(Screen.EditItem(barcode = manual)) }) { Text("Use number") }
        }
    }
}

@Composable
fun PhotoCapture(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    ).build()
            )
            .build()
    }
    Column(Modifier.fillMaxSize()) {
        Header("Photo", onBack = onCancel)
        CameraPermission {
            Box(Modifier.fillMaxWidth().height(460.dp)) { CameraView(Modifier.fillMaxSize(), null, capture) }
            error?.let { Text(it, color = Red, modifier = Modifier.padding(8.dp)) }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
                    val file = File(dir, "item-${System.currentTimeMillis()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                busy = false
                                onDone(file.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                busy = false
                                error = exception.message ?: "Capture failed"
                            }
                        },
                    )
                }, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Capture") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
