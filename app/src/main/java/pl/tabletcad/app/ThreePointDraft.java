package pl.tabletcad.app;

import java.util.List;

/** Persisted unfinished three-point construction; a completed entity is stored in the sketch. */
public final class ThreePointDraft {
    private ThreePointDraft() {}
    /** Mutates only the supplied candidate document. Invalid third points keep the two-point draft. */
    public static pl.tabletcad.app.model.SketchPrimitive append(pl.tabletcad.app.model.CadProject p,String tool,double[] point,String id) {
        if(!SketchShapes.threePointTool(tool))throw new IllegalArgumentException("Nieobsługiwane narzędzie trzypunktowe");
        validate(tool,java.util.Collections.singletonList(point));
        validate(p.threePointTool,p.threePointDraft);
        if(!p.threePointDraft.isEmpty()&&!tool.equals(p.threePointTool))throw new IllegalArgumentException("Zakończ lub anuluj rozpoczętą konstrukcję");
        if(p.threePointDraft.size()<2) {
            java.util.List<double[]> candidate=new java.util.ArrayList<>(p.threePointDraft);candidate.add(point.clone());validate(tool,candidate);
            p.threePointTool=tool;p.threePointDraft.add(point.clone());return null;
        }
        pl.tabletcad.app.model.SketchPrimitive shape=SketchShapes.threePoint(id,tool,p.threePointDraft.get(0),p.threePointDraft.get(1),point).withConstruction(p.drawingConstruction);
        p.sketch.add(shape);p.threePointDraft.clear();p.threePointTool="";return shape;
    }
    public static void validate(String tool,List<double[]> points) {
        if(points.isEmpty()){if(!tool.isEmpty())throw new IllegalArgumentException("Narzędzie konstrukcji bez punktów");return;}
        if(!SketchShapes.threePointTool(tool)||points.size()>2)throw new IllegalArgumentException("Niepoprawny zapis rozpoczętej konstrukcji");
        for(double[] p:points)if(p==null||p.length!=2||!Double.isFinite(p[0])||!Double.isFinite(p[1])||Math.abs(p[0])>5000||Math.abs(p[1])>5000)
            throw new IllegalArgumentException("Niepoprawny punkt rozpoczętej konstrukcji");
        if(points.size()==2&&Math.hypot(points.get(0)[0]-points.get(1)[0],points.get(0)[1]-points.get(1)[1])<.01)
            throw new IllegalArgumentException("Punkty konstrukcji muszą być różne");
    }
}
