package com.imlupp.customizeliveupdate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.imlupp.customizeliveupdate.ui.theme.CustomizeLiveUpdateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var appThemeMode by mutableStateOf(AppThemeMode.SYSTEM)

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    companion object {
        lateinit var database: AppDatabase
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pickup_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            val themeDao = database.themeDao()
            val savedTheme = themeDao.getTheme()
            val mode = when (savedTheme?.mode) {
                AppThemeMode.LIGHT.name -> AppThemeMode.LIGHT
                AppThemeMode.DARK.name -> AppThemeMode.DARK
                AppThemeMode.SYSTEM.name -> AppThemeMode.SYSTEM
                else -> AppThemeMode.SYSTEM
            }
            withContext(Dispatchers.Main) {
                appThemeMode = mode
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "通知权限被拒绝，提醒可能无法显示，请在设置中开启", Toast.LENGTH_LONG).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val pickupDao = database.pickupDao()
            val mealDao = database.mealDao()

            val pickupItems = pickupDao.getAll().first().sortedBy { it.id }
            val mealItems = mealDao.getAll().first().sortedBy { it.id }

            withContext(Dispatchers.Main) {
                pickupItems.forEachIndexed { index, item ->
                    sendPickupLiveUpdate(
                        context = this@MainActivity,
                        location = item.location,
                        code = item.code,
                        dbId = item.id,
                        displayNumber = index + 1
                    )
                }
                mealItems.forEachIndexed { index, item ->
                    sendMealLiveUpdate(
                        context = this@MainActivity,
                        type = item.type,
                        location = item.location,
                        code = item.code,
                        dbId = item.id,
                        displayNumber = index + 1
                    )
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            CustomizeLiveUpdateTheme {
                MainApp(permissionLauncher = permissionLauncher)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pickup_code_channel",
                "快递取件提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "显示快递取件码的持续通知" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}

// 空间互动引擎
fun Modifier.liquidTilt(
    maxTilt: Float = 45f,
    scaleDown: Float = 0.95f
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val targetRotationX = if (isPressed && cardSize.height > 0) {
        val normalizedY = (pressPosition.y - cardSize.height / 2f) / (cardSize.height / 2f)
        -normalizedY.coerceIn(-1f, 1f) * maxTilt
    } else 0f

    val targetRotationY = if (isPressed && cardSize.width > 0) {
        val normalizedX = (pressPosition.x - cardSize.width / 2f) / (cardSize.width / 2f)
        normalizedX.coerceIn(-1f, 1f) * maxTilt
    } else 0f

    val animatedRotationX by animateFloatAsState(
        targetValue = targetRotationX,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "rotationX"
    )

    val animatedRotationY by animateFloatAsState(
        targetValue = targetRotationY,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "rotationY"
    )

    this
        .onSizeChanged { cardSize = it }
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            rotationX = animatedRotationX
            rotationY = animatedRotationY
            cameraDistance = 16f * density
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                pressPosition = down.position

                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed || change.isConsumed) break
                    pressPosition = change.position
                }
                isPressed = false
            }
        }
}

// 液态玻璃材质
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 0.2f,
    bgAlpha: Float = 0.35f,
    borderWidth: Dp = 1.dp
): Modifier {
    val isDark = isSystemInDarkThemeCustom()

    val glassColor = if (isDark) {
        if (bgAlpha > 0.8f) Color(0xFF1C1C1E).copy(alpha = bgAlpha)
        else Color(0xFF2C2C30).copy(alpha = bgAlpha)
    } else {
        Color.White.copy(alpha = bgAlpha)
    }

    val borderColor = if (isDark) {
        Color.White.copy(alpha = borderAlpha.coerceAtLeast(0.15f))
    } else {
        Color.White.copy(alpha = borderAlpha + 0.3f)
    }

    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(glassColor)
        .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
}

@Composable
fun isSystemInDarkThemeCustom(): Boolean {
    return when (appThemeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

enum class BottomTab(val label: String) {
    Pickup("取件码"),
    Meal("取餐码"),
    Settings("设置")
}

@Composable
fun MainApp(permissionLauncher: ActivityResultLauncher<String>) {
    val tabs = BottomTab.values().toList()
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Pickup) }
    val isDark = isSystemInDarkThemeCustom()

    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF3A1F44), Color(0xFF131A38), Color(0xFF182A38)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC), Color(0xFFF1E1FF)))
    }

    val contentColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { currentTab ->
                when (currentTab) {
                    BottomTab.Pickup -> MyScreen(permissionLauncher)
                    BottomTab.Meal -> MealScreen(permissionLauncher)
                    BottomTab.Settings -> SettingsScreenRoot(
                        currentTheme = appThemeMode,
                        onThemeChange = { applyAppTheme(it) }
                    )
                }
            }
        }

        // 悬浮式液态玻璃底部导航栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
                .height(72.dp)
                .liquidGlass(cornerRadius = 36.dp, bgAlpha = if (isDark) 0.5f else 0.7f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val icon = when (tab) {
                        BottomTab.Pickup -> Icons.Filled.LocalShipping
                        BottomTab.Meal -> Icons.Filled.Fastfood
                        BottomTab.Settings -> Icons.Filled.Settings
                    }
                    val iconTint = if (isSelected) {
                        if (isDark) Color.White else Color.Black
                    } else {
                        if (isDark) Color.White.copy(0.4f) else Color.Black.copy(0.4f)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedTab = tab },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) iconTint else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

// 公用液态大标题
@Composable
fun GlassHeader(title: String, onAddClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LocalContentColor.current
            )
        )
        if (onAddClick != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .liquidTilt(maxTilt = 20f, scaleDown = 0.85f)
                    .liquidGlass(cornerRadius = 22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onAddClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加",
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MyScreen(permissionLauncher: ActivityResultLauncher<String>) {
    val context = LocalContext.current
    var showAddPickupDialog by remember { mutableStateOf(false) }
    var pickupLocation by remember { mutableStateOf("") }
    var pickupCode by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val pickupItems by MainActivity.database.pickupDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(title = "快递", onAddClick = { showAddPickupDialog = true })

        if (pickupItems.isEmpty()) {
            EmptyStateGlass("📦", "还没有添加任何取件码", "点击右上角“+”添加吧～")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pickupItems) { item ->
                    val displayNumber = pickupItems.sortedBy { it.id }.indexOfFirst { it.id == item.id } + 1
                    GlassCard(
                        subtitle = item.location,
                        code = item.code,
                        displayNumber = displayNumber,
                        onDone = {
                            coroutineScope.launch(Dispatchers.IO) {
                                MainActivity.database.pickupDao().delete(item)
                                NotificationManagerCompat.from(context).cancel(item.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddPickupDialog) {
        GlassAddDialog(
            title = "新取件码",
            field1Label = "取件点 (如: 丰巢A区)",
            field1Value = pickupLocation,
            onField1Change = { pickupLocation = it },
            field2Label = "取件码 (如: 874920)",
            field2Value = pickupCode,
            onField2Change = { pickupCode = it },
            onDismiss = { showAddPickupDialog = false },
            onConfirm = {
                if (pickupLocation.isBlank() || pickupCode.isBlank()) {
                    Toast.makeText(context, "请填写取件点和取件码", Toast.LENGTH_SHORT).show()
                    return@GlassAddDialog
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@GlassAddDialog
                }
                coroutineScope.launch {
                    addPickupItemAndNotify(pickupLocation, pickupCode, context)
                    pickupLocation = ""; pickupCode = ""
                }
                showAddPickupDialog = false
            }
        )
    }
}

@Composable
fun MealScreen(permissionLauncher: ActivityResultLauncher<String>) {
    val context = LocalContext.current
    var showAddMealDialog by remember { mutableStateOf(false) }
    var mealLocation by remember { mutableStateOf("") }
    var mealCode by remember { mutableStateOf("") }
    val mealTypes = listOf("咖啡", "奶茶", "西餐", "中餐")
    var selectedMealType by remember { mutableStateOf(mealTypes.first()) }
    val coroutineScope = rememberCoroutineScope()
    val mealItems by MainActivity.database.mealDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(title = "取餐", onAddClick = { showAddMealDialog = true })

        if (mealItems.isEmpty()) {
            EmptyStateGlass("🍱", "还没有添加任何取餐码", "点击右上角“+”添加吧～")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mealItems) { item ->
                    val iconRes = when (item.type) {
                        "咖啡" -> R.drawable.coffee_cup
                        "奶茶" -> R.drawable.orange_juice
                        "西餐" -> R.drawable.burger
                        "中餐" -> R.drawable.orange_chicken
                        else -> R.drawable.ic_delivery
                    }
                    GlassCard(
                        subtitle = item.location,
                        code = item.code,
                        displayNumber = null,
                        imageRes = iconRes,
                        onDone = {
                            coroutineScope.launch(Dispatchers.IO) {
                                MainActivity.database.mealDao().delete(item)
                                NotificationManagerCompat.from(context).cancel(item.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddMealDialog) {
        GlassAddDialog(
            title = "新取餐码",
            field1Label = "取餐点 (如: 龙信蜜雪)",
            field1Value = mealLocation,
            onField1Change = { mealLocation = it },
            field2Label = "取餐码 (如: C471)",
            field2Value = mealCode,
            onField2Change = { mealCode = it },
            extraContent = {
                LiquidSegmentedControl(
                    items = mealTypes,
                    selectedItem = selectedMealType,
                    onItemSelected = { selectedMealType = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            },
            onDismiss = { showAddMealDialog = false },
            onConfirm = {
                if (mealLocation.isBlank() || mealCode.isBlank()) {
                    Toast.makeText(context, "请填写完整", Toast.LENGTH_SHORT).show()
                    return@GlassAddDialog
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@GlassAddDialog
                }
                coroutineScope.launch {
                    addMealItemAndNotify(selectedMealType, mealLocation, mealCode, context)
                    mealLocation = ""; mealCode = ""
                }
                showAddMealDialog = false
            }
        )
    }
}

@Composable
fun LiquidSegmentedControl(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    val selectedIndex = items.indexOf(selectedItem).takeIf { it >= 0 } ?: 0
    val isDark = isSystemInDarkThemeCustom()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFF767680).copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val segmentWidth = maxWidth / items.size

        // 1. 底层静止层
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { type ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        val animatedOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            label = "lensOffset"
        )

        // 2. 液态镜片外观层
        val lensBorderBrush = Brush.linearGradient(
            colors = listOf(
                if (isDark) Color(0xFF00FFC2) else Color(0xFF00FFC2).copy(alpha = 0.8f),
                if (isDark) Color(0xFF0066FF) else Color(0xFF0066FF).copy(alpha = 0.6f),
                if (isDark) Color(0xFFA200FF) else Color(0xFFA200FF).copy(alpha = 0.6f),
                if (isDark) Color(0xFF0066FF) else Color(0xFF0066FF).copy(alpha = 0.8f)
            )
        )

        val lensBackgroundBrush = Brush.verticalGradient(
            colors = listOf(
                if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 1.0f),
                if (isDark) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.6f)
            )
        )

        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(brush = lensBackgroundBrush, shape = RoundedCornerShape(50))
                .border(width = 1.5.dp, brush = lensBorderBrush, shape = RoundedCornerShape(50))
        )

        // 3. 核心魔术层：动态裁切高亮文字
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = object : androidx.compose.ui.graphics.Shape {
                        override fun createOutline(
                            size: androidx.compose.ui.geometry.Size,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            density: androidx.compose.ui.unit.Density
                        ): androidx.compose.ui.graphics.Outline {
                            val offsetPx = animatedOffset.toPx()
                            val widthPx = segmentWidth.toPx()
                            return androidx.compose.ui.graphics.Outline.Rounded(
                                androidx.compose.ui.geometry.RoundRect(
                                    left = offsetPx,
                                    top = 0f,
                                    right = offsetPx + widthPx,
                                    bottom = size.height,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                                )
                            )
                        }
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { type ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF32ADE6) else Color(0xFF007AFF)
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 4. 顶层隐形点击层
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { type ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(type) }
                )
            }
        }
    }
}

// ========== 复用的 UI 组件 ==========

@Composable
fun EmptyStateGlass(icon: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .liquidGlass(cornerRadius = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 64.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current))
        Spacer(modifier = Modifier.height(8.dp))
        Text(subtitle, style = TextStyle(fontSize = 16.sp, color = LocalContentColor.current.copy(alpha = 0.6f)))
    }
}

@Composable
fun GlassCard(
    subtitle: String,
    code: String,
    displayNumber: Int? = null,
    imageRes: Int? = null,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidTilt(maxTilt = 6f)
            .liquidGlass(cornerRadius = 24.dp)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayNumber != null) {
                Text(
                    text = "#$displayNumber",
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LocalContentColor.current.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).padding(end = 16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = code,
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = LocalContentColor.current)
                )
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current.copy(alpha = 0.8f))
                )
            }

            Box(
                modifier = Modifier
                    .liquidTilt(maxTilt = 15f, scaleDown = 0.9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalContentColor.current.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDone() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "已取",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalContentColor.current)
                )
            }
        }
    }
}

@Composable
fun GlassAddDialog(
    title: String,
    field1Label: String, field1Value: String, onField1Change: (String) -> Unit,
    field2Label: String, field2Value: String, onField2Change: (String) -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 32.dp, bgAlpha = 0.98f)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LocalContentColor.current),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                extraContent?.invoke()

                GlassTextField(value = field1Value, onValueChange = onField1Change, hint = field1Label)
                Spacer(modifier = Modifier.height(16.dp))
                GlassTextField(value = field2Value, onValueChange = onField2Change, hint = field2Label)

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidTilt(maxTilt = 12f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(LocalContentColor.current.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDismiss() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontWeight = FontWeight.SemiBold, color = LocalContentColor.current)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .liquidTilt(maxTilt = 12f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(LocalContentColor.current.copy(alpha = 0.9f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onConfirm() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val inverseColor = if (isSystemInDarkThemeCustom()) Color.Black else Color.White
                        Text("添加", fontWeight = FontWeight.Bold, color = inverseColor)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    val isDark = isSystemInDarkThemeCustom()

    val bgColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF767680).copy(alpha = 0.12f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontSize = 18.sp, color = LocalContentColor.current),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        style = TextStyle(
                            fontSize = 18.sp,
                            color = LocalContentColor.current.copy(alpha = 0.4f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

// ========== 设置页面重构 ==========
enum class SettingsSubPage { MAIN, CHANGELOG }

@Composable
fun SettingsScreenRoot(currentTheme: AppThemeMode, onThemeChange: (AppThemeMode) -> Unit) {
    var subPage by rememberSaveable { mutableStateOf(SettingsSubPage.MAIN) }
    BackHandler(enabled = subPage != SettingsSubPage.MAIN) { subPage = SettingsSubPage.MAIN }

    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        }
    ) { page ->
        when (page) {
            SettingsSubPage.MAIN -> SettingsScreen(currentTheme, onThemeChange) { subPage = SettingsSubPage.CHANGELOG }
            SettingsSubPage.CHANGELOG -> ChangeLogScreen { subPage = SettingsSubPage.MAIN }
        }
    }
}

@Composable
fun SettingsScreen(currentTheme: AppThemeMode, onThemeChange: (AppThemeMode) -> Unit, onOpenChangeLog: () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkThemeCustom()

    Column(modifier = Modifier.fillMaxSize()) {
        GlassHeader(title = "设置")

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 关于卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(cornerRadius = 32.dp, bgAlpha = if (isDark) 0.15f else 0.4f)
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 应用图标占位
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .liquidGlass(cornerRadius = 20.dp, bgAlpha = 0.8f, borderWidth = 2.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Logo",
                                tint = if (isDark) Color(0xFF00FFC2) else Color(0xFF007AFF),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text = "CustomizeLiveUpdate",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LocalContentColor.current
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Version 1.1.3",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalContentColor.current.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }

            // 主题模式视觉画廊
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "外观偏好",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalContentColor.current.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "浅色",
                            icon = Icons.Filled.WbSunny,
                            isSelected = currentTheme == AppThemeMode.LIGHT,
                            onClick = { onThemeChange(AppThemeMode.LIGHT) }
                        )
                        ThemeSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "深色",
                            icon = Icons.Filled.DarkMode,
                            isSelected = currentTheme == AppThemeMode.DARK,
                            onClick = { onThemeChange(AppThemeMode.DARK) }
                        )
                        ThemeSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "系统",
                            icon = Icons.Filled.BrightnessAuto,
                            isSelected = currentTheme == AppThemeMode.SYSTEM,
                            onClick = { onThemeChange(AppThemeMode.SYSTEM) }
                        )
                    }
                }
            }

            // 玻璃悬浮交互列表组
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "更多信息",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalContentColor.current.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(cornerRadius = 24.dp)
                    ) {
                        Column {
                            SettingsListRow(
                                title = "更新日志",
                                onClick = onOpenChangeLog
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(horizontal = 20.dp)
                                    .background(LocalContentColor.current.copy(alpha = 0.08f))
                            )
                            SettingsListRow(
                                title = "GitHub 开源地址",
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/imlupp/CustomizeLiveUpdate")))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelectionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkThemeCustom()

    val activeBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00FFC2).copy(alpha = 0.8f),
            Color(0xFF0066FF).copy(alpha = 0.8f)
        )
    )
    val inactiveBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.1f else 0.3f),
            Color.White.copy(alpha = if (isDark) 0.05f else 0.1f)
        )
    )

    Box(
        modifier = modifier
            .liquidTilt(maxTilt = 15f)
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) {
                    if (isDark) Color.White.copy(0.15f) else LocalContentColor.current.copy(0.08f)
                } else {
                    if (isDark) Color.Black.copy(0.2f) else Color.White.copy(0.3f)
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = if (isSelected) activeBorderBrush else inactiveBorderBrush,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) {
                    if (isDark) Color(0xFF00FFC2) else Color(0xFF007AFF)
                } else {
                    LocalContentColor.current.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.5f)
                )
            )
        }
    }
}


@Composable
fun SettingsListRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = "进入",
            tint = LocalContentColor.current.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
fun ChangeLogScreen(onBack: () -> Unit) {
    val changeLogData = listOf(
        "1.1.3" to listOf("\uD83C\uDFAD 新增 全新美学设计，视觉焕新", "\uD83C\uDF08 新增 拟物按压动画", "⚡ 优化 整体性能表现"),
        "1.1.2" to listOf("🌕 新增 主题模式设置", "📋 新增 应用内更新日志，新功能一目了然", "🔧 修复 主题模式重启应用后不生效的问题", "✂️ 优化 精简应用组件，降低空间占用"),
        "1.1.1" to listOf("🧭 新增 无取件码/取餐码时的添加引导", "🐛 修复 页面切换时异常显示的问题"),
        "1.1.0-beta" to listOf("🎨 优化 全新UI设计，界面更简洁易操作", "ℹ️ 新增 设置页面「关于」板块", "✅ 优化 「已取」按钮样式，交互更清晰", "📄 优化 实时通知样式，去除冗余信息，展示更直观"),
        "1.0.2-beta" to listOf("🍱 新增 取餐码功能模块，一App两用", "⚙️ 新增 设置选项（功能持续完善中）", "🔄 优化 餐品类型选择逻辑，操作更顺滑", "🎨 优化 为不同餐品匹配图标，辨识度提升", "🐛 修复 偶现覆盖安装后闪退的问题"),
        "1.0.1" to listOf("🛠 优化 首次启动时主动请求通知权限", "🐛 修复 偶现通知无法显示为实时活动样式", "🎨 优化 通知中心快递图标颜色显示"),
        "1.0.0" to listOf("✨ 新增 手动添加取件码至 Live Update 通知", "📦 新增 取件码列表管理功能", "🔧 修复 重新打开 App 时通知未恢复显示的问题")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 16.dp, start = 16.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .liquidTilt(maxTilt = 25f, scaleDown = 0.8f)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "返回", tint = LocalContentColor.current, modifier = Modifier.graphicsLayer(rotationZ = 180f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("What's New", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = LocalContentColor.current))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().liquidGlass(cornerRadius = 16.dp, bgAlpha = 0.15f).padding(16.dp)) {
                    Text(
                        text = "💡 注意：Beta版本为开发后面向用户的测试版本，不提供下载，如需使用请前往Github项目Release下载。",
                        style = TextStyle(fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = 0.7f), lineHeight = 20.sp)
                    )
                }
            }
            items(changeLogData) { (version, items) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidTilt(maxTilt = 8f)
                        .liquidGlass(cornerRadius = 24.dp)
                        .padding(20.dp)
                ) {
                    Column {

                        Text(
                            text = "v $version",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSystemInDarkThemeCustom()) Color(0xFF00FFC2) else Color(0xFF007AFF)
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        items.forEach { itemText ->
                            Row(modifier = Modifier.padding(bottom = 10.dp)) {

                                val emoji = itemText.substring(0, 2)
                                val text = itemText.substring(2).trim()
                                Text(text = emoji, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = text,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = LocalContentColor.current.copy(alpha = 0.85f),
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun applyAppTheme(mode: AppThemeMode) {
    appThemeMode = mode
    CoroutineScope(Dispatchers.IO).launch {
        MainActivity.database.themeDao().saveTheme(ThemeEntity(id = 0, mode = mode.name))
    }
}

// ========== 后台逻辑==========

private suspend fun addPickupItemAndNotify(location: String, code: String, context: Context) {
    withContext(Dispatchers.IO) {
        val dao = MainActivity.database.pickupDao()
        dao.insert(PickupItem(location = location, code = code))
        val allItems = dao.getAll().first().sortedBy { it.id }
        val insertedItem = allItems.last()
        withContext(Dispatchers.Main) {
            sendPickupLiveUpdate(context, insertedItem.location, insertedItem.code, insertedItem.id, allItems.size)
        }
    }
}

private suspend fun addMealItemAndNotify(type: String, location: String, code: String, context: Context) {
    withContext(Dispatchers.IO) {
        val dao = MainActivity.database.mealDao()
        dao.insert(MealItem(type = type, location = location, code = code))
        val allItems = dao.getAll().first().sortedBy { it.id }
        val insertedItem = allItems.last()
        withContext(Dispatchers.Main) {
            sendMealLiveUpdate(context, insertedItem.type, insertedItem.location, insertedItem.code, insertedItem.id, allItems.size)
        }
    }
}

private fun sendPickupLiveUpdate(context: Context, location: String, code: String, dbId: Int, displayNumber: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

    val builder = NotificationCompat.Builder(context, "pickup_code_channel")
        .setSmallIcon(R.drawable.ic_delivery)
        .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.delivery_man))
        .setContentTitle(code)
        .setContentText(location)
        .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(code).bigText(location))
        .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false).setRequestPromotedOngoing(true)

    val cancelIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = "ACTION_MARK_AS_PICKED_UP"
        putExtra("notification_id", dbId)
    }
    val cancelPendingIntent = PendingIntent.getBroadcast(context, dbId, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "已取件", cancelPendingIntent)

    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(dbId, builder.build())
}

private fun sendMealLiveUpdate(context: Context, type: String, location: String, code: String, dbId: Int, displayNumber: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

    val smallIconRes = when (type) {
        "咖啡" -> R.drawable.coffee
        "奶茶" -> R.drawable.milkshake
        "西餐" -> R.drawable.mcdonalds
        "中餐" -> R.drawable.rice
        else -> R.drawable.ic_delivery
    }
    val largeIconRes = when (type) {
        "咖啡" -> R.drawable.coffee_cup
        "奶茶" -> R.drawable.orange_juice
        "西餐" -> R.drawable.burger
        "中餐" -> R.drawable.orange_chicken
        else -> R.drawable.delivery_man
    }

    val builder = NotificationCompat.Builder(context, "pickup_code_channel")
        .setSmallIcon(smallIconRes)
        .setLargeIcon(BitmapFactory.decodeResource(context.resources, largeIconRes))
        .setContentTitle(code)
        .setContentText(location)
        .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(code).bigText(location))
        .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false).setRequestPromotedOngoing(true)

    val cancelIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = "ACTION_MARK_AS_PICKED_UP"
        putExtra("notification_id", dbId)
    }
    val cancelPendingIntent = PendingIntent.getBroadcast(context, dbId, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "已取餐", cancelPendingIntent)

    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(dbId, builder.build())
}

enum class AppThemeMode(val label: String) {
    LIGHT("浅色模式"),
    DARK("深色模式"),
    SYSTEM("跟随系统")
}