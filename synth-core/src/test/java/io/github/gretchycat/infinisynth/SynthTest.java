package io.github.gretchycat.infinisynth;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
import java.nio.*;

public class SynthTest {
    private FmSynth.Patch fm(double depth){
        var o=new FmSynth.Operator(1,0,0,1,0,FmSynth.Waveform.SINE,.5,new Envelope(0,.01,0,.1,.5,.1,1));
        return new FmSynth.Patch(List.of(o,o),new double[][]{{0,depth},{0,0}},new double[]{1,0},.5,0,1);
    }
    @Test public void envelopeReleasesFromAttackAndDecayLevels(){
        Envelope e=new Envelope(0,1,0,1,.5,1,1);
        assertEquals(.5,e.value(.5,.5),1e-8);assertEquals(.25,e.value(1,.5),1e-8);
        assertEquals(0,e.value(2,.5),0);assertEquals(.5,e.value(10,11),0);
    }
    @Test public void envelopesHandleZeroTimesAndDelay(){
        Envelope e=new Envelope(.1,0,0,0,.8,0,1);
        assertEquals(0,e.value(.05,1),0);assertEquals(.8,e.value(.2,1),0);assertEquals(0,e.value(1,1),0);
    }
    @Test public void fmMatrixChangesSoundAndIsImmutable() throws Exception {
        AudioBuffer plain=FmSynth.render(fm(0),440,.2,44100),mod=FmSynth.render(fm(5),440,.2,44100);
        assertFalse(Arrays.equals(plain.samples(),mod.samples()));assertEquals(13230,mod.frames());
        assertTrue(Arrays.stream(toDouble(mod.samples())).map(Math::abs).max().orElse(0)>.1);
        assertEquals(0,mod.sample(mod.frames()-1,0),.001);
        var p=fm(2);p.modulation()[0][1]=0;assertEquals(2,p.modulation()[0][1],0);
    }
    private double[] toDouble(float[] f){double[] d=new double[f.length];for(int i=0;i<f.length;i++)d[i]=f[i];return d;}
    @Test public void panRoutesCarrierToOneChannel()throws Exception{
        var p=fm(0);var mono=new FmSynth.Patch(p.operators(),p.modulation(),p.output(),1,-1,1);
        var b=FmSynth.render(mono,440,.1,44100);for(int f=0;f<b.frames();f++)assertEquals(0,b.sample(f,1),0);
    }
    @Test public void sampleLoopAndEnvelopeAreApplied() throws Exception {
        AudioBuffer source=new AudioBuffer(8000,1,new float[]{1,-1,1,-1});
        var e=new Envelope(0,0,0,0,1,.01,1);
        var out=SampleSynth.render(source,new SampleSynth.Patch(0,1,-1,0,4,true,e),.02,8000);
        assertEquals(1,out.sample(20,0),.001);assertEquals(0,out.sample(20,1),0);
        assertTrue(Math.abs(out.sample(out.frames()-1,0))<.02);
    }
    @Test public void cancellationAndBudgetsRemainExplicit()throws Exception{
        assertThrows(IllegalArgumentException.class,()->FmSynth.render(fm(1),100000,1,44100));
        assertThrows(IllegalArgumentException.class,()->new Envelope(0,-1,0,0,1,0,1));
        Thread.currentThread().interrupt();try{assertThrows(InterruptedIOException.class,()->FmSynth.render(fm(1),440,1,44100));}finally{Thread.interrupted();}
        assertThrows(IllegalArgumentException.class,()->new ChipSynth.Program(ChipSynth.Chip.SID6581,44100,44100,985248,List.of(new ChipSynth.Write(2,0,1),new ChipSynth.Write(1,0,2))));
    }
    @Test public void nativeOplAndSidProduceDifferentRealChipAudio() throws Exception {
        var op=new ChipPatches.OplOperator(1,0,0,15,4,5,8,0,true,false,false,false);
        var opl=ChipSynth.render(ChipPatches.opl(new ChipPatches.OplPatch(List.of(op,op),0,3,true,true,false,false),440,.1,.1,44100));
        var voice=new ChipPatches.SidVoice(440,2048,2,0,4,10,5,false,false,false,false);
        var sid=ChipSynth.render(ChipPatches.sid(new ChipPatches.SidPatch(List.of(voice),1024,0,0,15,false,985248),.1,.1,44100));
        assertEquals(8820,opl.frames());assertEquals(8820,sid.frames());
        assertTrue(peak(opl)>.005);assertTrue(peak(sid)>.005);assertFalse(Arrays.equals(opl.samples(),sid.samples()));
    }
    private float peak(AudioBuffer b){float peak=0;for(float f:b.samples())peak=Math.max(peak,Math.abs(f));return peak;}
    @Test public void chipEnvelopesAndRoutingAreControllable()throws Exception{
        var op=new ChipPatches.OplOperator(1,0,0,15,4,5,8,0,true,false,false,false);
        var left=ChipSynth.render(ChipPatches.opl(new ChipPatches.OplPatch(List.of(op,op),1,0,true,false,false,false),440,.1,.1,44100));
        for(int i=0;i<left.frames();i++)assertEquals(0,left.sample(i,1),0);
        var muted=new ChipPatches.OplOperator(1,63,0,15,4,5,8,0,true,false,false,false);
        var quiet=ChipSynth.render(ChipPatches.opl(new ChipPatches.OplPatch(List.of(muted,muted),1,0,true,true,false,false),440,.1,.1,44100));
        assertTrue(peak(quiet)<peak(left)*.05);
    }
    @Test public void cmsUsesBothChipsWithIndependentStereoAndNoise()throws Exception{
        List<ChipPatches.CmsVoice> voices=new ArrayList<>();
        for(int i=0;i<12;i++)voices.add(new ChipPatches.CmsVoice(i==6?180:100,4,i==0?15:0,i==6?15:0,i==0||i==6,false));
        var env=new ChipPatches.CmsEnvelope(false,3,false,false,false);
        var patch=new ChipPatches.CmsPatch(voices,List.of(0,0,0,0),List.of(env,env,env,env),8000000);
        var rendered=ChipSynth.render(ChipPatches.cms(patch,.1,44100));
        assertTrue(peak(rendered)>.01);assertFalse(Arrays.equals(rendered.channel(0).samples(),rendered.channel(1).samples()));
        voices.set(0,new ChipPatches.CmsVoice(100,4,15,0,false,true));
        var noise=ChipSynth.render(ChipPatches.cms(new ChipPatches.CmsPatch(voices,patch.noiseRates(),patch.envelopes(),8000000),.1,44100));
        assertFalse(Arrays.equals(rendered.channel(0).samples(),noise.channel(0).samples()));assertArrayEquals(rendered.channel(1).samples(),noise.channel(1).samples(),0);
        var zero=new ChipPatches.CmsEnvelope(true,0,false,false,false);
        var muted=ChipSynth.render(ChipPatches.cms(new ChipPatches.CmsPatch(patch.voices(),patch.noiseRates(),List.of(zero,zero,zero,zero),8000000),.1,44100));assertEquals(0,peak(muted),0);
        var external=new ChipPatches.CmsEnvelope(true,3,false,true,false);
        var clocks=List.of(external,external,external,external);
        var held=ChipSynth.render(ChipPatches.cms(new ChipPatches.CmsPatch(patch.voices(),patch.noiseRates(),clocks,8000000),.1,44100));
        var clocked=ChipSynth.render(ChipPatches.cms(new ChipPatches.CmsPatch(patch.voices(),patch.noiseRates(),clocks,8000000,List.of(200,200)),.1,44100));
        assertTrue(peak(held)>.001);assertTrue(peak(clocked)>.001);assertFalse(Arrays.equals(held.samples(),clocked.samples()));
        assertThrows(IllegalArgumentException.class,()->new ChipSynth.Program(ChipSynth.Chip.CMS,44100,100,8000000,List.of(new ChipSynth.Write(0,66,1))));
    }

}
