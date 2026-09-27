package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import pl.rafal.contextlauncher.ModeShortcutActivity
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.ui.ModeIcon

// Skróty "Włącz tryb …" widoczne dla innych aplikacji: menu ikony w innym launcherze, procedury, Tasker, tagi NFC.
object ModeShortcuts {

    fun publish(context: Context, modes: List<ModeEntity>) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val shortcuts = modes.take(max).mapIndexed { index, mode ->
                ShortcutInfoCompat.Builder(context, "mode_${mode.id}")
                    .setShortLabel(mode.name)
                    .setLongLabel("Włącz tryb ${mode.name}")
                    .setIcon(IconCompat.createWithBitmap(iconBitmap(context, mode)))
                    .setIntent(
                        Intent(context, ModeShortcutActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(ModeShortcutActivity.EXTRA_MODE_ID, mode.id),
                    )
                    .setRank(index)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    // Ikona skrótu: symbol trybu na kółku w kolorze trybu (sam biały kontur byłby niewidoczny na jasnym menu).
    private fun iconBitmap(context: Context, mode: ModeEntity): Bitmap {
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mode.color.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        ContextCompat.getDrawable(context, ModeIcon.of(mode.icon).res)?.let { drawable ->
            val glyph = drawable.toBitmap(size * 6 / 10, size * 6 / 10)
            canvas.drawBitmap(glyph, size * 2f / 10, size * 2f / 10, null)
        }
        return bitmap
    }
}
