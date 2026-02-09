package com.imlupp.customizeliveupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_MARK_AS_PICKED_UP") {
            val notificationId = intent.getIntExtra("notification_id", -1)
            if (notificationId != -1) {
                // 取消通知
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(notificationId)

                // 删除数据库对应条目（关键！）
                CoroutineScope(Dispatchers.IO).launch {

                    val pickupDao = MainActivity.database.pickupDao()
                    val mealDao = MainActivity.database.mealDao()

                    val pickupItem = pickupDao.getAll().first().find { it.id == notificationId }
                    if (pickupItem != null) {
                        pickupDao.delete(pickupItem)
                        return@launch
                    }

                    val mealItem = mealDao.getAll().first().find { it.id == notificationId }
                    if (mealItem != null) {
                        mealDao.delete(mealItem)
                    }
                }

            }
        }
    }
}