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
            } else {
                Toast.makeText(this, R.string.toast_invalid_mime, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun handleSendImage(intent: Intent) {
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: intent.clipData?.getItemAt(0)?.uri

        if (imageUri != null) {
            val sourcePackage = getCallingPackageName()
            val sourceAppName = getAppNameFromPackage(sourcePackage)

            lifecycleScope.launch {
                val success = ClipboardHelper.copySharedUriToClipboard(
                    context = this@ShareActivity,
                    sharedUri = imageUri,
                    sourcePackage = sourcePackage,
                    sourceAppName = sourceAppName
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
