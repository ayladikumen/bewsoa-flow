package ai.bewsoa.flow.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Export files live in the cache dir, not files/: the OS is free to reclaim
 * them, and an export is a one-shot handoff to the share sheet rather than
 * something the app needs to keep.
 */
private const val EXPORT_DIR = "export"
private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

object Sharing {

    fun writeExport(context: Context, filename: String, content: String): Uri {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(content)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Hands the file to the system share sheet. The user picks the destination —
     * nothing leaves the device until they do.
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** Called on app start; yesterday's export is nobody's business. */
    fun pruneOld(context: Context) {
        val dir = File(context.cacheDir, EXPORT_DIR)
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    fun mimeFor(format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> "text/csv"
        ExportFormat.JSON -> "application/json"
        ExportFormat.MARKDOWN -> "text/markdown"
    }

    fun extensionFor(format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> "csv"
        ExportFormat.JSON -> "json"
        ExportFormat.MARKDOWN -> "md"
    }
}

enum class ExportFormat(val label: String) {
    CSV("CSV"),
    JSON("JSON"),
    MARKDOWN("Markdown")
}
