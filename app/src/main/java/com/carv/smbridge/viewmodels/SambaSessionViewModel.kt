package com.carv.smbridge.viewmodels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.Serializable

data class SerializableSmbFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean
) : Serializable

data class SambaUiState(
    val host: String = "10.0.2.2",
    val share: String = "SMBridge",
    val user: String = "cristianalejandro",
    val password: String = "Tamal",
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val rootPath: String? = null,
    val currentPath: String? = null,
    val files: List<SerializableSmbFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val statusMessage: String = "",
    val downloadingFilePath: String? = null,
    val downloadProgress: Int? = null,
    val fileToOpen: Uri? = null
) : Serializable

sealed class ConnectionStatus : Serializable {
    object Idle : ConnectionStatus()
    object Loading : ConnectionStatus()
    data class Success(val smbPath: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class SambaSessionViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val uiState: StateFlow<SambaUiState> = savedStateHandle.getStateFlow("uiState", SambaUiState())

    private fun getAuthenticator(): NtlmPasswordAuthenticator? {
        val state = uiState.value
        return if (state.user.isNotBlank() && state.password.isNotBlank())
            NtlmPasswordAuthenticator(null, state.user, state.password)
        else null
    }

    fun onHostChanged(value: String) { savedStateHandle["uiState"] = uiState.value.copy(host = value) }
    fun onShareChanged(value: String) { savedStateHandle["uiState"] = uiState.value.copy(share = value) }
    fun onUserChanged(value: String) { savedStateHandle["uiState"] = uiState.value.copy(user = value) }
    fun onPasswordChanged(value: String) { savedStateHandle["uiState"] = uiState.value.copy(password = value) }
    fun resetConnectionStatus() { savedStateHandle["uiState"] = uiState.value.copy(connectionStatus = ConnectionStatus.Idle) }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            savedStateHandle["uiState"] = uiState.value.copy(connectionStatus = ConnectionStatus.Loading)
            val connectionResult = withTimeoutOrNull(10_000L) {
                try {
                    val state = uiState.value
                    val auth = NtlmPasswordAuthenticator(null, state.user, state.password)
                    val smbUrl = "smb://${state.host}/${state.share}/"
                    val smbDir = SmbFile(smbUrl, SingletonContext.getInstance().withCredentials(auth))
                    if (smbDir.exists()) ConnectionStatus.Success(smbUrl)
                    else ConnectionStatus.Error("No se encontró el recurso compartido.")
                } catch (e: Exception) {
                    ConnectionStatus.Error(e.message ?: "Error desconocido")
                }
            }
            if (connectionResult == null) {
                savedStateHandle["uiState"] = uiState.value.copy(
                    connectionStatus = ConnectionStatus.Error("Tiempo de espera agotado (10s).")
                )
            } else {
                val rootPath = (connectionResult as? ConnectionStatus.Success)?.smbPath
                savedStateHandle["uiState"] = uiState.value.copy(connectionStatus = connectionResult, rootPath = rootPath)
            }
        }
    }

    fun loadFiles(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            savedStateHandle["uiState"] =
                uiState.value.copy(isLoadingFiles = true, currentPath = path, statusMessage = "Cargando...")

            val auth = getAuthenticator() ?: return@launch run {
                savedStateHandle["uiState"] =
                    uiState.value.copy(isLoadingFiles = false, statusMessage = "Error: Sesión perdida.")
            }

            try {
                val ctx = SingletonContext.getInstance().withCredentials(auth)
                val dir = SmbFile(path, ctx)
                if (dir.isDirectory) {
                    val list = dir.listFiles()?.map {
                        SerializableSmbFile(it.path, it.name.removeSuffix("/"), it.isDirectory)
                    }?.sortedBy { !it.isDirectory } ?: emptyList()
                    savedStateHandle["uiState"] =
                        uiState.value.copy(isLoadingFiles = false, files = list, statusMessage = "${list.size} elementos")
                } else {
                    savedStateHandle["uiState"] =
                        uiState.value.copy(isLoadingFiles = false, statusMessage = "La ruta no es un directorio.")
                }
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(isLoadingFiles = false, statusMessage = "Error: ${e.message}")
            }
        }
    }

    fun openFile(fileWrapper: SerializableSmbFile, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            savedStateHandle["uiState"] = uiState.value.copy(downloadingFilePath = fileWrapper.path)
            val auth = getAuthenticator() ?: return@launch
            try {
                val smbFile = SmbFile(fileWrapper.path, SingletonContext.getInstance().withCredentials(auth))
                val tempFile = File(context.cacheDir, fileWrapper.name)
                smbFile.inputStream.use { it.copyTo(tempFile.outputStream()) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
                savedStateHandle["uiState"] =
                    uiState.value.copy(downloadingFilePath = null, fileToOpen = uri)
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(downloadingFilePath = null, statusMessage = "Error al descargar: ${e.message}")
            }
        }
    }

    fun onFileOpened() {
        savedStateHandle["uiState"] = uiState.value.copy(fileToOpen = null)
    }

    fun downloadFileToPublic(fileWrapper: SerializableSmbFile, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            savedStateHandle["uiState"] =
                uiState.value.copy(
                    downloadingFilePath = fileWrapper.path,
                    downloadProgress = 0,
                    statusMessage = "Iniciando descarga..."
                )

            val auth = getAuthenticator() ?: return@launch
            try {
                val smbFile = SmbFile(fileWrapper.path, SingletonContext.getInstance().withCredentials(auth))
                val fileSize = smbFile.length()

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileWrapper.name)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("No se pudo crear el archivo de destino.")

                resolver.openOutputStream(uri)?.use { output ->
                    smbFile.inputStream.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            if (fileSize > 0) {
                                val progress = ((totalRead * 100) / fileSize).toInt()
                                if (progress != lastProgress && progress % 2 == 0) {
                                    lastProgress = progress
                                    savedStateHandle["uiState"] = uiState.value.copy(
                                        downloadProgress = progress,
                                        statusMessage = "Descargando ${fileWrapper.name}: $progress%"
                                    )
                                }
                            }
                        }
                    }
                }

                savedStateHandle["uiState"] = uiState.value.copy(
                    downloadingFilePath = null,
                    downloadProgress = null,
                    statusMessage = "'${fileWrapper.name}' descargado correctamente."
                )
            } catch (e: Exception) {
                savedStateHandle["uiState"] = uiState.value.copy(
                    downloadingFilePath = null,
                    downloadProgress = null,
                    statusMessage = "Error al descargar: ${e.message}"
                )
            }
        }
    }

    fun uploadFile(fileUri: Uri, destinationPath: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = getAuthenticator() ?: return@launch
            val fileName = getFileName(fileUri, context) ?: return@launch
            savedStateHandle["uiState"] =
                uiState.value.copy(statusMessage = "Subiendo '$fileName'...", isLoadingFiles = true)
            try {
                val safeDest = if (destinationPath.endsWith("/")) destinationPath else "$destinationPath/"
                val dest = SmbFile("$safeDest$fileName", SingletonContext.getInstance().withCredentials(auth))
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    dest.outputStream.use { input.copyTo(it) }
                }
                loadFiles(destinationPath)
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "Error al subir: ${e.message}", isLoadingFiles = false)
            }
        }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = it.getString(idx)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    fun createFolder(parentPath: String, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = getAuthenticator() ?: return@launch
            try {
                val safeParent = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
                val newFolderPath = "$safeParent$folderName/"
                val folder = SmbFile(newFolderPath, SingletonContext.getInstance().withCredentials(auth))
                if (!folder.exists()) {
                    folder.mkdir()
                    savedStateHandle["uiState"] =
                        uiState.value.copy(statusMessage = "Carpeta '$folderName' creada.")
                    loadFiles(parentPath)
                } else {
                    savedStateHandle["uiState"] = uiState.value.copy(statusMessage = "Ya existe la carpeta.")
                }
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "Error al crear carpeta: ${e.message}")
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = getAuthenticator() ?: return@launch
            try {
                val ctx = SingletonContext.getInstance().withCredentials(auth)
                val oldFile = SmbFile(oldPath, ctx)

                if (!oldFile.exists()) {
                    savedStateHandle["uiState"] =
                        uiState.value.copy(statusMessage = "No existe el archivo o carpeta original.")
                    return@launch
                }

                val parent = oldFile.parent
                val safeNewName = newName.trim().removeSuffix("/")

                val newPath = "$parent$safeNewName"
                val newFile = SmbFile(newPath, ctx)

                if (newFile.exists()) {
                    savedStateHandle["uiState"] =
                        uiState.value.copy(statusMessage = "Ya existe un archivo o carpeta con ese nombre.")
                    return@launch
                }

                oldFile.renameTo(newFile)

                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "Renombrado a '$safeNewName'.")
                loadFiles(parent)
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "Error al renombrar: ${e.message}")
            }
        }
    }


    fun deleteFile(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = getAuthenticator() ?: return@launch
            try {
                val ctx = SingletonContext.getInstance().withCredentials(auth)
                val smbFile = SmbFile(filePath, ctx)

                if (!smbFile.exists()) {
                    savedStateHandle["uiState"] = uiState.value.copy(statusMessage = "No se encontró el archivo o carpeta.")
                    return@launch
                }

                if (smbFile.isDirectory) {
                    try {
                        smbFile.listFiles()?.forEach { child ->
                            if (child.isDirectory) {
                                deleteFile(child.path)
                            } else {
                                child.delete()
                            }
                        }
                    } catch (e: Exception) {
                        savedStateHandle["uiState"] = uiState.value.copy(statusMessage = "No se pudo listar contenido de la carpeta: ${e.message}")
                        return@launch
                    }
                }

                smbFile.delete()
                val parentPath = smbFile.parent
                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "'${smbFile.name.removeSuffix("/")}' eliminado correctamente.")
                loadFiles(parentPath)
            } catch (e: Exception) {
                savedStateHandle["uiState"] =
                    uiState.value.copy(statusMessage = "Error al eliminar: ${e.message}")
            }
        }
    }

}
