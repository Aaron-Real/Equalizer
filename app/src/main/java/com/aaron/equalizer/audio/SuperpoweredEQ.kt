package com.aaron.equalizer.audio

object SuperpoweredEQ {
    init {
        System.loadLibrary("equalizer") // Not SuperpoweredExample!
    }

    external fun applyEQ(
        inputBuffer: ShortArray,
        numSamples: Int,
        lowGain: Float,
        midGain: Float,
        highGain: Float,
        sampleRate: Int
    )
}
