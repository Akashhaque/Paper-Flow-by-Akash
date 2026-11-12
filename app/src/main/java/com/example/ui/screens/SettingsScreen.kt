package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ImageQualityOption
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val defaultPageSize by viewModel.selectedPageSize.collectAsState()
    val defaultOrientation by viewModel.selectedOrientation.collectAsState()
    val defaultQuality by viewModel.selectedImageQuality.collectAsState()

    var totalStorageBytes by remember { mutableStateOf(0L) }
    var cacheBytes by remember { mutableStateOf(0L) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allFiles) {
        withContext(Dispatchers.IO) {
            val docsSize = allFiles.sumOf { it.fileSize }
            totalStorageBytes = docsSize

            val cacheDir = File(context.cacheDir, "pdf_thumbs")
            cacheBytes = if (cacheDir.exists()) cacheDir.walkTopDown().sumOf { it.length() } else 0L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Preferences", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Preferences
            item {
                Text("General Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaperRed)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Language,
                            title = "Language",
                            subtitle = if (appLanguage == "bn") "বাংলা (Bangla)" else "English",
                            onClick = { showLanguageDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PaperLightRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = PaperRed, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Dark Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Comfortable viewing in low light", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.isDarkMode.value = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = PaperRed, checkedTrackColor = PaperLightRed)
                            )
                        }
                    }
                }
            }

            // PDF Defaults
            item {
                Text("Default Document Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaperRed)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Description,
                            title = "Default Page Size",
                            subtitle = defaultPageSize.displayName,
                            onClick = {
                                val next = when (defaultPageSize) {
                                    PageSizeOption.A4 -> PageSizeOption.LETTER
                                    PageSizeOption.LETTER -> PageSizeOption.LEGAL
                                    PageSizeOption.LEGAL -> PageSizeOption.A3
                                    else -> PageSizeOption.A4
                                }
                                viewModel.selectedPageSize.value = next
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Default.ScreenRotation,
                            title = "Default Orientation",
                            subtitle = defaultOrientation.displayName,
                            onClick = {
                                val next = when (defaultOrientation) {
                                    PageOrientation.PORTRAIT -> PageOrientation.LANDSCAPE
                                    PageOrientation.LANDSCAPE -> PageOrientation.AUTO
                                    PageOrientation.AUTO -> PageOrientation.PORTRAIT
                                }
                                viewModel.selectedOrientation.value = next
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Default.HighQuality,
                            title = "Image Quality",
                            subtitle = defaultQuality.displayName,
                            onClick = {
                                val next = when (defaultQuality) {
                                    ImageQualityOption.HIGH -> ImageQualityOption.ORIGINAL
                                    ImageQualityOption.ORIGINAL -> ImageQualityOption.MEDIUM
                                    else -> ImageQualityOption.HIGH
                                }
                                viewModel.selectedImageQuality.value = next
                            }
                        )
                    }
                }
            }

            // Storage & Cache
            item {
                Text("Storage & Maintenance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaperRed)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PaperLightRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = PaperRed, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Document Storage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${allFiles.size} PDF files • ${Formatter.formatFileSize(context, totalStorageBytes)} used",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Thumbnail Cache", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "${Formatter.formatFileSize(context, cacheBytes)} temporary previews",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val thumbDir = File(context.cacheDir, "pdf_thumbs")
                                        if (thumbDir.exists()) thumbDir.deleteRecursively()
                                        cacheBytes = 0L
                                        viewModel.showMessage("Thumbnail cache cleared!")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Clear Cache", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // About & Legal
            item {
                Text("About & Security", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaperRed)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.PrivacyTip,
                            title = "Privacy Policy",
                            subtitle = "100% on-device document processing",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "About PaperFlow",
                            subtitle = "Version 1.0 (Production Ready)",
                            onClick = { showAboutDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Default.Person,
                            title = "About PaperFlow Developer",
                            subtitle = "Md Mohaimenul Haque Akash • Contact & Info",
                            onClick = { showDeveloperDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Choose Language") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.appLanguage.value = "en"
                                showLanguageDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appLanguage == "en", onClick = { viewModel.appLanguage.value = "en"; showLanguageDialog = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("English", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.appLanguage.value = "bn"
                                showLanguageDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appLanguage == "bn", onClick = { viewModel.appLanguage.value = "bn"; showLanguageDialog = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("বাংলা (Bangla)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Close") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("PaperFlow – PDF Maker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All-in-One Native Android PDF Maker & Document Scanner.", fontSize = 13.sp)
                    Text("Features:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Smart camera document scanner with ID card & passport modes", fontSize = 12.sp)
                    Text("• Image to PDF with live filters & page sizes", fontSize = 12.sp)
                    Text("• Universal DOCX, XLSX, PPTX to PDF converter", fontSize = 12.sp)
                    Text("• Full PDF editor with pens, highlighters & digital signatures", fontSize = 12.sp)
                    Text("• PDF organizer: Merge, split, rotate, delete, compress", fontSize = 12.sp)
                    Text("• Google ML Kit OCR text recognition", fontSize = 12.sp)
                    Text("• Watermark and page numbering tools", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Built with Jetpack Compose & Material 3.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK", color = PaperRed) }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zero Cloud Upload Guarantee", fontWeight = FontWeight.Bold, color = PaperRed)
                    Text(
                        "PaperFlow operates 100% offline on your device. Your sensitive files, photos, camera scans, and documents are never transmitted to any external server.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "All optical character recognition, conversions, annotations, and compression algorithms execute entirely on your phone.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Understood", color = PaperRed) }
            }
        )
    }

    if (showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperLightRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PaperRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "About PaperFlow Developer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Developer Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = PaperRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Developer Name",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Md Mohaimenul Haque Akash",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Contact Number (Clickable to Dial)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+8801744708170"))
                                            context.startActivity(dialIntent)
                                        } catch (e: Exception) {
                                            viewModel.showMessage("Contact: +880-1744708170")
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Contact Number",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "+880-1744708170",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Email Address (Clickable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:akash.gland@gmail.com"))
                                            context.startActivity(emailIntent)
                                        } catch (e: Exception) {
                                            viewModel.showMessage("Email: akash.gland@gmail.com")
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Email Address",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "akash.gland@gmail.com",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Lead software engineer and designer behind PaperFlow. Dedicated to crafting high-performance, 100% private, offline-first mobile document utilities.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeveloperDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PaperLightRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PaperRed, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
