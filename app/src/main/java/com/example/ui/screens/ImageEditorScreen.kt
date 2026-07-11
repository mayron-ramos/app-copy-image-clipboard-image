package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.CopiedImage
import com.example.util.ClipboardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

@Composable
fun ImageEditorScreen(
    image: CopiedImage,
    onDismiss: () -> Unit,
    onSave: (newPath: String, replaceOriginal: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveChoiceDialog by remember { mutableStateOf(false) }

    // Load original bitmap on launch
    LaunchedEffect(image.id) {
        withContext(Dispatchers.IO) {
            try {
                originalBitmap = BitmapFactory.decodeFile(image.localFilePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (originalBitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF8AB4F8))
        }
        return
    }

    // Active Editor parameters
    var currentEditorTab by remember { mutableStateOf(0) } // 0: Rotate, 1: Filters, 2: Adjustments, 3: Paint, 4: Crop
    var rotation by remember { mutableStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf("ORIGINAL") }
    
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var selectedAdjustment by remember { mutableStateOf("BRIGHTNESS") } // "BRIGHTNESS" or "CONTRAST"

    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushThickness by remember { mutableStateOf(10f) }
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }

    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }
    var dragMode by remember { mutableStateOf<String?>(null) }

    // Undo/Redo Stacks
    val globalUndoStack = remember { mutableStateListOf<EditorStateSnapshot>() }
    val globalRedoStack = remember { mutableStateListOf<EditorStateSnapshot>() }
    var sliderStartSnapshot by remember { mutableStateOf<EditorStateSnapshot?>(null) }

    fun captureCurrentState(): EditorStateSnapshot {
        return EditorStateSnapshot(
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            filterType = filterType,
            brightness = brightness,
            contrast = contrast,
            strokes = strokes.toList(),
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom
        )
    }

    fun pushStateToUndo() {
        globalUndoStack.add(captureCurrentState())
        globalRedoStack.clear()
    }

    fun applyState(state: EditorStateSnapshot) {
        rotation = state.rotation
        flipHorizontal = state.flipHorizontal
        flipVertical = state.flipVertical
        filterType = state.filterType
        brightness = state.brightness
        contrast = state.contrast
        strokes.clear()
        strokes.addAll(state.strokes)
        cropLeft = state.cropLeft
        cropTop = state.cropTop
        cropRight = state.cropRight
        cropBottom = state.cropBottom
    }

    fun performGlobalUndo() {
        if (globalUndoStack.isNotEmpty()) {
            val last = globalUndoStack.removeAt(globalUndoStack.size - 1)
            globalRedoStack.add(captureCurrentState())
            applyState(last)
        }
    }

    fun performGlobalRedo() {
        if (globalRedoStack.isNotEmpty()) {
            val next = globalRedoStack.removeAt(globalRedoStack.size - 1)
            globalUndoStack.add(captureCurrentState())
            applyState(next)
        }
    }

    fun applyAspectCrop(aspectRatio: Float) {
        val bitmapWidth = originalBitmap!!.width.toFloat()
        val bitmapHeight = originalBitmap!!.height.toFloat()
        val isRotated90 = (rotation.toInt() % 180 != 0)
        
        val currentW = if (isRotated90) bitmapHeight else bitmapWidth
        val currentH = if (isRotated90) bitmapWidth else bitmapHeight
        
        val imageAspect = currentW / currentH
        
        if (aspectRatio > imageAspect) {
            val targetHeight = currentW / aspectRatio
            val normHeight = targetHeight / currentH
            val border = (1f - normHeight) / 2f
            cropTop = border.coerceIn(0f, 0.45f)
            cropBottom = border.coerceIn(0f, 0.45f)
            cropLeft = 0f
            cropRight = 0f
        } else {
            val targetWidth = currentH * aspectRatio
            val normWidth = targetWidth / currentW
            val border = (1f - normWidth) / 2f
            cropLeft = border.coerceIn(0f, 0.45f)
            cropRight = border.coerceIn(0f, 0.45f)
            cropTop = 0f
            cropBottom = 0f
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF8AB4F8),
                onPrimary = Color(0xFF202124),
                background = Color(0xFF121212),
                surface = Color(0xFF202124),
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(0xFF121212),
                topBar = {
                    Surface(color = Color(0xFF121212), tonalElevation = 0.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = onDismiss, enabled = !isSaving) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { performGlobalUndo() },
                                    enabled = globalUndoStack.isNotEmpty() && !isSaving
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Deshacer",
                                        tint = if (globalUndoStack.isNotEmpty()) Color(0xFF8AB4F8) else Color.Gray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { performGlobalRedo() },
                                    enabled = globalRedoStack.isNotEmpty() && !isSaving
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Redo,
                                        contentDescription = "Rehacer",
                                        tint = if (globalRedoStack.isNotEmpty()) Color(0xFF8AB4F8) else Color.Gray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showSaveChoiceDialog = true },
                                enabled = !isSaving
                            ) {
                                Text("Guardar", fontWeight = FontWeight.ExtraBold, color = Color(0xFF8AB4F8))
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = Color(0xFF1F1F1F),
                        tonalElevation = 4.dp,
                        modifier = Modifier.navigationBarsPadding() // CRITICAL FIX: Ensure controls stay completely above gesture nav bar/pill!
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. ACTIVE PANEL AREA (Transform, Filters, or Adjustments)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 165.dp)
                                    .animateContentSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (currentEditorTab) {
                                    0 -> {
                                        // ROTATE/TRANSFORM
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Rotate
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { 
                                                    pushStateToUndo()
                                                    rotation = (rotation + 90f) % 360f 
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .background(Color(0xFF2D2D2D), shape = CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.RotateRight,
                                                        contentDescription = "Rotar",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Rotar 90°",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }

                                            // Flip Horizontal
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { 
                                                    pushStateToUndo()
                                                    flipHorizontal = !flipHorizontal 
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .background(if (flipHorizontal) Color(0xFF8AB4F8) else Color(0xFF2D2D2D), shape = CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CompareArrows,
                                                        contentDescription = "Espejo Horizontal",
                                                        tint = if (flipHorizontal) Color(0xFF202124) else Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Espejo H",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }

                                            // Flip Vertical
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { 
                                                    pushStateToUndo()
                                                    flipVertical = !flipVertical 
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .background(if (flipVertical) Color(0xFF8AB4F8) else Color(0xFF2D2D2D), shape = CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowUpward,
                                                        contentDescription = "Espejo Vertical",
                                                        tint = if (flipVertical) Color(0xFF202124) else Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Espejo V",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                    1 -> {
                                        // FILTERS
                                        val filterList = listOf(
                                            "ORIGINAL" to "Original",
                                            "GRAYSCALE" to "B&N",
                                            "SEPIA" to "Sepia",
                                            "INVERT" to "Invertir",
                                            "WARM" to "Cálido",
                                            "COOL" to "Frío",
                                            "VINTAGE" to "Vintage",
                                            "BRIGHT" to "Brillo+"
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            filterList.forEach { (type, label) ->
                                                val isSelected = filterType == type
                                                
                                                val gradientBrush = when (type) {
                                                    "ORIGINAL" -> Brush.sweepGradient(listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC)))
                                                    "GRAYSCALE" -> Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFFCCCCCC)))
                                                    "SEPIA" -> Brush.linearGradient(listOf(Color(0xFF704214), Color(0xFFF5F5DC)))
                                                    "INVERT" -> Brush.linearGradient(listOf(Color.Black, Color.White))
                                                    "WARM" -> Brush.linearGradient(listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF)))
                                                    "COOL" -> Brush.linearGradient(listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)))
                                                    "VINTAGE" -> Brush.linearGradient(listOf(Color(0xFFF39F86), Color(0xFFF9D423)))
                                                    "BRIGHT" -> Brush.radialGradient(listOf(Color.White, Color(0xFFFFD166)))
                                                    else -> Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
                                                }

                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.clickable { 
                                                        pushStateToUndo()
                                                        filterType = type 
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .background(gradientBrush, shape = RoundedCornerShape(12.dp))
                                                            .border(
                                                                width = if (isSelected) 3.dp else 0.dp,
                                                                color = if (isSelected) Color(0xFF8AB4F8) else Color.Transparent,
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color(0xFF8AB4F8) else Color.LightGray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    2 -> {
                                        // ADJUSTMENTS
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp)
                                            ) {
                                                if (selectedAdjustment == "BRIGHTNESS") {
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Brillo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                            Text(
                                                                text = if (brightness >= 0) "+${brightness.toInt()}" else "${brightness.toInt()}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF8AB4F8)
                                                            )
                                                        }
                                                        Slider(
                                                            value = brightness,
                                                            onValueChange = { 
                                                                if (sliderStartSnapshot == null) {
                                                                    sliderStartSnapshot = captureCurrentState()
                                                                }
                                                                brightness = it 
                                                            },
                                                            onValueChangeFinished = {
                                                                sliderStartSnapshot?.let {
                                                                    globalUndoStack.add(it)
                                                                    globalRedoStack.clear()
                                                                    sliderStartSnapshot = null
                                                                }
                                                            },
                                                            valueRange = -100f..100f,
                                                            colors = SliderDefaults.colors(
                                                                activeTrackColor = Color(0xFF8AB4F8),
                                                                thumbColor = Color(0xFF8AB4F8)
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Contraste", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                            Text(
                                                                text = String.format(Locale.getDefault(), "%.1fx", contrast),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF8AB4F8)
                                                            )
                                                        }
                                                        Slider(
                                                            value = contrast,
                                                            onValueChange = { 
                                                                if (sliderStartSnapshot == null) {
                                                                    sliderStartSnapshot = captureCurrentState()
                                                                }
                                                                contrast = it 
                                                            },
                                                            onValueChangeFinished = {
                                                                sliderStartSnapshot?.let {
                                                                    globalUndoStack.add(it)
                                                                    globalRedoStack.clear()
                                                                    sliderStartSnapshot = null
                                                                }
                                                            },
                                                            valueRange = 0.5f..2.0f,
                                                            colors = SliderDefaults.colors(
                                                                activeTrackColor = Color(0xFF8AB4F8),
                                                                thumbColor = Color(0xFF8AB4F8)
                                                            )
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Brightness button
                                                val isBrightSelected = selectedAdjustment == "BRIGHTNESS"
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.clickable { selectedAdjustment = "BRIGHTNESS" }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(if (isBrightSelected) Color(0xFF8AB4F8).copy(alpha = 0.15f) else Color.Transparent, shape = CircleShape)
                                                            .border(1.dp, if (isBrightSelected) Color(0xFF8AB4F8) else Color(0xFF333333), shape = CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.LightMode,
                                                            contentDescription = "Brillo",
                                                            tint = if (isBrightSelected) Color(0xFF8AB4F8) else Color.LightGray,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Brillo",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isBrightSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isBrightSelected) Color(0xFF8AB4F8) else Color.LightGray
                                                    )
                                                }

                                                // Contrast button
                                                val isContrastSelected = selectedAdjustment == "CONTRAST"
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.clickable { selectedAdjustment = "CONTRAST" }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(if (isContrastSelected) Color(0xFF8AB4F8).copy(alpha = 0.15f) else Color.Transparent, shape = CircleShape)
                                                            .border(1.dp, if (isContrastSelected) Color(0xFF8AB4F8) else Color(0xFF333333), shape = CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Contrast,
                                                            contentDescription = "Contraste",
                                                            tint = if (isContrastSelected) Color(0xFF8AB4F8) else Color.LightGray,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Contraste",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isContrastSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isContrastSelected) Color(0xFF8AB4F8) else Color.LightGray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    3 -> {
                                        // PAINT/DRAW
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Grosor del pincel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                        Text("${brushThickness.toInt()} px", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF8AB4F8))
                                                    }
                                                    Slider(
                                                        value = brushThickness,
                                                        onValueChange = { brushThickness = it },
                                                        valueRange = 2f..50f,
                                                        colors = SliderDefaults.colors(
                                                            activeTrackColor = Color(0xFF8AB4F8),
                                                            thumbColor = Color(0xFF8AB4F8)
                                                        )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    IconButton(
                                                        onClick = { performGlobalUndo() },
                                                        enabled = globalUndoStack.isNotEmpty()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Undo,
                                                            contentDescription = "Deshacer",
                                                            tint = if (globalUndoStack.isNotEmpty()) Color(0xFF8AB4F8) else Color.Gray,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { performGlobalRedo() },
                                                        enabled = globalRedoStack.isNotEmpty()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Redo,
                                                            contentDescription = "Rehacer",
                                                            tint = if (globalRedoStack.isNotEmpty()) Color(0xFF8AB4F8) else Color.Gray,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            pushStateToUndo()
                                                            strokes.clear()
                                                            currentStrokePoints.clear()
                                                        },
                                                        enabled = strokes.isNotEmpty()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Limpiar todo",
                                                            tint = if (strokes.isNotEmpty()) Color.Red.copy(alpha = 0.8f) else Color.Gray,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val colors = listOf(
                                                    Color.Red,
                                                    Color(0xFFFF9800),
                                                    Color.Yellow,
                                                    Color.Green,
                                                    Color(0xFF00BCD4),
                                                    Color.Blue,
                                                    Color(0xFF9C27B0),
                                                    Color.White,
                                                    Color.Black,
                                                    Color.Gray
                                                )
                                                colors.forEach { color ->
                                                    val isSelected = brushColor == color
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(color, shape = CircleShape)
                                                            .border(
                                                                width = if (isSelected) 3.dp else 1.dp,
                                                                color = if (isSelected) Color(0xFF8AB4F8) else Color(0xFF555555),
                                                                shape = CircleShape
                                                            )
                                                            .clickable { brushColor = color }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    4 -> {
                                        // CROP
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Recorte Horiz. (Izq/Der)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Slider(
                                                            value = cropLeft + cropRight,
                                                            onValueChange = { total ->
                                                                if (sliderStartSnapshot == null) {
                                                                    sliderStartSnapshot = captureCurrentState()
                                                                }
                                                                val half = total / 2f
                                                                cropLeft = half.coerceIn(0f, 0.45f)
                                                                cropRight = half.coerceIn(0f, 0.45f)
                                                            },
                                                            onValueChangeFinished = {
                                                                sliderStartSnapshot?.let {
                                                                    globalUndoStack.add(it)
                                                                    globalRedoStack.clear()
                                                                    sliderStartSnapshot = null
                                                                }
                                                            },
                                                            valueRange = 0f..0.8f,
                                                            modifier = Modifier.weight(1f),
                                                            colors = SliderDefaults.colors(
                                                                activeTrackColor = Color(0xFF8AB4F8),
                                                                thumbColor = Color(0xFF8AB4F8)
                                                            )
                                                        )
                                                        Text(
                                                            text = "${((cropLeft + cropRight) * 100).toInt()}%",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF8AB4F8),
                                                            modifier = Modifier.width(36.dp)
                                                        )
                                                    }
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Recorte Vert. (Arriba/Abajo)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Slider(
                                                            value = cropTop + cropBottom,
                                                            onValueChange = { total ->
                                                                if (sliderStartSnapshot == null) {
                                                                    sliderStartSnapshot = captureCurrentState()
                                                                }
                                                                val half = total / 2f
                                                                cropTop = half.coerceIn(0f, 0.45f)
                                                                cropBottom = half.coerceIn(0f, 0.45f)
                                                            },
                                                            onValueChangeFinished = {
                                                                sliderStartSnapshot?.let {
                                                                    globalUndoStack.add(it)
                                                                    globalRedoStack.clear()
                                                                    sliderStartSnapshot = null
                                                                }
                                                            },
                                                            valueRange = 0f..0.8f,
                                                            modifier = Modifier.weight(1f),
                                                            colors = SliderDefaults.colors(
                                                                activeTrackColor = Color(0xFF8AB4F8),
                                                                thumbColor = Color(0xFF8AB4F8)
                                                            )
                                                        )
                                                        Text(
                                                            text = "${((cropTop + cropBottom) * 100).toInt()}%",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF8AB4F8),
                                                            modifier = Modifier.width(36.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        pushStateToUndo()
                                                        cropLeft = 0f
                                                        cropRight = 0f
                                                        cropTop = 0f
                                                        cropBottom = 0f
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Original", style = MaterialTheme.typography.bodySmall)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        pushStateToUndo()
                                                        applyAspectCrop(1f) 
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("1:1 (Cuadrado)", style = MaterialTheme.typography.bodySmall)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        pushStateToUndo()
                                                        applyAspectCrop(16f / 9f) 
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("16:9", style = MaterialTheme.typography.bodySmall)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        pushStateToUndo()
                                                        applyAspectCrop(9f / 16f) 
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("9:16", style = MaterialTheme.typography.bodySmall)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        pushStateToUndo()
                                                        applyAspectCrop(4f / 3f) 
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("4:3", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 2. MAIN CATEGORY TABS
                            Divider(color = Color(0xFF2D2D2D), thickness = 1.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val tabs = listOf(
                                    0 to ("Transformar" to Icons.Default.CropRotate),
                                    1 to ("Filtros" to Icons.Default.ColorLens),
                                    2 to ("Ajustes" to Icons.Default.Tune),
                                    3 to ("Pintar" to Icons.Default.Brush),
                                    4 to ("Recortar" to Icons.Default.Crop)
                                )
                                tabs.forEach { (index, tabInfo) ->
                                    val (title, icon) = tabInfo
                                    val isSelected = currentEditorTab == index
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0xFF8AB4F8).copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFF8AB4F8) else Color(0xFF333333),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable { currentEditorTab = index }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF8AB4F8) else Color.LightGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF8AB4F8) else Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val previewColorMatrix = rememberPreviewColorMatrix(
                        brightness = brightness,
                        contrast = contrast,
                        filterType = filterType
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize(0.85f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val rotatedRatio = remember(originalBitmap, rotation) {
                            val isRotated90 = (rotation.toInt() % 180 != 0)
                            val w = if (isRotated90) originalBitmap!!.height else originalBitmap!!.width
                            val h = if (isRotated90) originalBitmap!!.width else originalBitmap!!.height
                            w.toFloat() / h.toFloat()
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(rotatedRatio)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // 1. Image with transformation layers
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationZ = rotation
                                        scaleX = if (flipHorizontal) -1f else 1f
                                        scaleY = if (flipVertical) -1f else 1f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = originalBitmap,
                                    contentDescription = "Vista previa de edición",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.colorMatrix(previewColorMatrix),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 2. Painting canvas overlay
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(currentEditorTab) {
                                        if (currentEditorTab == 3) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    pushStateToUndo()
                                                    val relX = offset.x / size.width.toFloat()
                                                    val relY = offset.y / size.height.toFloat()
                                                    currentStrokePoints.clear()
                                                    currentStrokePoints.add(Offset(relX, relY))
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val newOffset = change.position
                                                    val relX = newOffset.x / size.width.toFloat()
                                                    val relY = newOffset.y / size.height.toFloat()
                                                    currentStrokePoints.add(Offset(relX, relY))
                                                },
                                                onDragEnd = {
                                                    if (currentStrokePoints.isNotEmpty()) {
                                                        strokes.add(
                                                            DrawingStroke(
                                                                points = currentStrokePoints.toList(),
                                                                color = brushColor,
                                                                width = brushThickness
                                                            )
                                                        )
                                                        currentStrokePoints.clear()
                                                    }
                                                }
                                            )
                                        }
                                    }
                            ) {
                                // Draw completed strokes
                                strokes.forEach { stroke ->
                                    val path = Path()
                                    if (stroke.points.isNotEmpty()) {
                                        path.moveTo(stroke.points[0].x * size.width, stroke.points[0].y * size.height)
                                        for (i in 1 until stroke.points.size) {
                                            path.lineTo(stroke.points[i].x * size.width, stroke.points[i].y * size.height)
                                        }
                                        drawPath(
                                            path = path,
                                            color = stroke.color,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = stroke.width,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                
                                // Draw active stroke
                                if (currentStrokePoints.isNotEmpty()) {
                                    val path = Path()
                                    path.moveTo(currentStrokePoints[0].x * size.width, currentStrokePoints[0].y * size.height)
                                    for (i in 1 until currentStrokePoints.size) {
                                        path.lineTo(currentStrokePoints[i].x * size.width, currentStrokePoints[i].y * size.height)
                                    }
                                    drawPath(
                                        path = path,
                                        color = brushColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = brushThickness,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // 3. Cropping overlays
                            val hasCrop = cropLeft > 0f || cropRight > 0f || cropTop > 0f || cropBottom > 0f
                            if (hasCrop || currentEditorTab == 4) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(currentEditorTab) {
                                            if (currentEditorTab == 4) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val w = size.width.toFloat()
                                                        val h = size.height.toFloat()
                                                        val leftPx = cropLeft * w
                                                        val topPx = cropTop * h
                                                        val rightPx = (1f - cropRight) * w
                                                        val bottomPx = (1f - cropBottom) * h

                                                        val touchX = offset.x
                                                        val touchY = offset.y

                                                        val tolerance = 50f
                                                        
                                                        if (kotlin.math.abs(touchX - leftPx) < tolerance && kotlin.math.abs(touchY - topPx) < tolerance) {
                                                            dragMode = "TOP_LEFT"
                                                        } else if (kotlin.math.abs(touchX - rightPx) < tolerance && kotlin.math.abs(touchY - topPx) < tolerance) {
                                                            dragMode = "TOP_RIGHT"
                                                        } else if (kotlin.math.abs(touchX - leftPx) < tolerance && kotlin.math.abs(touchY - bottomPx) < tolerance) {
                                                            dragMode = "BOTTOM_LEFT"
                                                        } else if (kotlin.math.abs(touchX - rightPx) < tolerance && kotlin.math.abs(touchY - bottomPx) < tolerance) {
                                                            dragMode = "BOTTOM_RIGHT"
                                                        } else if (touchX in leftPx..rightPx && touchY in topPx..bottomPx) {
                                                            dragMode = "MOVE"
                                                        } else {
                                                            dragMode = null
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        if (dragMode != null) {
                                                            change.consume()
                                                            val w = size.width.toFloat()
                                                            val h = size.height.toFloat()
                                                            val dx = dragAmount.x / w
                                                            val dy = dragAmount.y / h

                                                            when (dragMode) {
                                                                "MOVE" -> {
                                                                    val currentCropW = (1f - cropRight - cropLeft).coerceIn(0.05f, 1f)
                                                                    val currentCropH = (1f - cropBottom - cropTop).coerceIn(0.05f, 1f)
                                                                    
                                                                    val newCropLeft = (cropLeft + dx).coerceIn(0f, 1f - currentCropW)
                                                                    cropLeft = newCropLeft
                                                                    cropRight = (1f - newCropLeft - currentCropW).coerceIn(0f, 1f)

                                                                    val newCropTop = (cropTop + dy).coerceIn(0f, 1f - currentCropH)
                                                                    cropTop = newCropTop
                                                                    cropBottom = (1f - newCropTop - currentCropH).coerceIn(0f, 1f)
                                                                }
                                                                "TOP_LEFT" -> {
                                                                    cropLeft = (cropLeft + dx).coerceIn(0f, 1f - cropRight - 0.05f)
                                                                    cropTop = (cropTop + dy).coerceIn(0f, 1f - cropBottom - 0.05f)
                                                                }
                                                                "TOP_RIGHT" -> {
                                                                    cropRight = (cropRight - dx).coerceIn(0f, 1f - cropLeft - 0.05f)
                                                                    cropTop = (cropTop + dy).coerceIn(0f, 1f - cropBottom - 0.05f)
                                                                }
                                                                "BOTTOM_LEFT" -> {
                                                                    cropLeft = (cropLeft + dx).coerceIn(0f, 1f - cropRight - 0.05f)
                                                                    cropBottom = (cropBottom - dy).coerceIn(0f, 1f - cropTop - 0.05f)
                                                                }
                                                                "BOTTOM_RIGHT" -> {
                                                                    cropRight = (cropRight - dx).coerceIn(0f, 1f - cropLeft - 0.05f)
                                                                    cropBottom = (cropBottom - dy).coerceIn(0f, 1f - cropTop - 0.05f)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = { dragMode = null },
                                                    onDragCancel = { dragMode = null }
                                                )
                                            }
                                        }
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val left = cropLeft * w
                                    val top = cropTop * h
                                    val right = (1f - cropRight) * w
                                    val bottom = (1f - cropBottom) * h

                                    // Transparent overlays surrounding the cropped rect
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, 0f),
                                        size = androidx.compose.ui.geometry.Size(left, h)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(right, 0f),
                                        size = androidx.compose.ui.geometry.Size(w - right, h)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(left, 0f),
                                        size = androidx.compose.ui.geometry.Size(right - left, top)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(left, bottom),
                                        size = androidx.compose.ui.geometry.Size(right - left, h - bottom)
                                    )

                                    // Crop borders
                                    drawRect(
                                        color = if (currentEditorTab == 4) Color(0xFF8AB4F8) else Color.White.copy(alpha = 0.8f),
                                        topLeft = Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = if (currentEditorTab == 4) 2.dp.toPx() else 1.dp.toPx()
                                        )
                                    )

                                    // Prominent corner handles in crop tab
                                    if (currentEditorTab == 4) {
                                        val cornerLen = 20.dp.toPx()
                                        val cornerThickness = 4.dp.toPx()
                                        val cornerColor = Color(0xFF8AB4F8)

                                        // Top Left
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(left - 2.dp.toPx(), top - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerLen, cornerThickness)
                                        )
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(left - 2.dp.toPx(), top - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerThickness, cornerLen)
                                        )

                                        // Top Right
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(right - cornerLen + 2.dp.toPx(), top - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerLen, cornerThickness)
                                        )
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(right - cornerThickness + 2.dp.toPx(), top - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerThickness, cornerLen)
                                        )

                                        // Bottom Left
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(left - 2.dp.toPx(), bottom - cornerThickness + 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerLen, cornerThickness)
                                        )
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(left - 2.dp.toPx(), bottom - cornerLen + 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerThickness, cornerLen)
                                        )

                                        // Bottom Right
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(right - cornerLen + 2.dp.toPx(), bottom - cornerThickness + 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerLen, cornerThickness)
                                        )
                                        drawRect(
                                            color = cornerColor,
                                            topLeft = Offset(right - cornerThickness + 2.dp.toPx(), bottom - cornerLen + 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(cornerThickness, cornerLen)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF8AB4F8))
                                Text("Guardando imagen...", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showSaveChoiceDialog = false },
            title = { Text("Guardar Cambios") },
            text = { Text("¿Deseas reemplazar el archivo de imagen original con los cambios o crear una nueva copia?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveChoiceDialog = false
                        isSaving = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val processed = processBitmap(
                                source = originalBitmap!!,
                                rotation = rotation,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                brightness = brightness,
                                contrast = contrast,
                                filterType = filterType,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                strokes = strokes
                            )
                            val folder = ClipboardHelper.getStorageFolder(context)
                            val extension = File(image.localFilePath).extension.ifEmpty { "png" }
                            val newFile = File(folder, "edited_${System.currentTimeMillis()}.$extension")
                            
                            try {
                                newFile.outputStream().use { out ->
                                    val compFormat = if (extension.lowercase() == "jpg" || extension.lowercase() == "jpeg") {
                                        Bitmap.CompressFormat.JPEG
                                    } else {
                                        Bitmap.CompressFormat.PNG
                                    }
                                    processed.compress(compFormat, 95, out)
                                }
                                withContext(Dispatchers.Main) {
                                    onSave(newFile.absolutePath, true)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                processed.recycle()
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Reemplazar original")
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = {
                        showSaveChoiceDialog = false
                        isSaving = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val processed = processBitmap(
                                source = originalBitmap!!,
                                rotation = rotation,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                brightness = brightness,
                                contrast = contrast,
                                filterType = filterType,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                strokes = strokes
                            )
                            val folder = ClipboardHelper.getStorageFolder(context)
                            val extension = File(image.localFilePath).extension.ifEmpty { "png" }
                            val newFile = File(folder, "edited_${System.currentTimeMillis()}.$extension")
                            
                            try {
                                newFile.outputStream().use { out ->
                                    val compFormat = if (extension.lowercase() == "jpg" || extension.lowercase() == "jpeg") {
                                        Bitmap.CompressFormat.JPEG
                                    } else {
                                        Bitmap.CompressFormat.PNG
                                    }
                                    processed.compress(compFormat, 95, out)
                                }
                                withContext(Dispatchers.Main) {
                                    onSave(newFile.absolutePath, false)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                processed.recycle()
                                isSaving = false
                            }
                        }
                    }
                ) {
                    Text("Guardar como copia")
                }
            }
        )
    }
}

// Support data classes for editing state tracking
data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

data class EditorStateSnapshot(
    val rotation: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val filterType: String,
    val brightness: Float,
    val contrast: Float,
    val strokes: List<DrawingStroke>,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float
)

@Composable
fun rememberPreviewColorMatrix(
    brightness: Float,
    contrast: Float,
    filterType: String
): androidx.compose.ui.graphics.ColorMatrix {
    return remember(brightness, contrast, filterType) {
        val cm = android.graphics.ColorMatrix()
        
        when (filterType) {
            "GRAYSCALE" -> cm.setSaturation(0f)
            "SEPIA" -> {
                cm.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "INVERT" -> {
                cm.set(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "WARM" -> {
                cm.set(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "COOL" -> {
                cm.set(floatArrayOf(
                    0.8f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "VINTAGE" -> {
                cm.set(floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 0f,
                    0.1f, 0.8f, 0.1f, 0f, 0f,
                    0.1f, 0.1f, 0.6f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "BRIGHT" -> {
                cm.set(floatArrayOf(
                    1f, 0f, 0f, 0f, 50f,
                    0f, 1f, 0f, 0f, 50f,
                    0f, 0f, 1f, 0f, 50f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            else -> cm.reset()
        }
        
        val scale = contrast
        val translate = brightness + (128f * (1f - scale))
        val cmAdj = android.graphics.ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val finalCm = android.graphics.ColorMatrix()
        finalCm.setConcat(cmAdj, cm)
        
        androidx.compose.ui.graphics.ColorMatrix(finalCm.array)
    }
}

fun processBitmap(
    source: Bitmap,
    rotation: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    brightness: Float,
    contrast: Float,
    filterType: String,
    cropLeft: Float = 0f,
    cropTop: Float = 0f,
    cropRight: Float = 0f,
    cropBottom: Float = 0f,
    strokes: List<DrawingStroke> = emptyList()
): Bitmap {
    val matrix = android.graphics.Matrix()
    matrix.postRotate(rotation)
    val sx = if (flipHorizontal) -1f else 1f
    val sy = if (flipVertical) -1f else 1f
    matrix.postScale(sx, sy)
    
    var result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    
    val cm = android.graphics.ColorMatrix()
    when (filterType) {
        "GRAYSCALE" -> cm.setSaturation(0f)
        "SEPIA" -> {
            cm.set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "INVERT" -> {
            cm.set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "WARM" -> {
            cm.set(floatArrayOf(
                1.2f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "COOL" -> {
            cm.set(floatArrayOf(
                0.8f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "VINTAGE" -> {
            cm.set(floatArrayOf(
                0.9f, 0.1f, 0.1f, 0f, 0f,
                0.1f, 0.8f, 0.1f, 0f, 0f,
                0.1f, 0.1f, 0.6f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "BRIGHT" -> {
            cm.set(floatArrayOf(
                1f, 0f, 0f, 0f, 50f,
                0f, 1f, 0f, 0f, 50f,
                0f, 0f, 1f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        else -> cm.reset()
    }
    
    val scale = contrast
    val translate = brightness + (128f * (1f - scale))
    val cmAdj = android.graphics.ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    
    val finalCm = android.graphics.ColorMatrix()
    finalCm.setConcat(cmAdj, cm)
    
    val output = Bitmap.createBitmap(result.width, result.height, result.config ?: Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(finalCm)
    }
    canvas.drawBitmap(result, 0f, 0f, paint)
    
    if (result != source && result != output) {
        result.recycle()
    }
    
    // Crop calculation
    val w = output.width
    val h = output.height
    val leftBound = (cropLeft * w).toInt().coerceIn(0, w - 1)
    val topBound = (cropTop * h).toInt().coerceIn(0, h - 1)
    val rightBound = ((1f - cropRight) * w).toInt().coerceIn(leftBound + 1, w)
    val bottomBound = ((1f - cropBottom) * h).toInt().coerceIn(topBound + 1, h)
    
    val croppedW = rightBound - leftBound
    val croppedH = bottomBound - topBound
    
    val croppedOutput = Bitmap.createBitmap(output, leftBound, topBound, croppedW, croppedH)
    if (output != croppedOutput) {
        output.recycle()
    }
    
    // Render strokes on the cropped bitmap
    val finalCanvas = android.graphics.Canvas(croppedOutput)
    strokes.forEach { stroke ->
        if (stroke.points.isNotEmpty()) {
            val strokePaint = Paint().apply {
                color = stroke.color.toArgb()
                style = Paint.Style.STROKE
                val scaleFactor = w.toFloat() / 800f
                strokeWidth = maxOf(2f, stroke.width * scaleFactor)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            val path = android.graphics.Path()
            val x0 = stroke.points[0].x * w - leftBound
            val y0 = stroke.points[0].y * h - topBound
            path.moveTo(x0, y0)
            for (i in 1 until stroke.points.size) {
                val xi = stroke.points[i].x * w - leftBound
                val yi = stroke.points[i].y * h - topBound
                path.lineTo(xi, yi)
            }
            finalCanvas.drawPath(path, strokePaint)
        }
    }
    
    return croppedOutput
}
