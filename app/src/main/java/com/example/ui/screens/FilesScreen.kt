package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Folder
import com.example.data.model.PDFFile
import com.example.ui.AppScreen
import com.example.ui.FileSortOrder
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allFiles by viewModel.allFiles.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val favoriteFiles by viewModel.favoriteFiles.collectAsState()
    val folders by viewModel.allFolders.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Recent, 2: Favorites, 3: Folders
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.DATE_MODIFIED) }
    var isGridView by remember { mutableStateOf(false) }

    var fileToRename by remember { mutableStateOf<PDFFile?>(null) }
    var fileForDetails by remember { mutableStateOf<PDFFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var activeFolderFilter by remember { mutableStateOf<Folder?>(null) }

    // Filter by tab
    val baseList = when (selectedTab) {
        0 -> allFiles
        1 -> recentFiles
        2 -> favoriteFiles
        else -> if (activeFolderFilter != null) allFiles.filter { it.folderId == activeFolderFilter?.id } else allFiles
    }

    // Filter by search query
    val searchedList = if (searchQuery.isBlank()) {
        baseList
    } else {
        baseList.filter { it.fileName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // Sort list
    val sortedList = remember(searchedList, sortOrder) {
        when (sortOrder) {
            FileSortOrder.DATE_MODIFIED -> searchedList.sortedByDescending { it.modifiedAt }
            FileSortOrder.NAME -> searchedList.sortedBy { it.fileName.lowercase() }
            FileSortOrder.SIZE -> searchedList.sortedByDescending { it.fileSize }
            FileSortOrder.PAGE_COUNT -> searchedList.sortedByDescending { it.pageCount }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeFolderFilter != null) "Folder: ${activeFolderFilter?.folderName}" else "Document Manager",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    if (activeFolderFilter != null) {
                        IconButton(onClick = { activeFolderFilter = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Folders")
                        }
                    }
                },
                actions = {
                    // Sort Menu
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Date Modified") },
                                onClick = { sortOrder = FileSortOrder.DATE_MODIFIED; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("File Name") },
                                onClick = { sortOrder = FileSortOrder.NAME; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("File Size") },
                                onClick = { sortOrder = FileSortOrder.SIZE; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Page Count") },
                                onClick = { sortOrder = FileSortOrder.PAGE_COUNT; sortMenuExpanded = false }
                            )
                        }
                    }

                    // Grid / List toggle
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View"
                        )
                    }

                    if (selectedTab == 3 && activeFolderFilter == null) {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = PaperRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by document name...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PaperRed) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaperRed,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("file_search_input")
            )

            // Tabs Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = PaperRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PaperRed
                    )
                }
            ) {
                val tabTitles = listOf("All (${allFiles.size})", "Recent", "Favorites", "Folders (${folders.size})")
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            activeFolderFilter = null
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content: Folders view or Files view
            if (selectedTab == 3 && activeFolderFilter == null) {
                // Folder List
                if (folders.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No folders created yet", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showCreateFolderDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Folder")
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders) { folder ->
                            val folderFilesCount = allFiles.count { it.folderId == folder.id }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeFolderFilter = folder },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = PaperRed,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.folderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "$folderFilesCount files",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // File List / Grid
                if (sortedList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FindInPage, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No documents match '$searchQuery'" else "No documents found",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedList, key = { it.id }) { file ->
                            PdfFileGridItem(
                                file = file,
                                onClick = { viewModel.openPdf(file) },
                                onToggleFavorite = { viewModel.toggleFavorite(file) },
                                onRename = { fileToRename = file },
                                onDuplicate = { viewModel.duplicateFile(file) },
                                onDelete = { viewModel.deleteFile(file) },
                                onDetails = { fileForDetails = file }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedList, key = { it.id }) { file ->
                            PdfFileListItem(
                                file = file,
                                onClick = { viewModel.openPdf(file) },
                                onToggleFavorite = { viewModel.toggleFavorite(file) },
                                onRename = { fileToRename = file },
                                onDuplicate = { viewModel.duplicateFile(file) },
                                onDelete = { viewModel.deleteFile(file) },
                                onDetails = { fileForDetails = file }
                            )
                        }
                    }
                }
            }
        }
    }

    fileToRename?.let { file ->
        RenameFileDialog(
            initialName = file.fileName,
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                fileToRename = null
            },
            onDismiss = { fileToRename = null }
        )
    }

    fileForDetails?.let { file ->
        FileDetailsDialog(
            file = file,
            onDismiss = { fileForDetails = null }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { folderName ->
                viewModel.createFolder(folderName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}
