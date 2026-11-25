package com.akib.ipadoslauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen() }
    }
}

data class LaunchApp(
    val title: String,
    val pkg: String,
    val icon: Drawable,
    val launchIntent: Intent
)

@Suppress("DEPRECATION")
private fun loadLaunchableApps(pm: PackageManager): List<LaunchApp> {
    val byPackage = linkedMapOf<String, LaunchApp>()

    // First use Android's normal launcher activity query.
    val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcherQuery, PackageManager.MATCH_ALL).forEach { result ->
        val activityInfo = result.activityInfo ?: return@forEach
        val packageName = activityInfo.packageName
        if (!byPackage.containsKey(packageName)) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).setClassName(packageName, activityInfo.name)
            runCatching {
                byPackage[packageName] = LaunchApp(
                    result.loadLabel(pm).toString(),
                    packageName,
                    result.loadIcon(pm),
                    launchIntent
                )
            }
        }
    }

    // Fallback for vendor ROMs that return an incomplete launcher query.
    // QUERY_ALL_PACKAGES in the manifest gives the launcher visibility here.
    runCatching {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
    }.getOrDefault(emptyList()).forEach { appInfo ->
        val packageName = appInfo.packageName
        if (appInfo.enabled && !byPackage.containsKey(packageName)) {
            val launchIntent = runCatching {
                pm.getLaunchIntentForPackage(packageName)
            }.getOrNull() ?: return@forEach

            runCatching {
                byPackage[packageName] = LaunchApp(
                    pm.getApplicationLabel(appInfo).toString(),
                    packageName,
                    pm.getApplicationIcon(appInfo),
                    launchIntent
                )
            }
        }
    }

    return byPackage.values.sortedBy { it.title.lowercase() }
}

@Composable
fun LauncherScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pm = ctx.packageManager
    val preferences = remember(ctx) {
        ctx.getSharedPreferences("launcher_setup", Context.MODE_PRIVATE)
    }
    val roleManager = remember(ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.getSystemService(RoleManager::class.java)
        } else null
    }

    var apps by remember { mutableStateOf(emptyList<LaunchApp>()) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var iconSize by remember { mutableFloatStateOf(58f) }
    var labelSize by remember { mutableFloatStateOf(12f) }
    var columns by remember { mutableIntStateOf(6) }
    var labels by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<LaunchApp?>(null) }
    var isDefaultLauncher by remember {
        mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true)
    }
    var showSetupDialog by remember {
        mutableStateOf(
            !isDefaultLauncher &&
                !preferences.getBoolean("setup_prompt_dismissed", false)
        )
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultLauncher = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        if (isDefaultLauncher) {
            preferences.edit().putBoolean("setup_prompt_dismissed", true).apply()
            showSetupDialog = false
        }
    }

    val requestDefaultLauncher = {
        val manager = roleManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            manager != null &&
            manager.isRoleAvailable(RoleManager.ROLE_HOME)
        ) {
            roleRequestLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        }
        Unit
    }

    LaunchedEffect(refreshCounter) {
        apps = loadLaunchableApps(pm)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF101522)).padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { refreshCounter++ }) { Text("Refresh apps") }
                if (!isDefaultLauncher) {
                    TextButton(onClick = requestDefaultLauncher) { Text("Set default") }
                }
                TextButton(onClick = { settings = true }) { Text("Settings") }
                TextButton(onClick = { edit = !edit }) {
                    Text(if (edit) "Done" else "Edit")
                }
            }

            if (apps.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No launchable apps found", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Refresh apps to scan installed applications again.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { refreshCounter++ }) { Text("Refresh apps") }
                }
            } else {
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
                                    runCatching { ctx.startActivity(app.launchIntent) }
                                }
                            }
                        ) {
                            Box(
                                Modifier.size(iconSize.dp)
                                    .background(Color(0xFF29364D), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(app.icon, app.title, Modifier.fillMaxSize().padding(4.dp))
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
                            runCatching { ctx.startActivity(app.launchIntent) }
                        }
                    ) {
                        AppIcon(app.icon, app.title, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetupDialog = false
                preferences.edit().putBoolean("setup_prompt_dismissed", true).apply()
            },
            title = { Text("Set up iPadOS Launcher") },
            text = {
                Text(
                    "Choose iPadOS 26 Launcher as your default Home app to use it whenever you press the Home button."
                )
            },
            confirmButton = {
                TextButton(onClick = requestDefaultLauncher) { Text("Set as default launcher") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetupDialog = false
                    preferences.edit().putBoolean("setup_prompt_dismissed", true).apply()
                }) { Text("Not now") }
            }
        )
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Home Screen & Appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isDefaultLauncher) "Default launcher: iPadOS 26" else "Default launcher: Not set")
                    if (!isDefaultLauncher) {
                        OutlinedButton(
                            onClick = requestDefaultLauncher,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set as default launcher") }
                    }
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
                    ) { Text("Set up swipe gesture") }
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

@Composable
private fun AppIcon(
    drawable: Drawable,
    title: String,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(drawable) {
        val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 96).coerceAtMost(512)
        val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 96).coerceAtMost(512)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = imageBitmap,
        contentDescription = title,
        modifier = modifier
    )
}
