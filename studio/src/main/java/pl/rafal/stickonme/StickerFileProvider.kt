package pl.rafal.stickonme

import androidx.core.content.FileProvider

// Osobna klasa (a nie sam FileProvider), bo launcher ma już swój FileProvider w manifeście —
// dwa wpisy o tej samej nazwie klasy by się zlały przy łączeniu manifestów.
class StickerFileProvider : FileProvider()
