package io.github.sapog3090.opprice.work

import android.Manifest
import android.annotation.SuppressLint
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
import io.github.sapog3090.opprice.R
import io.github.sapog3090.opprice.data.CheckOutcome
import io.github.sapog3090.opprice.data.source
import io.github.sapog3090.opprice.ui.describe
import io.github.sapog3090.opprice.ui.rub

object Notifier {
    private const val CHANNEL = "price_drops"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_drops),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_drops_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // проверено в canNotify()
    fun notifyDrop(context: Context, outcome: CheckOutcome) {
        val snapshot = outcome.snapshot ?: return
        if (!canNotify(context)) return
        val was = outcome.previous?.minPrice
        val text = buildString {
            append(rub(snapshot.minPrice))
            snapshot.source?.let { append(" на ").append(it.title) }
            if (was != null && was > snapshot.minPrice) append(", было ").append(rub(was))
        }
        val open = PendingIntent.getActivity(
            context,
            outcome.tracked.id.toInt(),
            Intent(Intent.ACTION_VIEW, Uri.parse(snapshot.bestUrl)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle(outcome.tracked.describe())
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${snapshot.bestTitle}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(outcome.tracked.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом — молча пропускаем.
        }
    }
}
