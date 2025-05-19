#include <jni.h>
#include <malloc.h>
#include <Superpowered3BandEQ.h>
#include <cstring>
#include <android/log.h>
#include "Superpowered.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "SuperpoweredEQ", __VA_ARGS__)
#define LICENSE_KEY_MAX_LEN 1024

static bool isLicenseInitialized = false;

extern "C" JNIEXPORT void JNICALL
Java_com_aaron_equalizer_audio_SuperpoweredEQ_initializeLicense(
        JNIEnv *env,
        jclass /* this */,
        jstring licenseKey
) {
    const char *key = env->GetStringUTFChars(licenseKey, nullptr);
/*    if (key) {
        Superpowered::Initialize(
                key,
                false, // enableAudioAnalysis
                false, // enableFFTAndFrequencyDomain
                false, // enableAudioTimeStretching
                false, // enableAudioEffects
                false, // enableAudioPlayer
                false, // enableRecorder
                false  // enableAudioNetworking
        );
        isLicenseInitialized = true;
        env->ReleaseStringUTFChars(licenseKey, key);
        LOGD("Superpowered license initialized.");
    } else {
        LOGD("License key string is null.");
    }*/
}

extern "C" JNIEXPORT void JNICALL
Java_com_aaron_equalizer_audio_SuperpoweredEQ_applyEQ(
        JNIEnv *env,
        jclass /* this */,
        jshortArray audioData_,
        jint numSamples,
        jfloat lowGain,
        jfloat midGain,
        jfloat highGain
) {
    if (!isLicenseInitialized) {
        LOGD("Superpowered license not initialized.");
        return;
    }

    jshort *audioData = env->GetShortArrayElements(audioData_, nullptr);
    if (audioData == nullptr) return;

    float *floatBuffer = (float *)malloc(sizeof(float) * numSamples);
    if (floatBuffer == nullptr) {
        env->ReleaseShortArrayElements(audioData_, audioData, 0);
        return;
    }

    for (int i = 0; i < numSamples; ++i) {
        floatBuffer[i] = audioData[i] / 32768.0f;
    }

    Superpowered::ThreeBandEQ eq(44100);
    eq.low = lowGain;
    eq.mid = midGain;
    eq.high = highGain;
    eq.enabled = true;

    eq.process(floatBuffer, floatBuffer, numSamples);

    for (int i = 0; i < numSamples; ++i) {
        float outSample = floatBuffer[i] * 32768.0f;
        if (outSample > 32767.0f) outSample = 32767.0f;
        if (outSample < -32768.0f) outSample = -32768.0f;
        audioData[i] = (short)outSample;
    }

    free(floatBuffer);
    env->ReleaseShortArrayElements(audioData_, audioData, 0);
}
