package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.CadProject;

/** Mass properties per body from {@link NativeCadEngine#bodyInertia} with per-body densities. */
public final class BodyAnalysis {
    private BodyAnalysis() {}
    public static final int STRIDE=16;
    public static final class Body {
        public final int id;public final double volume,area,density;public final double[] centroid,tensor,principal;
        Body(double[] row,double density) {
            id=(int)row[0];volume=row[1];area=row[14];this.density=density;centroid=new double[]{row[2],row[3],row[4]};
            tensor=new double[]{row[5],row[6],row[7],row[8],row[9],row[10]};double[] p={row[11],row[12],row[13]};Arrays.sort(p);principal=p;
        }
        public boolean hasMass(){return density>0;}
        /** kg; requires a density. */
        public double mass(){return MaterialDensity.massKg(volume,density);}
        /** Inertia value (unit density, mm^5) converted to kg·mm². */
        public double kgmm2(double unitDensityValue){if(!hasMass())throw new IllegalArgumentException("Brak gęstości materiału");return unitDensityValue*density*1e-9;}
    }
    public static List<Body> read(CadProject p,double[] rows) {
        if(rows.length%STRIDE!=0)throw new IllegalArgumentException("Niepoprawne dane analizy brył");
        List<Body> result=new ArrayList<>();for(int i=0;i<rows.length;i+=STRIDE)result.add(new Body(Arrays.copyOfRange(rows,i,i+STRIDE),MaterialDensity.of(p,(int)rows[i])));
        return result;
    }
    /** Whole-model centre of mass weighted by mass when every body has a density, otherwise by volume. */
    public static double[] centre(List<Body> bodies) {
        boolean massWeighted=true;for(Body b:bodies)massWeighted&=b.hasMass();
        double total=0,x=0,y=0,z=0;for(Body b:bodies){double w=massWeighted?b.mass():b.volume;total+=w;x+=w*b.centroid[0];y+=w*b.centroid[1];z+=w*b.centroid[2];}
        if(!(total>0))throw new IllegalArgumentException("Brak objętości");return new double[]{x/total,y/total,z/total,massWeighted?1:0};
    }
}
