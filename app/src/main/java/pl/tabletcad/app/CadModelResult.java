package pl.tabletcad.app;

import java.util.*;

/** Atomic kernel result: mesh, face provenance and detected cylindrical holes. */
public final class CadModelResult {
    public final double[] mesh;
    public final Map<Integer,Integer> bodies=new HashMap<>(),features=new HashMap<>();
    /** Holes (concave cylinders) and, in {@link #cylinders}, also shafts for external threads. */
    public final List<HoleThread.Cylinder> holes=new ArrayList<>(),cylinders=new ArrayList<>();
    public CadModelResult(double[] packed) {
        if(packed==null||packed.length<1||packed[0]!=Math.floor(packed[0])||packed[0]<13||packed[0]>=packed.length)throw new IllegalArgumentException("Brak poprawnego wyniku OCCT");
        int length=(int)packed[0];mesh=Arrays.copyOfRange(packed,1,1+length);if(!CadMesh.isValid(mesh,false))throw new IllegalArgumentException("Niepoprawna siatka wyniku OCCT");
        int i=1+length;for(;i+2<packed.length;i+=3) {
            if(packed[i]==-8){int count=(int)packed[i+1];i+=3;if(count<0||packed.length-i!=count*15)throw new IllegalArgumentException("Niepoprawna tabela otworów");for(int j=0;j<count;j++){HoleThread.Cylinder c=new HoleThread.Cylinder(packed,i+j*15);cylinders.add(c);if(!c.external)holes.add(c);}i=packed.length;break;}
            if(packed[i]<1||packed[i+1]<1)throw new IllegalArgumentException("Brak tożsamości ściany/bryły");bodies.put((int)packed[i]-1,(int)packed[i+1]);features.put((int)packed[i]-1,(int)packed[i+2]);
        }
        if(i!=packed.length)throw new IllegalArgumentException("Niekompletne odniesienia wyniku");
        for(int j=12;j<mesh.length;j+=13)if(!bodies.containsKey((int)mesh[j]-1))throw new IllegalArgumentException("Trójkąt nie posiada odniesienia do bryły");
    }
}
