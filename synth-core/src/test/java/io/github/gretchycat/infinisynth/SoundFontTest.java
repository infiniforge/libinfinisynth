package io.github.gretchycat.infinisynth;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SoundFontTest {
    private byte[] chunk(String id,byte[] body)throws IOException{var o=new ByteArrayOutputStream();o.write(id.getBytes(StandardCharsets.US_ASCII));o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.length).array());o.write(body);if((body.length&1)!=0)o.write(0);return o.toByteArray();}
    private byte[] list(String kind,byte[]...chunks)throws IOException{var o=new ByteArrayOutputStream();o.write(kind.getBytes(StandardCharsets.US_ASCII));for(byte[] c:chunks)o.write(c);return chunk("LIST",o.toByteArray());}
    private byte[] header(String name,int start,int end,int ls,int le,int link,int type){var b=ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);b.put(Arrays.copyOf(name.getBytes(StandardCharsets.US_ASCII),20));b.putInt(start).putInt(end).putInt(ls).putInt(le).putInt(22050).put((byte)60).put((byte)-3).putShort((short)link).putShort((short)type);return b.array();}
    private byte[] bank(short[] pool,byte[] low,byte[]...headers)throws IOException{var smpl=ByteBuffer.allocate(pool.length*2).order(ByteOrder.LITTLE_ENDIAN);for(short v:pool)smpl.putShort(v);var table=new ByteArrayOutputStream();for(byte[] h:headers)table.write(h);table.write(header("EOS",0,0,0,0,0,1));var body=new ByteArrayOutputStream();body.write("sfbk".getBytes());body.write(list("sdta",low==null?new byte[][]{chunk("smpl",smpl.array())}:new byte[][]{chunk("smpl",smpl.array()),chunk("sm24",low)}));body.write(list("pdta",chunk("shdr",table.toByteArray())));return chunk("RIFF",body.toByteArray());}
    @Test public void extractsOriginalMonoSamplesAndLoopMetadata()throws Exception{
        byte[] b=bank(new short[]{0,32767,-32768,16384,0,0},null,header("Piano",1,5,2,4,0,1));
        var sf=SoundFontBank.read(new ByteArrayInputStream(b));assertEquals("Piano",sf.samples().get(0).name());assertEquals(1,sf.samples().get(0).loopStart());assertEquals(3,sf.samples().get(0).loopEnd());
        var pcm=sf.extract(0);assertEquals(22050,pcm.sampleRate());assertArrayEquals(new float[]{32767/32768f,-1,.5f,0},pcm.samples(),0);
    }
    @Test public void linkedStereoAnd24BitPoolPreserveChannels()throws Exception{
        byte[] b=bank(new short[]{32767,0,-32768,16384},new byte[]{(byte)255,1,0,0},header("Left",0,2,0,2,1,4),header("Right",2,4,2,4,0,2));
        var sf=SoundFontBank.read(new ByteArrayInputStream(b));var pcm=sf.extract(0);assertEquals(2,pcm.channels());assertEquals(8388607/8388608f,pcm.sample(0,0),0);assertEquals(-1,pcm.sample(0,1),0);
        assertArrayEquals(pcm.samples(),sf.extract(1).samples(),0);
    }
    @Test public void malformedBanksNeverProduceSubstituteSamples()throws Exception{
        byte[] valid=bank(new short[]{1,2,3,4},null,header("Sample",0,4,1,3,0,1));
        assertThrows(IOException.class,()->SoundFontBank.read(new ByteArrayInputStream(Arrays.copyOf(valid,valid.length-1))));
        byte[] outOfBounds=bank(new short[]{1,2},null,header("Bad",0,9,0,1,0,1));assertThrows(IOException.class,()->SoundFontBank.read(new ByteArrayInputStream(outOfBounds)));
        byte[] badLink=bank(new short[]{1,2},null,header("Bad",0,2,0,2,9,4));assertThrows(IOException.class,()->SoundFontBank.read(new ByteArrayInputStream(badLink)));
        byte[] rom=bank(new short[]{1,2},null,header("ROM",0,2,0,2,0,32769));assertThrows(IOException.class,()->SoundFontBank.read(new ByteArrayInputStream(rom)));
    }
}
