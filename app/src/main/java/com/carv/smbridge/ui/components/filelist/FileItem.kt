package com.carv.smbridge.ui.components.filelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carv.smbridge.R
import com.carv.smbridge.utils.getFileTypeDetails
import com.carv.smbridge.viewmodels.SerializableSmbFile

@Composable
fun FileItem(
    file: SerializableSmbFile,
    isDownloading: Boolean,
    downloadProgress: Int?,
    onNavigate: (String) -> Unit,
    onOpenFile: (SerializableSmbFile) -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val fileDetails = getFileTypeDetails(file.name, file.isDirectory)

    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = file.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isDownloading && downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }

            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = fileDetails.iconResId),
                contentDescription = null,
                tint = fileDetails.color
            )
        },
        trailingContent = {
            if (isDownloading) {
                Text("${downloadProgress ?: 0}%", style = MaterialTheme.typography.bodySmall)
            } else {
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.options),
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (!file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download)) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cloud_download),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.trash),
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (file.isDirectory) onNavigate(file.path) else onOpenFile(file)
            }
    )
}
