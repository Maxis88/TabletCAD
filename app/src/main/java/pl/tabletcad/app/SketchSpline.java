package pl.tabletcad.app;

import java.io.*;
import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Natural cubic interpolant through source points; exact cubic Bezier spans, not a polyline. */
public final class SketchSpline {
    private SketchSpline() {}
    public static boolean is(SketchPrimitive p){return p.type.equals("Splajn");}
    public static double[] points(List<double[]> source) {
        double[] p=new double[source.size()*2];for(int i=0;i<source.size();i++){p[2*i]=source.get(i)[0];p[2*i+1]=source.get(i)[1];}return p;
    }
    /** A spline whose last point returns to the first (within 0.01 mm) is a closed, periodic curve. */
    public static boolean closed(double[] p) {
        return p!=null&&p.length>=8&&p.length%2==0&&Math.hypot(p[0]-p[p.length-2],p[1]-p[p.length-1])<.01;
    }
    /** Closed-profile segments for the kernel: kind 2 (cubic Bezier) per span; each ends where the next starts. */
    public static double[] featureCurves(double[] p) {
        if(!closed(p))throw new IllegalArgumentException("Splajn nie jest zamknięty");
        double[] spans=spans(p);int n=spans.length/8;double[] out=new double[n*7];
        for(int i=0;i<n;i++){int o=8*i,c=7*i;out[c]=2;System.arraycopy(spans,o,out,c+1,6);}
        return out;
    }
    /** Through points of a closed spline rebuilt from kernel segments (start of each span, first repeated). */
    public static double[] fromFeatureCurves(double[] curves) {
        int n=curves.length/7;double[] p=new double[(n+1)*2];
        for(int i=0;i<n;i++){p[2*i]=curves[7*i+1];p[2*i+1]=curves[7*i+2];}
        p[2*n]=p[0];p[2*n+1]=p[1];return p;
    }
    public static boolean hasBezier(double[] curves){if(curves==null)return false;for(int i=0;i<curves.length;i+=7)if(curves[i]==2)return true;return false;}
    public static double[] spans(double[] p) {
        if(closed(p))return periodicSpans(p);
        if(p==null||p.length<6||p.length>256||p.length%2!=0)throw new IllegalArgumentException("Splajn: 3–128 punktów");
        int n=p.length/2;double[][] second=new double[2][n];
        for(int i=0;i<n;i++) {
            for(int k=0;k<2;k++)if(!Double.isFinite(p[2*i+k])||Math.abs(p[2*i+k])>5000)throw new IllegalArgumentException("Punkty splajnu poza ±5000 mm");
            if(i>0&&Math.hypot(p[2*i]-p[2*i-2],p[2*i+1]-p[2*i-1])<.01)throw new IllegalArgumentException("Sąsiednie punkty splajnu muszą być różne");
        }
        for(int k=0;k<2;k++) {
            double[] diagonal=new double[n],rhs=new double[n];
            for(int i=1;i<n-1;i++) {
                diagonal[i]=4;rhs[i]=6*(p[2*(i+1)+k]-2*p[2*i+k]+p[2*(i-1)+k]);
                if(i>1){double t=1/diagonal[i-1];diagonal[i]-=t;rhs[i]-=rhs[i-1]*t;}
            }
            for(int i=n-2;i>0;i--)second[k][i]=(rhs[i]-second[k][i+1])/diagonal[i];
        }
        double[] result=new double[(n-1)*8];
        for(int i=0;i<n-1;i++)for(int k=0;k<2;k++) {
            double a=p[2*i+k],b=p[2*(i+1)+k],ma=second[k][i],mb=second[k][i+1];int o=8*i;
            result[o+k]=a;result[o+2+k]=a+(b-a-(2*ma+mb)/6)/3;
            result[o+4+k]=b-(b-a+(ma+2*mb)/6)/3;result[o+6+k]=b;
        }
        for(double v:result)if(!Double.isFinite(v)||Math.abs(v)>5000)throw new IllegalArgumentException("Krzywa splajnu wychodzi poza ±5000 mm");
        return result;
    }
    public static double[] sample(double[] p,int steps) {
        if(steps<1||steps>256)throw new IllegalArgumentException("Niepoprawna dokładność splajnu");
        double[] spans=spans(p),out=new double[((p.length/2-1)*steps+1)*2];int cursor=0;
        out[cursor++]=p[0];out[cursor++]=p[1];
        for(int o=0;o<spans.length;o+=8)for(int j=1;j<=steps;j++) {
            double t=(double)j/steps,u=1-t;
            for(int k=0;k<2;k++)out[cursor++]=u*u*u*spans[o+k]+3*u*u*t*spans[o+2+k]+3*u*t*t*spans[o+4+k]+t*t*t*spans[o+6+k];
        }return out;
    }
    public static void writeDxf(Writer writer,double[] p)throws IOException {
        double[] spans=spans(p);int count=spans.length/8,controls=3*count+1;
        writer.write("0\nSPLINE\n8\nSKETCH\n70\n8\n71\n3\n72\n"+(controls+4)+"\n73\n"+controls+"\n74\n0\n");
        for(int i=0;i<4;i++)writer.write("40\n0\n");
        for(int i=1;i<count;i++)for(int j=0;j<3;j++)writer.write("40\n"+i+"\n");
        for(int i=0;i<4;i++)writer.write("40\n"+count+"\n");
        writer.write("10\n"+p[0]+"\n20\n"+p[1]+"\n30\n0\n");
        for(int o=0;o<spans.length;o+=8)for(int j=2;j<8;j+=2)writer.write("10\n"+spans[o+j]+"\n20\n"+spans[o+j+1]+"\n30\n0\n");
    }
    /** Periodic C2 cubic through the points (the last point equals the first): one Bezier span per gap. */
    public static double[] periodicSpans(double[] p) {
        int m=p.length/2-1;
        if(m<3||m>127)throw new IllegalArgumentException("Zamknięty splajn: 3–127 różnych punktów");
        for(int i=0;i<m;i++){
            for(int k=0;k<2;k++)if(!Double.isFinite(p[2*i+k])||Math.abs(p[2*i+k])>5000)throw new IllegalArgumentException("Punkty splajnu poza ±5000 mm");
            int j=(i+1)%m;if(Math.hypot(p[2*j]-p[2*i],p[2*j+1]-p[2*i+1])<.01)throw new IllegalArgumentException("Sąsiednie punkty splajnu muszą być różne");
        }
        double[][] second=new double[2][];
        for(int k=0;k<2;k++) {
            double[][] a=new double[m][m+1];
            for(int i=0;i<m;i++){int prev=(i-1+m)%m,next=(i+1)%m;
                a[i][prev]+=1;a[i][i]+=4;a[i][next]+=1;a[i][m]=6*(p[2*next+k]-2*p[2*i+k]+p[2*prev+k]);}
            second[k]=solve(a,m);
        }
        double[] result=new double[m*8];
        for(int i=0;i<m;i++)for(int k=0;k<2;k++) {
            int j=(i+1)%m;double a=p[2*i+k],b=p[2*j+k],ma=second[k][i],mb=second[k][j];int o=8*i;
            result[o+k]=a;result[o+2+k]=a+(b-a-(2*ma+mb)/6)/3;
            result[o+4+k]=b-(b-a+(ma+2*mb)/6)/3;result[o+6+k]=b;
        }
        for(double v:result)if(!Double.isFinite(v)||Math.abs(v)>5000)throw new IllegalArgumentException("Krzywa splajnu wychodzi poza ±5000 mm");
        return result;
    }
    private static double[] solve(double[][] a,int n) {
        for(int c=0;c<n;c++){int pivot=c;for(int r=c+1;r<n;r++)if(Math.abs(a[r][c])>Math.abs(a[pivot][c]))pivot=r;
            double[] t=a[c];a[c]=a[pivot];a[pivot]=t;
            for(int r=c+1;r<n;r++){double f=a[r][c]/a[c][c];if(f==0)continue;for(int k=c;k<=n;k++)a[r][k]-=f*a[c][k];}}
        double[] x=new double[n];for(int r=n-1;r>=0;r--){double v=a[r][n];for(int k=r+1;k<n;k++)v-=a[r][k]*x[k];x[r]=v/a[r][r];}
        return x;
    }
}
