package com.imlupp.customizeliveupdate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.imlupp.customizeliveupdate.ui.theme.CustomizeLiveUpdateTheme
import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import android.widget.Toast
import androidx.room.Room
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import kotlinx.coroutines.flow.first  // 加这个
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    companion object {
        lateinit var database: AppDatabase
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化数据库
        database = androidx.room.Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pickup_database"
        ).build()

        CoroutineScope(Dispatchers.IO).launch {
            val dao = database.pickupDao()
            val allItems = dao.getAll().first()  // 获取所有记录

            // 按 id 排序，计算每个条目的显示序号
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

        // 注册通知权限请求
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // 权限已授予，可以继续发送通知
            } else {
                // 可以在这里加提示，比如 Toast
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
}

@Composable
fun MyScreen(
    permissionLauncher: ActivityResultLauncher<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pickupLocation by remember { mutableStateOf("") }
    var pickupCode by remember { mutableStateOf("") }

    // 获取所有条目（Flow 自动更新列表）
    val pickupItems by MainActivity.database.pickupDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "快递取件码 Live Update",
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
                if (pickupLocation.isNotBlank() && pickupCode.isNotBlank()) {
                    val locationToSave = pickupLocation   // 先备份当前值
                    val codeToSave = pickupCode

                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = MainActivity.database.pickupDao()
                        val item = PickupItem(location = locationToSave, code = codeToSave)
                        dao.insert(item)
                        // 获取当前所有条目（按 id 升序，模拟插入顺序）
                        val allItems = dao.getAll().first().sortedBy { it.id }

                        // 找到刚插入的这条（用 code 和 location 匹配，或直接用 max id）
                        val insertedItem = allItems.last()  // 最新插入的通常是最后一条

                        // 计算显示编号：列表中的位置 +1
                        val displayNumber = allItems.indexOfFirst { it.id == insertedItem.id } + 1


                        withContext(Dispatchers.Main) {
                            sendPickupLiveUpdate(
                                context = context,
                                location = insertedItem.location,
                                code = insertedItem.code,
                                dbId = insertedItem.id,          // 通知 ID 和取消用这个
                                displayNumber = displayNumber    // 显示用 1、2、3...
                            )
                            pickupLocation = ""
                            pickupCode = ""
                        }
                        Log.d("PickupApp", "插入成功，显示编号 #$displayNumber，真实 ID ${insertedItem.id}")
                    }


                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加并发送到 Live Update")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 列表：类似 To-Do
        Text("我的取件列表", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(pickupItems) { item ->
                Column {   // 加一个 Column 作为容器
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allItems = pickupItems.sortedBy { it.id }  // 排序
                        val displayNumber = allItems.indexOfFirst { it.id == item.id } + 1

                        Text("#$displayNumber  ", style = MaterialTheme.typography.labelLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("取件点：${item.location}")
                            Text("取件码：${item.code}", style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { /* 删除逻辑 */ }) {
                            Icon(Icons.Filled.Delete, "删除")
                        }
                    }
                    HorizontalDivider()   // 现在在 Column 里面，可以调用
                }
            }
        }
    }
}

private fun sendPickupLiveUpdate(context: Context, location: String, code: String, dbId: Int,displayNumber: Int) {
    val channelId = "pickup_code_channel"

    // 创建通道（不变）

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_delivery)  // ← 这里换成你的快递图标！
        .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_delivery)) // 可选，大图标
        .setContentTitle(code)
        .setContentText("取件点：$location")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle("快递取件提醒 #$displayNumber")  // 加 ID 区分
                .bigText("取件点：$location\n取件码：$code")
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setRequestPromotedOngoing(true)

    // 已取件按钮（改用 id）
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