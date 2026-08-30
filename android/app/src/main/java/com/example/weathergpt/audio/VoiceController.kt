package com.example.weathergpt.audio

import android.content.Context
import android.util.Log
import com.example.weathergpt.data.SpeechClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class VoiceController(
    private val context: Context
) {

    private val recorder =
        VoiceRecorder(context)

    fun startRecording(): File {
        Log.d(
            "WeatherGPTVoice",
            "Starting recording"
        )

        return recorder.start()
    }

    fun stopRecording(): File? {

        Log.d(
            "WeatherGPTVoice",
            "Stopping recording"
        )

        return recorder.stop()
    }

    fun cancelRecording() {

        Log.d(
            "WeatherGPTVoice",
            "Cancelling recording"
        )

        recorder.cancel()
    }

    suspend fun transcribe(
        file: File
    ): String {

        Log.d(
            "WeatherGPTVoice",
            "Transcribing ${file.name}"
        )

        if (!file.exists()) {
            throw Exception(
                "Audio file does not exist."
            )
        }

        val size =
            file.length()

        Log.d(
            "WeatherGPTVoice",
            "Audio size=$size bytes"
        )

        if (size < 1000L) {
            throw Exception(
                "Audio recording is too small."
            )
        }

        val body =
            file.asRequestBody(
                "audio/3gpp".toMediaType()
            )

        val multipart =
            MultipartBody.Part.createFormData(
                name = "file",
                filename = file.name,
                body = body
            )

        Log.d(
            "WeatherGPTVoice",
            "Uploading audio to backend..."
        )

        try {

            val response =
                SpeechClient.api.transcribe(
                    file = multipart
                )

            Log.d(
                "WeatherGPTVoice",
                "Backend status=${response.status}"
            )

            Log.d(
                "WeatherGPTVoice",
                "Backend language=${response.language_code}"
            )

            Log.d(
                "WeatherGPTVoice",
                "Backend transcript=${response.transcript}"
            )

            Log.d(
                "WeatherGPTVoice",
                "Backend message=${response.message}"
            )

            if (
                response.status != "success"
            ) {

                throw Exception(
                    response.message
                        ?: "Speech transcription failed."
                )
            }

            val transcript =
                response.transcript
                    ?.trim()
                    .orEmpty()

            if (transcript.isEmpty()) {

                throw Exception(
                    "Speech was recorded but no words were detected."
                )
            }

            return transcript

        } catch (e: Exception) {

            Log.e(
                "WeatherGPTVoice",
                "Upload/transcription failed: ${e.message}",
                e
            )

            throw e
        }
    }
}
