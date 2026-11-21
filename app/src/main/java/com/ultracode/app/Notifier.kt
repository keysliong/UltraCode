package com.ultracode.app

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
import androidx.core.content.ContextCompat

object Notifier {
    private const val CH_ASK = "agent_questions"
    private const val CH_DONE = "agent_done"
    private const val ID_ASK = 1001
    private const val ID_DONE = 1002

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_ASK, "Вопросы агента", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Когда ИИ-агенту нужно что-то у вас спросить"
            })
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "Готовые ответы", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission")
    fun question(ctx: Context, question: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🤖 Агент хочет спросить")
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_ASK, n)
    }

    @Suppress("MissingPermission")
    fun done(ctx: Context, model: String, preview: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Ответ готов · $model")
            .setContentText(preview.take(200))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_DONE, n)
    }

    fun cancelQuestion(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_ASK)
}
