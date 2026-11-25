package com.akib.ipadoslauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen() }
    }
}

data class LaunchApp(
    val title: String,
    val pkg: String,
    val icon: android.graphics.drawable.Drawable
)

@Composable
fun LauncherScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pm = ctx.packageManager
    var apps by remember { mutableStateOf(emptyList<LaunchApp>()) }
    var edit by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var iconSize by remember { mutableFloatStateOf(58f) }
    var labelSize by remember { mutableFloatStateOf(12f) }
    var columns by remember { mutableIntStateOf(6) }
    var labels by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<LaunchApp?>(null) }

    LaunchedEffect(Unit) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        apps = pm.queryIntentActivities(query, PackageManager.MATCH_ALL).mapNotNull { result ->
            val activityInfo = result.activityInfo ?: return@mapNotNull null
            LaunchApp(
                result.loadLabel(pm).toString(),
                activityInfo.packageName,
                result.loadIcon(pm)
            )
        }.distinctBy { it.pkg }.sortedBy { it.title.lowercase() }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF101522)).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { settings = true }) { Text("Settings") }
                TextButton(onClick = { edit = !edit }) {
                    Text(if (edit) "Done" else "Edit")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (edit) {
                                chosen = app
                            } else {
                                runCatching {
                                    pm.getLaunchIntentForPackage(app.pkg)?.let(ctx::startActivity)
                                }
                            }
                        }
                    ) {
                        Box(
                            Modifier.size(iconSize.dp)
                                .background(Color(0xFF29364D), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.graphics.painter.DrawablePainter(app.icon),
                                app.title,
                                Modifier.fillMaxSize().padding(4.dp)
                            )
                            if (edit) {
                                Text(
                                    "−",
                                    Modifier.align(Alignment.TopStart)
                                        .background(Color(0xFFCE4854), RoundedCornerShape(50))
                                        .padding(horizontal = 5.dp),
                                    color = Color.White
                                )
                            }
                        }
                        if (labels) {
                            Text(
                                app.title,
                                color = Color.White,
                                fontSize = labelSize.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xAA29364D), RoundedCornerShape(26.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                apps.take(5).forEach { app ->
                    Box(
                        Modifier.padding(horizontal = 8.dp).size(44.dp).clickable {
                            runCatching {
                                pm.getLaunchIntentForPackage(app.pkg)?.let(ctx::startActivity)
                            }
                        }
                    ) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.graphics.painter.DrawablePainter(app.icon),
                            app.title,
                            Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Home Screen & Appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Icon size: ${iconSize.toInt()} dp")
                    Slider(iconSize, { iconSize = it }, valueRange = 40f..90f)
                    Text("Label size: ${labelSize.toInt()} sp")
                    Slider(labelSize, { labelSize = it }, valueRange = 9f..18f)
                    Text("Grid columns: $columns")
                    Slider(
                        columns.toFloat(),
                        { columns = it.toInt().coerceIn(3, 9) },
                        valueRange = 3f..9f,
                        steps = 5
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show labels")
                        Spacer(Modifier.weight(1f))
                        Switch(labels, { labels = it })
                    }
                    HorizontalDivider()
                    Text("Swipe to close apps", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Swipe up from the bottom indicator to open Recent Apps, then swipe an app card upward to dismiss it."
                    )
                    OutlinedButton(
                        onClick = {
                            settings = false
                            runCatching {
                                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set up swipe gesture")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settings = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
                    Text("Android Settings")
                }
            }
        )
    }

    chosen?.let { app ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(app.title) },
            text = { Text("Open Android app info to remove or uninstall this app.") },
            confirmButton = {
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + app.pkg)
                        )
                    )
                    chosen = null
                }) { Text("App info") }
            },
            dismissButton = {
                TextButton(onClick = { chosen = null }) { Text("Cancel") }
            }
        )
    }
}
