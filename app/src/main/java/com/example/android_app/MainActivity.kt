package com.example.android_app

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.android_app.ui.theme.AndroidappTheme

/**
 * @param label Название объекта
 * @param confidence Уверенность нейросети
 * @param boundingBox Граница
 */
data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidappTheme {
                ObjectDetectionScreen()
            }
        }
    }
}

@Composable
fun ObjectDetectionScreen() {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }

    //выбор изображения
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            //TODO: НЕЙРОСЕТЬ ДОБАВИТЬ
            detections = runObjectDetection(uri)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Object Detection App",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            //кнопка импорта изображения
            Button(onClick = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Text(text = "Импортировать изображение")
            }

            Spacer(modifier = Modifier.height(16.dp))

            //отображение изображения и результатов детекции
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    //слой отрисовки рамок (bounding boxes)
                    DetectionOverlay(detections = detections)
                } else {
                    Text(text = "Изображение не выбрано")
                }
            }
        }
    }
}

/**
 * Слой для отрисовки рамок и подписей поверх изображения.
 */
@Composable
fun DetectionOverlay(detections: List<Detection>) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        detections.forEach { detection ->
            //координаты под размер Canvas
            val rect = Rect(
                left = detection.boundingBox.left * canvasWidth,
                top = detection.boundingBox.top * canvasHeight,
                right = detection.boundingBox.right * canvasWidth,
                bottom = detection.boundingBox.bottom * canvasHeight
            )

            //рамка объекта
            drawRect(
                color = Color.Red,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 4f)
            )

            //текст подписи
            val labelText = "${detection.label} (${(detection.confidence * 100).toInt()}%)"
            val textLayoutResult = textMeasurer.measure(labelText, style = style)
            
            drawRect(
                color = Color.Red.copy(alpha = 0.7f),
                topLeft = Offset(rect.left, rect.top - textLayoutResult.size.height),
                size = Size(textLayoutResult.size.width.toFloat() + 8f, textLayoutResult.size.height.toFloat())
            )

            //надпись
            drawText(
                textMeasurer = textMeasurer,
                text = labelText,
                style = style.copy(color = Color.White),
                topLeft = Offset(rect.left + 4f, rect.top - textLayoutResult.size.height)
            )
        }
    }
}

/**
 * TODO: Заглушка для работы нейросети.
 * yolo или другач модель.
 */
fun runObjectDetection(imageUri: Uri): List<Detection> {
    //вызов модели
    //обработка результатов
    
    //рамки (границы)
    return listOf(
        Detection(
            label = "Дерево",
            confidence = 0.95f,
            boundingBox = Rect(0.1f, 0.2f, 0.4f, 0.8f)
        ),
        Detection(
            label = "Автомобиль",
            confidence = 0.88f,
            boundingBox = Rect(0.5f, 0.5f, 0.9f, 0.9f)
        )
    )
}
