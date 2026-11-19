package io.github.gretchycat.infinisynth;
import java.io.InterruptedIOException;
import java.util.*;

/** Up to eight independently enveloped operators, arbitrary FM matrix including feedback. */
public final class FmSynth {
    public enum Waveform { SINE, TRIANGLE, SAW, PULSE, NOISE }
    public record Operator(double ratio,double detuneCents,double fixedHz,double level,double phase,
        Waveform waveform,double pulseWidth,Envelope envelope) {
        public Operator {
            if(!Double.isFinite(ratio)||ratio<0||ratio>64||!Double.isFinite(detuneCents)||Math.abs(detuneCents)>4800||
                !Double.isFinite(fixedHz)||fixedHz<0||fixedHz>96000||!Double.isFinite(level)||level<0||level>16||
                !Double.isFinite(phase)||phase<0||phase>1||!Double.isFinite(pulseWidth)||pulseWidth<=0||pulseWidth>=1||waveform==null||envelope==null)
                throw new IllegalArgumentException("Invalid operator parameters");
        }
    }
    public record Patch(List<Operator> operators,double[][] modulation,double[] output,double gain,double pan,long seed) {
        public Patch {
            operators=Collections.unmodifiableList(new ArrayList<>(operators));int n=operators.size();
            if(n<1||n>8||modulation.length!=n||output.length!=n||!Double.isFinite(gain)||gain<0||gain>8||!Double.isFinite(pan)||Math.abs(pan)>1)throw new IllegalArgumentException("Invalid FM patch");
            double[][] copy=new double[n][];
            for(int i=0;i<n;i++){
                if(modulation[i].length!=n)throw new IllegalArgumentException("FM matrix must be square");copy[i]=modulation[i].clone();
                for(double v:copy[i])if(!Double.isFinite(v)||Math.abs(v)>32)throw new IllegalArgumentException("FM depth must be -32..32 radians");
                if(!Double.isFinite(output[i])||Math.abs(output[i])>8)throw new IllegalArgumentException("Invalid carrier gain");
            }
            modulation=copy;output=output.clone();
        }
        @Override public double[][] modulation(){return Arrays.stream(modulation).map(double[]::clone).toArray(double[][]::new);}
        @Override public double[] output(){return output.clone();}
    }
    public static AudioBuffer render(Patch patch,double frequency,double noteSeconds,int sampleRate) throws InterruptedIOException {
        if(!Double.isFinite(frequency)||frequency<=0||frequency>=sampleRate/2.0||!Double.isFinite(noteSeconds)||noteSeconds<=0||noteSeconds>30||sampleRate<8000||sampleRate>192000)
            throw new IllegalArgumentException("Invalid note or sample rate");
        int n=patch.operators.size();double tail=patch.operators.stream().mapToDouble(o->o.envelope.release()).max().orElse(0);
        long total=Math.round((noteSeconds+tail)*sampleRate);
        if(total*2>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Generation sample budget exceeded");
        double[] phase=new double[n],freq=new double[n],previous=new double[n],next=new double[n];
        for(int j=0;j<n;j++){
            Operator o=patch.operators.get(j);phase[j]=o.phase;
            freq[j]=(o.fixedHz>0?o.fixedHz:frequency*o.ratio)*Math.pow(2,o.detuneCents/1200);
            if(freq[j]>=sampleRate/2.0)throw new IllegalArgumentException("Operator frequency exceeds Nyquist");
        }
        float[] pcm=new float[(int)total*2];Random random=new Random(patch.seed);
        double left=Math.sqrt((1-patch.pan)/2),right=Math.sqrt((1+patch.pan)/2);
        for(int f=0;f<total;f++){
            if((f&1023)==0&&Thread.currentThread().isInterrupted())throw new InterruptedIOException("Synthesis cancelled");
            double time=(double)f/sampleRate,mono=0;
            for(int j=0;j<n;j++){
                Operator o=patch.operators.get(j);double modulation=0;
                for(int k=0;k<n;k++)modulation+=patch.modulation[j][k]*previous[k];
                double p=phase[j]+modulation/(Math.PI*2);p-=Math.floor(p);
                double v=switch(o.waveform){
                    case SINE->Math.sin(p*Math.PI*2);
                    case TRIANGLE->1-4*Math.abs(p-.5);
                    case SAW->2*p-1;
                    case PULSE->p<o.pulseWidth?1:-1;
                    case NOISE->random.nextDouble()*2-1;
                };
                next[j]=v*o.level*o.envelope.value(time,noteSeconds);mono+=next[j]*patch.output[j];
                phase[j]=(phase[j]+freq[j]/sampleRate)%1;
            }
            System.arraycopy(next,0,previous,0,n);
            pcm[f*2]=(float)(mono*patch.gain*left);pcm[f*2+1]=(float)(mono*patch.gain*right);
        }
        return new AudioBuffer(sampleRate,2,pcm);
    }
}
