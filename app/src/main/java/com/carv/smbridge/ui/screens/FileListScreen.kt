package com.carv.smbridge.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carv.smbridge.R
import com.carv.smbridge.ui.components.filelist.*
import com.carv.smbridge.viewmodels.SambaSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SambaSessionViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<com.carv.smbridge.viewmodels.SerializableSmbFile?>(null) }
    var showDeleteDialog by remember { mutableStateOf<com.carv.smbridge.viewmodels.SerializableSmbFile?>(null) }

    BackHandler {
        onBack()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFile(it, currentPath, context)
        }
    }

    LaunchedEffect(currentPath) {
        viewModel.loadFiles(currentPath)
    }

    LaunchedEffect(uiState.fileToOpen) {
        uiState.fileToOpen?.let { fileUri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, context.contentResolver.getType(fileUri) ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, R.string.open_with.toString()))
            viewModel.onFileOpened()
        }
    }

    Scaffold(
        topBar = {
            FileListTopBar(
                displayPath = uiState.rootPath?.let {
                    currentPath.removePrefix(it).let { path ->
                        "${uiState.share}/${path.trimStart('/')}".removeSuffix("/")
                    }
                } ?: uiState.share,
                onBack = onBack
            )
        },
        floatingActionButton = {
            FileListFabMenu(
                onCreateFolder = { showCreateFolderDialog = true },
                onUploadFile = { filePickerLauncher.launch("*/*") }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(horizontal = 8.dp)) {
            Text(
                text = uiState.statusMessage,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )

            if (uiState.isLoadingFiles) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.empty_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.files, key = { it.path }) { file ->
                        FileItem(
                            file = file,
                            isDownloading = uiState.downloadingFilePath == file.path,
                            downloadProgress = if (uiState.downloadingFilePath == file.path) uiState.downloadProgress else null,
                            onNavigate = onNavigate,
                            onOpenFile = { viewModel.openFile(it, context) },
                            onDownload = { viewModel.downloadFileToPublic(file, context) },
                            onRename = { showRenameDialog = file },
                            onDelete = { showDeleteDialog = file }
                        )
                    }
                }
            }
        }
    }

    CreateFolderDialog(
        show = showCreateFolderDialog,
        onDismiss = { showCreateFolderDialog = false },
        onCreate = { name ->
            viewModel.createFolder(currentPath, name.trim())
            showCreateFolderDialog = false
        }
    )

    RenameDialog(
        show = showRenameDialog != null,
        fileToRename = showRenameDialog,
        onDismiss = { showRenameDialog = null },
        onRename = { newName ->
            showRenameDialog?.let { file ->
                viewModel.renameFile(file.path, newName.trim())
                showRenameDialog = null
            }
        }
    )

    DeleteDialog(
        show = showDeleteDialog != null,
        file = showDeleteDialog,
        onDismiss = { showDeleteDialog = null },
        onDelete = {
            showDeleteDialog?.let { file ->
                viewModel.deleteFile(file.path)
                showDeleteDialog = null
            }
        }
    )
}