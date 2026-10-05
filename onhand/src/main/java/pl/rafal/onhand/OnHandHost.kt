package pl.rafal.onhand

// Gospodarz OnHand — aplikacja, w której OnHand mieszka (LaunchOnMe). OnHand nie zna bazy launchera:
// pyta o tryby i prosi o przypięcie przez ten interfejs (jak interfejs wstrzykiwany z zewnątrz w .NET —
// biblioteka definiuje kontrakt, aplikacja dostarcza implementację). Launcher rejestruje go w LaunchOnMeApp.
interface OnHandHost {
    // Tryb launchera: id, nazwa i kolor (ARGB) do kropki przy nazwie.
    class Mode(val id: Long, val name: String, val color: Long)

    suspend fun modes(): List<Mode>

    // W których trybach notatka jest przypięta.
    suspend fun pinnedModes(noteId: Long): Set<Long>

    suspend fun pin(noteId: Long, modeId: Long, title: String)

    suspend fun unpin(noteId: Long, modeId: Long)

    // Notatki usunięte w OnHand — gospodarz sprząta swoje odnośniki do nich.
    suspend fun notesDeleted(noteIds: Collection<Long>)
}
