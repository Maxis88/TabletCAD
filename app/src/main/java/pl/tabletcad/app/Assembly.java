package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Basic assemblies: components (groups of bodies), associative instances (BodyInstance records),
 * face-to-face placement ("Dosuń ściany"), interference list and a bill of materials.
 *
 * <p>An instance transform is stored as x' = R·x + t with R about an axis through the origin
 * (BodyInstance with point 0), so placements compose exactly.</p>
 */
public final class Assembly {
    private Assembly() {}

    /** Removes bodies that no longer exist and empty instance components. */
    /** Removes persistent mates that move one of the bodies or hold another body against it; returns how many. */
    public static int removeMates(CadProject p,Collection<Integer> bodies) {
        int removed=0;
        for(int j=p.committedFeatures.size()-1;j>=0;j--){double[] f=p.committedFeatures.get(j);if(!MateFeature.is(f))continue;
            int moved=j<p.featureBodyTargets.size()?p.featureBodyTargets.get(j):-1;
            if(bodies.contains(moved)||bodies.contains(MateFeature.other(f))){FeatureRemoval.apply(p,FeatureRemoval.plan(p,j));removed++;}}
        return removed;
    }
    public static void prune(CadProject p) {
        Set<Integer> alive=BodyGraph.bodies(p);
        for(Component c:p.components)c.bodies.retainAll(alive);
        p.components.removeIf(c->c.instance()&&c.bodies.isEmpty());
    }
    /** Creates a component of the given bodies (moved out of any other component). */
    public static Component group(CadProject p,String name,Collection<Integer> bodies) {
        if(bodies.isEmpty())throw new IllegalArgumentException("Wybierz co najmniej jedną bryłę komponentu");
        Set<Integer> alive=BodyGraph.bodies(p);for(int b:bodies)if(!alive.contains(b))throw new IllegalArgumentException("Bryła "+b+" nie istnieje");
        for(Component c:p.components){if(c.instance()){for(int b:bodies)if(c.bodies.contains(b))throw new IllegalArgumentException("Bryła wystąpienia należy do swojego komponentu");}c.bodies.removeAll(bodies);}
        Component c=Component.create(name==null||name.trim().isEmpty()?Component.nextName(p.components):name);c.bodies.addAll(bodies);p.components.add(c);
        p.components.removeIf(x->x!=c&&!x.instance()&&x.bodies.isEmpty());return c;
    }
    /** Bodies a component stands for: its own, or for a single body without a component that body. */
    public static Component find(CadProject p,String id){for(Component c:p.components)if(c.id.equals(id))return c;return null;}

    // ---- rigid transforms (3×3 rotation row-major + translation) ----
    static double[] matrix(double[] axis,double degrees) {
        double l=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);double[] m={1,0,0,0,1,0,0,0,1};if(!(l>1e-12)||degrees==0)return m;
        double x=axis[0]/l,y=axis[1]/l,z=axis[2]/l,a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a),t=1-c;
        return new double[]{t*x*x+c,t*x*y-s*z,t*x*z+s*y, t*x*y+s*z,t*y*y+c,t*y*z-s*x, t*x*z-s*y,t*y*z+s*x,t*z*z+c};
    }
    static double[] multiply(double[] a,double[] b){double[] r=new double[9];for(int i=0;i<3;i++)for(int j=0;j<3;j++){double v=0;for(int k=0;k<3;k++)v+=a[i*3+k]*b[k*3+j];r[i*3+j]=v;}return r;}
    public static double[] apply(double[] m,double[] v){return new double[]{m[0]*v[0]+m[1]*v[1]+m[2]*v[2],m[3]*v[0]+m[4]*v[1]+m[5]*v[2],m[6]*v[0]+m[7]*v[1]+m[8]*v[2]};}
    /** Axis (unit) and angle [°] of a rotation matrix; zero axis for the identity. */
    static double[] axisAngle(double[] m) {
        double trace=m[0]+m[4]+m[8],cos=Math.max(-1,Math.min(1,(trace-1)/2)),angle=Math.acos(cos);
        if(angle<1e-12)return new double[]{0,0,0,0};
        double[] axis;
        if(Math.PI-angle>1e-6){axis=new double[]{m[7]-m[5],m[2]-m[6],m[3]-m[1]};}
        else {// 180°: axis from the largest diagonal term
            double xx=(m[0]+1)/2,yy=(m[4]+1)/2,zz=(m[8]+1)/2;
            if(xx>=yy&&xx>=zz){double x=Math.sqrt(xx);axis=new double[]{x,m[1]/(2*x),m[2]/(2*x)};}
            else if(yy>=zz){double y=Math.sqrt(yy);axis=new double[]{m[1]/(2*y),y,m[5]/(2*y)};}
            else {double z=Math.sqrt(zz);axis=new double[]{m[2]/(2*z),m[5]/(2*z),z};}
        }
        double l=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);return new double[]{axis[0]/l,axis[1]/l,axis[2]/l,Math.toDegrees(angle)};
    }
    /** Rotation and translation of an instance record as {R(9), t(3)}. */
    static double[][] placement(double[] f) {
        double[] r=matrix(new double[]{f[5],f[6],f[7]},f[8]),p={f[9],f[10],f[11]},rp=apply(r,p);
        return new double[][]{r,{p[0]-rp[0]+f[2],p[1]-rp[1]+f[3],p[2]-rp[2]+f[4]}};
    }
    static double[] record(int created,double[] r,double[] t) {
        double[] aa=axisAngle(r);
        return BodyInstance.create(created,t,new double[]{aa[0],aa[1],aa[2]},aa[3],new double[3]);
    }
    /** delta ∘ record: applies the placement of {@code delta} after the current placement of {@code f}. */
    public static double[] compose(double[] delta,double[] f) {
        double[][] d=placement(delta),o=placement(f);double[] r=multiply(d[0],o[0]),t=apply(d[0],o[1]);
        return record(BodyInstance.created(f),r,new double[]{t[0]+d[1][0],t[1]+d[1][1],t[2]+d[1][2]});
    }
    /** Instance record for an explicit placement: rotation about an axis through a point, then translation. */
    public static double[] instance(int created,double[] translation,double[] axis,double angle,double[] point) {
        double[] r=matrix(axis,angle),rp=apply(r,point);
        return record(created,r,new double[]{point[0]-rp[0]+translation[0],point[1]-rp[1]+translation[1],point[2]-rp[2]+translation[2]});
    }

    /**
     * Face-to-face placement: the instance moves so its planar face (point, outward normal, both in the
     * current placed position) lies on the target face with opposite normals and the given gap.
     */
    public static double[] mate(double[] current,double[] movingPoint,double[] movingNormal,double[] targetPoint,double[] targetNormal,double gap) {
        double[] m=unit(movingNormal),t=unit(targetNormal),want={-t[0],-t[1],-t[2]};
        double cos=Math.max(-1,Math.min(1,m[0]*want[0]+m[1]*want[1]+m[2]*want[2]));double[] axis=cross(m,want);
        if(Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2])<1e-12)axis=cos>0?new double[]{0,0,0}:perpendicular(m);
        double angle=cos>1-1e-15?0:Math.toDegrees(Math.acos(cos));
        double[] rd=matrix(axis,angle);
        // Rotate about the moving face point, then slide along the target normal onto the target plane (+ gap).
        double[] rotatedPoint=movingPoint.clone();
        double distance=(rotatedPoint[0]-targetPoint[0])*t[0]+(rotatedPoint[1]-targetPoint[1])*t[1]+(rotatedPoint[2]-targetPoint[2])*t[2];
        double[] td={-t[0]*(distance-gap),-t[1]*(distance-gap),-t[2]*(distance-gap)};
        double[][] old=placement(current);double[] r=multiply(rd,old[0]);
        // x' = Rd(R x + t0 − pm) + pm + td
        double[] shifted={old[1][0]-movingPoint[0],old[1][1]-movingPoint[1],old[1][2]-movingPoint[2]},rs=apply(rd,shifted);
        double[] tr={rs[0]+movingPoint[0]+td[0],rs[1]+movingPoint[1]+td[1],rs[2]+movingPoint[2]+td[2]};
        return record(BodyInstance.created(current),r,tr);
    }
    /**
     * Rigid motion {R(9), t(3)} (x' = R·x + t) placing a moving planar face on a target face: opposite
     * normals (contact) or, with {@code flush}, equal normals (faces in one plane), at the given gap.
     */
    public static double[][] planeDelta(double[] mp,double[] mn,double[] tp,double[] tn,double gap,boolean flush) {
        double[] m=unit(mn),t=unit(tn),want=flush?t.clone():new double[]{-t[0],-t[1],-t[2]};
        double[] rd=turn(m,want);
        double distance=(mp[0]-tp[0])*t[0]+(mp[1]-tp[1])*t[1]+(mp[2]-tp[2])*t[2],k=-(distance-gap);
        double[] rp=apply(rd,mp);
        return new double[][]{rd,{mp[0]-rp[0]+t[0]*k,mp[1]-rp[1]+t[1]*k,mp[2]-rp[2]+t[2]*k}};
    }
    /** Rigid motion making the moving axis (point, direction) coincide with the target axis (coaxial). */
    public static double[][] axisDelta(double[] mp,double[] md,double[] tp,double[] td) {
        double[] m=unit(md),d=unit(td);if(m[0]*d[0]+m[1]*d[1]+m[2]*d[2]<0)m=new double[]{-m[0],-m[1],-m[2]};
        double[] rd=turn(m,d),rp=apply(rd,mp),v={tp[0]-mp[0],tp[1]-mp[1],tp[2]-mp[2]};double along=v[0]*d[0]+v[1]*d[1]+v[2]*d[2];
        return new double[][]{rd,{mp[0]-rp[0]+v[0]-along*d[0],mp[1]-rp[1]+v[1]-along*d[1],mp[2]-rp[2]+v[2]-along*d[2]}};
    }
    /** Rotation taking unit vector a onto unit vector b (180° about a perpendicular axis when opposite). */
    static double[] turn(double[] a,double[] b) {
        double cos=Math.max(-1,Math.min(1,a[0]*b[0]+a[1]*b[1]+a[2]*b[2]));double[] axis=cross(a,b);
        if(Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2])<1e-12){if(cos>0)return matrix(new double[3],0);axis=perpendicular(a);}
        return matrix(axis,Math.toDegrees(Math.acos(cos)));
    }
    /** Instance record moved by a rigid delta. */
    public static double[] composeDelta(double[][] delta,double[] f){return compose(record(BodyInstance.created(f),delta[0],delta[1]),f);}
    /** History records (rotation about the origin, then translation) applying a rigid delta to an ordinary body. */
    public static List<double[]> transformRecords(double[][] delta) {
        List<double[]> r=new ArrayList<>();double[] aa=axisAngle(delta[0]);
        if(Math.abs(aa[3])>1e-9)r.add(BodyTransform.rotate(new double[3],new double[]{aa[0],aa[1],aa[2]},aa[3]));
        double[] t=delta[1];if(Math.abs(t[0])+Math.abs(t[1])+Math.abs(t[2])>1e-9)r.add(BodyTransform.move(t[0],t[1],t[2]));
        return r;
    }
    static double[] unit(double[] v){double l=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);if(!(l>1e-12))throw new IllegalArgumentException("Zerowa normalna ściany");return new double[]{v[0]/l,v[1]/l,v[2]/l};}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double[] perpendicular(double[] n){double[] a=Math.abs(n[0])<.9?new double[]{1,0,0}:new double[]{0,1,0};return unit(cross(n,a));}

    /** History indices of the instance records that created the bodies of a component. */
    public static List<Integer> instanceRecords(CadProject p,Component c) {
        List<Integer> r=new ArrayList<>();for(int i=0;i<p.committedFeatures.size();i++){double[] f=p.committedFeatures.get(i);if(BodyInstance.is(f)&&c.bodies.contains(BodyInstance.created(f)))r.add(i);}return r;
    }

    /** Bill of materials rows: component, body, source, volume, density, mass. */
    public static String bom(CadProject p,List<BodyAnalysis.Body> bodies,boolean csv) {
        StringBuilder out=new StringBuilder(csv?"Komponent;Bryła;Wystąpienie;Objętość [mm³];Gęstość [kg/m³];Masa [kg]\n":"");
        Map<String,Integer> counts=new LinkedHashMap<>();double total=0;boolean allMass=true;
        for(BodyAnalysis.Body b:bodies) {
            Component c=Component.of(p.components,b.id);String component=c==null?"—":c.name;String source=c!=null&&c.instance()?Optional.ofNullable(find(p,c.instanceOf)).map(x->x.name).orElse("?"):"";
            String mass=b.hasMass()?String.format(Locale.US,"%.6f",b.mass()):"";if(b.hasMass())total+=b.mass();else allMass=false;
            if(csv)out.append(component).append(';').append(BodyGraph.name(p,b.id)).append(';').append(source).append(';').append(String.format(Locale.US,"%.3f",b.volume)).append(';').append(b.hasMass()?String.format(Locale.US,"%.1f",b.density):"").append(';').append(mass).append('\n');
            String key=c==null?BodyGraph.name(p,b.id):c.instance()?source:c.name;counts.merge(key,1,Integer::sum);
        }
        if(csv)return out.toString();
        out.append("Pozycje (komponent/bryła × liczba sztuk):\n");
        for(Map.Entry<String,Integer> e:counts.entrySet())out.append("· ").append(e.getKey()).append(" × ").append(e.getValue()).append('\n');
        out.append("\nBrył razem: ").append(bodies.size());
        if(allMass&&!bodies.isEmpty())out.append(String.format(Locale.US,"\nMasa całkowita: %.6f kg",total));
        return out.toString();
    }
}
