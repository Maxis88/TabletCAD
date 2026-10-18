package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Shape-preserving sketch topology edits; callers protect dependent solid features. */
public final class SketchEditing {
    private SketchEditing() {}

    public static SketchPrimitive toggleConstruction(SketchPrimitive source) {
        if(!source.type.equals("Linia")&&!source.type.equals("Pomocnicza"))
            throw new IllegalArgumentException("Wybierz linię lub najpierw rozbij figurę na odcinki");
        return new SketchPrimitive(source.id,source.type.equals("Linia")?"Pomocnicza":"Linia",
            source.x1,source.y1,source.x2,source.y2,source.driving);
    }

    public static List<SketchPrimitive> explode(SketchPrimitive source) {
        if(SketchSpline.is(source))throw new IllegalArgumentException("Splajn zachowuje krzywiznę; edytuj jego punkty");
        if(source.curves!=null) {
            if(!source.closedProfile())throw new IllegalArgumentException("Łuk jest już pojedynczym elementem");
            List<SketchPrimitive> result=new ArrayList<>();
            for(int i=0;i<source.curves.length;i+=7) {
                double[] s=Arrays.copyOfRange(source.curves,i,i+7);String id=i==0?source.id:UUID.randomUUID().toString();
                result.add(s[0]==1?new SketchPrimitive(id,s,false):new SketchPrimitive(id,"Linia",s[1],s[2],s[5],s[6]));
            }
            return result;
        }
        if(source.driving!=null)throw new IllegalArgumentException("Najpierw odblokuj wymiary figury");
        double[] vertices=source.vertices;
        if(vertices==null) {
            if(!source.type.equals("Prostokąt"))throw new IllegalArgumentException("Rozbijanie obsługuje prostokąty i zamknięte polilinie");
            vertices=new double[]{source.x1,source.y1,source.x2,source.y1,source.x2,source.y2,source.x1,source.y2};
        }
        SketchContours.validate(vertices);
        List<SketchPrimitive> result=new ArrayList<>();
        for(int i=0;i<vertices.length;i+=2) {
            int next=(i+2)%vertices.length;
            result.add(new SketchPrimitive(i==0?source.id:UUID.randomUUID().toString(),"Linia",
                vertices[i],vertices[i+1],vertices[next],vertices[next+1]));
        }
        return result;
    }
}
