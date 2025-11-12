package com.carv.smbridge.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.carv.smbridge.R

data class FileTypeDetails(val iconResId: Int, val color: Color)

@Composable
fun getFileTypeDetails(fileName: String, isDirectory: Boolean): FileTypeDetails {
    val colorFolder = MaterialTheme.colorScheme.primary
    val colorMusic = Color(0xFF9C27B0)
    val colorImage = Color(0xFF4CAF50)
    val colorVideo = Color(0xFFE53935)
    val colorDoc = Color(0xFF1E88E5)
    val colorArchive = Color(0xFFF57C00)
    val colorDefault = MaterialTheme.colorScheme.onSurfaceVariant

    if (isDirectory) return FileTypeDetails(R.drawable.folder, colorFolder)

    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when (extension) {
        "mp3", "wav", "ogg", "m4a" -> FileTypeDetails(R.drawable.music, colorMusic)
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> FileTypeDetails(R.drawable.image, colorImage)
        "mp4", "mkv", "webm", "avi" -> FileTypeDetails(R.drawable.video, colorVideo)
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "pdf" -> FileTypeDetails(R.drawable.file, colorDoc)
        "zip", "rar", "7z" -> FileTypeDetails(R.drawable.file_archive, colorArchive)
        else -> FileTypeDetails(R.drawable.file, colorDefault)
    }
}