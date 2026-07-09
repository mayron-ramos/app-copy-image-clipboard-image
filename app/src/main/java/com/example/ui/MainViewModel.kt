package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CopiedImage
import com.example.data.CopiedImageRepository
import com.example.util.ClipboardHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CopiedImageRepository
    val autoHideOrigins = MutableStateFlow<Set<String>>(emptySet())
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = CopiedImageRepository(database.copiedImageDao())
        autoHideOrigins.value = ClipboardHelper.getAutoHideOrigins(application)
    }

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Selected app filter (null means "All")
    val selectedAppFilter = MutableStateFlow<String?>(null)

    // Sort options
    val sortField = MutableStateFlow(SortField.DATE)
    val sortOrder = MutableStateFlow(SortOrder.DESCENDING)

    // Transparent copy preference state
    val isTransparentCopyEnabled = MutableStateFlow(
        ClipboardHelper.isTransparentCopyEnabled(application)
    )

    // Service active preference state
    val isServiceActive = MutableStateFlow(
        ClipboardHelper.isServiceActive(application)
    )

    // Storage controls state
    val isLimitByCountEnabled = MutableStateFlow(
        ClipboardHelper.isLimitByCountEnabled(application)
    )
    val limitByCountValue = MutableStateFlow(
        ClipboardHelper.getLimitByCountValue(application)
    )
    val isLimitByAgeEnabled = MutableStateFlow(
        ClipboardHelper.isLimitByAgeEnabled(application)
    )
    val limitByAgeValue = MutableStateFlow(
        ClipboardHelper.getLimitByAgeValue(application)
    )
    val isLimitBySizeEnabled = MutableStateFlow(
        ClipboardHelper.isLimitBySizeEnabled(application)
    )
    val limitBySizeValue = MutableStateFlow(
        ClipboardHelper.getLimitBySizeValue(application)
    )

    fun toggleServiceActive(enabled: Boolean) {
        isServiceActive.value = enabled
        ClipboardHelper.setServiceActive(getApplication(), enabled)
    }

    // Dynamic list of unique apps available in history
    val availableApps: StateFlow<List<String>> = repository.allImages
         .map { images ->
             images.mapNotNull { it.sourceAppName }.distinct().sorted()
         }
         .stateIn(
             scope = viewModelScope,
             started = SharingStarted.WhileSubscribed(5000),
             initialValue = emptyList()
         )

    // Filtered and sorted images list (excludes hidden images)
    val uiState: StateFlow<List<CopiedImage>> = combine(
        repository.allImages,
        searchQuery,
        selectedAppFilter,
        sortField,
        sortOrder
    ) { images, query, appFilter, field, order ->
        var filteredList = images.filter { !it.isHidden }
        
        // Apply search query
        if (query.isNotBlank()) {
            filteredList = filteredList.filter {
                it.originalFileName?.contains(query, ignoreCase = true) == true
            }
        }
        
        // Apply app filter
        if (appFilter != null) {
            filteredList = filteredList.filter {
                if (appFilter == "Otros") {
                    it.sourceAppName.isNullOrEmpty()
                } else {
                    it.sourceAppName == appFilter
                }
            }
        }

        // Apply sorting
        filteredList = when (field) {
            SortField.DATE -> {
                if (order == SortOrder.ASCENDING) {
                    filteredList.sortedBy { it.timestamp }
                } else {
                    filteredList.sortedByDescending { it.timestamp }
                }
            }
            SortField.NAME -> {
                if (order == SortOrder.ASCENDING) {
                    filteredList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.originalFileName ?: "" })
                } else {
                    filteredList.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.originalFileName ?: "" })
                }
            }
        }
        
        filteredList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSortField(field: SortField) {
        sortField.value = field
    }

    fun updateSortOrder(order: SortOrder) {
        sortOrder.value = order
    }

    fun selectAppFilter(app: String?) {
        selectedAppFilter.value = app
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleTransparentCopy(enabled: Boolean) {
        isTransparentCopyEnabled.value = enabled
        ClipboardHelper.setTransparentCopyEnabled(getApplication(), enabled)
    }

    fun copyImageToClipboard(image: CopiedImage) {
        viewModelScope.launch {
            val file = File(image.localFilePath)
            if (file.exists()) {
                ClipboardHelper.copyLocalFileToClipboard(getApplication(), file, image.mimeType)
            }
        }
    }

    fun deleteImage(image: CopiedImage) {
        viewModelScope.launch {
            // Delete local file first
            val file = File(image.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            // Delete from database
            repository.delete(image)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            // Delete all local files first
            val localFolder = File(getApplication<Application>().filesDir, "copied_images")
            if (localFolder.exists()) {
                localFolder.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
            // Clear database
            repository.deleteAll()
        }
    }

    fun toggleLimitByCount(enabled: Boolean) {
        isLimitByCountEnabled.value = enabled
        ClipboardHelper.setLimitByCountEnabled(getApplication(), enabled)
    }

    fun updateLimitByCountValue(value: Int) {
        limitByCountValue.value = value
        ClipboardHelper.setLimitByCountValue(getApplication(), value)
    }

    fun toggleLimitByAge(enabled: Boolean) {
        isLimitByAgeEnabled.value = enabled
        ClipboardHelper.setLimitByAgeEnabled(getApplication(), enabled)
    }

    fun updateLimitByAgeValue(value: Int) {
        limitByAgeValue.value = value
        ClipboardHelper.setLimitByAgeValue(getApplication(), value)
    }

    fun toggleLimitBySize(enabled: Boolean) {
        isLimitBySizeEnabled.value = enabled
        ClipboardHelper.setLimitBySizeEnabled(getApplication(), enabled)
    }

    fun updateLimitBySizeValue(value: Int) {
        limitBySizeValue.value = value
        ClipboardHelper.setLimitBySizeValue(getApplication(), value)
    }

    // Flow for hidden images
    val hiddenImages: StateFlow<List<CopiedImage>> = repository.allImages
         .map { images ->
             images.filter { it.isHidden }
         }
         .stateIn(
             scope = viewModelScope,
             started = SharingStarted.WhileSubscribed(5000),
             initialValue = emptyList()
         )

    fun toggleAutoHideOrigin(origin: String) {
        val current = autoHideOrigins.value.toMutableSet()
        if (current.contains(origin)) {
            current.remove(origin)
        } else {
            current.add(origin)
        }
        autoHideOrigins.value = current
        ClipboardHelper.setAutoHideOrigins(getApplication(), current)
    }

    fun toggleImageHidden(image: CopiedImage) {
        viewModelScope.launch {
            val updated = image.copy(isHidden = !image.isHidden)
            repository.insert(updated)
        }
    }

    fun saveEditedImage(
        originalImage: CopiedImage,
        newFilePath: String,
        replaceOriginal: Boolean,
        onResult: () -> Unit
    ) {
        viewModelScope.launch {
            if (replaceOriginal) {
                // Delete old file if different path
                val oldFile = File(originalImage.localFilePath)
                val newFile = File(newFilePath)
                if (oldFile.absolutePath != newFile.absolutePath && oldFile.exists()) {
                    oldFile.delete()
                }
                val updated = originalImage.copy(
                    localFilePath = newFilePath,
                    timestamp = System.currentTimeMillis()
                )
                repository.insert(updated)
            } else {
                // Save as copy
                val newFile = File(newFilePath)
                val extension = newFile.extension.ifEmpty { "png" }
                val timestamp = System.currentTimeMillis()
                val originalName = originalImage.originalFileName ?: "edited_image"
                val baseName = originalName.substringBeforeLast(".")
                val newName = "${baseName}_edit_$timestamp.$extension"

                val imageRecord = CopiedImage(
                    localFilePath = newFilePath,
                    mimeType = originalImage.mimeType,
                    originalFileName = newName,
                    timestamp = timestamp,
                    isCopied = originalImage.isCopied,
                    sourcePackage = originalImage.sourcePackage,
                    sourceAppName = originalImage.sourceAppName,
                    sourceUrl = originalImage.sourceUrl,
                    isHidden = originalImage.isHidden
                )
                repository.insert(imageRecord)
            }
            onResult()
        }
    }

    fun runManualCleanup(onResult: (Int, Long) -> Unit) {
        viewModelScope.launch {
            val result = ClipboardHelper.performStorageCleanup(getApplication())
            onResult(result.first, result.second)
        }
    }
}

enum class SortField {
    DATE, NAME
}

enum class SortOrder {
    ASCENDING, DESCENDING
}
