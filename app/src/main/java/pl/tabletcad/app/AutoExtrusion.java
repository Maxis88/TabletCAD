package pl.tabletcad.app;

import pl.tabletcad.app.model.CadProject;

/** Exact Boolean classification of an extrusion tool against its explicitly selected body. */
public final class AutoExtrusion {
    private AutoExtrusion() {}
    public static double[] packed(CadProject p) {
        int n=0;for(double[] f:p.committedFeatures)n+=f.length;
        double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}
        return BodyGraph.pack(p,raw);
    }
    /** Candidate has independent new tools; changes them into add/cut while retaining their world extent. */
    public static String resolve(CadProject candidate,int firstFeature,int target,String source) {
        double[] packed=packed(candidate);boolean add=false,cut=false;
        boolean[] cuts=new boolean[candidate.committedFeatures.size()-firstFeature];
        for(int i=firstFeature;i<candidate.committedFeatures.size();i++) {
            int tool=candidate.featureBodyOutputs.get(i);
            double[] distance=NativeCadEngine.bodyDistance(candidate.width,candidate.height,candidate.depth,candidate.importedStep?0:candidate.filletRadius,candidate.featureBase,packed,source,target,tool);
            if(distance.length<8)throw new IllegalArgumentException("Nie można rozpoznać styku wyciągnięcia z bryłą: "+NativeCadEngine.lastFailure().reason);
            boolean inside=distance[7]>1e-7;
            if(!inside&&distance[0]>1e-6)throw new IllegalArgumentException("Profil nie styka się z wybraną bryłą. Wybierz inną bryłę albo ręcznie wybierz Nowa bryła.");
            cuts[i-firstFeature]=inside;if(inside)cut=true;else add=true;
        }
        for(int i=firstFeature;i<candidate.committedFeatures.size();i++) {
            double[] f=candidate.committedFeatures.get(i).clone();boolean inside=cuts[i-firstFeature];
            f[1]=FeatureDirection.featureCode(inside?"cut":"add",FeatureDirection.isSymmetric(f[1]));
            if(inside){f[6]=-f[6];double[] range=candidate.extrusionExtents.get(i);if(range!=null)range[0]=-range[0];}
            candidate.committedFeatures.set(i,f);candidate.featureBodyTargets.set(i,target);candidate.featureBodyOutputs.set(i,target);
        }
        return cut&&add?"Połącz / Wytnij (według profilu)":cut?"Wytnij":"Połącz z bryłą";
    }
}
