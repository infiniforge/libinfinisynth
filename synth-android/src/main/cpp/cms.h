#pragma once
void* cms_create(int clock,int rate);
void cms_destroy(void* state);
void cms_write(void* state,int reg,int value);
void cms_sample(void* state,float* stereo);
void cms_clock(void* state,int envelope);
