package pl.tabletcad.app;

import android.graphics.*;
import java.util.*;

/** Small shaded isometric preview of a tessellated B-Rep body (13 doubles per triangle). */
public final class ThumbnailRenderer {
    private ThumbnailRenderer() {}

    public static Bitmap render(double[] mesh,int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(236,241,244));
        if(mesh==null||mesh.length<13)return bitmap;
        double yaw=Math.toRadians(-35),pitch=Math.toRadians(25),cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        int count=mesh.length/13;float[][] xs=new float[count][3],ys=new float[count][3];double[] depth=new double[count];boolean[] front=new boolean[count];double[] shade=new double[count];
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(int t=0;t<count;t++) {
            int o=t*13;double nx=mesh[o+9],ny=mesh[o+10],nz=mesh[o+11];
            double nrz=-nx*sy+nz*cy,nfz=ny*sp+nrz*cp;front[t]=nfz>1e-7;
            // Light from upper left, slightly toward the viewer.
            double nrx=nx*cy+nz*sy,nry=ny*cp-nrz*sp;shade[t]=Math.max(0,(-.35*nrx+.55*nry+.76*nfz));
            for(int v=0;v<3;v++) {
                double x=mesh[o+v*3],y=mesh[o+v*3+1],z=mesh[o+v*3+2];
                double rx=x*cy+z*sy,rz=-x*sy+z*cy,ry=y*cp-rz*sp,fz=y*sp+rz*cp;
                xs[t][v]=(float)rx;ys[t][v]=(float)-ry;depth[t]+=fz/3;
                minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,-ry);maxY=Math.max(maxY,-ry);
            }
        }
        double scale=Math.min((width*.84)/Math.max(1e-9,maxX-minX),(height*.84)/Math.max(1e-9,maxY-minY));
        double ox=width/2.0-(minX+maxX)/2*scale,oy=height/2.0-(minY+maxY)/2*scale;
        Integer[] order=new Integer[count];for(int i=0;i<count;i++)order[i]=i;
        Arrays.sort(order,(a,b)->Double.compare(depth[a],depth[b]));
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);fill.setStyle(Paint.Style.FILL_AND_STROKE);fill.setStrokeWidth(.6f);
        Path path=new Path();
        for(int t:order) {
            if(!front[t])continue;
            int base=(int)Math.round(150+95*shade[t]);
            fill.setColor(Color.rgb(Math.min(255,base-58),Math.min(255,base+2),Math.min(255,base-6)));
            path.reset();path.moveTo((float)(xs[t][0]*scale+ox),(float)(ys[t][0]*scale+oy));
            path.lineTo((float)(xs[t][1]*scale+ox),(float)(ys[t][1]*scale+oy));path.lineTo((float)(xs[t][2]*scale+ox),(float)(ys[t][2]*scale+oy));path.close();
            canvas.drawPath(path,fill);
        }
        return bitmap;
    }
}
