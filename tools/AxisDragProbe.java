import pl.tabletcad.app.AxisDrag;
import java.util.Random;

public final class AxisDragProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args) {
        Random random=new Random(94721);int verified=0;
        for(int i=0;i<1200;i++) {
            double[] o={random.nextDouble()*50-25,random.nextDouble()*50-25,random.nextDouble()*50-25};
            double[] n={random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5};
            double l=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);for(int j=0;j<3;j++)n[j]/=l;
            double[] center={3,-4,8};double yaw=random.nextDouble()*360-180,pitch=random.nextDouble()*160-80,t=random.nextDouble()*100-50;
            double x=o[0]+t*n[0]-center[0],y=o[1]+t*n[1]-center[1],z=o[2]+t*n[2]-center[2];
            double a=Math.toRadians(yaw),b=Math.toRadians(pitch),rx=x*Math.cos(a)+z*Math.sin(a),rz=-x*Math.sin(a)+z*Math.cos(a);
            double ry=y*Math.cos(b)-rz*Math.sin(b),depth=y*Math.sin(b)+rz*Math.cos(b),scale=2+random.nextDouble()*10;
            double sx=900+rx*420/(420-depth)*scale,sy=600-ry*420/(420-depth)*scale;
            double actual=AxisDrag.parameter(o,n,center,yaw,pitch,scale,900,600,sx,sy);
            if(!Double.isFinite(actual)||Math.abs(actual-t)>1e-6)throw new AssertionError("axis projection "+i+": "+actual+" != "+t);verified++;
        }
        check(verified==1200,"1200 deterministic arbitrary axes and camera orientations recover exact parameter");
        check(Double.isNaN(AxisDrag.parameter(new double[3],new double[]{0,0,1},new double[3],0,0,4,900,600,900,600)),"camera-parallel axis is explicitly ambiguous");
        check(Double.isNaN(AxisDrag.parameter(new double[3],new double[3],new double[3],20,30,4,900,600,940,630)),"zero axis rejected");
        check(Double.isNaN(AxisDrag.parameter(new double[3],new double[]{1,0,0},new double[3],0,0,0,900,600,940,630)),"invalid scale rejected");
        System.out.println("ALL AXIS DRAG TESTS PASSED");
    }
}
