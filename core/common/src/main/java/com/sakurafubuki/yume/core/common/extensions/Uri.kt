package com.sakurafubuki.yume.core.common.extensions

import android.net.Uri

val Uri.isExternalStorageDocument: Boolean
    get() = "com.android.externalstorage.documents" == authority

val Uri.isDownloadsDocument: Boolean
    get() = "com.android.providers.downloads.documents" == authority

val Uri.isMediaDocument: Boolean
    get() = "com.android.providers.media.documents" == authority

val Uri.isGooglePhotosUri: Boolean
    get() = "com.google.android.apps.photos.content" == authority

val Uri.isLocalPhotoPickerUri: Boolean
    get() = toString().contains("com.android.providers.media.photopicker")

val Uri.isCloudPhotoPickerUri: Boolean
    get() = toString().contains("com.google.android.apps.photos.cloudpicker")

fun String.stripUserInfoFromHttpUrl(): String {
    val parsedUri = runCatching { Uri.parse(this) }.getOrNull() ?: return this
    return parsedUri.stripUserInfoFromHttpUri().toString()
}

fun Uri.stripUserInfoFromHttpUri(): Uri {
    val scheme = scheme.orEmpty()
    val isHttp = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    if (!isHttp) return this

    val authority = encodedAuthority ?: return this
    if (!authority.contains('@')) return this

    val hostPart = authority.substringAfter('@')
    return buildUpon().encodedAuthority(hostPart).build()
}
