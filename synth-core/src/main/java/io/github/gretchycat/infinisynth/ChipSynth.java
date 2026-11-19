package io.github.gretchycat.infinisynth;
import java.io.InterruptedIOException;
import java.util.*;

/** Register-level chip emulation. Library loads no native code until a render is requested. */
public final class ChipSynth {
    public enum Chip { OPL3, SID6581, CMS }
    public record Write(int frame,int register,int value){
        public Write{if(frame<0||register<0||register>511||value<0||value>255)throw new IllegalArgumentException("Invalid chip register write");}
    }
    public record Program(Chip chip,int sampleRate,int frames,int clockHz,List<Write> writes){
        public Program{
            writes=Collections.unmodifiableList(new ArrayList<>(writes));
            if(chip==null||sampleRate<8000||sampleRate>192000||frames<1||(long)frames*2>AudioBuffer.MAX_SAMPLES||frames>sampleRate*30||clockHz<(chip==Chip.CMS?1000000:800000)||clockHz>(chip==Chip.CMS?16000000:1200000)||writes.size()>200000)throw new IllegalArgumentException("Invalid chip program or resource budget");
            int previous=-1;
            for(Write w:writes){if(w.frame<previous||w.frame>=frames||w.register>(chip==Chip.OPL3?511:chip==Chip.SID6581?24:65))throw new IllegalArgumentException("Chip writes must be ordered, in range and within output");previous=w.frame;}
        }
    }
    private static final class Native { static {System.loadLibrary("infinisynth");} }
    private static native float[] renderNative(int chip,int rate,int frames,int clock,long[] events) throws InterruptedIOException;
    public static AudioBuffer render(Program program) throws InterruptedIOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Chip render cancelled");
        new Native();long[] events=new long[program.writes.size()];
        for(int i=0;i<events.length;i++){Write w=program.writes.get(i);events[i]=((long)w.frame<<32)|((long)w.register<<8)|w.value;}
        return new AudioBuffer(program.sampleRate,2,renderNative(program.chip.ordinal(),program.sampleRate,program.frames,program.clockHz,events));
    }
}
