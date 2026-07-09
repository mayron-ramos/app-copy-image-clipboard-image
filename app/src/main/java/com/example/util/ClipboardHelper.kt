package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.CopiedImage
import com.example.data.CopiedImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ClipboardHelper {

    suspend fun copySharedUriToClipboard(
        context: Context,
        sharedUri: Uri,
        sourcePackage: String? = null,
        sourceAppName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val rawMimeType = contentResolver.getType(sharedUri) ?: "image/png"
            
            // Resolve file extension
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(rawMimeType) ?: "png"
            
            // Create target file name
            val timestamp = System.currentTimeMillis()
            val fileName = "img_$timestamp.$extension"
            
            // Create local copy directory inside filesDir
            val localFolder = File(context.filesDir, "copied_images")
            if (!localFolder.exists()) {
                localFolder.mkdirs()
            }
            val localFile = File(localFolder, fileName)
            
            // Get original file name if possible
            var originalName: String? = null
            contentResolver.query(sharedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    originalName = cursor.getString(nameIndex)
                }
            }
            if (originalName == null) {
                originalName = sharedUri.lastPathSegment
            }

            // Copy bytes from stream
            contentResolver.openInputStream(sharedUri)?.use { inputStream ->
                localFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext false

            // Save to database
            val database = AppDatabase.getDatabase(context)
            val repository = CopiedImageRepository(database.copiedImageDao())
            val imageRecord = CopiedImage(
                localFilePath = localFile.absolutePath,
                mimeType = rawMimeType,
                originalFileName = originalName,
                timestamp = timestamp,
                isCopied = true,
                sourcePackage = sourcePackage,
                sourceAppName = sourceAppName
            )
            repository.insert(imageRecord)

            // Copy to clipboard via FileProvider
            copyLocalFileToClipboard(context, localFile, rawMimeType)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copyLocalFileToClipboard(context: Context, file: File, mimeType: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)

        // Create ClipData
        val clip = ClipData.newUri(context.contentResolver, "Clipped Image", fileUri)
        
        clipboard.setPrimaryClip(clip)
    }

    // App preferences helpers
    private const val PREFS_NAME = "copy_image_prefs"
    private const val KEY_TRANSPARENT_COPY = "pref_transparent_copy"

    fun isTransparentCopyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TRANSPARENT_COPY, true) // Enabled by default
    }

    fun setTransparentCopyEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRANSPARENT_COPY, enabled).apply()
    }
}
