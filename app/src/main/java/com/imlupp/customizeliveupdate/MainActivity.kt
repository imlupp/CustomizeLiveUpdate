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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.style.TextOverflow

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
        bottomBar = {
            NavigationBar {
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
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            BottomTab.Pickup -> MyScreen(
                permissionLauncher = permissionLauncher,
                modifier = Modifier.padding(innerPadding)
            )

            BottomTab.Meal -> MealScreen(
                permissionLauncher = permissionLauncher,
                modifier = Modifier.padding(innerPadding)
            )

            BottomTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
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

    val pickupItems by MainActivity.database.pickupDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "快递取件码 Live Updates",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = pickupLocation,
            onValueChange = { pickupLocation = it },
            label = { Text("取件点（如：丰巢A区）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = pickupCode,
            onValueChange = { pickupCode = it },
            label = { Text("取件码（如：874920）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (pickupLocation.isBlank() || pickupCode.isBlank()) {
                    Toast.makeText(context, "请填写取件点和取件码", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // 检查权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // 使用 rememberCoroutineScope 安全启动协程
                coroutineScope.launch {
                    addPickupItemAndNotify(
                        location = pickupLocation,
                        code = pickupCode,
                        context = context
                    )
                    // 清空输入框（在主线程）
                    pickupLocation = ""
                    pickupCode = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加至取件列表")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("我的取件列表", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(pickupItems) { item ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allItems = pickupItems.sortedBy { it.id }
                        val displayNumber = allItems.indexOfFirst { it.id == item.id } + 1

                        Text("#$displayNumber  ", style = MaterialTheme.typography.labelLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("取件点：${item.location}")
                            Text("取件码：${item.code}", style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = {
                            // 删除逻辑在这里
                            CoroutineScope(Dispatchers.IO).launch {
                                // 1. 从数据库删除这条记录
                                MainActivity.database.pickupDao().delete(item)

                                // 2. 取消对应的通知（用 dbId，也就是 item.id）
                                NotificationManagerCompat.from(context).cancel(item.id)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error  // 红色更醒目
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
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

    val mealItems by MainActivity.database.mealDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "取餐码 Live Updates",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // SegmentedButton 部分（颜色参数已修正）
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            // 可选：加点间距或高度调整
            // colors = SegmentedButtonDefaults.colors() 如果想用默认主题色，就不用写 colors
        ) {
            mealTypes.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = selectedMealType == type,
                    onClick = { selectedMealType = type },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = mealTypes.size
                    ),
                    // 这里是正确颜色参数（用你的主题色或自定义）
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,          // 选中背景（通常蓝色）
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,          // 选中文字（白色）
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant, // 未选中背景（浅灰）
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant, // 未选中文字（深灰）
                        // 可选：加边框颜色
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelLarge,  // 字体大一点，更清晰
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))  // 加大点间距，美观

        TextField(
            value = mealLocation,
            onValueChange = { mealLocation = it },
            label = { Text("取餐点（如：星巴克一楼）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = mealCode,
            onValueChange = { mealCode = it },
            label = { Text("取餐码（如：A47）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (mealLocation.isBlank() || mealCode.isBlank()) {
                    Toast.makeText(context, "请填写取餐点和取餐码", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT).show()
                    return@Button
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
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加至取餐列表")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("我的取餐列表", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(mealItems) { item ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allItems = mealItems.sortedBy { it.id }
                        val displayNumber = allItems.indexOfFirst { it.id == item.id } + 1

                        Text(
                            "#$displayNumber  ",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("类型：${item.type}")
                            Text("取餐点：${item.location}")
                            Text(
                                "取餐码：${item.code}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        IconButton(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                MainActivity.database.mealDao().delete(item)
                                NotificationManagerCompat.from(context).cancel(item.id)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "版本：1.0.2-beta",
            style = MaterialTheme.typography.bodyMedium
        )
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
        .setContentText("取件点：$location")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle("快递取件提醒 #$displayNumber")
                .bigText("取件点：$location\n取件码：$code")
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
        .setContentText("取餐点：$location")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle("取餐提醒 #$displayNumber")
                .bigText("取餐点：$location\n取餐码：$code")
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
