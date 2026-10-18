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
    public static double[] spans(double[] p) {
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
}
