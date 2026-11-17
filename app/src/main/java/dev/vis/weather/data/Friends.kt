package dev.vis.weather.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A person in "Family & friends": a name, an optional photo and the place they live.
 * Contacts are picked with the system picker (no contacts permission); only the name and a copy of the
 * thumbnail are kept, on this device.
 */
data class Friend(
    val id: String,
    val name: String,
    val place: Place,
    val photo: String? = null,
    val contactUri: String? = null,
) {
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

object Friends {
    private val _all = MutableStateFlow<List<Friend>?>(null)

    fun flow(context: Context): StateFlow<List<Friend>> {
        if (_all.value == null) _all.value = load(context)
        @Suppress("UNCHECKED_CAST")
        return (_all as MutableStateFlow<List<Friend>>).asStateFlow()
    }

    fun all(context: Context): List<Friend> = _all.value ?: load(context).also { _all.value = it }

    private fun sp(context: Context) = context.getSharedPreferences("friends", Context.MODE_PRIVATE)

    private fun load(context: Context): List<Friend> = runCatching {
        val a = JSONArray(sp(context).getString("list", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val p = o.getJSONObject("place")
            Friend(
                o.getString("id"), o.getString("name"),
                Place(p.getString("name"), p.optString("region"), p.getDouble("lat"), p.getDouble("lon")),
                o.optString("photo").takeIf { it.isNotBlank() }, o.optString("contact").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<Friend>) {
        _all.value = list
        sp(context).edit {
            putString("list", JSONArray().apply {
                list.forEach { f ->
                    put(
                        JSONObject().put("id", f.id).put("name", f.name).put("photo", f.photo ?: "").put("contact", f.contactUri ?: "")
                            .put("place", JSONObject().put("name", f.place.name).put("region", f.place.region).put("lat", f.place.lat).put("lon", f.place.lon))
                    )
                }
            }.toString())
        }
    }

    fun add(context: Context, friend: Friend) = save(context, all(context).filter { it.id != friend.id } + friend)

    fun remove(context: Context, friend: Friend) {
        friend.photo?.let { runCatching { File(it).delete() } }
        save(context, all(context).filter { it.id != friend.id })
    }

    fun newId() = UUID.randomUUID().toString()

    /** Reads name + thumbnail from a contact the user just picked (the picker grants temporary access). */
    fun readPickedContact(context: Context, uri: Uri): Pair<String, String?>? = runCatching {
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI), null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getString(0) ?: return@use null
            val photoUri = c.getString(1)
            val saved = photoUri?.let { copyPhoto(context, Uri.parse(it)) }
            name to saved
        }
    }.getOrNull()

    private fun copyPhoto(context: Context, uri: Uri): String? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val dir = File(context.filesDir, "friends").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.absolutePath
    }.getOrNull()
}
