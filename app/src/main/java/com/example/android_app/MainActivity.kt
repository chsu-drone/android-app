package com.example.android_app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android_app.ui.theme.AndroidappTheme
import java.nio.FloatBuffer

//модель
data class Detection(val label: String, val confidence: Float, val boundingBox: Rect)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AndroidappTheme { MainScreen() } }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var ratio by remember { mutableStateOf(1f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val loaded = loadBitmap(context, uri)
            bitmap = loaded
            ratio = loaded.width.toFloat() / loaded.height.toFloat()
            detections = runDetection(context, loaded)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Дрон-детектор", style = MaterialTheme.typography.headlineMedium)
            
            Spacer(Modifier.height(16.dp))

            Button(onClick = { 
                picker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("Импорт");
            }

            Spacer(Modifier.height(16.dp))

            //область для фото и рамки
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val screenRatio = maxWidth / maxHeight
                        val matchHeight = ratio < screenRatio
                        
                        Box(Modifier
                            .fillMaxSize()
                            .aspectRatio(ratio, matchHeightConstraintsFirst = matchHeight)
                            .align(Alignment.Center)
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            DetectionOverlay(detections)
                        }
                    }
                } else {
                    Text("Фото не выбрано")
                }
            }
        }
    }
}

@Composable
fun DetectionOverlay(detections: List<Detection>) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        detections.forEach { d ->
            val r = Rect(
                d.boundingBox.left * size.width,
                d.boundingBox.top * size.height,
                d.boundingBox.right * size.width,
                d.boundingBox.bottom * size.height
            )
            drawRect(Color.Red,
                Offset(r.left, r.top),
                Size(r.width, r.height),
                style = Stroke(4f))
            
            val text = "${d.label} ${(d.confidence * 100).toInt()}%"
            val style = TextStyle(color = Color.White, fontSize = 10.sp, background = Color.Red.copy(alpha = 0.7f))
            val layout = measurer.measure(text, style = style)
            drawText(measurer, text, Offset(r.left, r.top - layout.size.height), style = style)
        }
    }
}

private var ortSession: OrtSession? = null

//классы модели (не классы которые исопльзуем!!!)
private val visDroneLabels = listOf(
    "pedestrian", //пешеход
    "people", //люди
    "bicycle", //велосипед
    "car", //машина
    "van", //микроавтобус
    "truck", //камаз (типо)
    "tricycle", //треёхоклсный
    "awning-tricycle", //рикша
    "bus", //автобус
    "motor" //мотоцикл (навреное)
)

//распознавнаие объектов (главная функиця)
fun runDetection(context: Context, bitmap: Bitmap): List<Detection> {
    val env = OrtEnvironment.getEnvironment()
    
    //загрузка модели из файла
    if (ortSession == null) {
        ortSession = env.createSession(
            context.assets.open
                ("best.onnx").readBytes())
    }

    //классы для распознования!!!! (из файла)
    val userLabels = context.assets.open("classes.name").bufferedReader().readLines().map { it.trim() }.toSet()

    //подготовка картинок
    //для yolo
    val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
    val buffer = FloatBuffer.allocate(1 * 3 * 640 * 640)
    val pixels = IntArray(640 * 640)
    resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)

    //заполняем буфер: переводим пиксели в формат Float (0..1)
    //данные идут по каналам: сначала все R, потом G, потом B
    for (i in 0 until 640 * 640) {
        val p = pixels[i]
        buffer.put(i, ((p shr 16) and 0xFF) / 255f) //красный
        buffer.put(i + 409600, ((p shr 8) and 0xFF) / 255f) //зеленый
        buffer.put(i + 819200, (p and 0xFF) / 255f) //синий
    }
    buffer.rewind()

    //запуск модели
    val input = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, 640, 640))
    val result = ortSession?.run(mapOf("images" to input))
    
    val all = mutableListOf<Detection>()
    result?.use {
        //выход
        val out = (it[0].value as Array<*>)[0] as Array<FloatArray>
        
        for (i in 0 until 8400) {
            var maxScore = 0f
            var classId = -1
            
            //поиск по классам (берём с высокой вероянтснобю)
            for (j in 0 until 10) {
                val score = out[j + 4][i]
                if (score > maxScore) {
                    maxScore = score
                    classId = j
                }
            }

            //мелкие объекты
            if (classId != -1 && maxScore > 0.35f) {
                var label = visDroneLabels[classId]
                
                //в классах есть пешеходы и люди - решил их объединять
                if ((label == "pedestrian" || label == "people") && userLabels.contains("people")) {
                    label = "people"
                }

                //подсчёт корриднат у классов
                if (userLabels.contains(label)) {
                    val cx = out[0][i] / 640f //x
                    val cy = out[1][i] / 640f //y
                    val w = out[2][i] / 640f  //ширина
                    val h = out[3][i] / 640f  //высота
                    
                    all.add(Detection(label,
                        maxScore,
                        Rect(
                            cx - w/2,
                            cy - h/2,
                            cx + w/2,
                            cy + h/2)))
                }
            }
        }
    }
    //совпадающие рамки
    return applyNMS(all)
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap {
    val src = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.createSource(context.contentResolver, uri)
    } else {
        @Suppress("DEPRECATION")
        return MediaStore.Images.Media.getBitmap(
            context.contentResolver,
            uri).copy(
            Bitmap.Config.ARGB_8888,
            true)
    }
    return ImageDecoder.decodeBitmap(src).copy(Bitmap.Config.ARGB_8888,
        true)
}

//non maximum suppression оставляет только рамку с самой высокой уверенностью.
private fun applyNMS(list: List<Detection>): List<Detection> {
    val sorted = list.sortedByDescending { it.confidence }
    val res = mutableListOf<Detection>()
    val skip = BooleanArray(sorted.size)
    for (i in sorted.indices) {
        if (skip[i]) continue
        res.add(sorted[i])
        for (j in i + 1 until sorted.size) {
            val r1 = sorted[i].boundingBox
            val r2 = sorted[j].boundingBox
            val inter = Rect(maxOf(r1.left, r2.left), maxOf(r1.top, r2.top), minOf(r1.right, r2.right), minOf(r1.bottom, r2.bottom))
            val iArea = if (inter.width > 0 && inter.height > 0) inter.width * inter.height else 0f
            val uArea = (r1.width * r1.height) + (r2.width * r2.height) - iArea
            if (iArea / uArea > 0.45f) skip[j] = true
        }
    }
    return res
}
