package com.rheerkens.androidkeyboard.ai

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.settings.Settings
import java.io.IOException

/** A request ready to send, or why it can't be sent yet ([problem], shown on the keyboard). */
data class JobCheck(val job: ImageJob?, val problem: String?)

/**
 * The request for [prompt] with the current [settings], reading the API key or token through
 * [secret]. Says what is missing instead when the service isn't set up.
 */
fun imageJob(settings: Settings, prompt: String, secret: (Secret) -> String?, allowLocalHttp: Boolean): JobCheck {
    val text = prompt.trim()
    if (text.isEmpty()) return JobCheck(null, "Describe the image first")
    val connection = aiConnection(settings, secret, allowLocalHttp)
    val endpoint = connection.base?.endpoint(AiAction.CREATE_IMAGE.path) ?: return JobCheck(null, connection.problem)
    val model = if (settings.aiService == AiService.OPENAI) settings.imageModel else settings.imageServerModel.trim()
    return JobCheck(ImageJob(endpoint, connection.token, model.ifEmpty { null }, settings.imageQuality, text), null)
}

/** The shared connection for any AI action, or what is missing in Settings ([problem]). */
data class AiConnectionCheck(val base: ApiBase?, val token: String?, val problem: String?)

/** The API base and key or token from [settings], or what is still missing, for the keyboard to show. */
fun aiConnection(settings: Settings, secret: (Secret) -> String?, allowLocalHttp: Boolean): AiConnectionCheck {
    val base = AiConnection.apiBase(settings.aiService, settings.aiServerUrl, allowLocalHttp)
    if (base.url == null) return AiConnectionCheck(null, null,
        if (settings.aiServerUrl.isBlank()) "Add your server's address in Settings › Experimental"
        else "Check your server's address in Settings › Experimental")
    val token = when (settings.aiService) {
        AiService.OPENAI -> secret(Secret.OPENAI_API_KEY)
            ?: return AiConnectionCheck(null, null, "Add your OpenAI API key in Settings › Experimental")
        AiService.OWN_SERVER -> secret(Secret.SERVER_TOKEN)
    }
    return AiConnectionCheck(base, token, null)
}

/** Whether this Android version can save to the gallery without a storage permission (Android 10+). */
val gallerySupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Saves a created image to Pictures/Lowkey, where gallery apps show it. Android 10+ only, which
 * needs no permission for the app's own new photos. Call on an IO dispatcher.
 */
fun saveToGallery(context: Context, bytes: ByteArray, mimeType: String, timeMs: Long): Boolean {
    if (!gallerySupported) return false
    val resolver = context.contentResolver
    val ext = mimeType.substringAfter('/').replace("jpeg", "jpg")
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "lowkey-$timeMs.$ext")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lowkey")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = try {
        resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    } catch (_: RuntimeException) {
        null
    } ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("No output stream")
        val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        if (published != 1) throw IOException("Not published")
        true
    } catch (_: IOException) {
        runCatching { resolver.delete(uri, null, null) }
        false
    } catch (_: RuntimeException) {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

/**
 * What the chosen service still needs, for the chip, Create and the options, or null when it is set
 * up. [saved]: the saved key and token hints; [stillRefused]: the service turned that one down.
 */
fun aiSetupOf(s: Settings, saved: Map<Secret, String?>, stillRefused: (Secret) -> Boolean): AiSetup? = when (s.aiService) {
    AiService.OPENAI -> when {
        saved[Secret.OPENAI_API_KEY] == null -> AiSetup.NO_KEY
        stillRefused(Secret.OPENAI_API_KEY) -> AiSetup.BAD_KEY
        else -> null
    }
    AiService.OWN_SERVER -> when {
        s.aiServerUrl.isBlank() -> AiSetup.NO_SERVER
        AiConnection.apiBase(s.aiService, s.aiServerUrl, BuildConfig.DEBUG).url == null -> AiSetup.BAD_SERVER
        stillRefused(Secret.SERVER_TOKEN) -> AiSetup.BAD_TOKEN
        else -> null
    }
}

/** The own server's host (and port), as the options name it. */
fun serverHostOf(s: Settings): String =
    AiConnection.apiBase(AiService.OWN_SERVER, s.aiServerUrl, BuildConfig.DEBUG).url
        ?.let { runCatching { java.net.URI(it) }.getOrNull() }
        ?.let { uri -> uri.host.orEmpty() + if (uri.port >= 0) ":${uri.port}" else "" }
        .orEmpty()
