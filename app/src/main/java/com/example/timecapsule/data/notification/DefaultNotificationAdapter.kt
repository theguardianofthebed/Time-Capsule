package com.example.timecapsule.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [NotificationAdapter] using Android system Notification APIs.
 *
 * Channel, Permission & Cancellation Properties:
 * 1. Complete Permission Evaluation: Checks app-wide notification availability, API 33+ runtime
 *    `POST_NOTIFICATIONS` permission, and API 26+ channel importance.
 * 2. Immutable Routing Intent: Uses explicit [MainActivity] target, immutable [PendingIntent] flags,
 *    and unique encoded data URI per `(accountUid, capsuleId)` pair to prevent PendingIntent overwrites.
 * 3. Lockscreen Privacy: Enforces [NotificationCompat.VISIBILITY_PRIVATE] and generic resource text.
 * 4. Functional Account Cancellation: Tracks posted notifications per account UID and cancels them
 *    during account logout / switch.
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

    private val postedAccountNotifications = ConcurrentHashMap<String, MutableSet<Pair<String, Int>>>()

    override fun areNotificationsPermitted(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = manager?.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        return true
    }

    override fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val existing = manager?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val name = context.getString(R.string.notification_channel_name)
                val descriptionText = context.getString(R.string.notification_channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    override fun postUnlockNotification(accountUid: String, capsuleId: String): NotificationPostResult {
        val cleanUid = accountUid.trim()
        val cleanCapsuleId = capsuleId.trim()
        if (cleanUid.isBlank() || cleanCapsuleId.isBlank()) {
            return NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return NotificationPostResult.NOT_ATTEMPTED_PERMISSION_DENIED
            }
        }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = manager?.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return NotificationPostResult.NOT_ATTEMPTED_CHANNEL_BLOCKED
            }
        }

        val dataUri = Uri.Builder()
            .scheme("timecapsule")
            .authority("notification")
            .appendPath(cleanUid)
            .appendPath(cleanCapsuleId)
            .build()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_NOTIFICATION_TAP
            putExtra(EXTRA_ACCOUNT_UID, cleanUid)
            putExtra(EXTRA_CAPSULE_ID, cleanCapsuleId)
            data = dataUri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = ("$cleanUid/$cleanCapsuleId").hashCode()
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

        val tag = "capsule_unlock:${Uri.encode(cleanUid)}:${Uri.encode(cleanCapsuleId)}"
        val notificationId = requestCode

        return try {
            NotificationManagerCompat.from(context).notify(tag, notificationId, notification)
            postedAccountNotifications.computeIfAbsent(cleanUid) { ConcurrentHashMap.newKeySet() }
                .add(tag to notificationId)
            NotificationPostResult.ATTEMPTED_SUCCESS
        } catch (_: SecurityException) {
            NotificationPostResult.ATTEMPTED_FAILED
        } catch (_: Exception) {
            NotificationPostResult.ATTEMPTED_FAILED
        }
    }

    override fun cancelAccountNotifications(accountUid: String) {
        val cleanUid = accountUid.trim()
        if (cleanUid.isBlank()) return

        val tagPrefix = "capsule_unlock:${Uri.encode(cleanUid)}:"

        val memoryEntries = postedAccountNotifications.remove(cleanUid)
        val managerCompat = NotificationManagerCompat.from(context)
        if (memoryEntries != null) {
            for ((tag, id) in memoryEntries) {
                try {
                    managerCompat.cancel(tag, id)
                } catch (_: Exception) {
                    // Ignore cancel exceptions
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val active = try {
                manager?.activeNotifications
            } catch (_: Exception) {
                null
            }
            if (active != null) {
                for (sno in active) {
                    val tag = sno.tag
                    if (tag != null && tag.startsWith(tagPrefix)) {
                        try {
                            manager?.cancel(tag, sno.id)
                        } catch (_: Exception) {
                            // Ignore cancel exceptions
                        }
                    }
                }
            }
        }
    }
}
