package com.qcenaman.francesinha.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Receipt / invoice photos kept inside the app. */
object Receipts {
    fun dir(ctx: Context): File = File(ctx.filesDir, "receipts").apply { mkdirs() }
    fun file(ctx: Context, name: String): File = File(dir(ctx), name)
    fun newFile(ctx: Context): File = File(dir(ctx), "r" + Store.newId() + ".jpg")
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    /** Copies a picked image in; returns its file name. */
    fun copyIn(ctx: Context, src: Uri): String? = runCatching {
        val f = newFile(ctx)
        ctx.contentResolver.openInputStream(src)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f.name
    }.getOrNull()

    fun thumbnail(ctx: Context, name: String, size: Int = 240): Bitmap? = runCatching {
        val f = file(ctx, name)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun delete(ctx: Context, name: String) {
        file(ctx, name).delete()
    }
}

/** One-file backup (trips, plans, settings, photos) to save off the phone, and restore. */
object Backup {
    fun export(ctx: Context): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "francesinha-backup-" + com.qcenaman.francesinha.logic.Fmt.isoDate(System.currentTimeMillis()) + ".zip")
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
            put("sessions.json", Codec.encode(Store.sessions.value).toByteArray())
            put("plans.json", Codec.encodePlans(PlanStore.plans.value).toByteArray())
            put("settings.json", Prefs.exportAll(ctx).toString().toByteArray())
            Receipts.dir(ctx).listFiles()?.forEach { f -> put("receipts/" + f.name, f.readBytes()) }
            TrackStore.folder(ctx).listFiles()?.forEach { f -> put("tracks/" + f.name, f.readBytes()) }
        }
        return out
    }

    /** Replaces everything with the backup's content. Returns number of trips restored, or null if not a backup. */
    fun restore(ctx: Context, src: Uri): Int? = runCatching {
        var sessions: String? = null
        var plans: String? = null
        var settings: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        val tracks = mutableMapOf<String, ByteArray>()
        ZipInputStream(ctx.contentResolver.openInputStream(src)!!).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                when {
                    e.name == "sessions.json" -> sessions = String(bytes)
                    e.name == "plans.json" -> plans = String(bytes)
                    e.name == "settings.json" -> settings = String(bytes)
                    e.name.startsWith("receipts/") && !e.name.contains("..") -> photos[e.name.removePrefix("receipts/")] = bytes
                    e.name.startsWith("tracks/") && !e.name.contains("..") -> tracks[e.name.removePrefix("tracks/")] = bytes
                }
            }
        }
        val list = Codec.decode(sessions ?: return@runCatching null)
        Store.update { list }
        plans?.let { p -> val pl = Codec.decodePlans(p); PlanStore.replaceAll(pl) }
        settings?.let { Prefs.importAll(ctx, JSONObject(it)) }
        photos.forEach { (name, bytes) -> if (name.isNotBlank() && !name.contains('/')) Receipts.file(ctx, name).writeBytes(bytes) }
        tracks.forEach { (name, bytes) -> if (name.isNotBlank() && !name.contains('/')) java.io.File(TrackStore.folder(ctx), name).writeBytes(bytes) }
        list.size
    }.getOrNull()
}
