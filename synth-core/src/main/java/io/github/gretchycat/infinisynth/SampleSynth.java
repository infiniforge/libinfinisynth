package io.github.gretchycat.infinisynth;
import java.io.InterruptedIOException;

/** Sample audition with pitch, looping, pan, gain and a fully editable amplitude envelope. */
public final class SampleSynth {
    public record Patch(double semitones,double gain,double pan,int loopStart,int loopEnd,boolean loop,Envelope envelope){
        public Patch{if(!Double.isFinite(semitones)||Math.abs(semitones)>96||!Double.isFinite(gain)||gain<0||gain>8||!Double.isFinite(pan)||Math.abs(pan)>1||envelope==null)throw new IllegalArgumentException("Invalid sample patch");}
    }
    public static AudioBuffer render(AudioBuffer source,Patch patch,double noteSeconds,int rate) throws InterruptedIOException {
        if(source.frames()==0||!Double.isFinite(noteSeconds)||noteSeconds<=0||noteSeconds>30||rate<8000||rate>192000)throw new IllegalArgumentException("Invalid sample note");
        if(patch.loop&&(patch.loopStart<0||patch.loopEnd>source.frames()||patch.loopEnd<=patch.loopStart))throw new IllegalArgumentException("Invalid sample loop");
        long count=Math.round((noteSeconds+patch.envelope.release())*rate);
        if(count*2>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Sample render budget exceeded");
        float[] pcm=new float[(int)count*2];double step=(double)source.sampleRate()/rate*Math.pow(2,patch.semitones/12),p=0;
        double left=source.channels()==1?Math.sqrt((1-patch.pan)/2):Math.min(1,1-patch.pan);
        double right=source.channels()==1?Math.sqrt((1+patch.pan)/2):Math.min(1,1+patch.pan);
        for(int f=0;f<count;f++,p+=step){
            if((f&1023)==0&&Thread.currentThread().isInterrupted())throw new InterruptedIOException("Sample render cancelled");
            if(patch.loop&&p>=patch.loopEnd)p=patch.loopStart+(p-patch.loopStart)%(patch.loopEnd-patch.loopStart);
            if(p>=source.frames())break;int a=(int)p,b=a+1;
            if(patch.loop&&b>=patch.loopEnd)b=patch.loopStart;else b=Math.min(b,source.frames()-1);
            double fraction=p-a,amp=patch.gain*patch.envelope.value((double)f/rate,noteSeconds);
            for(int ch=0;ch<2;ch++){
                int channel=source.channels()==1?0:Math.min(ch,source.channels()-1);
                double sample=source.sample(a,channel)*(1-fraction)+source.sample(b,channel)*fraction;
                pcm[f*2+ch]=(float)(sample*amp*(ch==0?left:right));
            }
        }
        return new AudioBuffer(rate,2,pcm);
    }
}
