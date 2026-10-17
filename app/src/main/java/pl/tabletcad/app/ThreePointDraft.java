package pl.tabletcad.app;

import java.util.List;

/** Persisted unfinished three-point construction; a completed entity is stored in the sketch. */
public final class ThreePointDraft {
    private ThreePointDraft() {}
    public static void validate(String tool,List<double[]> points) {
        if(points.isEmpty()){if(!tool.isEmpty())throw new IllegalArgumentException("Narzędzie konstrukcji bez punktów");return;}
        if(!SketchShapes.threePointTool(tool)||points.size()>2)throw new IllegalArgumentException("Niepoprawny zapis rozpoczętej konstrukcji");
        for(double[] p:points)if(p==null||p.length!=2||!Double.isFinite(p[0])||!Double.isFinite(p[1])||Math.abs(p[0])>5000||Math.abs(p[1])>5000)
            throw new IllegalArgumentException("Niepoprawny punkt rozpoczętej konstrukcji");
        if(points.size()==2&&Math.hypot(points.get(0)[0]-points.get(1)[0],points.get(0)[1]-points.get(1)[1])<.01)
            throw new IllegalArgumentException("Punkty konstrukcji muszą być różne");
    }
}
