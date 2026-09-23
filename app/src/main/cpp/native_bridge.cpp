#include <jni.h>
#include <cstdio>

extern "C" int kairo98_core_probe(unsigned short *code_segment,
                                  unsigned short *instruction_pointer);

extern "C" JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeInitializeAndReset(JNIEnv *env, jobject) {
    unsigned short cs = 0;
    unsigned short ip = 0;
    int result = kairo98_core_probe(&cs, &ip);
    char message[120];
    std::snprintf(message, sizeof(message),
                  result == 0 ? "21/W core initialized and reset: CS:IP=%04x:%04x"
                              : "21/W core reset failed: CS:IP=%04x:%04x",
                  cs, ip);
    return env->NewStringUTF(message);
}
