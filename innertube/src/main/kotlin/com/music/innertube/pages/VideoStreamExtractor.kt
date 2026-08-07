package com.music.innertube.pages

import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse

object VideoStreamExtractor {

    suspend fun getVideoStreamUrl(videoId: String): String? {
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

            val bestFormat = streamingData.adaptiveFormats
                .filter { it.width != null }
                .sortedWith(
                    compareByDescending<PlayerResponse.StreamingData.Format> {
                        it.mimeType.startsWith("video/mp4")
                    }
                        .thenByDescending { it.height ?: 0 }
                        .thenByDescending { it.bitrate },
                )
                .firstOrNull() ?: continue

            val url = NewPipeExtractor.getStreamUrl(bestFormat, videoId)
            if (url != null) return url
        }

        return null
    }

    fun getAvailableQualities(
        formats: List<PlayerResponse.StreamingData.Format>,
    ): List<QualityOption> = formats
        .filter { it.width != null && it.qualityLabel != null }
        .distinctBy { it.qualityLabel }
        .map { QualityOption(it.itag, it.qualityLabel ?: "?", it.height ?: 0) }
        .sortedByDescending { it.height }

    data class QualityOption(
        val itag: Int,
        val label: String,
        val height: Int,
    )
}
