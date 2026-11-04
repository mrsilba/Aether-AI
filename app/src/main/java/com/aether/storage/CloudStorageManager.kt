package com.aether.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class StorageProviderType(val displayName: String) {
    GOOGLE_DRIVE("Google Drive"),
    LOCAL_STORAGE_ACCESS_FRAMEWORK("Android Storage Access Framework (SAF)"),
    LOCAL_ZIP_ARCHIVE("Local ZIP Archive Extraction"),
    DIRECT_FILE_IMPORT("Direct File Importer")
}

data class CloudStorageFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isFolder: Boolean,
    val modifiedTimeMs: Long,
    val downloadUrl: String? = null
)

class ZipArchiveExtractor {

    /**
     * Extracts a ZIP archive safely into target directory with strict Zip-Slip protection.
     */
    fun extractSafely(
        zipInputStream: InputStream,
        targetDir: File,
        onFileExtracted: ((String) -> Unit)? = null
    ): Result<Int> {
        return try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val canonicalTargetRoot = targetDir.canonicalPath

            val zis = ZipInputStream(zipInputStream)
            var count = 0
            var entry: ZipEntry?

            val buffer = ByteArray(32 * 1024)

            while (zis.nextEntry.also { entry = it } != null) {
                val curEntry = entry ?: break
                val newFile = File(targetDir, curEntry.name)

                // Critical: Zip-Slip vulnerability prevention
                val canonicalDest = newFile.canonicalPath
                if (!canonicalDest.startsWith("$canonicalTargetRoot${File.separator}") && canonicalDest != canonicalTargetRoot) {
                    throw SecurityException("Zip-Slip path traversal attempt detected for entry: ${curEntry.name}")
                }

                if (curEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(newFile)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                    fos.flush()
                    fos.close()
                    count++
                    onFileExtracted?.invoke(curEntry.name)
                }
                zis.closeEntry()
            }
            zis.close()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class CloudStorageManager(
    private val context: Context,
    private val workspaceRoot: File
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val zipExtractor = ZipArchiveExtractor()

    /**
     * Imports a local ZIP archive from a File or Uri into the workspace.
     */
    suspend fun importZipArchive(
        zipFile: File,
        targetSubDir: String = "",
        onProgress: ((String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val dest = if (targetSubDir.isBlank()) workspaceRoot else File(workspaceRoot, targetSubDir)
        FileInputStream(zipFile).use { fis ->
            zipExtractor.extractSafely(fis, dest, onProgress)
        }
    }

    /**
     * Imports content from an Android SAF Content Uri into the workspace.
     */
    suspend fun importFromContentUri(
        uri: Uri,
        destFileName: String,
        subDir: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (subDir.isBlank()) workspaceRoot else File(workspaceRoot, subDir)
            if (!targetDir.exists()) targetDir.mkdirs()

            val destFile = File(targetDir, destFileName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream for Uri: $uri"))

            val outputStream = FileOutputStream(destFile)
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads and imports a file from a remote Google Drive URL into the local workspace.
     */
    suspend fun importFromRemoteUrl(
        downloadUrl: String,
        destFileName: String,
        subDir: String = "",
        authToken: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (subDir.isBlank()) workspaceRoot else File(workspaceRoot, subDir)
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, destFileName)

            val reqBuilder = Request.Builder().url(downloadUrl)
            if (!authToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $authToken")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty body"))
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(32 * 1024)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                outputStream.write(buffer, 0, len)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
