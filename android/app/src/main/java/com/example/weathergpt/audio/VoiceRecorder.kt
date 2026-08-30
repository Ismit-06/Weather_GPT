package com.example.weathergpt.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(
    private val context: Context
) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {

        stop()

        val file =
            File.createTempFile(
                "weatherGPT_voice_",
                ".3gp",
                context.cacheDir
            )

        val mediaRecorder =
            MediaRecorder()

        mediaRecorder.setAudioSource(
            MediaRecorder.AudioSource.MIC
        )

        mediaRecorder.setOutputFormat(
            MediaRecorder.OutputFormat.THREE_GPP
        )

        mediaRecorder.setAudioEncoder(
            MediaRecorder.AudioEncoder.AMR_NB
        )

        mediaRecorder.setAudioSamplingRate(
            8000
        )

        mediaRecorder.setAudioEncodingBitRate(
            12200
        )

        mediaRecorder.setOutputFile(
            file.absolutePath
        )

        android.util.Log.d(
            "WeatherGPTVoice",
            "Preparing recorder: ${file.absolutePath}"
        )

        mediaRecorder.setOnErrorListener { _, what, extra ->
            android.util.Log.e(
                "WeatherGPTVoice",
                "MediaRecorder error: what=$what extra=$extra"
            )
        }

        mediaRecorder.setOnInfoListener { _, what, extra ->
            android.util.Log.d(
                "WeatherGPTVoice",
                "MediaRecorder info: what=$what extra=$extra"
            )
        }

        mediaRecorder.prepare()

        android.util.Log.d(
            "WeatherGPTVoice",
            "Recorder prepared"
        )

        mediaRecorder.start()

        android.util.Log.d(
            "WeatherGPTVoice",
            "Recorder started"
        )

        recorder = mediaRecorder
        outputFile = file

        return file
    }

    fun stop(): File? {

        val current =
            recorder ?: return outputFile

        try {

            android.util.Log.d(
                "WeatherGPTVoice",
                "Stopping recorder"
            )

            current.stop()

            android.util.Log.d(
                "WeatherGPTVoice",
                "Recorder stopped"
            )

        } catch (e: RuntimeException) {

            android.util.Log.e(
                "WeatherGPTVoice",
                "Recorder stop failed",
                e
            )
        }

        current.release()

        android.util.Log.d(
            "WeatherGPTVoice",
            "Recorder released; file=${outputFile?.absolutePath}"
        )

        recorder = null

        return outputFile
    }

    fun cancel() {

        val file = outputFile

        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
        }

        recorder?.release()

        recorder = null
        outputFile = null

        file?.delete()
    }

    fun isRecording(): Boolean {
        return recorder != null
    }
}
