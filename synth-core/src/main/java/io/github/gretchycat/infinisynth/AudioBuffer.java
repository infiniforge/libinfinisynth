package io.github.gretchycat.infinisynth;

/** Immutable interleaved float PCM. No normalization is applied implicitly. */
public final class AudioBuffer {
    public static final int MAX_SAMPLES = 16_000_000;
    private final int sampleRate, channels;
    private final float[] samples;
    public AudioBuffer(int sampleRate, int channels, float[] samples) {
        if(sampleRate<8000 || sampleRate>192000 || channels<1 || channels>32 || samples.length%channels!=0 || samples.length>MAX_SAMPLES)
            throw new IllegalArgumentException("Invalid PCM format or sample budget exceeded");
        for(float v:samples) if(!Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite PCM");
        this.sampleRate=sampleRate;this.channels=channels;this.samples=samples.clone();
    }
    public int sampleRate(){return sampleRate;}
    public int channels(){return channels;}
    public int frames(){return samples.length/channels;}
    public double seconds(){return (double)frames()/sampleRate;}
    public float sample(int frame,int channel){if(channel<0||channel>=channels)throw new IndexOutOfBoundsException();return samples[frame*channels+channel];}
    public float[] samples(){return samples.clone();}
    public AudioBuffer channel(int channel){float[] mono=new float[frames()];for(int f=0;f<frames();f++)mono[f]=sample(f,channel);return new AudioBuffer(sampleRate,1,mono);}
}
