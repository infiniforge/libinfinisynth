package io.github.gretchycat.infinisynth;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded SF2 sample-pool extraction, including linked stereo and optional 24-bit sample data. */
public final class SoundFontBank {
    public record Sample(String name,int start,int end,int loopStart,int loopEnd,int sampleRate,int rootKey,int correction,int link,int type){}
    private final byte[] bytes;private final int pool,lowPool;private final List<Sample> samples;
    private SoundFontBank(byte[] bytes,int pool,int lowPool,List<Sample> samples){this.bytes=bytes;this.pool=pool;this.lowPool=lowPool;this.samples=Collections.unmodifiableList(new ArrayList<>(samples));}
    public List<Sample> samples(){return samples;}
    private static String id(byte[] b,int p){return new String(b,p,4,StandardCharsets.US_ASCII);}
    private static long u32(byte[] b,int p){return Integer.toUnsignedLong(ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).getInt());}
    public static SoundFontBank read(InputStream input) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        while((n=input.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Bank load cancelled");if(out.size()+(long)n>64L*1024*1024)throw new IOException("SoundFont exceeds 64 MiB limit");if(n==0)throw new IOException("SoundFont stream made no progress");out.write(block,0,n);}
        byte[] b=out.toByteArray();if(b.length<12||!id(b,0).equals("RIFF")||!id(b,8).equals("sfbk")||u32(b,4)+8!=b.length)throw new IOException("Expected an uncompressed SF2 RIFF bank");
        int pool=-1,poolSize=0,low=-1,lowSize=0,headers=-1,headerSize=0;
        for(int p=12;p<b.length;){
            if(p+8>b.length)throw new IOException("Truncated SF2 chunk");long endLong=p+8L+u32(b,p+4);if(endLong>b.length)throw new IOException("SF2 chunk outside RIFF");int end=(int)endLong;
            if(id(b,p).equals("LIST")){
                if(p+12>end)throw new IOException("Truncated SF2 list");String list=id(b,p+8);
                for(int q=p+12;q<end;){
                    if(q+8>end)throw new IOException("Truncated SF2 subchunk");long stop=q+8L+u32(b,q+4);if(stop>end)throw new IOException("SF2 subchunk outside list");int length=(int)stop-q-8;
                    String kind=id(b,q);
                    if(list.equals("INFO")&&kind.equals("ifil")&&(length<4||((b[q+8]&255)|((b[q+9]&255)<<8))!=2))throw new IOException("Only SoundFont 2 sample pools are supported");
                    if(list.equals("sdta")&&kind.equals("smpl")){if(pool>=0)throw new IOException("Duplicate sample pool");pool=q+8;poolSize=length;}
                    if(list.equals("sdta")&&kind.equals("sm24")){if(low>=0)throw new IOException("Duplicate 24-bit pool");low=q+8;lowSize=length;}
                    if(list.equals("pdta")&&kind.equals("shdr")){if(headers>=0)throw new IOException("Duplicate sample headers");headers=q+8;headerSize=length;}
                    q=(int)stop+(length&1);
                }
            }
            p=end+((end-(p+8))&1);
        }
        if(pool<0||poolSize<2||(poolSize&1)!=0||headers<0||headerSize<92||headerSize%46!=0||headerSize/46>65536)throw new IOException("Missing or invalid SF2 sample tables");
        if(poolSize>=4&&id(b,pool).equals("OggS"))throw new IOException("Compressed SF3 samples are not supported");
        String terminal=new String(b,headers+headerSize-46,20,StandardCharsets.ISO_8859_1).split("\u0000",2)[0];
        if(!terminal.equals("EOS"))throw new IOException("Missing terminal SF2 sample header");
        if(low>=0&&lowSize<poolSize/2)throw new IOException("Truncated 24-bit pool");
        List<Sample> samples=new ArrayList<>();
        for(int h=headers;h<headers+headerSize-46;h+=46){
            String name=new String(b,h,20,StandardCharsets.ISO_8859_1).split("\u0000",2)[0];
            long start=u32(b,h+20),end=u32(b,h+24),ls=u32(b,h+28),le=u32(b,h+32),rate=u32(b,h+36);
            int type=(b[h+44]&255)|((b[h+45]&255)<<8),link=(b[h+42]&255)|((b[h+43]&255)<<8);
            if(start>=end||end>poolSize/2||rate<8000||rate>192000||ls<start||le<ls||le>end)throw new IOException("Invalid SF2 sample bounds or rate");
            if(type!=1&&type!=2&&type!=4)throw new IOException("ROM, linked-only or compressed samples are not supported");
            samples.add(new Sample(name,(int)start,(int)end,(int)(ls-start),(int)(le-start),(int)rate,b[h+40]&255,b[h+41],link,type));
        }
        for(int i=0;i<samples.size();i++){
            Sample s=samples.get(i);if(s.type!=1){
                if(s.link>=samples.size()||s.link==i)throw new IOException("Invalid stereo sample link");Sample other=samples.get(s.link);
                if(other.link!=i||other.type!=(s.type==2?4:2)||other.end-other.start!=s.end-s.start||other.sampleRate!=s.sampleRate)throw new IOException("Mismatched stereo sample pair");
            }
        }
        return new SoundFontBank(b,pool,low,samples);
    }
    public AudioBuffer extract(int index){
        Sample s=samples.get(index);Sample l=s.type==2?samples.get(s.link):s,r=s.type==4?samples.get(s.link):s;
        int channels=s.type==1?1:2,frames=s.end-s.start;
        if((long)frames*channels>AudioBuffer.MAX_SAMPLES)throw new IllegalArgumentException("Sample extraction budget exceeded");
        float[] pcm=new float[frames*channels];
        for(int f=0;f<frames;f++){pcm[f*channels]=value(l.start+f);if(channels==2)pcm[f*2+1]=value(r.start+f);}
        return new AudioBuffer(s.sampleRate,channels,pcm);
    }
    private float value(int sample){int i=pool+sample*2;int v=(short)((bytes[i]&255)|(bytes[i+1]<<8));return lowPool<0?v/32768f:((v<<8)|(bytes[lowPool+sample]&255))/8388608f;}
}
