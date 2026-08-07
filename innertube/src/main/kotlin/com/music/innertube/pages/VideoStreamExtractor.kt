package com.music.innertube.pages

import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse

object VideoStreamExtractor {

    data class VideoQuality(
        val label: String,
        val itag: Int,
        val height: Int?,
        val url: String,
        val audioUrl: String?,
    )

    suspend fun getVideoQualities(videoId: String): List<VideoQuality> {
        val signatureTimestamp = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()

        val playerClients = listOf(
            YouTubeClient.TVHTML5,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.ANDROID_VR_1_43_32,
        )

        for (client in playerClients) {
            val response = YouTube.player(
                videoId = videoId,
                playlistId = null,
                client = client,
                signatureTimestamp = if (client.useWebPoTokens) null else signatureTimestamp,
                poToken = null,
            ).getOrNull() ?: continue

            if (response.playabilityStatus.status != "OK") continue

            val streamingData = response.streamingData ?: continue

            val bestAudioUrl = streamingData.adaptiveFormats
                .filter { it.width == null && it.mimeType.startsWith("audio/") }
                .maxByOrNull { it.bitrate }
                ?.let { NewPipeExtractor.getStreamUrl(it, videoId) }

            val qualities = streamingData.adaptiveFormats
                .filter { it.width != null && it.qualityLabel != null }
                .sortedByDescending { it.height ?: 0 }
                .mapNotNull { format ->
                    val url = NewPipeExtractor.getStreamUrl(format, videoId) ?: return@mapNotNull null
                    VideoQuality(
                        label = format.qualityLabel ?: format.height?.let { "${it}p" } ?: "Unknown",
                        itag = format.itag,
                        height = format.height,
                        url = url,
                        audioUrl = bestAudioUrl,
                    )
                }
                .distinctBy { it.height }

            if (qualities.isNotEmpty()) return qualities
        }

        return emptyList()
    }

    suspend fun getBestVideoUrl(videoId: String): String? {
        val qualities = getVideoQualities(videoId)
        return qualities.firstOrNull()?.url
    }
}
