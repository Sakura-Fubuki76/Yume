package com.sakurafubuki.yume.core.common.extensions

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun File.getSubtitles(): List<File> = withContext(Dispatchers.IO) {
    val mediaName = this@getSubtitles.nameWithoutExtension
    val parentDir = this@getSubtitles.parentFile
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    val extSet = subtitleExtensions.map { it.lowercase() }.toSet()

    val exactMatches = subtitleExtensions.mapNotNull { ext ->
        File(parentDir, "$mediaName.$ext").takeIf { it.exists() && it.isFile }
    }

    val variantMatches = parentDir.listFiles()?.filter { file ->
        file.isFile &&
        file.name.startsWith("$mediaName.") &&
        file.extension.lowercase() in extSet &&
        file.name !in exactMatches.map { it.name }
    } ?: emptyList()

    val strictMatches = exactMatches + variantMatches

    val strictNames = (exactMatches.map { it.name } + variantMatches.map { it.name }).toSet()
    val fuzzyMatches = parentDir.listFiles()?.filter { file ->
        file.isFile &&
        file.extension.lowercase() in extSet &&
        file.name !in strictNames &&
        fuzzyTitleMatch(mediaName + "." + file.extension, file.name)
    } ?: emptyList()

    strictMatches + fuzzyMatches
}

suspend fun File.getLocalSubtitles(
    context: Context,
    excludeSubsList: List<Uri> = emptyList(),
): List<Uri> = withContext(Dispatchers.Default) {
    val excludeSubsPathSet = excludeSubsList.mapNotNull { context.getPath(it) }.toSet()

    getSubtitles().mapNotNull { file ->
        if (file.path !in excludeSubsPathSet) {
            file.toUri()
        } else {
            null
        }
    }
}

fun String.getThumbnail(): File? {
    val filePathWithoutExtension = this.substringBeforeLast(".")
    val imageExtensions = listOf("png", "jpg", "jpeg")
    for (imageExtension in imageExtensions) {
        val file = File("$filePathWithoutExtension.$imageExtension")
        if (file.exists()) return file
    }
    return null
}

fun File.isSubtitle(): Boolean {
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    return extension.lowercase() in subtitleExtensions
}

fun File.deleteFiles() {
    try {
        listFiles()?.onEach {
            it.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != Environment.getExternalStorageDirectory()?.path } ?: "Internal Storage"

internal data class AnimeTitleInfo(val showName: String, val episode: String)

internal fun extractAnimeInfo(filename: String): AnimeTitleInfo? {

    var name = filename.substringBeforeLast(".")

    name = name.trimStart().replace(Regex("""^[\[【][^\]】]+[\]】]\s*"""), "")

    val epPatterns = listOf(
        Regex("""\s*[-–~]\s*(\d{1,4})(?:v\d+)?(?:\s|$)"""),
        Regex("""[\[【](\d{1,4})(?:v\d+)?[\]】]"""),
        Regex("""\s+[eE][pP]?\s*(\d{1,4})(?:\s|$)"""),
        Regex("""[#＃](\d{1,4})(?:\s|$)"""),
    )

    var episode = ""
    var showName = name
    for (pattern in epPatterns) {
        val match = pattern.find(name)
        if (match != null) {
            val ep = match.groupValues[1]
            episode = ep.padStart(2, '0')
            showName = name.substring(0, match.range.first).trim()
            break
        }
    }

    showName = showName
        .replace(Regex("""\s*[\[【][^\]】]*[\]】]"""), "")
        .replace(Regex("""\s*[\(（][^\)）]*[\)）]"""), "")
        .trim()

    if (showName.isEmpty() || showName.length < 2) return null
    return AnimeTitleInfo(showName, episode)
}

private fun tokenize(s: String): Set<String> =
    s.lowercase()
        .replace(Regex("""[～~]"""), "")
        .split(Regex("""[\s\-_.,!?+/&|]+"""))
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length >= 1 }
        .toSet()

private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
    val intersection = a.intersect(b).size
    val union = a.union(b).size
    if (union == 0) return 0.0
    return intersection.toDouble() / union.toDouble()
}

fun fuzzyTitleMatch(fileName1: String, fileName2: String): Boolean {
    val info1 = extractAnimeInfo(fileName1) ?: return false
    val info2 = extractAnimeInfo(fileName2) ?: return false

    if (info1.episode.isNotEmpty() && info2.episode.isNotEmpty()) {
        if (info1.episode != info2.episode) return false
    }

    val tokens1 = tokenize(info1.showName)
    val tokens2 = tokenize(info2.showName)

    if (tokens1.isEmpty() || tokens2.isEmpty()) return false

    val jaccard = jaccardSimilarity(tokens1, tokens2)

    val threshold = if (info1.episode.isNotEmpty() && info2.episode.isNotEmpty()) 0.4 else 0.6
    return jaccard >= threshold
}
