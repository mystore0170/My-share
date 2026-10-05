package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransferCategory
import com.example.data.model.TransferItem
import com.example.data.utils.FileUtils
import com.example.ui.theme.DeepIndigo
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TealAccent

/**
 * Filter definition for GetContent() MIME selection
 */
data class FileTypeFilter(
    val title: String,
    val mimeType: String,
    val category: TransferCategory,
    val icon: ImageVector,
    val testTag: String
)

val DEFAULT_FILE_FILTERS = listOf(
    FileTypeFilter("All Files", "*/*", TransferCategory.FILES, Icons.Default.FolderOpen, "file_selection_chip_all"),
    FileTypeFilter("Documents", "application/*", TransferCategory.FILES, Icons.Default.Description, "file_selection_chip_docs"),
    FileTypeFilter("PDFs", "application/pdf", TransferCategory.FILES, Icons.Default.PictureAsPdf, "file_selection_chip_pdf"),
    FileTypeFilter("Images", "image/*", TransferCategory.PHOTOS, Icons.Default.Image, "file_selection_chip_images"),
    FileTypeFilter("Videos", "video/*", TransferCategory.VIDEOS, Icons.Default.Videocam, "file_selection_chip_videos"),
    FileTypeFilter("Audio", "audio/*", TransferCategory.AUDIO, Icons.Default.Audiotrack, "file_selection_chip_audio")
)

/**
 * A dedicated, reusable file selection UI component using ActivityResultContracts.GetContent()
 * to allow users to select media or documents from their device for upcoming transfer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileSelectionComponent(
    onFileSelected: (Uri, TransferCategory) -> Unit,
    modifier: Modifier = Modifier,
    stagedItems: List<TransferItem> = emptyList(),
    onRemoveItem: (String) -> Unit = {},
    initialFilterMime: String = "*/*",
    initialCategory: TransferCategory? = null,
    title: String = "Select Files for Transfer",
    subtitle: String = "Pick documents or media from device storage using system selector"
) {
    var selectedFilter by remember {
        mutableStateOf(
            DEFAULT_FILE_FILTERS.firstOrNull {
                if (initialCategory != null) it.category == initialCategory else it.mimeType == initialFilterMime
            } ?: DEFAULT_FILE_FILTERS[0]
        )
    }

    // ActivityResultContracts.GetContent() launcher
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected(uri, selectedFilter.category)
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_selection_component"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = ElectricBlue.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "File Selector",
                            tint = ElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (stagedItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${stagedItems.size} staged",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File Type Category Chips (Horizontal Scrollable)
            Text(
                text = "Target Type (MIME: ${selectedFilter.mimeType})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DEFAULT_FILE_FILTERS.forEach { filter ->
                    val isSelected = selectedFilter.title == filter.title
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = filter.icon,
                                contentDescription = filter.title,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.16f),
                            selectedLabelColor = ElectricBlue,
                            selectedLeadingIconColor = ElectricBlue
                        ),
                        modifier = Modifier.testTag(filter.testTag)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Dropzone / Primary Action Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        getContentLauncher.launch(selectedFilter.mimeType)
                    }
                    .testTag("file_selection_dropzone"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        color = ElectricBlue
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Select File",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Choose ${selectedFilter.title}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Tap here to browse device files with GetContent()",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            getContentLauncher.launch(selectedFilter.mimeType)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.testTag("file_selection_get_content_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Browse Storage",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Browse ${selectedFilter.title}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Direct Shortcuts for Documents vs Media
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedFilter = DEFAULT_FILE_FILTERS.first { it.title == "Documents" }
                        getContentLauncher.launch("application/*")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("file_selection_pick_document_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Pick Document",
                        modifier = Modifier.size(16.dp),
                        tint = ElectricBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Documents",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = {
                        selectedFilter = DEFAULT_FILE_FILTERS.first { it.title == "Images" }
                        getContentLauncher.launch("image/*")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("file_selection_pick_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Pick Media",
                        modifier = Modifier.size(16.dp),
                        tint = TealAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Media / Photos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Staged Items Preview (if any items are staged)
            AnimatedVisibility(
                visible = stagedItems.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Staged for Upcoming Transfer (${stagedItems.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ready",
                                tint = TealAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ready to Send",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    stagedItems.take(5).forEach { item ->
                        StagedItemRow(
                            item = item,
                            onRemove = { onRemoveItem(item.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (stagedItems.size > 5) {
                        Text(
                            text = "+ ${stagedItems.size - 5} more items selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StagedItemRow(
    item: TransferItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (item.category) {
        TransferCategory.PHOTOS -> Icons.Default.Image
        TransferCategory.VIDEOS -> Icons.Default.Videocam
        TransferCategory.AUDIO -> Icons.Default.Audiotrack
        TransferCategory.CONTACTS -> Icons.Default.Description
        TransferCategory.APPS -> Icons.Default.Description
        TransferCategory.FILES -> {
            if (item.name.endsWith(".pdf", ignoreCase = true)) {
                Icons.Default.PictureAsPdf
            } else {
                Icons.AutoMirrored.Filled.InsertDriveFile
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_selection_staged_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = ElectricBlue.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.category.name,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FileUtils.formatFileSize(item.size)} • ${item.category.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("file_selection_remove_item_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
