/* Adapter around the standalone BSD-3-Clause libvgm SAA1099 core. */
#include "cms.h"
#include "third_party/libvgm/emu/cores/saa1099_mame.c"
void* cms_create(int clock,int rate){void* s=saa1099m_create(clock,rate);if(s)saa1099m_reset(s);return s;}
void cms_destroy(void* s){if(s)saa1099m_destroy(s);}
void cms_write(void* s,int reg,int value){saa1099m_write(s,1,reg);saa1099m_write(s,0,value);}
void cms_sample(void* s,float* stereo){DEV_SMPL l=0,r=0;DEV_SMPL* out[]={&l,&r};saa1099m_update(s,1,out);stereo[0]=l/32768.0f;stereo[1]=r/32768.0f;}
void cms_clock(void* s,int envelope){saa1099m_write(s,1,24+envelope);}
