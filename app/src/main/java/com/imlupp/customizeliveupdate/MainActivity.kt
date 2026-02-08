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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
        ).build()

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

        // 恢复旧通知
        CoroutineScope(Dispatchers.IO).launch {
            val dao = database.pickupDao()
            val allItems = dao.getAll().first()
            val sortedItems = allItems.sortedBy { it.id }

            withContext(Dispatchers.Main) {
                sortedItems.forEachIndexed { index, item ->
                    val displayNumber = index + 1
                    sendPickupLiveUpdate(
                        context = this@MainActivity,
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MyScreen(
                        permissionLauncher = permissionLauncher,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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