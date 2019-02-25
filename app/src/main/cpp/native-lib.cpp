#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
printHelloMethod(JNIEnv *env, jclass type, jstring input) {
	return input;
}


const JNINativeMethod gMethods[] = { 
        {"printHello","(Ljava/lang/String;)Ljava/lang/String;",(void *) printHelloMethod},
};


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {

    //LOGD("JNI_OnLoad");
    JNIEnv *env = NULL;
    jint result = -1;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        //LOGD("GetEnv Error");
        return -1;
    }
    const char *gClassName = "com/github/qs00019/messnative/TestSo";
    jclass myClass = env->FindClass(gClassName);
    if (myClass == NULL) {
        //LOGD("cannot get class:%s", gClassName);
        return -1;
    }
    if (env->RegisterNatives(myClass, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) <
        0) {
        //LOGD("register native method failed!");
        return -1;
    }
   
    return JNI_VERSION_1_6; 

}






