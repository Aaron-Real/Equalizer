package com.aaron.equalizer.audio

object SuperpoweredEQ {
    init {
        System.loadLibrary("equalizer")
    }

    @JvmStatic
    external fun applyEQ(audioData: ShortArray, numSamples: Int, lowGain: Float, midGain: Float, highGain: Float)

    @JvmStatic
    external fun initializeLicense(key: String)
}
