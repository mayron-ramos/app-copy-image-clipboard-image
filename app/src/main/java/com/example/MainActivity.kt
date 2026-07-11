package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import com.example.data.CopiedImage
import com.example.ui.MainViewModel
import com.example.ui.SortField
import com.example.ui.SortOrder
import com.example.ui.theme.*
import com.example.util.ClipboardHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    viewModel = viewModel,
                    onAuthenticate = { onSuccess, onFailure ->
                        showBiometricPrompt(onSuccess, onFailure)
                    }
                )
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        runOnUiThread { onSuccess() }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        runOnUiThread { onFailure() }
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Acceso a Ocultos")
                .setSubtitle("Autentícate para ver la carpeta protegida")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { onFailure() }
            }
        } else {
            runOnUiThread { onFailure() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onAuthenticate: (onSuccess: () -> Unit, onFailure: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isTransparentCopyEnabled by viewModel.isTransparentCopyEnabled.collectAsState()
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val copiedImages by viewModel.uiState.collectAsState()
    val availableApps by viewModel.availableApps.collectAsState()
    val selectedAppFilter by viewModel.selectedAppFilter.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val isLimitByCountEnabled by viewModel.isLimitByCountEnabled.collectAsState()
    val limitByCountValue by viewModel.limitByCountValue.collectAsState()
    val isLimitByAgeEnabled by viewModel.isLimitByAgeEnabled.collectAsState()
    val limitByAgeValue by viewModel.limitByAgeValue.collectAsState()
    val isLimitBySizeEnabled by viewModel.isLimitBySizeEnabled.collectAsState()
    val limitBySizeValue by viewModel.limitBySizeValue.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<CopiedImage?>(null) }
    var selectedImageForDetail by remember { mutableStateOf<CopiedImage?>(null) }
    var showPrivacyConfirmDialog by remember { mutableStateOf(false) }
    var privacyTargetImage by remember { mutableStateOf<CopiedImage?>(null) }
    var showHowToUseBottomSheet by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var manualUrlInput by remember { mutableStateOf("") }
    var isDownloadingManualUrl by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    var currentTab by remember { mutableStateOf(0) }
    
    // Hidden and Editor state variables
    var selectedImageForEdit by remember { mutableStateOf<CopiedImage?>(null) }
    var isUnlocked by remember { mutableStateOf(false) }
    var showManualPasscodeDialog by remember { mutableStateOf(false) }

    val systemNavigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (currentTab) {
                                    0 -> Icons.Default.Collections
                                    1 -> Icons.Default.BarChart
                                    2 -> Icons.Default.Settings
                                    else -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = when (currentTab) {
                                    0 -> "Mis Imágenes"
                                    1 -> "Estadísticas"
                                    2 -> "Opciones"
                                    else -> "Carpeta Oculta"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("app_title")
                            )
                            Text(
                                text = when (currentTab) {
                                    0 -> "Historial: ${copiedImages.size}"
                                    1 -> "Análisis de copias"
                                    2 -> "Configuración de la app"
                                    else -> "Imágenes protegidas"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHowToUseBottomSheet = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Cómo usar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = { showAddUrlDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_url_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Descargar de URL"
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = {
                        isUnlocked = false
                        currentTab = 0
                    },
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Inicio") },
                    label = { Text("Inicio") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = {
                        isUnlocked = false
                        currentTab = 1
                    },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Estadísticas") },
                    label = { Text("Estadísticas") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        isUnlocked = false
                        currentTab = 2
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Opciones") },
                    label = { Text("Opciones") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = {
                        if (currentTab == 3) return@NavigationBarItem
                        onAuthenticate(
                            {
                                isUnlocked = true
                                currentTab = 3
                            },
                            {
                                showManualPasscodeDialog = true
                            }
                        )
                    },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Ocultos") },
                    label = { Text("Ocultos") }
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            0 -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("main_lazy_column"),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 32.dp),
                    verticalItemSpacing = 4.dp,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Section 2: Search Bar
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_bar"),
                                placeholder = { 
                                    Text(
                                        text = "Buscar imágenes en el historial...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ) 
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Search, 
                                        contentDescription = "Buscar",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    ) 
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear, 
                                                contentDescription = "Limpiar",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                shape = CircleShape,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }

                    // Section 3: Filter Chips by Application Source
                    if (availableApps.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Filtrar por origen",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedAppFilter == null,
                                            onClick = { viewModel.selectAppFilter(null) },
                                            label = { Text("Todas") },
                                            leadingIcon = {
                                                if (selectedAppFilter == null) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }

                                    items(availableApps) { appName ->
                                        FilterChip(
                                            selected = selectedAppFilter == appName,
                                            onClick = { viewModel.selectAppFilter(appName) },
                                            label = { Text(appName) },
                                            leadingIcon = {
                                                if (selectedAppFilter == appName) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }

                                    item {
                                        FilterChip(
                                            selected = selectedAppFilter == "Otros",
                                            onClick = { viewModel.selectAppFilter("Otros") },
                                            label = { Text("Otros / Sistema") },
                                            leadingIcon = {
                                                if (selectedAppFilter == "Otros") {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 4b: Sorting options
                    if (copiedImages.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ordenar:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                
                                FilterChip(
                                    selected = sortField == SortField.DATE,
                                    onClick = { viewModel.updateSortField(SortField.DATE) },
                                    label = { Text("Fecha") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )

                                FilterChip(
                                    selected = sortField == SortField.NAME,
                                    onClick = { viewModel.updateSortField(SortField.NAME) },
                                    label = { Text("Nombre") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SortByAlpha,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Surface(
                                    onClick = {
                                        val nextOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
                                        viewModel.updateSortOrder(nextOrder)
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (sortOrder == SortOrder.ASCENDING) "Asc" else "Desc",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 5: History Grid Items
                    if (copiedImages.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyHistoryState()
                        }
                    } else {
                        val twitterImages = copiedImages.filter { 
                            val url = it.sourceUrl ?: ""
                            url.contains("twitter.com", ignoreCase = true) || url.contains("x.com", ignoreCase = true)
                        }
                        val genericLinkImages = copiedImages.filter { 
                            val url = it.sourceUrl ?: ""
                            url.isNotEmpty() && !url.contains("twitter.com", ignoreCase = true) && !url.contains("x.com", ignoreCase = true)
                        }
                        val directImages = copiedImages.filter { 
                            it.sourceUrl.isNullOrEmpty()
                        }

                        if (twitterImages.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader(
                                    title = "Publicaciones de Twitter / X",
                                    count = twitterImages.size,
                                    icon = Icons.Default.AlternateEmail
                                )
                            }
                            items(twitterImages, key = { "tw_${it.id}" }) { image ->
                                HistoryGridItem(
                                    image = image,
                                    onItemClick = {
                                        selectedImageForDetail = image
                                    },
                                    onCopyAgain = {
                                        viewModel.copyImageToClipboard(image)
                                        Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { imageToDelete = image }
                                )
                            }
                        }

                        if (genericLinkImages.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader(
                                    title = "Enlaces Genéricos",
                                    count = genericLinkImages.size,
                                    icon = Icons.Default.Link
                                )
                            }
                            items(genericLinkImages, key = { "gen_${it.id}" }) { image ->
                                HistoryGridItem(
                                    image = image,
                                    onItemClick = {
                                        selectedImageForDetail = image
                                    },
                                    onCopyAgain = {
                                        viewModel.copyImageToClipboard(image)
                                        Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { imageToDelete = image }
                                )
                            }
                        }

                        if (directImages.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader(
                                    title = "Imágenes Copiadas Directamente",
                                    count = directImages.size,
                                    icon = Icons.Default.Image
                                )
                            }
                            items(directImages, key = { "dir_${it.id}" }) { image ->
                                HistoryGridItem(
                                    image = image,
                                    onItemClick = {
                                        selectedImageForDetail = image
                                    },
                                    onCopyAgain = {
                                        viewModel.copyImageToClipboard(image)
                                        Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { imageToDelete = image }
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                // Estadísticas content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val statsByApp = remember(copiedImages) {
                        copiedImages.groupBy { it.sourceAppName ?: "Otros/Portapapeles" }
                            .mapValues { it.value.size }
                            .toList()
                            .sortedByDescending { it.second }
                    }

                    val spaceStatsByApp = remember(copiedImages) {
                        copiedImages.groupBy { it.sourceAppName ?: "Otros/Portapapeles" }
                            .mapValues { entry ->
                                entry.value.sumOf { image ->
                                    try { File(image.localFilePath).length() } catch (e: Exception) { 0L }
                                }
                            }
                            .toList()
                            .sortedByDescending { it.second }
                    }

                    val weeklyTrend = remember(copiedImages) {
                        (0..6).map { offset ->
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                            val dayOfWeek = c.get(Calendar.DAY_OF_WEEK)
                            val dayLabel = when (dayOfWeek) {
                                Calendar.SUNDAY -> "Dom"
                                Calendar.MONDAY -> "Lun"
                                Calendar.TUESDAY -> "Mar"
                                Calendar.WEDNESDAY -> "Mié"
                                Calendar.THURSDAY -> "Jue"
                                Calendar.FRIDAY -> "Vie"
                                Calendar.SATURDAY -> "Sáb"
                                else -> ""
                            }
                            val count = copiedImages.count { image ->
                                val imgCal = Calendar.getInstance().apply { timeInMillis = image.timestamp }
                                imgCal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                                        imgCal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
                            }
                            dayLabel to count
                        }.reversed()
                    }

                    // Tarjetas de Resumen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card 1: Total images
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Total",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Imágenes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${copiedImages.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Card 2: Space occupied
                        val totalSpaceFormatted = remember(copiedImages) {
                            val bytes = copiedImages.sumOf { image ->
                                try { File(image.localFilePath).length() } catch (e: Exception) { 0L }
                            }
                            when {
                                bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
                                bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
                                else -> "$bytes B"
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Espacio",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Espacio usado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = totalSpaceFormatted,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Card 1: Distribución por Origen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Origen de las Imágenes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (copiedImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay datos para mostrar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                AppSourcesDonutChart(stats = statsByApp, total = copiedImages.size)
                            }
                        }
                    }

                    // Card 2: Almacenamiento por Origen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Almacenamiento por Origen",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (copiedImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay datos para mostrar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val totalBytes = spaceStatsByApp.sumOf { it.second }
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    spaceStatsByApp.forEach { (appName, bytes) ->
                                        val percentage = if (totalBytes > 0) (bytes.toFloat() / totalBytes * 100).toInt() else 0
                                        val formattedSize = when {
                                            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
                                            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
                                            else -> "$bytes B"
                                        }
                                        
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = appName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$formattedSize ($percentage%)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            val progress = if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f
                                            LinearProgressIndicator(
                                                progress = progress,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = MaterialTheme.colorScheme.secondary,
                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Actividad Semanal
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Actividad (Últimos 7 días)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (copiedImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay datos para mostrar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val maxCount = weeklyTrend.maxOf { it.second }.coerceAtLeast(1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    weeklyTrend.forEach { (day, count) ->
                                        val ratio = count.toFloat() / maxCount
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (count > 0) "$count" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight(0.75f * ratio + 0.05f)
                                                    .width(18.dp)
                                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                    .background(
                                                        if (count > 0) MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = day,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Opciones Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
                    val cardBorder = remember(isServiceActive, activeColor, inactiveColor) {
                        BorderStroke(
                            width = 1.dp,
                            color = if (isServiceActive) {
                                activeColor.copy(alpha = 0.25f)
                            } else {
                                inactiveColor
                            }
                        )
                    }

                    Card(
                        modifier = Modifier
                             .fillMaxWidth()
                             .testTag("settings_card"),
                        shape = RoundedCornerShape(28.dp),
                        border = cardBorder,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isServiceActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (isServiceActive) "Servicio Activo" else "Servicio Inactivo",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isServiceActive) "Listo para copiar al compartir" else "Actívalo para capturar copias",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = isServiceActive,
                                    onCheckedChange = { viewModel.toggleServiceActive(it) },
                                    modifier = Modifier.testTag("service_status_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }

                            // Inner settings bar for transparent copy
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isServiceActive) {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Cerrar tras copiar",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Cierra la app al instante",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isTransparentCopyEnabled,
                                        onCheckedChange = { viewModel.toggleTransparentCopy(it) },
                                        modifier = Modifier.testTag("transparent_copy_switch")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Control de Almacenamiento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )

                    // Control de Almacenamiento Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Limit by Count (images per category)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Límite por categoría",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Conserva un máximo de imágenes por origen/categoría",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isLimitByCountEnabled,
                                        onCheckedChange = { viewModel.toggleLimitByCount(it) }
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isLimitByCountEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(5, 10, 20, 50, 100).forEach { count ->
                                            FilterChip(
                                                selected = limitByCountValue == count,
                                                onClick = { viewModel.updateLimitByCountValue(count) },
                                                label = { Text("$count imgs") }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            
                            // 2. Limit by Age
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Límite por antigüedad",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Elimina imágenes más viejas que el tiempo seleccionado",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isLimitByAgeEnabled,
                                        onCheckedChange = { viewModel.toggleLimitByAge(it) }
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isLimitByAgeEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(1, 3, 7, 14, 30).forEach { days ->
                                            FilterChip(
                                                selected = limitByAgeValue == days,
                                                onClick = { viewModel.updateLimitByAgeValue(days) },
                                                label = { Text(if (days == 1) "1 día" else "$days días") }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            
                            // 3. Limit by Size
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Límite de tamaño total",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Evita que la app supere cierto almacenamiento",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isLimitBySizeEnabled,
                                        onCheckedChange = { viewModel.toggleLimitBySize(it) }
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = isLimitBySizeEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(10, 20, 50, 100, 200).forEach { mb ->
                                            FilterChip(
                                                selected = limitBySizeValue == mb,
                                                onClick = { viewModel.updateLimitBySizeValue(mb) },
                                                label = { Text("$mb MB") }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            
                            // Manual trigger & Cache clean
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Limpieza de almacenamiento",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Aplica los límites activos inmediatamente",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Button(
                                    onClick = {
                                        viewModel.runManualCleanup { count, bytes ->
                                            val spaceFreed = when {
                                                bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
                                                bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
                                                else -> "$bytes B"
                                            }
                                            Toast.makeText(
                                                context,
                                                "Limpieza completada: $count imágenes borradas ($spaceFreed liberados)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Limpiar ahora")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ubicación y Copias de Seguridad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )

                    // Card for Storage Location & Backups
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Storage location choice
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column {
                                    Text(
                                        text = "Ubicación de guardado",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Elige dónde se almacenarán las imágenes descargadas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                var storageLocation by remember { mutableStateOf(com.example.util.ClipboardHelper.getStorageLocation(context)) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = storageLocation == "INTERNAL",
                                        onClick = { 
                                            com.example.util.ClipboardHelper.setStorageLocation(context, "INTERNAL")
                                            storageLocation = "INTERNAL"
                                            Toast.makeText(context, "Guardando en Almacenamiento Interno", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("Interno (Privado)") }
                                    )
                                    FilterChip(
                                        selected = storageLocation == "EXTERNAL",
                                        onClick = { 
                                            com.example.util.ClipboardHelper.setStorageLocation(context, "EXTERNAL")
                                            storageLocation = "EXTERNAL"
                                            Toast.makeText(context, "Guardando en Carpeta Externa de la App", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("Externo (App)") }
                                    )
                                    FilterChip(
                                        selected = storageLocation == "PUBLIC",
                                        onClick = { 
                                            com.example.util.ClipboardHelper.setStorageLocation(context, "PUBLIC")
                                            storageLocation = "PUBLIC"
                                            Toast.makeText(context, "Guardando en Galería Pública (Pictures)", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("Público (Pictures)") }
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            
                            // Backup and Restore options
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Respaldo y Restauración",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val uri = com.example.util.BackupHelper.exportBackup(context)
                                            if (uri != null) {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Guardar Copia de Seguridad"))
                                            } else {
                                                Toast.makeText(context, "Error al exportar copia de seguridad", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Exportar")
                                    }
                                    
                                    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                                    ) { zipUri ->
                                        if (zipUri != null) {
                                            val success = com.example.util.BackupHelper.importBackup(context, zipUri)
                                            if (success) {
                                                Toast.makeText(context, "Copia de seguridad restaurada con éxito", Toast.LENGTH_LONG).show()
                                                
                                                // Clean restart to reload completely new DB and assets
                                                var currentContext = context
                                                while (currentContext is android.content.ContextWrapper) {
                                                    if (currentContext is android.app.Activity) {
                                                        currentContext.recreate()
                                                        break
                                                    }
                                                    currentContext = currentContext.baseContext
                                                }
                                            } else {
                                                Toast.makeText(context, "Error: Archivo de copia no válido", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    
                                    FilledTonalButton(
                                        onClick = {
                                            pickerLauncher.launch("application/zip")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Importar")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Zona de Peligro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )

                    // Clear History Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Limpiar Historial",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Elimina de forma permanente todas las imágenes guardadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            
                            IconButton(
                                onClick = { showClearHistoryDialog = true },
                                modifier = Modifier.background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Borrar todo",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Seguridad y Auto-Ocultar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Auto Hide Origins Title
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Auto-ocultar por origen",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Oculta automáticamente imágenes de estos orígenes al momento de copiar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Dynamic chip row of origins
                            val autoHideOrigins by viewModel.autoHideOrigins.collectAsState()
                            val possibleOrigins = remember(availableApps) {
                                (listOf("Otros/Portapapeles") + availableApps).distinct()
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                possibleOrigins.forEach { origin ->
                                    val isSelected = autoHideOrigins.contains(origin)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleAutoHideOrigin(origin) },
                                        label = { Text(origin) },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // 2. PIN Fallback Management
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PIN de Seguridad",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Configura o cambia el PIN de acceso a Ocultos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        val prefs = context.getSharedPreferences("passcode_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().remove("custom_pin").apply()
                                        Toast.makeText(context, "PIN de respaldo borrado. Define uno nuevo la próxima vez que accedas.", Toast.LENGTH_LONG).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restablecer PIN")
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                if (isUnlocked) {
                    val hiddenImages by viewModel.hiddenImages.collectAsState()
                    
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .testTag("hidden_lazy_column"),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 32.dp),
                        verticalItemSpacing = 4.dp,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Carpeta Oculta Desbloqueada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Las imágenes guardadas aquí están protegidas y no aparecen en el inicio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { isUnlocked = false; currentTab = 0 }) {
                                            Icon(Icons.Default.Lock, contentDescription = "Bloquear", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        if (hiddenImages.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(72.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No hay imágenes ocultas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Usa la opción de ocultar en los detalles de cualquier imagen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                                }
                            }
                        } else {
                            items(hiddenImages, key = { "hid_${it.id}" }) { image ->
                                HistoryGridItem(
                                    image = image,
                                    onItemClick = {
                                        selectedImageForDetail = image
                                    },
                                    onCopyAgain = {
                                        viewModel.copyImageToClipboard(image)
                                        Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { imageToDelete = image }
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Carpeta Protegida",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Para ver las imágenes de esta carpeta, necesitas desbloquear usando tu seguridad biométrica o el PIN de respaldo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                onAuthenticate(
                                    {
                                        isUnlocked = true
                                    },
                                    {
                                        showManualPasscodeDialog = true
                                    }
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Desbloquear con Biométrico")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { showManualPasscodeDialog = true }
                        ) {
                            Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Usar PIN de Respaldo")
                        }
                    }
                }
            }
            else -> {}
        }
        }

    if (showManualPasscodeDialog) {
        PasscodeDialog(
            context = context,
            onSuccess = {
                showManualPasscodeDialog = false
                isUnlocked = true
                currentTab = 3
            },
            onDismiss = {
                showManualPasscodeDialog = false
            }
        )
    }

    if (selectedImageForEdit != null) {
        com.example.ui.screens.ImageEditorScreen(
            image = selectedImageForEdit!!,
            onDismiss = { selectedImageForEdit = null },
            onSave = { newFilePath, replaceOriginal ->
                viewModel.saveEditedImage(selectedImageForEdit!!, newFilePath, replaceOriginal) {
                    selectedImageForEdit = null
                    selectedImageForDetail = null
                    Toast.makeText(context, "Imagen guardada con éxito", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Modal Bottom Sheet for "How to Use" Tutorial
    if (showHowToUseBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHowToUseBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "¿Cómo usar?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TutorialStep(number = 1, text = "Busca una imagen en cualquier app (ej. Twitter, WhatsApp, Navegador).")
                TutorialStep(number = 2, text = "Presiona Compartir en la imagen.")
                TutorialStep(number = 3, text = "Selecciona 'Copiar imagen' de la lista de aplicaciones.")
                TutorialStep(number = 4, text = "¡La imagen se copiará automáticamente al portapapeles y se guardará en tu historial!")

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showHowToUseBottomSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Entendido", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }


    // Interactive Detail Dialog Sheet (Offers full-resolution preview and robust actions)
    if (selectedImageForDetail != null) {
        val image = selectedImageForDetail!!
        val imageTitle = if (image.originalFileName == null || image.originalFileName.startsWith("url_image")) {
            java.io.File(image.localFilePath).name
        } else {
            image.originalFileName
        }
        val file = File(image.localFilePath)
        
        Dialog(
            onDismissRequest = { selectedImageForDetail = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect {
                dialogWindow?.let { window ->
                    window.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Surface(
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 40.dp + maxOf(systemNavigationBarsPadding, 16.dp)
                                ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.copyImageToClipboard(image)
                                    Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Copiar otra vez",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = { shareImageFile(context, image) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Compartir",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Scrollable content starting at very top (no top bar padding)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Netflix-style Hero Image Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                                .clickable { showFullScreenImage = true }
                        ) {
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = image.originalFileName ?: "Imagen",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Elegant vertical gradient to ensure readability for overlay controls/text
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.5f),
                                                    Color.Transparent,
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.8f)
                                                )
                                            )
                                        )
                                )

                                // Overlay metadata at bottom of Hero
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    if (!image.sourceAppName.isNullOrEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = image.sourceAppName.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = imageTitle ?: "Imagen Copiada",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Fullscreen toggle floating button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .clickable { showFullScreenImage = true }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Pantalla completa",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ImageNotSupported,
                                        contentDescription = "No encontrado",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        // Info section below the Hero with top padding to separate it from the Hero
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Directly show the metadata items without a Card container
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                            Text(
                                text = "Información del Archivo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            MetadataItem(
                                icon = Icons.Default.Title,
                                label = "Nombre del archivo",
                                value = imageTitle ?: "Archivo sin nombre",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nombre de archivo", imageTitle ?: ""))
                                    Toast.makeText(context, "Nombre copiado", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            MetadataItem(
                                icon = Icons.Default.Apps,
                                label = "Origen compartido",
                                value = image.sourceAppName ?: "Sistema / Desconocido",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Origen compartido", image.sourceAppName ?: ""))
                                    Toast.makeText(context, "Origen copiado", Toast.LENGTH_SHORT).show()
                                }
                            )

                            if (!image.sourcePackage.isNullOrEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                MetadataItem(
                                    icon = Icons.Default.Code,
                                    label = "Paquete de origen",
                                    value = image.sourcePackage,
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Paquete de origen", image.sourcePackage))
                                        Toast.makeText(context, "Paquete copiado", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            if (!image.sourceUrl.isNullOrEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                MetadataItem(
                                    icon = Icons.Default.Link,
                                    label = "Enlace de origen",
                                    value = image.sourceUrl,
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Enlace de origen", image.sourceUrl))
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            MetadataItem(
                                icon = Icons.Default.Schedule,
                                label = "Fecha de copiado",
                                value = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(image.timestamp)),
                                onClick = {
                                    val formattedDate = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(image.timestamp))
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Fecha de copiado", formattedDate))
                                    Toast.makeText(context, "Fecha copiada", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            MetadataItem(
                                icon = Icons.Default.Folder,
                                label = "Ruta local",
                                value = image.localFilePath,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ruta local", image.localFilePath))
                                    Toast.makeText(context, "Ruta copiada", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            MetadataItem(
                                icon = Icons.Default.Info,
                                label = "Formato MIME",
                                value = image.mimeType,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Formato MIME", image.mimeType))
                                    Toast.makeText(context, "MIME copiado", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

                // Transparent Overlay Top Bar containing floating Back, Edit, Hide, and Delete buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedImageForDetail = null },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Button
                        IconButton(
                            onClick = {
                                selectedImageForEdit = image
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = Color.White
                            )
                        }

                        // Hide/Show Toggle Button
                        IconButton(
                            onClick = {
                                privacyTargetImage = image
                                showPrivacyConfirmDialog = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (image.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (image.isHidden) "Hacer visible" else "Ocultar",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                imageToDelete = image
                                selectedImageForDetail = null
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Confirmation Dialogs
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.confirm_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.confirm_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_all_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (imageToDelete != null) {
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.confirm_delete_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        imageToDelete?.let { viewModel.deleteImage(it) }
                        imageToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_item_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showPrivacyConfirmDialog && privacyTargetImage != null) {
        val target = privacyTargetImage!!
        AlertDialog(
            onDismissRequest = { 
                showPrivacyConfirmDialog = false
                privacyTargetImage = null
            },
            title = {
                Text(
                    text = if (target.isHidden) "Hacer imagen visible" else "Mover a Ocultos",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (target.isHidden) {
                        "¿Estás seguro de que deseas hacer esta imagen visible en la galería principal? Ya no estará oculta."
                    } else {
                        "¿Estás seguro de que deseas mover esta imagen a la carpeta oculta? Estará protegida por tu PIN o datos biométricos."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleImageHidden(target)
                        Toast.makeText(
                            context,
                            if (target.isHidden) "Imagen visible en el inicio" else "Imagen movida a Ocultos",
                            Toast.LENGTH_SHORT
                        ).show()
                        showPrivacyConfirmDialog = false
                        privacyTargetImage = null
                        selectedImageForDetail = null
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrivacyConfirmDialog = false
                        privacyTargetImage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddUrlDialog) {
        ModalBottomSheet(
            onDismissRequest = { 
                if (!isDownloadingManualUrl) {
                    showAddUrlDialog = false
                    manualUrlInput = ""
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Descargar desde URL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "Introduce una URL de imagen o publicación (ej: Twitter/X) para descargarla y copiarla al portapapeles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = manualUrlInput,
                    onValueChange = { manualUrlInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_url_input"),
                    label = { Text("Enlace / URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    enabled = !isDownloadingManualUrl,
                    trailingIcon = {
                        if (manualUrlInput.isNotEmpty() && !isDownloadingManualUrl) {
                            IconButton(onClick = { manualUrlInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                if (!isDownloadingManualUrl) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.startsWith("http://") || text.startsWith("https://")) {
                                        manualUrlInput = text.trim()
                                    } else {
                                        Toast.makeText(context, "El portapapeles no contiene una URL válida", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pegar portapapeles")
                        }
                    }
                }

                if (isDownloadingManualUrl) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Descargando imagen...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            showAddUrlDialog = false
                            manualUrlInput = ""
                        },
                        enabled = !isDownloadingManualUrl
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (manualUrlInput.isBlank()) {
                                Toast.makeText(context, "Por favor, introduce una URL", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val trimmedUrl = manualUrlInput.trim()
                            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                                Toast.makeText(context, "La URL debe comenzar con http:// o https://", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isDownloadingManualUrl = true
                            coroutineScope.launch {
                                try {
                                    val result = ClipboardHelper.copyImageUrlToClipboard(
                                        context = context,
                                        imageUrl = trimmedUrl,
                                        sourcePackage = context.packageName,
                                        sourceAppName = "Manualmente"
                                    )
                                    when (result) {
                                        ClipboardHelper.CopyImageUrlResult.SUCCESS -> {
                                            Toast.makeText(context, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                                            showAddUrlDialog = false
                                            manualUrlInput = ""
                                        }
                                        ClipboardHelper.CopyImageUrlResult.NOT_AN_IMAGE -> {
                                            Toast.makeText(context, "La URL no contiene una imagen válida.", Toast.LENGTH_LONG).show()
                                        }
                                        ClipboardHelper.CopyImageUrlResult.TWITTER_NO_IMAGES -> {
                                            Toast.makeText(context, "Esta publicación de Twitter/X no contiene imágenes.", Toast.LENGTH_LONG).show()
                                        }
                                        ClipboardHelper.CopyImageUrlResult.NETWORK_ERROR -> {
                                            Toast.makeText(context, "Error de red al descargar la imagen.", Toast.LENGTH_LONG).show()
                                        }
                                        ClipboardHelper.CopyImageUrlResult.FAILED -> {
                                            Toast.makeText(context, "No se pudo procesar la URL de la imagen.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error inesperado al procesar la URL", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isDownloadingManualUrl = false
                                }
                            }
                        },
                        enabled = !isDownloadingManualUrl && manualUrlInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Descargar")
                    }
                }
            }
        }
    }
}

@Composable
fun PulsatingStatusDot(isActive: Boolean) {
    if (!isActive) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulsating_dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E676).copy(alpha = alpha * 0.4f))
        )
        Box(
            modifier = Modifier
                .size(10.dp * scale)
                .clip(CircleShape)
                .background(Color(0xFF00E676))
        )
    }
}

@Composable
fun TutorialStep(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp
        )
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp)
            .testTag("empty_history_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Multi-layered visual element for clipboard history
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background dashed circle or ornament
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Historial vacío",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun HistoryGridItem(
    image: CopiedImage,
    onItemClick: () -> Unit,
    onCopyAgain: () -> Unit,
    onDelete: () -> Unit
) {
    val file = File(image.localFilePath)
    val context = LocalContext.current
    
    val aspectRatio = remember(image.id) {
        when (image.id % 3) {
            0 -> 0.8f    // Tall portrait
            1 -> 1.0f    // Square
            else -> 1.25f // Landscape
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onItemClick() }
            .testTag("history_item_${image.id}")
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        if (file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = image.originalFileName ?: "Imagen copiada",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = "No encontrado",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }

        // Elegant source app overlay (Bottom-Left)
        val appIconDrawable = remember(image.sourcePackage) {
            if (!image.sourcePackage.isNullOrEmpty()) {
                try {
                    context.packageManager.getApplicationIcon(image.sourcePackage)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (appIconDrawable != null) {
                AsyncImage(
                    model = appIconDrawable,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        // Action Overlay buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Delete action (Top-Left)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Copy action (Top-Right)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onCopyAgain() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar de nuevo",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun MetadataItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copiar valor",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun shareImageFile(context: Context, image: CopiedImage) {
    val file = File(image.localFilePath)
    if (!file.exists()) {
        Toast.makeText(context, "Archivo no encontrado", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = image.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir imagen"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Capsule Shape Definition
val CapsuleShape = RoundedCornerShape(50.dp)

val maxLineSpan = 2

fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Hace un momento"
        diff < 3600_000 -> "Hace ${diff / 60_000} min"
        diff < 86400_000 -> "Hace ${diff / 3600_000} h"
        else -> {
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Composable
fun AppSourcesDonutChart(stats: List<Pair<String, Int>>, total: Int) {
    if (total == 0) return
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                stats.forEachIndexed { index, pair ->
                    val sweepAngle = (pair.second.toFloat() / total) * 360f
                    val color = colors[index % colors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 24f)
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Legend
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats.forEachIndexed { index, pair ->
                val color = colors[index % colors.size]
                val percentage = (pair.second.toFloat() / total * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = pair.first,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${pair.second} ($percentage%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// PIN Lock / Passcode Fallback implementation
// ==========================================
@Composable
fun PasscodeDialog(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("passcode_prefs", android.content.Context.MODE_PRIVATE) }
    val savedPin = remember { prefs.getString("custom_pin", null) }
    
    var pinText by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(if (savedPin == null) "SET" else "VERIFY") }
    var setPinFirst by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (step) {
                        "SET" -> "Establecer PIN de Acceso"
                        "CONFIRM" -> "Confirmar PIN de Acceso"
                        else -> "Ingresa tu PIN"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = when (step) {
                        "SET" -> "Crea un PIN de 4 dígitos para proteger tu carpeta oculta"
                        "CONFIRM" -> "Vuelve a ingresar el PIN para confirmar"
                        else -> "La sección de imágenes ocultas está protegida"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val active = index < pinText.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Keyboard Grid (3x4)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )
                    
                    keys.forEach { rowKeys ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowKeys.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (key.isEmpty()) Color.Transparent 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable(enabled = key.isNotEmpty()) {
                                            errorMessage = ""
                                            when (key) {
                                                "C" -> pinText = ""
                                                "⌫" -> {
                                                    if (pinText.isNotEmpty()) {
                                                        pinText = pinText.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    if (pinText.length < 4) {
                                                        pinText += key
                                                        if (pinText.length == 4) {
                                                            if (step == "SET") {
                                                                setPinFirst = pinText
                                                                pinText = ""
                                                                step = "CONFIRM"
                                                            } else if (step == "CONFIRM") {
                                                                if (pinText == setPinFirst) {
                                                                    prefs.edit().putString("custom_pin", pinText).apply()
                                                                    onSuccess()
                                                                } else {
                                                                    errorMessage = "Los PINs no coinciden. Inténtalo de nuevo."
                                                                    pinText = ""
                                                                    step = "SET"
                                                                }
                                                            } else {
                                                                if (pinText == savedPin) {
                                                                    onSuccess()
                                                                } else {
                                                                    errorMessage = "PIN incorrecto. Inténtalo de nuevo."
                                                                    pinText = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

