package pl.tabletcad.app;

import java.util.ArrayList;
import java.util.List;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Places a sketch element relative to the sketch origin or to a point of another element and,
 * on request, keeps it there with driving X/Y position dimensions (dimX/dimY links).
 * The element is moved rigidly, so its size and shape never change.
 */
public final class SketchPosition {
    private SketchPosition() {}
    /** Anchor index used for "centre of the rectangle / outline" (not a constraint anchor). */
    public static final int CENTRE=-1;

    /** Named reference points of an element: constraint anchors, plus the centre of a rectangle or curved outline. */
    public static List<String> pointNames(SketchPrimitive p) {
        List<String> names=new ArrayList<>();
        if(p.type.equals("Okrąg")&&p.vertices==null&&p.curves==null){names.add("Środek okręgu");return names;}
        if(p.isEllipse()){names.add("Środek elipsy");names.add("Koniec osi wielkiej");return names;}
        if(p.type.equals("Prostokąt")&&p.vertices==null){names.add("Środek prostokąta");names.add("Narożnik (x1, y1)");names.add("Narożnik (x2, y1)");names.add("Narożnik (x2, y2)");names.add("Narożnik (x1, y2)");return names;}
        if(p.curves!=null||SketchConstraints.anchorCount(p)==0){names.add("Środek obrysu");return names;}
        if(p.vertices!=null){for(int i=0;i<p.vertices.length/2;i++)names.add((p.type.equals("Splajn")?"Punkt ":"Wierzchołek ")+(i+1));return names;}
        names.add("Początek");names.add("Koniec");return names;
    }
    /** Maps a position in {@link #pointNames} to a constraint anchor or {@link #CENTRE}. */
    public static int anchorOf(SketchPrimitive p,int choice) {
        if(p.type.equals("Prostokąt")&&p.vertices==null)return choice==0?CENTRE:choice-1;
        if(p.curves!=null||SketchConstraints.anchorCount(p)==0)return CENTRE;
        return Math.max(0,Math.min(choice,SketchConstraints.anchorCount(p)-1));
    }
    /** Coordinates of the chosen point of the element. */
    public static double[] point(SketchPrimitive p,int anchor) {
        if(anchor!=CENTRE)return SketchConstraints.anchor(p,anchor);
        if(p.type.equals("Prostokąt"))return new double[]{(p.x1+p.x2)/2,(p.y1+p.y2)/2};
        double[] b=bounds(p);return new double[]{(b[0]+b[2])/2,(b[1]+b[3])/2};
    }
    static double[] bounds(SketchPrimitive p) {
        double[] o=SketchHit.outline(p);
        if(o==null)o=p.curves!=null?SketchCurves.samples(p.curves,96):p.vertices!=null?p.vertices:new double[]{p.x1,p.y1,p.x2,p.y2};
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i+1<o.length;i+=2){minX=Math.min(minX,o[i]);maxX=Math.max(maxX,o[i]);minY=Math.min(minY,o[i+1]);maxY=Math.max(maxY,o[i+1]);}
        return new double[]{minX,minY,maxX,maxY};
    }
    /** Whether the chosen point can carry driving position dimensions. */
    public static boolean lockable(SketchPrimitive p,int anchor){return SketchConstraints.anchorCount(p)>0&&(anchor!=CENTRE||p.type.equals("Prostokąt"));}

    /** Current offset of the element's point from the reference (origin when refId is null). */
    public static double[] offset(List<SketchPrimitive> sketch,String id,int anchor,String refId,int refAnchor) {
        double[] a=point(find(sketch,id),anchor),r=reference(sketch,refId,refAnchor);
        return new double[]{a[0]-r[0],a[1]-r[1]};
    }
    public static double[] reference(List<SketchPrimitive> sketch,String refId,int refAnchor) {
        return refId==null?new double[]{0,0}:SketchConstraints.anchor(find(sketch,refId),refAnchor);
    }
    static SketchPrimitive find(List<SketchPrimitive> sketch,String id) {
        for(SketchPrimitive p:sketch)if(p.id.equals(id))return p;
        throw new IllegalArgumentException("Nie znaleziono elementu szkicu");
    }

    /** Result of a placement: new geometry and constraint links. */
    public static final class Result {
        public final List<SketchPrimitive> sketch;public final List<SketchConstraints.Link> links;public final int added,removed;
        Result(List<SketchPrimitive> s,List<SketchConstraints.Link> l,int added,int removed){sketch=s;links=l;this.added=added;this.removed=removed;}
    }

    /**
     * Moves the element so its point lies at reference+(dx,dy). With lockX/lockY the previous
     * position dimensions of that point are replaced by driving ones measured from the reference.
     */
    public static Result place(List<SketchPrimitive> sketch,List<SketchConstraints.Link> links,String id,int anchor,
                               String refId,int refAnchor,double dx,double dy,boolean lockX,boolean lockY) {
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||Math.abs(dx)>10000||Math.abs(dy)>10000)throw new IllegalArgumentException("Niepoprawna odległość");
        if(id.equals(refId))throw new IllegalArgumentException("Odniesienie musi być innym elementem");
        SketchPrimitive source=find(sketch,id);
        if((lockX||lockY)&&!lockable(source,anchor))throw new IllegalArgumentException("Tego punktu nie można zwymiarować — element zostanie tylko przesunięty");
        double[] now=point(source,anchor),ref=reference(sketch,refId,refAnchor);
        double moveX=ref[0]+dx-now[0],moveY=ref[1]+dy-now[1];
        SketchPrimitive moved=SketchTransform.move(source,moveX,moveY);
        List<SketchPrimitive> geometry=new ArrayList<>(sketch);geometry.set(geometry.indexOf(source),moved);
        // The dimension is attached to a real anchor; the rectangle centre is carried by its first corner.
        int dimAnchor=anchor==CENTRE?0:anchor;
        double[] dimPoint=SketchConstraints.anchor(moved,lockX||lockY?dimAnchor:Math.max(0,Math.min(dimAnchor,SketchConstraints.anchorCount(moved)-1)));
        List<SketchConstraints.Link> out=new ArrayList<>();int removed=0;
        for(SketchConstraints.Link l:links) {
            boolean axisX=l.kind.equals("dimX"),axisY=l.kind.equals("dimY");
            boolean replaced=((axisX&&lockX)||(axisY&&lockY))&&touches(l,id,dimAnchor);
            if(replaced)removed++;else out.add(l);
        }
        int added=0;
        for(int axis=0;axis<2;axis++) {
            if(axis==0&&!lockX||axis==1&&!lockY)continue;
            String kind=axis==0?"dimX":"dimY";
            SketchConstraints.Link link=refId==null
                ?new SketchConstraints.Link(kind,id,dimAnchor,id,dimAnchor,dimPoint[axis],0)
                :new SketchConstraints.Link(kind,refId,refAnchor,id,dimAnchor,dimPoint[axis]-ref[axis],0);
            out.add(link);added++;
        }
        List<SketchPrimitive> solved=out.isEmpty()?geometry:SketchConstraints.resolve(geometry,out);
        SketchConstraints.validate(solved,out);
        return new Result(solved,out,added,removed);
    }
    /** Alignment modes relative to a reference element (bounding box of the reference, centre of a circle). */
    public static final String[] ALIGN={"Środek do środka","Środek w poziomie (X)","Środek w pionie (Y)","Do lewej krawędzi","Do prawej krawędzi","Do górnej krawędzi","Do dolnej krawędzi"};
    /** Translation that aligns the element with the reference in the given mode. */
    public static double[] alignMove(SketchPrimitive element,SketchPrimitive reference,int mode) {
        double[] r=bounds(reference);return alignMove(element,r,centre(reference,r),mode);
    }
    /** Same, against a reference box and centre (e.g. the outline of a projected face). */
    public static double[] alignMove(SketchPrimitive element,double[] r,double[] rc,int mode) {
        double[] e=bounds(element);double[] ec=centre(element,e);
        switch(mode) {
            case 0:return new double[]{rc[0]-ec[0],rc[1]-ec[1]};
            case 1:return new double[]{rc[0]-ec[0],0};
            case 2:return new double[]{0,rc[1]-ec[1]};
            case 3:return new double[]{r[0]-e[0],0};
            case 4:return new double[]{r[2]-e[2],0};
            case 5:return new double[]{0,r[3]-e[3]};
            case 6:return new double[]{0,r[1]-e[1]};
            default:throw new IllegalArgumentException("Nieznany sposób wyrównania");
        }
    }
    static double[] centre(SketchPrimitive p,double[] b) {
        if((p.type.equals("Okrąg")||p.isEllipse())&&p.vertices==null&&p.curves==null)return new double[]{p.x1,p.y1};
        return new double[]{(b[0]+b[2])/2,(b[1]+b[3])/2};
    }
    /**
     * Aligns the element to the reference and, with lock, keeps the aligned axes with driving
     * position dimensions between a point of the reference and a point of the element.
     */
    public static Result align(List<SketchPrimitive> sketch,List<SketchConstraints.Link> links,String id,String refId,int mode,boolean lock) {
        return align(sketch,links,id,java.util.Collections.singletonList(refId),mode,lock);
    }
    /** Alignment with a group of reference elements (their common box; centre of a single circle). */
    public static Result align(List<SketchPrimitive> sketch,List<SketchConstraints.Link> links,String id,List<String> refIds,int mode,boolean lock) {
        if(refIds.isEmpty())throw new IllegalArgumentException("Brak odniesienia");
        if(refIds.contains(id))throw new IllegalArgumentException("Wybierz inny element jako odniesienie");
        SketchPrimitive element=find(sketch,id),reference=find(sketch,refIds.get(0));String refId=reference.id;
        double[] box=groupBounds(sketch,refIds);
        double[] centre=refIds.size()==1?centre(reference,box):new double[]{(box[0]+box[2])/2,(box[1]+box[3])/2};
        double[] move=alignMove(element,box,centre,mode);
        boolean affectX=mode==0||mode==1||mode==3||mode==4,affectY=mode==0||mode==2||mode==5||mode==6;
        boolean canLock=lock&&SketchConstraints.anchorCount(element)>0&&SketchConstraints.anchorCount(reference)>0;
        if(!canLock) {
            SketchPrimitive moved=SketchTransform.move(element,move[0],move[1]);
            List<SketchPrimitive> geometry=new ArrayList<>(sketch);geometry.set(geometry.indexOf(element),moved);
            List<SketchPrimitive> solved=links.isEmpty()?geometry:SketchConstraints.edit(sketch,links,id,moved);
            return new Result(solved,new ArrayList<>(links),0,0);
        }
        double[] anchorNow=SketchConstraints.anchor(element,0),ref=SketchConstraints.anchor(reference,0);
        double dx=anchorNow[0]+move[0]-ref[0],dy=anchorNow[1]+move[1]-ref[1];
        return place(sketch,links,id,0,refId,0,dx,dy,affectX,affectY);
    }

    public static double[] groupBounds(List<SketchPrimitive> sketch,List<String> ids) {
        double[] out={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(String rid:ids){double[] b=bounds(find(sketch,rid));out[0]=Math.min(out[0],b[0]);out[1]=Math.min(out[1],b[1]);out[2]=Math.max(out[2],b[2]);out[3]=Math.max(out[3],b[3]);}
        return out;
    }
    /** A position dimension that fixes this point of the element (from the origin or from another element). */
    static boolean touches(SketchConstraints.Link l,String id,int anchor) {
        if(!SketchConstraints.position(l.kind))return false;
        if(SketchConstraints.fromOrigin(l))return l.a.equals(id)&&l.ai==anchor;
        if(l.a.equals(id)&&l.b.equals(id))return false;
        return l.a.equals(id)&&l.ai==anchor||l.b.equals(id)&&l.bi==anchor;
    }
}
