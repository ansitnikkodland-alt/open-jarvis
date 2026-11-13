package com.openjarvis.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openjarvis.R
import com.openjarvis.ui.ActionApprovalActivity
import com.openjarvis.ui.ActionApprovalCancelReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Approval requests are scoped to this app process. No persisted "allow" token
 * exists. Lost processes, notification failures and timeouts always deny.
 */
object ActionApprovalCoordinator {
    const val EXTRA_REQUEST_ID = "com.openjarvis.action_approval.REQUEST_ID"
    private const val CHANNEL_ID = "jarvis_action_approvals"
    private const val TIMEOUT_MS = 30_000L

    data class Request(val id: String, val details: String)

    private data class PendingRequest(
        val request: Request,
        val result: CompletableDeferred<Boolean>,
        val notificationId: Int
    )

    private val requests = ConcurrentHashMap<String, PendingRequest>()
    private val notificationIds = AtomicInteger(41000)
    private val intentIds = AtomicInteger(52000)

    fun getRequest(id: String): Request? = requests[id]?.request

    /** Called only from an app-internal confirmation Activity or cancel receiver. */
    fun respond(id: String, approved: Boolean): Boolean =
        requests[id]?.result?.complete(approved) == true

    suspend fun awaitApproval(context: Context, action: Action): Boolean {
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        val notificationId = notificationIds.getAndIncrement()
        val result = CompletableDeferred<Boolean>()
        val request = Request(id, ActionApprovalPolicy.describe(action))
        requests[id] = PendingRequest(request, result, notificationId)

        return try {
            if (!postApprovalNotification(appContext, request, notificationId)) {
                false
            } else {
                withTimeoutOrNull(TIMEOUT_MS) { result.await() } == true
            }
        } finally {
            requests.remove(id)
            runCatching {
                (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(notificationId)
            }
        }
    }

    private fun postApprovalNotification(
        context: Context,
        request: Request,
        notificationId: Int
    ): Boolean {
        return try {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Jarvis action confirmations",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        ) return false

        val approveScreen = Intent(context, ActionApprovalActivity::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, request.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            intentIds.getAndIncrement(),
            approveScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyBroadcast = Intent(context, ActionApprovalCancelReceiver::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, request.id)
        }
        val denyIntent = PendingIntent.getBroadcast(
            context,
            intentIds.getAndIncrement(),
            denyBroadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jarvis: approval required")
            .setContentText("Tap to review an action. It will be denied after 30 seconds.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setDeleteIntent(denyIntent)
            .addAction(0, "Deny", denyIntent)
            .setAutoCancel(false)
            .build()
        manager.notify(notificationId, notification)
        true
        } catch (_: Exception) {
            false
        }
    }
}
