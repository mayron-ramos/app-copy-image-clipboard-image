package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.util.ClipboardHelper
import kotlinx.coroutines.launch
import java.util.Locale

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("image/")) {
                handleSendImage(intent)
            } else if (type == "text/plain") {
                handleSendText(intent)
            } else {
                Toast.makeText(this, R.string.toast_invalid_mime, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun handleSendImage(intent: Intent) {
        val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.getItemAt(0)?.uri

        if (imageUri != null) {
            val sourcePackage = getCallingPackageName()
            val sourceAppName = getAppNameFromPackage(sourcePackage)

            // Extract source URL from extra text if present
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val urlRegex = "(https?://[^\\s]+)".toRegex()
            val urlMatch = urlRegex.find(sharedText)
            val extractedUrl = urlMatch?.value

            lifecycleScope.launch {
                // Respect service active flag!
                if (!ClipboardHelper.isServiceActive(this@ShareActivity)) {
                    Toast.makeText(this@ShareActivity, "Servicio inactivo. Actívalo en la aplicación Copiar imagen.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                val success = ClipboardHelper.copySharedUriToClipboard(
                    context = this@ShareActivity,
                    sharedUri = imageUri,
                    sourcePackage = sourcePackage,
                    sourceAppName = sourceAppName,
                    sourceUrl = extractedUrl
                )
                if (success) {
                    Toast.makeText(this@ShareActivity, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ShareActivity, R.string.toast_copied_failed, Toast.LENGTH_SHORT).show()
                }

                // Decide whether to close instantly (transparent copy) or open the main app
                if (ClipboardHelper.isTransparentCopyEnabled(this@ShareActivity)) {
                    finish()
                } else {
                    val mainIntent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(mainIntent)
                    finish()
                }
            }
        } else {
            Toast.makeText(this, R.string.toast_copied_failed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleSendText(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val urlRegex = "(https?://[^\\s]+)".toRegex()
        val urlMatch = urlRegex.find(sharedText)
        
        if (urlMatch != null) {
            val extractedUrl = urlMatch.value
            val sourcePackage = getCallingPackageName()
            val sourceAppName = getAppNameFromPackage(sourcePackage)

            lifecycleScope.launch {
                if (!ClipboardHelper.isServiceActive(this@ShareActivity)) {
                    Toast.makeText(this@ShareActivity, "Servicio inactivo. Actívalo en la aplicación Copiar imagen.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                Toast.makeText(this@ShareActivity, "Descargando imagen desde URL...", Toast.LENGTH_SHORT).show()
                val result = ClipboardHelper.copyImageUrlToClipboard(
                    context = this@ShareActivity,
                    imageUrl = extractedUrl,
                    sourcePackage = sourcePackage,
                    sourceAppName = sourceAppName
                )
                when (result) {
                    ClipboardHelper.CopyImageUrlResult.SUCCESS -> {
                        Toast.makeText(this@ShareActivity, R.string.toast_copied_success, Toast.LENGTH_SHORT).show()
                    }
                    ClipboardHelper.CopyImageUrlResult.NOT_AN_IMAGE -> {
                        Toast.makeText(this@ShareActivity, "La URL compartida no contiene una imagen válida.", Toast.LENGTH_LONG).show()
                    }
                    ClipboardHelper.CopyImageUrlResult.TWITTER_NO_IMAGES -> {
                        Toast.makeText(this@ShareActivity, "Esta publicación de Twitter/X no contiene imágenes.", Toast.LENGTH_LONG).show()
                    }
                    ClipboardHelper.CopyImageUrlResult.NETWORK_ERROR -> {
                        Toast.makeText(this@ShareActivity, "Error de red al intentar descargar la imagen.", Toast.LENGTH_LONG).show()
                    }
                    ClipboardHelper.CopyImageUrlResult.FAILED -> {
                        Toast.makeText(this@ShareActivity, "No se pudo procesar la URL de la imagen.", Toast.LENGTH_LONG).show()
                    }
                }

                if (ClipboardHelper.isTransparentCopyEnabled(this@ShareActivity)) {
                    finish()
                } else {
                    val mainIntent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(mainIntent)
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "El texto compartido no contiene una URL válida.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getCallingPackageName(): String? {
        val directCalling = callingPackage ?: callingActivity?.packageName
        if (directCalling != null) return directCalling

        val ref = referrer
        if (ref != null && ref.scheme == "android-app") {
            return ref.host
        }
        return null
    }

    private fun getAppNameFromPackage(packageName: String?): String? {
        if (packageName.isNullOrEmpty()) return null
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
        }
    }
}
