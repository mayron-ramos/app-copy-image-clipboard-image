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
        sourceAppName: String? = null,
        sourceUrl: String? = null
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
                sourceAppName = sourceAppName,
                sourceUrl = sourceUrl
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
    private const val KEY_SERVICE_ACTIVE = "pref_service_active"

    fun isTransparentCopyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TRANSPARENT_COPY, true) // Enabled by default
    }

    fun setTransparentCopyEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRANSPARENT_COPY, enabled).apply()
    }

    fun isServiceActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SERVICE_ACTIVE, true) // Enabled by default
    }

    fun setServiceActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, active).apply()
    }

    private fun isTwitterOrXUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.contains("twitter.com", ignoreCase = true) || trimmed.contains("x.com", ignoreCase = true)) &&
                trimmed.contains("/status/", ignoreCase = true)
    }

    private fun convertToFxTwitterApiUrl(url: String): String? {
        val trimmed = url.trim()
        val statusIndex = trimmed.indexOf("/status/", ignoreCase = true)
        if (statusIndex == -1) return null
        
        val noScheme = trimmed.replace(Regex("(?i)^https?://"), "")
        val firstSlashIndex = noScheme.indexOf('/')
        if (firstSlashIndex == -1) return null
        
        val pathAndMore = noScheme.substring(firstSlashIndex)
        val pathOnly = pathAndMore.substringBefore('?').substringBefore('#')
        
        return "https://api.fxtwitter.com$pathOnly"
    }

    enum class CopyImageUrlResult {
        SUCCESS,
        NOT_AN_IMAGE,
        TWITTER_NO_IMAGES,
        NETWORK_ERROR,
        FAILED
    }

    suspend fun copyImageUrlToClipboard(
        context: Context,
        imageUrl: String,
        sourcePackage: String? = null,
        sourceAppName: String? = null
    ): CopyImageUrlResult = withContext(Dispatchers.IO) {
        try {
            var targetUrl = imageUrl.trim()
            
            // Handle Twitter/X posts
            if (isTwitterOrXUrl(targetUrl)) {
                val apiUrl = convertToFxTwitterApiUrl(targetUrl)
                if (apiUrl != null) {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", "Mozilla/5.0 (compatible; ImageCopierBot/1.0)")
                        .build()
                    
                    var resolvedImageUrl: String? = null
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val jsonResponse = response.body?.string()
                                if (!jsonResponse.isNullOrEmpty()) {
                                    val jsonObject = org.json.JSONObject(jsonResponse)
                                    if (jsonObject.has("tweet")) {
                                        val tweet = jsonObject.getJSONObject("tweet")
                                        if (tweet.has("media")) {
                                            val media = tweet.getJSONObject("media")
                                            
                                            if (media.has("photos")) {
                                                val photos = media.getJSONArray("photos")
                                                if (photos.length() > 0) {
                                                    resolvedImageUrl = photos.getJSONObject(0).optString("url")
                                                }
                                            }
                                            
                                            if (resolvedImageUrl.isNullOrEmpty() && media.has("all")) {
                                                val allMedia = media.getJSONArray("all")
                                                for (i in 0 until allMedia.length()) {
                                                    val item = allMedia.getJSONObject(i)
                                                    val type = item.optString("type")
                                                    if (type == "photo" || type == "image") {
                                                        resolvedImageUrl = item.optString("url")
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext CopyImageUrlResult.NETWORK_ERROR
                    }
                    
                    if (resolvedImageUrl.isNullOrEmpty()) {
                        return@withContext CopyImageUrlResult.TWITTER_NO_IMAGES
                    }
                    targetUrl = resolvedImageUrl!!
                } else {
                    return@withContext CopyImageUrlResult.FAILED
                }
            }

            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(targetUrl)
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext CopyImageUrlResult.NETWORK_ERROR
                    
                    val body = response.body ?: return@withContext CopyImageUrlResult.FAILED
                    val contentType = response.header("Content-Type") ?: ""
                    
                    // Validate that it actually is an image!
                    if (!contentType.startsWith("image/", ignoreCase = true)) {
                        return@withContext CopyImageUrlResult.NOT_AN_IMAGE
                    }
                    
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType) ?: "png"
                    val timestamp = System.currentTimeMillis()
                    val fileName = "img_$timestamp.$extension"
                    
                    val localFolder = File(context.filesDir, "copied_images")
                    if (!localFolder.exists()) {
                        localFolder.mkdirs()
                    }
                    val localFile = File(localFolder, fileName)
                    
                    localFile.outputStream().use { fos ->
                        body.source().inputStream().use { inputStream ->
                            inputStream.copyTo(fos)
                        }
                    }
                    
                    var originalName = targetUrl.substringAfterLast('/').substringBefore('?')
                    if (originalName.isEmpty() || !originalName.contains('.')) {
                        originalName = "url_image.$extension"
                    }
                    
                    val database = AppDatabase.getDatabase(context)
                    val repository = CopiedImageRepository(database.copiedImageDao())
                    val imageRecord = CopiedImage(
                        localFilePath = localFile.absolutePath,
                        mimeType = contentType,
                        originalFileName = originalName,
                        timestamp = timestamp,
                        isCopied = true,
                        sourcePackage = sourcePackage,
                        sourceAppName = sourceAppName,
                        sourceUrl = imageUrl
                    )
                    repository.insert(imageRecord)
                    
                    copyLocalFileToClipboard(context, localFile, contentType)
                    CopyImageUrlResult.SUCCESS
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CopyImageUrlResult.NETWORK_ERROR
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CopyImageUrlResult.FAILED
        }
    }
}
