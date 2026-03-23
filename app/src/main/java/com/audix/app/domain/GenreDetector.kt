package com.audix.app.domain

import android.util.Log
import com.audix.app.BuildConfig
import com.audix.app.data.SongCache
import com.audix.app.data.SongCacheDao
import com.audix.app.network.GeminiApi
import com.audix.app.network.GeminiRequest
import com.audix.app.network.Content
import com.audix.app.network.Part
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GenreDetector(private val songCacheDao: SongCacheDao) {
    companion object {
        private const val TAG = "GenreDetector"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }

    private val api: GeminiApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(GeminiApi::class.java)
    }

    suspend fun detectGenre(title: String, artist: String): String? {
        val cached = songCacheDao.getGenreForSong(title, artist)
        if (cached != null) {
            Log.d(TAG, "Cache hit for '$title' by '$artist': $cached")
            return cached
        }

        val prompt = """
            Classify the song into ONE genre from:
            Rock,Pop,Hip-Hop,Classical,Jazz,Electronic (EDM),Metal,R&B,Lo-fi.
            Use the closest match based on title and artist.
            Return only the genre.

Title:$title
Artist:$artist

        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            )
        )

        return try {
            val response = api.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val genre = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (genre != null) {
                Log.d(TAG, "Detected genre for '$title' by '$artist': $genre")
                songCacheDao.insert(SongCache(title, artist, genre, System.currentTimeMillis()))
                genre
            } else {
                Log.e(TAG, "Empty choices in response")
                "Error: Unknown Response"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Genre detection cancelled")
            throw e
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "HTTP error detecting genre: ${e.code()}", e)
            if (e.code() == 429) {
                "Rate Limit Exceeded"
            } else {
                "Error: API Failed (${e.code()})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting genre", e)
            "Error: Network Offline"
        }
    }
}
