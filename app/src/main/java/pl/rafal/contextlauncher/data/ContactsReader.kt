package pl.rafal.contextlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kontakt z gwiazdką ("Ulubione" w aplikacji Kontakty).
data class FavoriteContact(
    val id: Long,
    val lookupUri: Uri,       // stały adres kontaktu (przeżywa synchronizację, w przeciwieństwie do samego id)
    val name: String,
    val number: String?,      // domyślny albo pierwszy numer telefonu
    val photo: ImageBitmap?,  // miniatura; null = pokażemy inicjały
)

class ContactsReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun starred(limit: Int = 12): List<FavoriteContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val resolver = context.contentResolver
        runCatching {
            // Zapytanie jak SELECT ... WHERE starred = 1 ORDER BY display_name — ContentResolver to "baza" systemu.
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                "${ContactsContract.Contacts.STARRED} = 1",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val id = c.getLong(0)
                        add(
                            FavoriteContact(
                                id = id,
                                lookupUri = ContactsContract.Contacts.getLookupUri(id, c.getString(1)),
                                name = c.getString(2).orEmpty(),
                                number = numberFor(id),
                                photo = c.getString(3)?.let { loadPhoto(Uri.parse(it)) },
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    // Najpierw numer oznaczony jako domyślny (IS_SUPER_PRIMARY), potem pierwszy z brzegu.
    private fun numberFor(contactId: Long): String? =
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun loadPhoto(uri: Uri): ImageBitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull()
}
