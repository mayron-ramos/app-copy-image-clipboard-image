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
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = CopiedImageRepository(database.copiedImageDao())
    }

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Selected app filter (null means "All")
    val selectedAppFilter = MutableStateFlow<String?>(null)

    // Transparent copy preference state
    val isTransparentCopyEnabled = MutableStateFlow(
        ClipboardHelper.isTransparentCopyEnabled(application)
    )

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

    // Filtered images list
    val uiState: StateFlow<List<CopiedImage>> = combine(
        repository.allImages,
        searchQuery,
        selectedAppFilter
    ) { images, query, appFilter ->
        var filteredList = images
        
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
        
        filteredList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
}
