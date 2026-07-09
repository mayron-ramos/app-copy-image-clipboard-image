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
            
            // Create local copy directory using custom storage location
            val localFolder = getStorageFolder(context)
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
            
            val autoHideOrigins = getAutoHideOrigins(context)
            val appNameToCheck = sourceAppName ?: "Otros/Portapapeles"
            val shouldHide = autoHideOrigins.contains(appNameToCheck)

            val imageRecord = CopiedImage(
                localFilePath = localFile.absolutePath,
                mimeType = rawMimeType,
                originalFileName = originalName,
                timestamp = timestamp,
                isCopied = true,
                sourcePackage = sourcePackage,
                sourceAppName = sourceAppName,
                sourceUrl = sourceUrl,
                isHidden = shouldHide
            )
            repository.insert(imageRecord)

            // Auto cleanup storage
            performStorageCleanup(context)

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
    private const val KEY_STORAGE_LOCATION = "pref_storage_location"

    private const val KEY_LIMIT_BY_COUNT_ENABLED = "pref_limit_by_count_enabled"
    private const val KEY_LIMIT_BY_COUNT_VALUE = "pref_limit_by_count_value"
    private const val KEY_LIMIT_BY_AGE_ENABLED = "pref_limit_by_age_enabled"
    private const val KEY_LIMIT_BY_AGE_VALUE = "pref_limit_by_age_value"
    private const val KEY_LIMIT_BY_SIZE_ENABLED = "pref_limit_by_size_enabled"
    private const val KEY_LIMIT_BY_SIZE_VALUE = "pref_limit_by_size_value"
    private const val KEY_AUTO_HIDE_ORIGINS = "pref_auto_hide_origins"

    fun getStorageLocation(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STORAGE_LOCATION, "INTERNAL") ?: "INTERNAL"
    }

    fun setStorageLocation(context: Context, location: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STORAGE_LOCATION, location).apply()
    }

    fun getStorageFolder(context: Context): File {
        val location = getStorageLocation(context)
        val folderName = "copied_images"
        val folder = when (location) {
            "EXTERNAL" -> {
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) File(extDir, folderName) else File(context.filesDir, folderName)
            }
            "PUBLIC" -> {
                val pubDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val target = File(pubDir, "CopiedImages")
                try {
                    if (!target.exists()) {
                        target.mkdirs()
                    }
                    if (target.canWrite() || android.os.Build.VERSION.SDK_INT >= 29) target else File(context.filesDir, folderName)
                } catch (e: Exception) {
                    File(context.filesDir, folderName)
                }
            }
            else -> File(context.filesDir, folderName)
        }
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

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

    fun isLimitByCountEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIMIT_BY_COUNT_ENABLED, false)
    }

    fun setLimitByCountEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIMIT_BY_COUNT_ENABLED, enabled).apply()
    }

    fun getLimitByCountValue(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LIMIT_BY_COUNT_VALUE, 20)
    }

    fun setLimitByCountValue(context: Context, value: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LIMIT_BY_COUNT_VALUE, value).apply()
    }

    fun isLimitByAgeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIMIT_BY_AGE_ENABLED, false)
    }

    fun setLimitByAgeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIMIT_BY_AGE_ENABLED, enabled).apply()
    }

    fun getLimitByAgeValue(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LIMIT_BY_AGE_VALUE, 7) // Default 7 days
    }

    fun setLimitByAgeValue(context: Context, value: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LIMIT_BY_AGE_VALUE, value).apply()
    }

    fun isLimitBySizeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LIMIT_BY_SIZE_ENABLED, false)
    }

    fun setLimitBySizeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIMIT_BY_SIZE_ENABLED, enabled).apply()
    }

    fun getLimitBySizeValue(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LIMIT_BY_SIZE_VALUE, 50) // Default 50 MB
    }

    fun setLimitBySizeValue(context: Context, value: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LIMIT_BY_SIZE_VALUE, value).apply()
    }

    fun getAutoHideOrigins(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_AUTO_HIDE_ORIGINS, emptySet()) ?: emptySet()
    }

    fun setAutoHideOrigins(context: Context, origins: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_AUTO_HIDE_ORIGINS, origins).apply()
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
                    
                    val localFolder = getStorageFolder(context)
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
                    
                    val autoHideOrigins = getAutoHideOrigins(context)
                    val appNameToCheck = sourceAppName ?: "Otros/Portapapeles"
                    val shouldHide = autoHideOrigins.contains(appNameToCheck)

                    val imageRecord = CopiedImage(
                        localFilePath = localFile.absolutePath,
                        mimeType = contentType,
                        originalFileName = originalName,
                        timestamp = timestamp,
                        isCopied = true,
                        sourcePackage = sourcePackage,
                        sourceAppName = sourceAppName,
                        sourceUrl = imageUrl,
                        isHidden = shouldHide
                    )
                    repository.insert(imageRecord)
                    
                    // Auto cleanup storage
                    performStorageCleanup(context)
                    
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

    suspend fun performStorageCleanup(context: Context): Pair<Int, Long> = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val repository = CopiedImageRepository(database.copiedImageDao())
            val allImages = repository.getAllImagesDirect()
            
            val toDelete = mutableSetOf<CopiedImage>()
            
            // 1. Check age limit
            if (isLimitByAgeEnabled(context)) {
                val daysLimit = getLimitByAgeValue(context)
                val cutOffTime = System.currentTimeMillis() - (daysLimit.toLong() * 24 * 60 * 60 * 1000)
                allImages.forEach { image ->
                    if (image.timestamp < cutOffTime) {
                        toDelete.add(image)
                    }
                }
            }
            
            // 2. Check max images per app/category
            if (isLimitByCountEnabled(context)) {
                val maxCount = getLimitByCountValue(context)
                // Group remaining images by sourceAppName (exclude those already marked for deletion)
                val remainingByApp = allImages.filter { !toDelete.contains(it) }
                    .groupBy { it.sourceAppName ?: "Otros/Portapapeles" }
                
                remainingByApp.forEach { (_, images) ->
                    val sorted = images.sortedByDescending { it.timestamp }
                    if (sorted.size > maxCount) {
                        for (i in maxCount until sorted.size) {
                            toDelete.add(sorted[i])
                        }
                    }
                }
            }
            
            // 3. Check total history size limit
            if (isLimitBySizeEnabled(context)) {
                val maxMegabytes = getLimitBySizeValue(context)
                val maxBytes = maxMegabytes.toLong() * 1024 * 1024
                
                val remaining = allImages.filter { !toDelete.contains(it) }
                    .sortedByDescending { it.timestamp }
                
                var currentTotalBytes = 0L
                remaining.forEach { image ->
                    val file = File(image.localFilePath)
                    if (file.exists()) {
                        val size = file.length()
                        if (currentTotalBytes + size > maxBytes) {
                            toDelete.add(image)
                        } else {
                            currentTotalBytes += size
                        }
                    } else {
                        toDelete.add(image)
                    }
                }
            }
            
            // Execute deletions
            var deletedCount = 0
            var bytesFreed = 0L
            toDelete.forEach { image ->
                try {
                    val file = File(image.localFilePath)
                    if (file.exists()) {
                        bytesFreed += file.length()
                        file.delete()
                    }
                    repository.delete(image)
                    deletedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            Pair(deletedCount, bytesFreed)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(0, 0L)
        }
    }
}
