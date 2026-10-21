package pl.tabletcad.app;

/**
 * Range of a value slider. Lengths and counts are not capped by the slider: a typed value
 * beyond the end, or the thumb released at the end, widens the range (the geometry kernel
 * decides what is feasible and the preview reports it). Angle ranges stay fixed.
 */
public final class SliderRange {
    public double min,max;public final boolean fixed;public final double limit;
    public SliderRange(double min,double max,boolean fixed,double limit){this.min=min;this.max=max;this.fixed=fixed;this.limit=limit;}
    /** Angle-like ranges (±180, 0–360, ±45, 0–90, 0–180) are not widened. */
    public static boolean angleLike(double min,double max){return (max==360||max==180||max==90||max==45)&&(min==-max||min>=0&&min<=1);}
    /** True when the range changed so that it contains v. */
    public boolean include(double v) {
        if(fixed||!Double.isFinite(v))return false;boolean changed=false;
        if(v>max&&max<limit){max=Math.min(limit,Math.max(v*1.5,max*2));changed=true;}
        if(v<min&&min<0&&min>-limit){min=Math.max(-limit,Math.min(v*1.5,min*2));changed=true;}
        return changed;
    }
    /** Thumb released at an end: the range doubles on that side. */
    public boolean released(double v) {
        if(fixed)return false;double span=max-min;
        if(v>=max-span*1e-6&&max<limit){max=Math.min(limit,max<=0?1:max*2);return true;}
        if(min<0&&v<=min+span*1e-6&&min>-limit){min=Math.max(-limit,min*2);return true;}
        return false;
    }
}
