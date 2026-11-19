#include <jni.h>
#include <stdlib.h>
#include <math.h>
#include <pthread.h>
#include "third_party/opl3.h"
#include "cms.h"
#define CHIPS_IMPL
#include "third_party/m6581.h"
static pthread_mutex_t sid_mutex = PTHREAD_MUTEX_INITIALIZER;
static void fail(JNIEnv* env,const char* type,const char* text){jclass c=(*env)->FindClass(env,type);if(c)(*env)->ThrowNew(env,c,text);}
JNIEXPORT jfloatArray JNICALL Java_io_github_gretchycat_infinisynth_ChipSynth_renderNative(JNIEnv* env,jclass type,jint chip,jint rate,jint frames,jint clock,jlongArray writes){
    (void)type;
    if(chip<0||chip>2||rate<8000||rate>192000||frames<1||frames>rate*30||(int64_t)frames*2>16000000||clock<(chip==2?1000000:800000)||clock>(chip==2?16000000:1200000)||!writes){fail(env,"java/lang/IllegalArgumentException","Invalid native render parameters");return NULL;}
    jsize count=(*env)->GetArrayLength(env,writes);if(count>200000){fail(env,"java/lang/IllegalArgumentException","Chip event budget exceeded");return NULL;}
    jlong* events=(*env)->GetLongArrayElements(env,writes,NULL);if(!events)return NULL;
    for(int i=0;i<count;i++){
        uint64_t frame=(uint64_t)events[i]>>32;
        if(frame>=(uint32_t)frames||((events[i]>>8)&0xffffff)>(chip==0?511:chip==1?24:65)||(i>0&&frame<((uint64_t)events[i-1]>>32))){
            (*env)->ReleaseLongArrayElements(env,writes,events,JNI_ABORT);fail(env,"java/lang/IllegalArgumentException","Invalid chip events");return NULL;
        }
    }
    float* out=calloc((size_t)frames*2,sizeof(float));if(!out){(*env)->ReleaseLongArrayElements(env,writes,events,JNI_ABORT);fail(env,"java/lang/OutOfMemoryError","Chip render allocation");return NULL;}
    jclass tc=(*env)->FindClass(env,"java/lang/Thread");jmethodID current=(*env)->GetStaticMethodID(env,tc,"currentThread","()Ljava/lang/Thread;");
    jobject thread=(*env)->CallStaticObjectMethod(env,tc,current);jmethodID interrupted=(*env)->GetMethodID(env,tc,"isInterrupted","()Z");
    opl3_chip* opl=NULL;m6581_t* sid=NULL;void* cms[2]={NULL,NULL};
    if(chip==0){opl=calloc(1,sizeof(*opl));if(opl)OPL3_Reset(opl,rate);}else if(chip==2){cms[0]=cms_create(clock,rate);cms[1]=cms_create(clock,rate);}else{pthread_mutex_lock(&sid_mutex);sid=calloc(1,sizeof(*sid));if(sid){m6581_desc_t desc={.tick_hz=clock,.sound_hz=rate,.magnitude=1.0f};m6581_init(sid,&desc);}}
    if((chip==0&&!opl)||(chip==1&&!sid)||(chip==2&&(!cms[0]||!cms[1]))){fail(env,"java/lang/OutOfMemoryError","Chip state allocation");goto cleanup;}
    int event=0;
    for(int f=0;f<frames;f++){
        if((f&1023)==0&&(*env)->CallBooleanMethod(env,thread,interrupted)){fail(env,"java/io/InterruptedIOException","Chip render cancelled");goto cleanup;}
        while(event<count&&((uint64_t)events[event]>>32)==(uint32_t)f){
            int reg=(events[event]>>8)&511,value=events[event]&255;
            if(opl)OPL3_WriteReg(opl,reg,value);
            else if(chip==2){if(reg>=64)cms_clock(cms[reg-64],0);else cms_write(cms[reg/32],reg%32,value);}
            else{uint64_t pins=M6581_CS|(reg&31);M6581_SET_DATA(pins,value);m6581_tick(sid,pins);}
            event++;
        }
        if(opl){int16_t sample[2];OPL3_GenerateResampled(opl,sample);out[f*2]=sample[0]/32768.0f;out[f*2+1]=sample[1]/32768.0f;}
        else if(chip==2){float a[2],b[2];cms_sample(cms[0],a);cms_sample(cms[1],b);out[f*2]=(a[0]+b[0])*.5f;out[f*2+1]=(a[1]+b[1])*.5f;}
        else{uint64_t pins;do{pins=m6581_tick(sid,0);}while(!(pins&M6581_SAMPLE));out[f*2]=sid->sample;out[f*2+1]=sid->sample;}
    }
cleanup:
    free(opl);free(sid);cms_destroy(cms[0]);cms_destroy(cms[1]);if(chip==1)pthread_mutex_unlock(&sid_mutex);
    (*env)->ReleaseLongArrayElements(env,writes,events,JNI_ABORT);
    jfloatArray result=NULL;if(!(*env)->ExceptionCheck(env)){result=(*env)->NewFloatArray(env,frames*2);if(result)(*env)->SetFloatArrayRegion(env,result,0,frames*2,out);}
    free(out);return result;
}
