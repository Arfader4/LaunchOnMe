package pl.rafal.contextlauncher.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File

// Wspólny odczyt zdjęć z dysku (naklejki, tapety).
// ImageDecoder (Android 9+) sam obraca obraz według orientacji zapisanej w EXIF — BitmapFactory tego nie robi,
// dlatego zdjęcia zrobione w pionie wczytywały się "na leżąco".
object ImageFiles {
    // Obraz pomniejszony tak, żeby dłuższy bok miał najwyżej maxSide px (mniejszych nie powiększamy).
    fun decode(path: String, maxSide: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h).coerceAtLeast(1))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            // Zwykła bitmapa w pamięci (nie "sprzętowa"): wycinanie tła i rysowanie na płótnie czytają piksele.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}
