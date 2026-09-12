package com.metrolist.innertube.models

/**
 * Vendored addition: one identity a signed-in Google account can act as.
 *
 * The personal channel, or one of the brand channels the account owns. Both
 * live behind the same cookie; which one answers is decided by [pageId] and
 * [dataSyncId], which is why they travel together.
 */
data class YouTubeChannel(
    val name: String,
    /** The @handle, where the channel has one. */
    val handle: String?,
    val thumbnailUrl: String?,
    /** Null on the personal channel, which needs no page id to be reached. */
    val pageId: String?,
    val dataSyncId: String?,
    /** True on the one the session is currently reading. */
    val selected: Boolean,
)
