package com.imlupp.customizeliveupdate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.imlupp.customizeliveupdate.ui.theme.CustomizeLiveUpdateTheme
import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.core.app.NotificationCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.text.style.TextDecoration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize




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

        // 应用启动时自动请求通知权限（Android 13+）
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
                    val displayNumber = index + 1
                    sendPickupLiveUpdate(
                        context = this@MainActivity,
                        location = item.location,
                        code = item.code,
                        dbId = item.id,
                        displayNumber = displayNumber
                    )
                }

                mealItems.forEachIndexed { index, item ->
                    val displayNumber = index + 1
                    sendMealLiveUpdate(
                        context = this@MainActivity,
                        type = item.type,
                        location = item.location,
                        code = item.code,
                        dbId = item.id,
                        displayNumber = displayNumber
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
            val channelId = "pickup_code_channel"
            val name = "快递取件提醒"
            val descriptionText = "显示快递取件码的持续通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

enum class BottomTab(val label: String) {
    Pickup("取件码"),
    Meal("取餐码"),
    Settings("设置")
}

@Composable
fun MainApp(
    permissionLauncher: ActivityResultLauncher<String>
) {
    val tabs = BottomTab.values().toList()
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Pickup) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp  // 加一点浮起感
            ){
                tabs.forEach { tab ->
                    val icon = when (tab) {
                        BottomTab.Pickup -> Icons.Filled.Home
                        BottomTab.Meal -> Icons.Filled.Search
                        BottomTab.Settings -> Icons.Filled.Settings
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // 从左到右滑动（默认方向）
                if (targetState.ordinal > initialState.ordinal) {
                    // 向右切换（下一个页面从右边滑入）
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    // 向左切换（上一个页面从左边滑入）
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
}

@Composable
fun MyScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pickupLocation by remember { mutableStateOf("") }
    var pickupCode by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()  // 关键：在这里获取协程作用域
    var showAddPickupDialog by remember { mutableStateOf(false) }

    val pickupItems by MainActivity.database.pickupDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        // horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween  // 左右撑开
        ) {
            Text(
                text = "快递取件码",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Start
            )

            // 右侧添加按钮（圆形 + 图标）
            IconButton(
                onClick = { showAddPickupDialog = true },  // 点击弹出对话框
                modifier = Modifier
                    .size(48.dp)


            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加取件码",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Box(modifier = modifier.fillMaxSize()) {
            // 主内容
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题 + 添加按钮 Row（保持不变）

                if (pickupItems.isEmpty()) {
                    // 正常空状态（如果你有的话，可以保留或删除）
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📦", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("还没有添加任何取件码", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击右上角“+”添加吧～", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(pickupItems) { item ->
                            // ★ 这里加上 displayNumber 的计算（和取餐码页面一模一样）
                            val allItems = pickupItems.sortedBy { it.id }
                            val displayNumber = allItems.indexOfFirst { it.id == item.id } + 1


                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 0.dp
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)  // 很淡的灰色
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "#$displayNumber",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        // color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        // 第一行：取件码（细体 + 灰色）
                                        Text(
                                            text = "取件码",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Normal,          // 细体（Normal 就是细体）
                                                color = MaterialTheme.colorScheme.onSurfaceVariant  // 灰色（通常是浅灰）
                                            ),
                                            modifier = Modifier.padding(bottom = 2.dp)   // 和下面一行拉开一点间距
                                        )

                                        // 第二行：取件码数字（更大、更醒目）
                                        Text(
                                            text = item.code,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 32.sp,                        // 比原来的 30.sp 稍小一点，避免挤，但已经很大了
                                                fontWeight = FontWeight.Bold,
                                                //letterSpacing = 1.sp                     // 字母/数字间距再拉大一点，更像验证码
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 4.dp)   // 和下面一行间距更大
                                        )
                                        Text(
                                            text = "取件点",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Normal,          // 细体（Normal 就是细体）
                                                color = MaterialTheme.colorScheme.onSurfaceVariant  // 灰色（通常是浅灰）
                                            ),
                                            modifier = Modifier.padding(bottom = 2.dp)   // 和下面一行拉开一点间距
                                        )
                                        // 第三行：取件点（保持原样，但整体间距已通过上面 padding 拉开）
                                        Text(
                                            text = item.location,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                MainActivity.database.pickupDao().delete(item)
                                                NotificationManagerCompat.from(context).cancel(item.id)
                                            }
                                        },
                                        modifier = Modifier
                                            .height(36.dp)                    // 按钮高度小一点，更精致
                                            .padding(start = 12.dp),          // 和左边文字留点间距
                                        shape = RoundedCornerShape(16.dp),   // 圆角
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),  // 浅红色背景
                                            contentColor = MaterialTheme.colorScheme.primary,                       // 红色文字
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(
                                            defaultElevation = 0.dp,          // 无阴影，更扁平
                                            pressedElevation = 2.dp
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "已取",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


        }

        Spacer(modifier = Modifier.height(10.dp))
        if (showAddPickupDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddPickupDialog = false
                    // 可选：清空输入框，避免下次打开残留旧数据
                    pickupLocation = ""
                    pickupCode = ""
                },
                title = {
                    Text(
                        text = "添加新取件码",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 原来的第一个 TextField
                        TextField(
                            value = pickupLocation,
                            onValueChange = { pickupLocation = it },
                            label = { Text("取件点（如：丰巢A区）") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 原来的第二个 TextField
                        TextField(
                            value = pickupCode,
                            onValueChange = { pickupCode = it },
                            label = { Text("取件码（如：874920）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (pickupLocation.isBlank() || pickupCode.isBlank()) {
                                Toast.makeText(context, "请填写取件点和取件码", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            // 检查权限
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            // 添加逻辑（和原来完全一样）
                            coroutineScope.launch {
                                addPickupItemAndNotify(
                                    location = pickupLocation,
                                    code = pickupCode,
                                    context = context
                                )
                                pickupLocation = ""
                                pickupCode = ""
                            }

                            showAddPickupDialog = false  // 关闭弹窗
                        }
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPickupDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }


    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mealTypes = listOf("咖啡", "奶茶", "西餐", "中餐")
    var selectedMealType by remember { mutableStateOf(mealTypes.first()) }
    var mealLocation by remember { mutableStateOf("") }
    var mealCode by remember { mutableStateOf("") }
    var showAddMealDialog by remember { mutableStateOf(false) }

    val mealItems by MainActivity.database.mealDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        // horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween  // 左右撑开
        ) {
            Text(
                text = "取餐码",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Start
            )

            // 右侧添加按钮（圆形 + 图标）
            IconButton(
                onClick = { showAddMealDialog = true },  // 点击弹出对话框
                modifier = Modifier
                    .size(48.dp)


            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加取餐码",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
            }
        }


            Box(modifier = modifier.fillMaxSize()) {
                // 主内容
                Column(modifier = Modifier.fillMaxSize()) {
                    // 标题 + 添加按钮 Row（保持不变）

                    if (mealItems.isEmpty()) {
                        // 正常空状态（如果你有的话，可以保留或删除）
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🍱", style = MaterialTheme.typography.displayLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "还没有添加任何取餐码",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击右上角“+”添加吧～", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(mealItems) { item ->
                                val allItems = mealItems.sortedBy { it.id }
                                val displayNumber = allItems.indexOfFirst { it.id == item.id } + 1

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 0.dp
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)  // 很淡的灰色
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.4f
                                        )
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
//                        Text(
//                            "#$displayNumber",
//                            style = MaterialTheme.typography.titleMedium,
//                            color = MaterialTheme.colorScheme.primary,
//                            modifier = Modifier.padding(end = 10.dp)
//                        )
                                        Image(
                                            painter = painterResource(
                                                id = when (item.type) {
                                                    "咖啡" -> R.drawable.coffee_cup
                                                    "奶茶" -> R.drawable.orange_juice
                                                    "西餐" -> R.drawable.burger
                                                    "中餐" -> R.drawable.orange_chicken
                                                    else -> R.drawable.ic_delivery
                                                },
                                            ),
                                            contentDescription = item.type,
                                            modifier = Modifier
                                                .size(70.dp)                  // 大图标，48dp 比较醒目
                                                .padding(end = 16.dp),        // 右边留空隙
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            // Text("类型：${item.type}")
                                            Text(item.location)
                                            Text(
                                                item.code,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 30.sp,                             // ★ 调大到 20sp（推荐先试这个）
                                                    fontWeight = FontWeight.Bold,                 // 加粗，更醒目
                                                    letterSpacing = 0.5.sp                        // 字母间距稍大一点，更易读（可选）
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    MainActivity.database.mealDao().delete(item)
                                                    NotificationManagerCompat.from(context)
                                                        .cancel(item.id)
                                                }
                                            },
                                            modifier = Modifier
                                                .height(36.dp)                    // 按钮高度小一点，更精致
                                                .padding(start = 12.dp),          // 和左边文字留点间距
                                            shape = RoundedCornerShape(16.dp),   // 圆角
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.1f
                                                ),  // 浅红色背景
                                                contentColor = MaterialTheme.colorScheme.primary,                       // 红色文字
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = 0.dp,          // 无阴影，更扁平
                                                pressedElevation = 2.dp
                                            ),
                                            contentPadding = PaddingValues(
                                                horizontal = 16.dp,
                                                vertical = 0.dp
                                            )
                                        ) {
                                            Text(
                                                text = "已取",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            if (showAddMealDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddMealDialog = false
                        // 可选：清空输入，避免下次打开有残留
                        mealLocation = ""
                        mealCode = ""
                    },
                    title = {
                        Text(
                            text = "添加新取餐码",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 类型选择 - 你的 SegmentedButton 部分（完整保留）
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp)
                            ) {
                                mealTypes.forEachIndexed { index, type ->
                                    SegmentedButton(
                                        selected = selectedMealType == type,
                                        onClick = { selectedMealType = type },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = mealTypes.size
                                        ),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primary,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            activeBorderColor = MaterialTheme.colorScheme.primary,
                                            inactiveBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            text = type,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Spacer(modifier = Modifier.height(10.dp))

                            // 取餐点输入框
                            TextField(
                                value = mealLocation,
                                onValueChange = { mealLocation = it },
                                label = { Text("取餐点（如：龙信蜜雪）") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Spacer(modifier = Modifier.height(10.dp))

                            // 取餐码输入框
                            TextField(
                                value = mealCode,
                                onValueChange = { mealCode = it },
                                label = { Text("取餐码（如：C471）") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (mealLocation.isBlank() || mealCode.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "请填写取餐点和取餐码",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                    return@TextButton
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT)
                                        .show()
                                    return@TextButton
                                }

                                coroutineScope.launch {
                                    addMealItemAndNotify(
                                        type = selectedMealType,
                                        location = mealLocation,
                                        code = mealCode,
                                        context = context
                                    )
                                    mealLocation = ""
                                    mealCode = ""
                                }

                                showAddMealDialog = false
                            }
                        ) {
                            Text("添加")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddMealDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }


enum class SettingsSubPage {
    MAIN,
    CHANGELOG
}
@Composable
fun SettingsScreenRoot(
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {

    var subPage by rememberSaveable {
        mutableStateOf(SettingsSubPage.MAIN)
    }
    // 🔥 关键：拦截系统返回
    BackHandler(enabled = subPage != SettingsSubPage.MAIN) {
        // 当不是主设置页时，返回到主设置页
        subPage = SettingsSubPage.MAIN
    }
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                // 下一页 → 从右滑入，左边出去
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                // 上一页 → 从左滑入，右边出去
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width } + fadeOut()
            }
        }
    ) { page ->

        when (page) {

            SettingsSubPage.MAIN -> {
                SettingsScreen(
                    currentTheme = currentTheme,
                    onThemeChange = onThemeChange,
                    onOpenChangeLog = {
                        subPage = SettingsSubPage.CHANGELOG
                    }
                )
            }

            SettingsSubPage.CHANGELOG -> {
                ChangeLogScreen(
                    onBack = {
                        subPage = SettingsSubPage.MAIN
                    }
                )
            }
        }
    }
}

enum class AppThemeMode(val label: String) {
    LIGHT("浅色模式"),
    DARK("深色模式"),
    SYSTEM("跟随系统")
}

@Composable
fun SettingsScreen(
    onOpenChangeLog: () -> Unit,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 当前选中的主题模式
    var selectedTheme by rememberSaveable { mutableStateOf(AppThemeMode.SYSTEM) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题（保持左对齐风格，和其他页面一致）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 主题模式选择
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "主题模式",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                AppThemeMode.entries.forEach { mode ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = { onThemeChange(mode) }
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Text(mode.label)
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // “关于我们”卡片式区域（简单美观）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                // horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "CustomizeLiveUpdate",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "版本：1.1.2-beta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "更新日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        onOpenChangeLog()
                    }
                )


                // GitHub 链接（可点击）
                Text(
                    text = "GitHub 项目地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable {
                            // 点击打开浏览器（需要添加 Intent 代码，下面有说明）
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/imlupp/CustomizeLiveUpdate"))
                            context.startActivity(intent)
                        }
                )

            }
        }



    }
}

fun applyAppTheme(mode: AppThemeMode) {
    appThemeMode = mode
    CoroutineScope(Dispatchers.IO).launch {
        MainActivity.database.themeDao().saveTheme(
            ThemeEntity(
                id = 0,
                mode = mode.name
            )
        )
    }
}


@Composable
fun ChangeLogScreen(
    onBack: () -> Unit, // 返回事件，由调用方决定怎么处理
    modifier: Modifier = Modifier
) {
    // 更新日志数据，每条可单独修改
    val changeLogData = listOf(
        "1.1.1" to listOf(
            "🧭 新增 无取件码/取餐码时的添加引导",
            "🐛 修复 页面切换时异常显示的问题"
        ),
        "1.1.0-beta" to listOf(
            "🎨 优化 全新UI设计，界面更简洁易操作",
            "ℹ️ 新增 设置页面「关于」板块",
            "✅ 优化 「已取」按钮样式，交互更清晰",
            "📄 优化 实时通知样式，去除冗余信息，展示更直观"
        ),
        "1.0.2-beta" to listOf(
            "🍱 新增 取餐码功能模块，一App两用",
            "⚙️ 新增 设置选项（功能持续完善中）",
            "🔄 优化 餐品类型选择逻辑，操作更顺滑",
            "🎨 优化 为不同餐品匹配图标，辨识度提升",
            "🐛 修复 偶现覆盖安装后闪退的问题"
        ),
        "1.0.1" to listOf(
            "🛠 优化 首次启动时主动请求通知权限",
            "🐛 修复 偶现通知无法显示为实时活动样式",
            "🎨 优化 通知中心快递图标颜色显示"
        ),
        "1.0.0" to listOf(
            "✨ 新增 手动添加取件码至 Live Update 通知",
            "📦 新增 取件码列表管理功能",
            "🔧 修复 重新打开 App 时通知未恢复显示的问题"
        )
    )

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部 AppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回"
                )

            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "更新日志",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }

        // 内容滚动
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 提示信息 Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 注意：Beta版本为开发后面向用户的测试版本，存在功能不完善或不稳定情况，若遇到问题请在 Issues 中提出或联系开发者。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 每个版本的卡片
            items(changeLogData) { (version, items) ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Version $version",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        items.forEach { itemText ->
                            Text(
                                text = itemText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // 底部留空
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


// 新增：独立的 suspend 函数，负责插入数据库并发送通知
private suspend fun addPickupItemAndNotify(location: String, code: String, context: Context) {
    withContext(Dispatchers.IO) {
        val dao = MainActivity.database.pickupDao()
        val item = PickupItem(location = location, code = code)
        dao.insert(item)

        val allItems = dao.getAll().first().sortedBy { it.id }
        val insertedItem = allItems.last()
        val displayNumber = allItems.indexOfFirst { it.id == insertedItem.id } + 1

        withContext(Dispatchers.Main) {
            sendPickupLiveUpdate(
                context = context,
                location = insertedItem.location,
                code = insertedItem.code,
                dbId = insertedItem.id,
                displayNumber = displayNumber
            )
        }
        Log.d("PickupApp", "插入成功，显示编号 #$displayNumber，真实 ID ${insertedItem.id}")
    }
}

private suspend fun addMealItemAndNotify(
    type: String,
    location: String,
    code: String,
    context: Context
) {
    withContext(Dispatchers.IO) {
        val dao = MainActivity.database.mealDao()
        val item = MealItem(type = type, location = location, code = code)
        dao.insert(item)

        val allItems = dao.getAll().first().sortedBy { it.id }
        val insertedItem = allItems.last()
        val displayNumber = allItems.indexOfFirst { it.id == insertedItem.id } + 1

        withContext(Dispatchers.Main) {
            sendMealLiveUpdate(
                context = context,
                type = insertedItem.type,
                location = insertedItem.location,
                code = insertedItem.code,
                dbId = insertedItem.id,
                displayNumber = displayNumber
            )
        }
        Log.d(
            "PickupApp",
            "插入取餐成功，显示编号 #$displayNumber，真实 ID ${insertedItem.id}"
        )
    }
}

private fun sendPickupLiveUpdate(
    context: Context,
    location: String,
    code: String,
    dbId: Int,
    displayNumber: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val channelId = "pickup_code_channel"

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_delivery)
        .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.delivery_man))
        .setContentTitle(code)
        .setContentText(location)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(code)
                .bigText(location)
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false)
        .setRequestPromotedOngoing(true)

    val cancelIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = "ACTION_MARK_AS_PICKED_UP"
        putExtra("notification_id", dbId)
    }
    val cancelPendingIntent = PendingIntent.getBroadcast(
        context,
        dbId,
        cancelIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    builder.addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "已取件",
        cancelPendingIntent
    )

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(dbId, builder.build())
}

private fun sendMealLiveUpdate(
    context: Context,
    type: String,
    location: String,
    code: String,
    dbId: Int,
    displayNumber: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val channelId = "pickup_code_channel"

    // 根据类型选择 small icon（纯白线条图标）
    val smallIconRes = when (type) {
        "咖啡" -> R.drawable.coffee
        "奶茶" -> R.drawable.milkshake
        "西餐" -> R.drawable.mcdonalds
        "中餐" -> R.drawable.rice
        else   -> R.drawable.ic_delivery
    }

    // large icon 可以用彩色版
    val largeIconRes = when (type) {
        "咖啡" -> R.drawable.coffee_cup
        "奶茶" -> R.drawable.orange_juice
        "西餐" -> R.drawable.burger
        "中餐" -> R.drawable.orange_chicken
        else   -> R.drawable.delivery_man
    }
    val largeIconBitmap = BitmapFactory.decodeResource(context.resources, largeIconRes)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(smallIconRes)
        .setLargeIcon(largeIconBitmap)
        .setContentTitle(code)
        .setContentText(location)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(code)
                .bigText(location)
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false)
        .setRequestPromotedOngoing(true)

    val cancelIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = "ACTION_MARK_AS_PICKED_UP"
        putExtra("notification_id", dbId)
    }
    val cancelPendingIntent = PendingIntent.getBroadcast(
        context,
        dbId,
        cancelIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    builder.addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "已取餐",
        cancelPendingIntent
    )

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(dbId, builder.build())
}
