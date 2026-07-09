package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {

    fun exportBackup(context: Context): Uri? {
        try {
            // 1. Force flush & Close database to ensure the backup is consistent
            val db = AppDatabase.getDatabase(context)
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            AppDatabase.resetInstance()

            // 2. Create ZIP file in cache
            val cacheFolder = context.cacheDir
            val backupFile = File(cacheFolder, "backup_copied_images.zip")
            if (backupFile.exists()) {
                backupFile.delete()
            }

            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                // Write DB files
                val dbFile = context.getDatabasePath("copy_image_database")
                val dbFolder = dbFile.parentFile
                if (dbFolder != null && dbFolder.exists()) {
                    dbFolder.listFiles()?.forEach { file ->
                        if (file.name.startsWith("copy_image_database")) {
                            val entry = ZipEntry("db/${file.name}")
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                // Write images from current storage directory
                val imagesFolder = ClipboardHelper.getStorageFolder(context)
                if (imagesFolder.exists()) {
                    imagesFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val entry = ZipEntry("images/${file.name}")
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            // Return shared URI via FileProvider
            val authority = "${context.packageName}.fileprovider"
            return FileProvider.getUriForFile(context, authority, backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun importBackup(context: Context, zipUri: Uri): Boolean {
        try {
            // Close and reset current DB
            AppDatabase.resetInstance()

            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(zipUri) ?: return false

            // Create temp restore directory
            val tempRestoreDir = File(context.cacheDir, "temp_restore")
            if (tempRestoreDir.exists()) {
                tempRestoreDir.deleteRecursively()
            }
            tempRestoreDir.mkdirs()

            // Unzip to temp folder
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tempRestoreDir, entry.name)
                    if (entry.name.endsWith("/")) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Validate temp folder content (must contain db/copy_image_database)
            val dbSrcFolder = File(tempRestoreDir, "db")
            val mainDbFile = File(dbSrcFolder, "copy_image_database")
            if (!mainDbFile.exists()) {
                tempRestoreDir.deleteRecursively()
                return false
            }

            // Replace DB files
            val dbFile = context.getDatabasePath("copy_image_database")
            val dbDestFolder = dbFile.parentFile ?: return false
            if (!dbDestFolder.exists()) {
                dbDestFolder.mkdirs()
            }

            // Delete old db files first
            dbDestFolder.listFiles()?.forEach { file ->
                if (file.name.startsWith("copy_image_database")) {
                    file.delete()
                }
            }

            // Copy new db files
            dbSrcFolder.listFiles()?.forEach { srcFile ->
                if (srcFile.name.startsWith("copy_image_database")) {
                    val destFile = File(dbDestFolder, srcFile.name)
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }

            // Copy images to current active storage folder
            val imgSrcFolder = File(tempRestoreDir, "images")
            val imgDestFolder = ClipboardHelper.getStorageFolder(context)
            if (imgSrcFolder.exists()) {
                if (!imgDestFolder.exists()) {
                    imgDestFolder.mkdirs()
                }
                imgSrcFolder.listFiles()?.forEach { srcFile ->
                    if (srcFile.isFile) {
                        val destFile = File(imgDestFolder, srcFile.name)
                        srcFile.copyTo(destFile, overwrite = true)
                    }
                }
            }

            // Clean up temp restore directory
            tempRestoreDir.deleteRecursively()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
