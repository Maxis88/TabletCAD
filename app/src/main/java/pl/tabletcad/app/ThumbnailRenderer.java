package pl.tabletcad.app;

import android.graphics.*;

/**
 * Small shaded isometric preview of a tessellated B-Rep body (13 doubles per triangle). Depth
 * buffered with flat arrays only: a threaded model (hundreds of thousands of triangles) no longer
 * runs the app out of memory while its library thumbnail is written.
 */
public final class ThumbnailRenderer {
    private ThumbnailRenderer() {}

    public static Bitmap render(double[] mesh,int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(236,241,244));
        if(mesh==null||mesh.length<13)return bitmap;
        double yaw=Math.toRadians(-35),pitch=Math.toRadians(25),cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX,minZ=minX;
        for(int o=0;o+12<mesh.length;o+=13)for(int v=0;v<3;v++) {
            double x=mesh[o+v*3],y=mesh[o+v*3+1],z=mesh[o+v*3+2];
            double rx=x*cy+z*sy,rz=-x*sy+z*cy,ry=y*cp-rz*sp,fz=y*sp+rz*cp;
            minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,-ry);maxY=Math.max(maxY,-ry);minZ=Math.min(minZ,fz);
        }
        double scale=Math.min((width*.84)/Math.max(1e-9,maxX-minX),(height*.84)/Math.max(1e-9,maxY-minY));
        double ox=width/2.0-(minX+maxX)/2*scale,oy=height/2.0-(minY+maxY)/2*scale;
        DepthRaster raster=new DepthRaster();raster.begin(new Rect(0,0,width,height),1f,1);
        float[] px=new float[3],py=new float[3],pq=new float[3];
        for(int o=0;o+12<mesh.length;o+=13) {
            double nx=mesh[o+9],ny=mesh[o+10],nz=mesh[o+11];
            double nrz=-nx*sy+nz*cy,nfz=ny*sp+nrz*cp;if(!(nfz>1e-7))continue;
            double nrx=nx*cy+nz*sy,nry=ny*cp-nrz*sp,shade=Math.max(0,(-.35*nrx+.55*nry+.76*nfz));
            for(int v=0;v<3;v++) {
                double x=mesh[o+v*3],y=mesh[o+v*3+1],z=mesh[o+v*3+2];
                double rx=x*cy+z*sy,rz=-x*sy+z*cy,ry=y*cp-rz*sp,fz=y*sp+rz*cp;
                px[v]=(float)(rx*scale+ox);py[v]=(float)(-ry*scale+oy);pq[v]=(float)(fz-minZ+1);
            }
            int base=(int)Math.round(150+95*shade);
            raster.triangle(px[0],py[0],pq[0],px[1],py[1],pq[1],px[2],py[2],pq[2],Color.rgb(Math.min(255,base-58),Math.min(255,base+2),Math.min(255,base-6)));
        }
        raster.draw(canvas,new Rect(0,0,width,height));
        return bitmap;
    }
}
