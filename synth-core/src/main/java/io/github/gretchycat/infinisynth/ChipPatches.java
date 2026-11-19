package io.github.gretchycat.infinisynth;
import java.util.*;

/** Editable patches translated to the same register API available to sequencers and other hosts. */
public final class ChipPatches {
    public record OplOperator(int multiplier,int attenuation,int keyScaleLevel,int attack,int decay,int sustain,int release,
        int waveform,boolean sustainHold,boolean keyScaleRate,boolean tremolo,boolean vibrato) {
        public OplOperator {
            for(int v:new int[]{multiplier,attack,decay,sustain,release})if(v<0||v>15)throw new IllegalArgumentException("OPL operator rates and multiplier are 0..15");
            if(attenuation<0||attenuation>63||keyScaleLevel<0||keyScaleLevel>3||waveform<0||waveform>7)throw new IllegalArgumentException("Invalid OPL level/waveform");
        }
    }
    public record OplPatch(List<OplOperator> operators,int algorithm,int feedback,boolean left,boolean right,boolean deepTremolo,boolean deepVibrato) {
        public OplPatch {
            operators=Collections.unmodifiableList(new ArrayList<>(operators));
            if(operators.size()!=2&&operators.size()!=4||algorithm<0||algorithm>(operators.size()==2?1:3)||feedback<0||feedback>7)throw new IllegalArgumentException("Invalid OPL patch");
        }
    }
    public record SidVoice(double frequency,int pulseWidth,int waveforms,int attack,int decay,int sustain,int release,
        boolean sync,boolean ring,boolean test,boolean filtered) {
        public SidVoice {
            if(!Double.isFinite(frequency)||frequency<0||frequency>20000||pulseWidth<0||pulseWidth>4095||waveforms<1||waveforms>15)throw new IllegalArgumentException("Invalid SID oscillator");
            for(int v:new int[]{attack,decay,sustain,release})if(v<0||v>15)throw new IllegalArgumentException("SID envelope rates/levels are 0..15");
        }
    }
    public record SidPatch(List<SidVoice> voices,int cutoff,int resonance,int filterModes,int volume,boolean voice3Off,int clockHz) {
        public SidPatch {
            voices=Collections.unmodifiableList(new ArrayList<>(voices));
            if(voices.isEmpty()||voices.size()>3||cutoff<0||cutoff>2047||resonance<0||resonance>15||filterModes<0||filterModes>7||volume<0||volume>15||(clockHz!=985248&&clockHz!=1022727))throw new IllegalArgumentException("Invalid SID patch");
        }
    }
    public static ChipSynth.Program opl(OplPatch patch,double frequency,double noteSeconds,double tailSeconds,int rate){
        validateTime(noteSeconds,tailSeconds,rate);if(!Double.isFinite(frequency)||frequency<=0)throw new IllegalArgumentException("Invalid OPL frequency");
        int block=0,fnum;double base=frequency*1048576/49716;
        while(base>1023&&block<7){block++;base/=2;}
        if(base>1023||base<1)throw new IllegalArgumentException("Frequency outside OPL range");fnum=(int)Math.round(base);
        int on=(block<<2)|(fnum>>8),off=(int)Math.round(noteSeconds*rate),frames=(int)Math.ceil((noteSeconds+tailSeconds)*rate);
        List<ChipSynth.Write> w=new ArrayList<>();add(w,0,0x105,1);add(w,0,0x104,patch.operators.size()==4?1:0);
        add(w,0,0xbd,(patch.deepTremolo?128:0)|(patch.deepVibrato?64:0));int[] slots={0,3,8,11};
        for(int i=0;i<patch.operators.size();i++){
            OplOperator o=patch.operators.get(i);int s=slots[i];
            add(w,0,0x20+s,o.multiplier|(o.sustainHold?32:0)|(o.keyScaleRate?16:0)|(o.tremolo?128:0)|(o.vibrato?64:0));
            add(w,0,0x40+s,(o.keyScaleLevel<<6)|o.attenuation);add(w,0,0x60+s,(o.attack<<4)|o.decay);
            add(w,0,0x80+s,(o.sustain<<4)|o.release);add(w,0,0xe0+s,o.waveform);
        }
        int pan=(patch.left?16:0)|(patch.right?32:0);
        add(w,0,0xc0,pan|(patch.feedback<<1)|(patch.algorithm&1));
        if(patch.operators.size()==4)add(w,0,0xc3,pan|((patch.algorithm>>1)&1));
        add(w,0,0xa0,fnum&255);add(w,0,0xb0,on|32);add(w,off,0xb0,on);
        return new ChipSynth.Program(ChipSynth.Chip.OPL3,rate,frames,985248,w);
    }
    public static ChipSynth.Program sid(SidPatch patch,double noteSeconds,double tailSeconds,int rate){
        validateTime(noteSeconds,tailSeconds,rate);List<ChipSynth.Write>w=new ArrayList<>();int filters=0;
        for(int i=0;i<patch.voices.size();i++){
            SidVoice v=patch.voices.get(i);int base=i*7;long freq=Math.round(v.frequency*16777216/patch.clockHz);
            if(freq>65535)throw new IllegalArgumentException("Frequency outside SID register range");
            add(w,0,base,(int)freq&255);add(w,0,base+1,(int)freq>>8);
            add(w,0,base+2,v.pulseWidth&255);add(w,0,base+3,v.pulseWidth>>8);
            add(w,0,base+5,(v.attack<<4)|v.decay);add(w,0,base+6,(v.sustain<<4)|v.release);
            add(w,0,base+4,(v.waveforms<<4)|(v.sync?2:0)|(v.ring?4:0)|(v.test?8:0)|1);
            if(v.filtered)filters|=1<<i;
        }
        add(w,0,21,patch.cutoff&7);add(w,0,22,patch.cutoff>>3);add(w,0,23,(patch.resonance<<4)|filters);
        add(w,0,24,patch.volume|(patch.filterModes<<4)|(patch.voice3Off?128:0));
        int off=(int)Math.round(noteSeconds*rate);
        for(int i=0;i<patch.voices.size();i++){SidVoice v=patch.voices.get(i);add(w,off,i*7+4,(v.waveforms<<4)|(v.sync?2:0)|(v.ring?4:0)|(v.test?8:0));}
        return new ChipSynth.Program(ChipSynth.Chip.SID6581,rate,(int)Math.ceil((noteSeconds+tailSeconds)*rate),patch.clockHz,w);
    }
    /** CMS/Game Blaster: two SAA1099 chips, six oscillators each. */
    public record CmsVoice(int frequency,int octave,int left,int right,boolean tone,boolean noise){
        public CmsVoice{if(frequency<0||frequency>255||octave<0||octave>7||left<0||left>15||right<0||right>15)throw new IllegalArgumentException("Invalid CMS voice registers");}
    }
    public record CmsEnvelope(boolean enabled,int mode,boolean threeBit,boolean externalClock,boolean invertRight){
        public CmsEnvelope{if(mode<0||mode>7)throw new IllegalArgumentException("CMS envelope mode is 0..7");}
        int register(){return (enabled?128:0)|(mode<<1)|(threeBit?16:0)|(externalClock?32:0)|(invertRight?1:0);}
    }
    public record CmsPatch(List<CmsVoice> voices,List<Integer> noiseRates,List<CmsEnvelope> envelopes,int clockHz,List<Integer> envelopeClockHz){
        public CmsPatch(List<CmsVoice> voices,List<Integer> noiseRates,List<CmsEnvelope> envelopes,int clockHz){this(voices,noiseRates,envelopes,clockHz,Arrays.asList(0,0));}
        public CmsPatch{
            envelopeClockHz=Collections.unmodifiableList(new ArrayList<>(envelopeClockHz));if(envelopeClockHz.size()!=2)throw new IllegalArgumentException("CMS needs two shared chip envelope clock rates");for(int hz:envelopeClockHz)if(hz<0||hz>20000)throw new IllegalArgumentException("CMS envelope clocks are 0..20000 Hz");
            voices=Collections.unmodifiableList(new ArrayList<>(voices));noiseRates=Collections.unmodifiableList(new ArrayList<>(noiseRates));envelopes=Collections.unmodifiableList(new ArrayList<>(envelopes));
            if(voices.size()!=12||noiseRates.size()!=4||envelopes.size()!=4||clockHz<1000000||clockHz>16000000)throw new IllegalArgumentException("CMS needs 12 voices, 4 noise and 4 envelope generators");
            for(int n:noiseRates)if(n<0||n>3)throw new IllegalArgumentException("CMS noise rate is 0..3");
        }
    }
    public static ChipSynth.Program cms(CmsPatch patch,double seconds,int rate){
        if(!Double.isFinite(seconds)||seconds<=0||seconds>30)throw new IllegalArgumentException("CMS duration must be >0 and <=30 seconds");
        List<ChipSynth.Write> w=new ArrayList<>();
        for(int chip=0;chip<2;chip++){
            int base=chip*32,tone=0,noise=0;
            for(int i=0;i<6;i++){CmsVoice v=patch.voices.get(chip*6+i);add(w,0,base+i,v.left|(v.right<<4));add(w,0,base+8+i,v.frequency);if(v.tone)tone|=1<<i;if(v.noise)noise|=1<<i;}
            for(int i=0;i<3;i++)add(w,0,base+16+i,patch.voices.get(chip*6+i*2).octave|(patch.voices.get(chip*6+i*2+1).octave<<4));
            add(w,0,base+20,tone);add(w,0,base+21,noise);add(w,0,base+22,patch.noiseRates.get(chip*2)|(patch.noiseRates.get(chip*2+1)<<4));
            for(int i=0;i<2;i++)add(w,0,base+24+i,patch.envelopes.get(chip*2+i).register());
            add(w,0,base+28,1);
        }
        int frames=Math.max(1,(int)Math.round(seconds*rate));long events=w.size();
        for(int chip=0;chip<2;chip++)if(externalClockEnabled(patch,chip))events+=(long)Math.ceil(seconds*patch.envelopeClockHz.get(chip));
        if(events>200000)throw new IllegalArgumentException("CMS envelope clock event budget exceeded; shorten the note or lower clock rates");
        for(int chip=0;chip<2;chip++){int hz=patch.envelopeClockHz.get(chip);if(hz>0&&externalClockEnabled(patch,chip))for(int n=1;;n++){int f=(int)Math.round((double)n*rate/hz);if(f>=frames)break;add(w,f,64+chip,0);}}
        w.sort(Comparator.comparingInt(ChipSynth.Write::frame));
        return new ChipSynth.Program(ChipSynth.Chip.CMS,rate,frames,patch.clockHz,w);
    }
    private static boolean externalClockEnabled(CmsPatch patch,int chip){for(int i=chip*2;i<chip*2+2;i++)if(patch.envelopes.get(i).enabled&&patch.envelopes.get(i).externalClock)return true;return false;}
    private static void add(List<ChipSynth.Write>w,int f,int r,int v){w.add(new ChipSynth.Write(f,r,v));}
    private static void validateTime(double note,double tail,int rate){if(!Double.isFinite(note)||!Double.isFinite(tail)||note<=0||tail<=0||note+tail>30||rate<8000||rate>192000)throw new IllegalArgumentException("Chip note + tail must be <=30 seconds");}
}
