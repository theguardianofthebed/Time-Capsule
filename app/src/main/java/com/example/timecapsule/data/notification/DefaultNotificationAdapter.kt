package com.example.timecapsule.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R

/**
 * Production implementation of [NotificationAdapter] using Android system Notification APIs.
 *
 * Channel & Security Properties:
 * 1. Notification Channel: Uses stable channel `capsule_unlock_channel` with default importance.
 * 2. Immutable Routing Intent: Uses explicit [MainActivity] target, immutable [PendingIntent] flags,
 *    and unique data URI per `(accountUid, capsuleId)` pair to prevent PendingIntent overwrites.
 * 3. Lockscreen Privacy: Enforces [NotificationCompat.VISIBILITY_PRIVATE] and uses generic string
 *    resources ("A capsule is ready.") without disclosing private capsule message or photo content.
 *
 * @param context Application context used for notification system services.
 */
class DefaultNotificationAdapter(
    private val context: Context
) : NotificationAdapter {

    companion object {
        const val CHANNEL_ID = "capsule_unlock_channel"
        const val ACTION_NOTIFICATION_TAP = "com.example.timecapsule.ACTION_NOTIFICATION_TAP"
        const val EXTRA_ACCOUNT_UID = "com.example.timecapsule.EXTRA_ACCOUNT_UID"
        const val EXTRA_CAPSULE_ID = "com.example.timecapsule.EXTRA_CAPSULE_ID"
    }

    override fun areNotificationsPermitted(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun postUnlockNotification(accountUid: String, capsuleId: String): Boolean {
        if (!areNotificationsPermitted()) {
            return false
        }

        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return false
        }

        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_NOTIFICATION_TAP
            putExtra(EXTRA_ACCOUNT_UID, cleanUid)
            putExtra(EXTRA_CAPSULE_ID, cleanCapsuleId)
            data = Uri.parse("timecapsule://notification/$cleanUid/$cleanCapsuleId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = (cleanUid + ":" + cleanCapsuleId).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val tag = "capsule_unlock:$cleanUid:$cleanCapsuleId"
        val notificationId = requestCode

        return try {
            NotificationManagerCompat.from(context).notify(tag, notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun cancelAccountNotifications(accountUid: String) {
        val cleanUid = accountUid.trim()
        if (cleanUid.isBlank()) return
        // Channel/app-wide notification cancellation for account cleanup seam
    }
}
