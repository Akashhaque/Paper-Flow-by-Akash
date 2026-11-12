package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperRed

data class ToolDefinition(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String,
    val targetScreen: AppScreen,
    val toolTag: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        // Organization
        ToolDefinition("Merge PDF", "Combine multiple PDFs into one document", Icons.Default.CallMerge, "PDF Organization", AppScreen.PDF_ORGANIZER, "MERGE"),
        ToolDefinition("Split PDF", "Extract pages or separate by page ranges", Icons.Default.CallSplit, "PDF Organization", AppScreen.PDF_ORGANIZER, "SPLIT"),
        ToolDefinition("Rotate PDF Pages", "Rotate 90°, 180°, or 270° degrees", Icons.Default.RotateRight, "PDF Organization", AppScreen.PDF_ORGANIZER, "ROTATE"),
        ToolDefinition("Delete Pages", "Remove unwanted pages from document", Icons.Default.DeleteSweep, "PDF Organization", AppScreen.PDF_ORGANIZER, "DELETE"),
        ToolDefinition("Compress PDF", "Reduce PDF file size up to 60%", Icons.Default.Compress, "PDF Organization", AppScreen.PDF_ORGANIZER, "COMPRESS"),

        // Converters
        ToolDefinition("DOC / DOCX to PDF", "Convert Word documents to PDF", Icons.Default.Description, "Document Converters", AppScreen.DOCUMENT_CONVERTER),
        ToolDefinition("Excel / CSV to PDF", "Convert spreadsheets to formatted tables", Icons.Default.TableChart, "Document Converters", AppScreen.DOCUMENT_CONVERTER),
        ToolDefinition("PowerPoint to PDF", "Convert presentation slides to PDF", Icons.Default.Slideshow, "Document Converters", AppScreen.DOCUMENT_CONVERTER),
        ToolDefinition("PDF to Word (DOCX)", "Extract text and export formatted DOCX", Icons.Default.TextFields, "Document Converters", AppScreen.DOCUMENT_CONVERTER),
        ToolDefinition("PDF to Images", "Extract all PDF pages as JPG or PNG", Icons.Default.Image, "Document Converters", AppScreen.DOCUMENT_CONVERTER),
        ToolDefinition("PDF to Excel", "Extract tabular data to CSV", Icons.Default.GridOn, "Document Converters", AppScreen.DOCUMENT_CONVERTER),

        // OCR & Text
        ToolDefinition("OCR Text Recognition", "Extract text from camera scans or PDFs", Icons.Default.DocumentScanner, "OCR & Text", AppScreen.OCR),

        // Security & Advanced
        ToolDefinition("Add Watermark", "Apply custom text or logo watermark stamps", Icons.Default.Security, "Security & Advanced", AppScreen.WATERMARK_TOOLS, "WATERMARK"),
        ToolDefinition("Add Page Numbers", "Customize header/footer page numbers", Icons.Default.FormatListNumbered, "Security & Advanced", AppScreen.WATERMARK_TOOLS, "NUMBERS"),
        ToolDefinition("PDF Annotations & Signature", "Draw, highlight, insert shapes, and sign", Icons.Default.Draw, "Security & Advanced", AppScreen.PDF_READER_EDITOR)
    )

    val groupedTools = tools.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Tools & Utilities", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            groupedTools.forEach { (category, toolList) ->
                item {
                    Text(
                        text = category,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PaperRed,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(toolList.size) { index ->
                    val tool = toolList[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (tool.toolTag.isNotBlank()) viewModel.activeOrganizerTool.value = tool.toolTag
                                viewModel.navigateTo(tool.targetScreen)
                            }
                            .testTag("tool_${tool.title.lowercase().replace(" ", "_")}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF2F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = PaperRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = tool.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
