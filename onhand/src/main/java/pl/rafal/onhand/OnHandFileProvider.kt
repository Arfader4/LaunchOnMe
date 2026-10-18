package pl.rafal.onhand

import androidx.core.content.FileProvider

// Osobna klasa (jak StickerFileProvider w StickOnMe): launcher ma już w manifeście androidx FileProvider,
// a dwa wpisy o tej samej nazwie klasy zlałyby się przy łączeniu manifestów w jeden.
class OnHandFileProvider : FileProvider()
