#include <jni.h>
#include <Superpowered3BandEQ.h>
#include <SuperpoweredSimple.h>
#include <SuperpoweredCPU.h>
#include <malloc.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_aaron_equalizer_audio_SuperpoweredEQ_applyEQ(JNIEnv *env, jobject /* this */,
                                                      jshortArray buffer_,
                                                      jint numberOfSamples,
                                                      jfloat lowGain,
                                                      jfloat midGain,
                                                      jfloat highGain,
                                                      jint sampleRate) {
    // Removed Superpowered::Initialize (caused undefined symbol)

    jshort *buffer = env->GetShortArrayElements(buffer_, NULL);
    float *floatBuffer = (float *)malloc(sizeof(float) * numberOfSamples);

    Superpowered::ThreeBandEQ *eq = new Superpowered::ThreeBandEQ((unsigned int)sampleRate);
    eq->low = lowGain;
    eq->mid = midGain;
    eq->high = highGain;

    Superpowered::ShortIntToFloat(buffer, floatBuffer, numberOfSamples);
    eq->process(floatBuffer, floatBuffer, numberOfSamples);
    Superpowered::FloatToShortInt(floatBuffer, buffer, numberOfSamples);

    delete eq;
    free(floatBuffer);
    env->ReleaseShortArrayElements(buffer_, buffer, 0);
}
