package com.carv.smbridge.ui.components.filelist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.carv.smbridge.R
import com.carv.smbridge.viewmodels.SerializableSmbFile

@Composable
fun CreateFolderDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_folder)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name.trim())
                            name = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.create_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDismiss()
                    name = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun RenameDialog(
    show: Boolean,
    fileToRename: SerializableSmbFile?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(show) {
        if (show) newName = fileToRename?.name ?: ""
    }

    if (show && fileToRename != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName.trim())
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun DeleteDialog(
    show: Boolean,
    file: SerializableSmbFile?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    if (show && file != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.delete) + " " + file.name
                )
            },
            text = {
                val type = if (file.isDirectory)
                    stringResource(R.string.directory_lower_case)
                else
                    stringResource(R.string.file_lower_case)

                Text(
                    text = stringResource(R.string.text_confirm_delete_dialo) + " " + type + "?",
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
