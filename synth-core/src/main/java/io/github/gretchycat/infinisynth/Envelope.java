package io.github.gretchycat.infinisynth;

/** Delay/attack/hold/decay/sustain/release envelope; release begins at the actual note-off level. */
public record Envelope(double delay,double attack,double hold,double decay,double sustain,double release,double curve) {
    public static final Envelope DEFAULT=new Envelope(0,.01,0,.15,.7,.3,1);
    public Envelope {
        for(double t:new double[]{delay,attack,hold,decay,release})if(!Double.isFinite(t)||t<0||t>60)throw new IllegalArgumentException("Envelope time must be 0..60 s");
        if(!Double.isFinite(sustain)||sustain<0||sustain>1||!Double.isFinite(curve)||curve<.1||curve>10)throw new IllegalArgumentException("Invalid envelope level/curve");
    }
    private double held(double time){
        double t=time-delay;if(t<0)return 0;
        if(t<attack)return Math.pow(t/attack,curve);t-=attack;
        if(t<hold)return 1;t-=hold;
        if(t<decay)return sustain+(1-sustain)*(1-Math.pow(t/decay,curve));
        return sustain;
    }
    public double value(double time,double noteOff){
        if(time<0)return 0;
        if(time<noteOff)return held(time);
        if(release==0||time-noteOff>=release)return 0;
        return held(noteOff)*Math.pow(1-(time-noteOff)/release,curve);
    }
}
