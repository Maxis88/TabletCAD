package pl.tabletcad.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.InputType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;
import pl.tabletcad.app.storage.ProjectRepository;

/**
 * Touch-first CAD viewport backed by tessellated Open CASCADE B-Rep geometry.
 */
public final class CadWorkspaceView extends View {
    private int bottomSystemInset;

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        bottomSystemInset = insets.getInsets(WindowInsets.Type.navigationBars()
            | WindowInsets.Type.mandatorySystemGestures()).bottom;
        invalidate();
        return super.onApplyWindowInsets(insets);
    }
    public interface FileRequestListener {
        void requestExport(String format);
        void requestOpenStep();
    }
    private static final int BG = Color.rgb(242, 245, 247);
    private static final int PANEL = Color.rgb(255, 255, 255);
    private static final int INK = Color.rgb(28, 39, 49);
    private static final int MUTED = Color.rgb(101, 116, 130);
    private static final int ACCENT = Color.rgb(13, 148, 136);
    private static final int GRID = Color.rgb(220, 227, 232);
    // Chrome palette: graphite bars around a light work area, teal accent for the active state.
    private static final int CHROME = Color.rgb(20, 26, 33);
    private static final int CHROME_RAISED = Color.rgb(33, 42, 52);
    private static final int CHROME_TEXT = Color.rgb(196, 206, 214);
    private static final int DIVIDER = Color.rgb(214, 222, 228);
    /** Layout tokens: one 64 dp top bar (project + tools + files), collapsible model tree. */
    private static final float TOP_BAR_DP = 64f, TOOLBAR_X_DP = 224f;
    private boolean treeCollapsed;
    private float topBar() { return dp(TOP_BAR_DP); }
    private String appVersion;
    /** Installed application version, e.g. "0.64.0" (shown in the top bar and project menu). */
    String appVersion() {
        if (appVersion == null) {
            try {
                String name = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
                int dash = name == null ? -1 : name.indexOf('-');
                appVersion = name == null ? "?" : dash > 0 ? name.substring(0, dash) : name;
            } catch (Exception error) { appVersion = "?"; }
        }
        return appVersion;
    }
    private float viewTop() { return dp(TOP_BAR_DP + 2); }
    private float leftWidth() { return dp(treeCollapsed ? 40 : 224); }
    private float toolbarX() { return dp(TOOLBAR_X_DP); }
    private float treeTop() { return dp(TOP_BAR_DP + 76); }
    /** Screen Y of the centre of model-tree row {@code index} (used by touch tests). */
    float treeRowCenterY(int index) { return treeTop() + index * dp(36) + dp(18) - treeScroll; }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleGestures;

    private float density;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private float cameraYaw = -28f;
    private float cameraPitch = 22f;
    private String mode = "Wybierz";
    private final String kernelStatus;
    private double[] nativePrism = new double[0];
    private double[] nativeMesh = new double[0];
    private java.util.Map<Integer,Integer> faceBodies=new java.util.HashMap<>(),faceFeatures=new java.util.HashMap<>();
    private int highlightBody,highlightFeature=Integer.MIN_VALUE;
    private List<HoleThread.Cylinder> holeCylinders=new ArrayList<>();
    private final java.util.Set<Integer> threadSelections=new java.util.LinkedHashSet<>();
    /** Faces removed by the shell being created or re-picked, keyed by display face index. */
    private final java.util.Map<Integer,ShellFeature.Face> shellSelections=new java.util.LinkedHashMap<>();
    private int shellEditIndex=-1;
    private final java.util.Map<Integer,ShellFeature.Face> draftSelections=new java.util.LinkedHashMap<>();private int draftEditIndex=-1;
    /** Face reference (point xyz, outward normal xyz, creator tag) of the hole being placed. */
    private double[] holeReference;private int holeEditIndex=-1;
    private GeometryPreviewWorker modelCommitWorker;private boolean modelOperationPending;
    private final java.util.Set<Integer> collapsedBodies=new java.util.HashSet<>();
    private List<ModelTree.Row> modelTreeRows=new ArrayList<>();
    private String selectedTreeKey="";private float treeScroll;
    private long treeLastTap;
    private TechnicalSheet technicalSheet;
    private String modelInfo = "Nie można odtworzyć geometrii dokumentu";
    private String operationMessage = "";
    private final String stepStatus;
    private double modelWidth = 80.0;
    private double modelHeight = 48.0;
    private double modelDepth = 32.0;
    private double meshCenterX;
    private double meshCenterY;
    private double meshCenterZ;
    private RectF sketchRect;
    private float sketchStartX;
    private float sketchStartY;
    private float sketchEndX, sketchEndY;
    private PointF snapMarker;
    private boolean sketching;
    private String sketchTool = "Prostokąt";
    private final ProjectRepository projectRepository;
    private CadProject project;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private final FileRequestListener fileRequestListener;
    private boolean sketchPlaneChosen;
    private String sketchPlane = "Nie wybrano";
    private int selectedFace = -1;
    private double selectedNormalX;
    private double selectedNormalY;
    private double selectedNormalZ = 1.0;
    private double selectedPointX;
    private double selectedPointY;
    private double selectedPointZ;
    private final List<ProjectedTriangle> projectedTriangles = new ArrayList<>();
    /** Back-facing triangles, collected only for the edge and transparent display modes. */
    private final List<ProjectedTriangle> backTriangles = new ArrayList<>();
    /** View-only display style; never changes the document or the B-Rep. */
    private static final int DISPLAY_SHADED=0,DISPLAY_EDGES=1,DISPLAY_TRANSPARENT=2;
    private int displayMode=DISPLAY_SHADED;
    /** View-only section plane (point xyz, normal xyz) and its OCCT result for the current mesh. */
    private double[] sectionPlane;private CadModelResult sectionResult;private double[] sectionSource;private GeometryPreviewWorker sectionWorker;
    private boolean showingSection(){return sectionPlane!=null&&sectionResult!=null&&featurePreview==null;}
    private final List<HiddenLineVisibility.Triangle> depthSurfaces = new HiddenLineVisibility.Index();
    private DisplayMeshTopology displayTopology;
    private final List<ProjectedTriangle> projectionPool=new ArrayList<>();
    private float[] surfaceVertices=new float[0];
    private int[] surfaceColors=new int[0];
    private MeasurePoint measureA;
    private MeasurePoint measureB;
    private double[] measureEdges=new double[0],measureProperties=new double[0],measuredCurve;
    private boolean measurePointMode;
    private android.widget.TextView measurementOutput;
    private String measuredEdgeText = "";
    private float touchDownX;
    private float touchDownY;
    private float workCenterX;
    private float workCenterY;
    private String selectedSketchId;
    private SketchPrimitive draggedSketch, dragPreview;
    private List<SketchPrimitive> dragSolvedPreview;
    private double dragStartU, dragStartV;
    private int dragHandle;
    private boolean dragWasSelected;
    private String treeSelection = "Model";
    private boolean showSolid = true;
    private boolean trimMode;
    private boolean cornerFilletPicking;private String cornerFirst;
    private boolean dimensionMode;
    private boolean coincidenceMode;
    private String coincidenceFirst;
    private int coincidenceAnchor;
    private String constraintKind="coincident";
    private boolean dragConstraintRejected;
    private boolean polylineTouch;
    private double[] polylinePreview;
    private android.widget.ScrollView propertyEditor;
    private android.app.AlertDialog projectChooser;
    private double[] featurePreview;
    private double[] filletEdges=new double[0];
    private final java.util.Map<Integer,Integer> filletSelections=new java.util.LinkedHashMap<>();
    private Runnable filletSelectionChanged;
    private android.widget.HorizontalScrollView nativeToolbar;
    private android.widget.PopupWindow toolbarPopup;
    private String toolbarState="";
    private boolean toolbarUpdatePosted;
    private float timelineScroll;
    private boolean showSketchDimensions = true;
    private int selectedTimelineIndex = -1;
    private long lastTimelineTap;
    private long lastSketchTreeTap;
    private boolean highlightSketch;
    private boolean sketchVisible = true;
    private boolean showExtrusionSketch;
    private final java.util.Set<String> extrusionSelections=new java.util.LinkedHashSet<>();
    private Runnable extrusionSelectionChanged;
    /** Waiting for a target face tap ("Do powierzchni"); receives the signed plane distance. */
    private java.util.function.DoubleConsumer extrusionFacePick;
    /** One-finger drag orbits the view instead of drawing while a sketch is active. */
    private boolean sketchOrbit;
    private static final class DimensionHit {
        final String entityId,kind;final int segment;final RectF bounds;
        DimensionHit(String id,String kind,int segment,RectF bounds){this.entityId=id;this.kind=kind;this.segment=segment;this.bounds=bounds;}
        String key(){return entityId+":"+kind+":"+segment;}
    }
    private final List<DimensionHit> dimensionHits=new ArrayList<>();
    private DimensionHit dimensionTouch,lastDimensionHit;
    private long lastDimensionTime;
    private float dimensionTouchX,dimensionTouchY;
    private DimensionHit drawingDimension;
    private GeometryPreviewWorker geometryPreviewWorker;
    private boolean previewViewDetached;
    private EditText extrusionDimension;
    private android.widget.CheckBox extrusionReverse,extrusionSymmetric;
    private double[] extrusionOrigin,extrusionAxis;
    private PointF extrusionHandle;
    private final RectF extrusionValueBounds=new RectF();
    private Runnable extrusionPreview;
    private boolean extrusionTouch,extrusionCancelled,extrusionPreviewScheduled;
    private double extrusionStartParameter,extrusionStartDepth;
    private final Runnable extrusionPreviewTask=()-> {
        extrusionPreviewScheduled=false;
        if(extrusionPreview!=null){removeCallbacks(extrusionPreview);extrusionPreview.run();}
    };

    private boolean propertiesVisible() {
        return propertyEditor != null || inSketch() || mode.equals("Zmierz") || mode.equals("Rysunek");
    }

    private float viewportRight() { return getWidth() - (propertiesVisible() ? dp(252) : 0); }

    private float maxTimelineScroll() {
        return Math.max(0, leftWidth() + dp(116) + CadTimeline.entries(project).size() * dp(64) - viewportRight());
    }
    private final List<double[]> arcDraft=new ArrayList<>();
    private SketchPrimitive threePointPreview;
    private double[] arcPreview;
    private List<SketchPrimitive> modelSnapGeometry;

    private boolean inSketch() { return mode.equals("Szkic") || mode.equals("Edytuj szkic"); }

    /** One finger on empty space orbits in every mode except while drawing sketch geometry. */
    private boolean orbitAllowed() {
        if(mode.equals("Szkic"))return !sketchPlaneChosen||sketchOrbit;
        return !mode.equals("Rysunek");
    }

    /** Target face tap for "Do powierzchni": picks the committed model, never the preview. */
    private void pickExtrusionFace(float x,float y) {
        double[] preview=featurePreview;featurePreview=null;projectNativeMesh(workCenterX,workCenterY);
        try {
            int face=findFaceAt(x,y);
            if(face<0){operationMessage="Dotknij ściany bryły, do której ma sięgać wyciągnięcie";return;}
            List<Double> points=new ArrayList<>();
            for(ProjectedTriangle t:projectedTriangles)if(t.faceId==face)for(int i=0;i<3;i++){points.add(t.wx[i]+meshCenterX);points.add(t.wy[i]+meshCenterY);points.add(t.wz[i]+meshCenterZ);}
            double[] vertices=new double[points.size()];for(int i=0;i<vertices.length;i++)vertices[i]=points.get(i);
            double distance=ExtrudeToFace.distance(new double[]{project.sketchOriginX,project.sketchOriginY,project.sketchOriginZ},
                new double[]{project.sketchUx,project.sketchUy,project.sketchUz},new double[]{project.sketchVx,project.sketchVy,project.sketchVz},vertices);
            java.util.function.DoubleConsumer target=extrusionFacePick;extrusionFacePick=null;selectedFace=face;target.accept(distance);
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        finally {featurePreview=preview;invalidate();}
    }

    private float topToolWidth() {return dp(64);}

    /** Clear visual picks without changing the document or leaving an active tool. */
    private void clearViewportSelection() {
        selectedFace=-1;selectedSketchId=null;highlightBody=0;highlightFeature=Integer.MIN_VALUE;
        highlightSketch=false;selectedTreeKey="";selectedTimelineIndex=-1;
        measureA=measureB=null;measuredCurve=null;measuredEdgeText="";
        coincidenceFirst=null;cornerFirst=null;
        threadSelections.clear();extrusionSelections.clear();filletSelections.clear();shellSelections.clear();draftSelections.clear();
        if(mode.equals("Gwint"))showThreadPanel();
        else if(mode.equals("Powłoka"))showShellPanel();
        else if(mode.equals("Pochyl ściany"))showDraftPanel();
        else if(extrusionSelectionChanged!=null)extrusionSelectionChanged.run();
        else if(filletSelectionChanged!=null)filletSelectionChanged.run();
        else if(mode.equals("Zmierz"))updateMeasurementOutput();
        else closePropertyEditor();
        operationMessage="Odznaczono wszystkie elementy";invalidate();
    }

    private void closePropertyEditor() {
        showExtrusionSketch=false;extrusionSelectionChanged=null;extrusionFacePick=null;
        invalidateGeometryPreview();
        removeCallbacks(extrusionPreviewTask);extrusionPreviewScheduled=false;
        extrusionDimension=null;extrusionReverse=extrusionSymmetric=null;
        extrusionOrigin=extrusionAxis=null;extrusionHandle=null;extrusionPreview=null;
        extrusionTouch=false;extrusionValueBounds.setEmpty();
        featurePreview=null;
        measurementOutput=null;
        filletEdges=new double[0];filletSelections.clear();filletSelectionChanged=null;
        if(propertyEditor!=null) {
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(getWindowToken(),0);
        }
        if (propertyEditor != null && propertyEditor.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)propertyEditor.getParent()).removeView(propertyEditor);
        propertyEditor = null;
        invalidate();
    }

    /** Non-modal native property form: leaves the model and toolbar interactive. */
    private final class PropertyBuilder {
        private String title = "Parametry";
        private View content;
        private String[] items;
        private android.content.DialogInterface.OnClickListener itemAction;
        private final List<String> labels = new ArrayList<>();
        private final List<android.content.DialogInterface.OnClickListener> actions = new ArrayList<>();
        PropertyBuilder setTitle(String value) {title=value;return this;}
        PropertyBuilder setView(View value) {content=value;return this;}
        PropertyBuilder setItems(String[] values, android.content.DialogInterface.OnClickListener action) {
            items=values;itemAction=action;return this;
        }
        PropertyBuilder setNegativeButton(String label, android.content.DialogInterface.OnClickListener action) {return button(label,action);}
        PropertyBuilder setNeutralButton(String label, android.content.DialogInterface.OnClickListener action) {return button(label,action);}
        PropertyBuilder setPositiveButton(String label, android.content.DialogInterface.OnClickListener action) {return button(label,action);}
        private PropertyBuilder button(String label, android.content.DialogInterface.OnClickListener action) {
            labels.add(label);actions.add(action);return this;
        }
        void show() {
            closePropertyEditor();
            if (!(getParent() instanceof android.widget.FrameLayout)) return;
            LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding((int)dp(12),(int)dp(12),(int)dp(12),(int)dp(20));body.setBackgroundColor(PANEL);
            android.widget.TextView heading=new android.widget.TextView(getContext());
            heading.setText(title);heading.setTextColor(INK);heading.setTextSize(17);body.addView(heading);
            if(content!=null) body.addView(content);
            if(items!=null) for(int i=0;i<items.length;i++) {
                final int index=i;android.widget.Button button=new android.widget.Button(getContext());
                button.setText(items[i]);body.addView(button);
                button.setOnClickListener(v->{closePropertyEditor();itemAction.onClick(null,index);invalidate();});
            }
            for(int i=0;i<labels.size();i++) {
                final int index=i;android.widget.Button button=new android.widget.Button(getContext());
                button.setText(labels.get(i));body.addView(button);
                button.setOnClickListener(v->{closePropertyEditor();
                    if(actions.get(index)!=null) actions.get(index).onClick(null,index);invalidate();});
            }
            propertyEditor=new android.widget.ScrollView(getContext());propertyEditor.setFillViewport(false);
            propertyEditor.addView(body);
            body.measure(View.MeasureSpec.makeMeasureSpec((int)dp(252),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            android.widget.FrameLayout.LayoutParams layout=new android.widget.FrameLayout.LayoutParams(
                (int)dp(252),Math.min(body.getMeasuredHeight(),Math.max((int)dp(100),getHeight()-(int)viewTop()-bottomSystemInset)));
            layout.gravity=android.view.Gravity.TOP|android.view.Gravity.RIGHT;layout.topMargin=(int)viewTop();
            ((android.widget.FrameLayout)getParent()).addView(propertyEditor,layout);
        }
    }

    public CadWorkspaceView(Context context, FileRequestListener fileRequestListener) {
        this(context,fileRequestListener,false);
    }

    /** The launch chooser needs metadata, not an eager rebuild of the last heavy model. */
    public CadWorkspaceView(Context context, FileRequestListener fileRequestListener,boolean deferInitialGeometry) {
        super(context);
        this.fileRequestListener = fileRequestListener;
        density = getResources().getDisplayMetrics().density;
        projectRepository = new ProjectRepository(context);
        project = projectRepository.loadOrCreate();
        operationMessage=projectRepository.loadNotice();
        if (!projectRepository.readOnly() && project.importedStep && project.importedStepSource.equals("imported.step")
                && projectRepository.importedStepFile().isFile()) {
            String sourceName = "source-" + UUID.randomUUID() + ".step";
            try {
                java.nio.file.Files.copy(projectRepository.importedStepFile().toPath(),
                    new File(projectRepository.importedStepFile().getParentFile(), sourceName).toPath());
                project.importedStepSource = sourceName;
            } catch (IOException error) {
                operationMessage = "Nie można utworzyć kopii źródłowego STEP";
            }
        }
        if (!SketchTransactions.active(project)&&project.sketchPlane.startsWith("Ściana")
                && Math.abs(project.sketchOriginX) < 1.0e-9
                && Math.abs(project.sketchOriginY) < 1.0e-9
                && Math.abs(project.sketchOriginZ) < 1.0e-9
                && Math.abs(project.sketchUx - 1.0) < 1.0e-9
                && Math.abs(project.sketchVy - 1.0) < 1.0e-9) {
            project.sketchPlane = "XY";
            project.setPrincipalSketchFrame("XY");
        }
        modelWidth = project.width;
        modelHeight = project.height;
        modelDepth = project.depth;
        sketchPlane = project.sketchPlane;
        try{projectRepository.save(project);}catch(RuntimeException error){operationMessage=projectRepository.readOnly()?projectRepository.loadNotice():"Nie można zapisać bieżącego stanu; poprzednie pliki zachowano.";}
        kernelStatus = NativeCadEngine.status();
        if(!deferInitialGeometry){updateNativeModel();if(!projectRepository.readOnly())resumeSketchWork();}
        // STEP is deliberately not exercised during Activity construction. A native
        // importer failure must never prevent the workspace from opening.
        stepStatus = "STEP: moduł zainstalowany";
        setBackgroundColor(BG);
        setFocusable(true);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if(projectRepository.readOnly()) {
                    if(e.getY()<=topBar()&&e.getX()>=getWidth()-dp(190))showExportPicker();
                    else operationMessage=projectRepository.loadNotice();
                    invalidate();return true;
                }
                if(SketchTransactions.active(project)&&e.getY()<topBar()&&e.getX()<toolbarX()) {
                    showSketchSessionActions();return true;
                }
                if(!project.threePointDraft.isEmpty()&&(e.getX()<leftWidth()||(e.getY()<topBar()&&e.getX()<toolbarX()))) {showThreePointActions(null);return true;}
                if(!arcDraft.isEmpty()&&(e.getX()<leftWidth()||(e.getY()<topBar()&&e.getX()<toolbarX()))) {showArcDraftActions(null);return true;}
                if(!project.polylineDraft.isEmpty() && ((e.getX()<leftWidth()&&e.getY()>topBar())
                        ||(e.getY()<topBar()&&e.getX()>getWidth()-dp(330)))) {
                    showPolylineActions(null);return true;
                }
                if(mode.equals("Szkic")&&(sketchTool.equals("Polilinia")||sketchTool.equals("Splajn"))&&sketchPlaneChosen
                        && e.getX()>getWidth()-dp(234)&&e.getY()>topBar()) {
                    float y=e.getY()+propertiesShift();
                    if(y>=dp(374)&&y<=dp(422))finishPolyline(true);
                    else if(y>=dp(432)&&y<=dp(480))finishPolyline(false);
                    else if(y>=dp(490)&&y<=dp(538))removePolylinePoint();
                    else if(y>=dp(548)&&y<=dp(596))cancelPolyline();
                    return true;
                }
                if(e.getX()<leftWidth() || e.getY()>getHeight()-dp(68)-bottomSystemInset) closePropertyEditor();
                float historyY = getHeight() - dp(62) - bottomSystemInset;
                if (e.getX() >= leftWidth() && e.getX() <= leftWidth()+dp(104) && e.getY() >= historyY) {
                    if(!arcDraft.isEmpty()){
                        if(e.getX()<leftWidth()+dp(52)){arcDraft.remove(arcDraft.size()-1);arcPreview=null;operationMessage="Cofnięto punkt rozpoczętego łuku";}
                        else operationMessage="Zakończ lub anuluj rozpoczęty łuk przed Ponów";
                        invalidate();return true;
                    }
                    if (e.getX() < leftWidth()+dp(52)) undo(); else redo();
                    return true;
                }
                if (e.getY() >= historyY && e.getX() >= leftWidth()+dp(116)
                        && e.getX() < viewportRight()) {
                    if(!project.threePointDraft.isEmpty()){showThreePointActions(null);return true;}
                    if(!arcDraft.isEmpty()){showArcDraftActions(null);return true;}
                    if(!project.polylineDraft.isEmpty()){showPolylineActions(null);return true;}
                    int index = (int)((e.getX() - leftWidth() - dp(116) + timelineScroll) / dp(64));
                    List<CadTimeline.Item> items=CadTimeline.entries(project);
                    if(index>=0&&index<items.size()) {
                        CadTimeline.Item item=items.get(index);
                        long now=android.os.SystemClock.uptimeMillis();
                        boolean edit=true;// selecting an operation means editing it
                        selectedTimelineIndex=index;lastTimelineTap=now;
                        highlightSketch=item.kind==CadTimeline.Kind.SKETCH;highlightBody=0;
                        highlightFeature=item.kind==CadTimeline.Kind.FEATURE?item.featureIndex:item.kind==CadTimeline.Kind.ACTIVE?project.committedFeatures.size():item.kind==CadTimeline.Kind.BASE||item.kind==CadTimeline.Kind.IMPORT?-1:item.kind==CadTimeline.Kind.FILLET?-2:Integer.MIN_VALUE;
                        operationMessage="Edycja: "+item.title;
                        if(!edit){invalidate();return true;}
                        if(SketchTransactions.active(project)){finishSketchWork();if(SketchTransactions.active(project))return true;}
                        switch(item.kind) {
                            case BASE:showDepthEditor(true);break;
                            case ACTIVE:showDepthEditor();break;
                            case FEATURE:showCommittedFeatureEditor(item.featureIndex);break;
                            case SKETCH:showSavedSketchPicker();break;
                            case FILLET:showAllEdgesFilletEditor();break;
                            case START:cameraYaw=-28;cameraPitch=22;zoom=1;panX=panY=0;invalidate();break;
                            default:operationMessage="Źródło importu pozostaje zachowane w projekcie";invalidate();break;
                        }
                    }
                    return true;
                }
                if (e.getY() <= topBar() && e.getX() >= getWidth() - dp(190)) {
                    showExportPicker();
                    return true;
                }
                if (e.getY() <= topBar() && e.getX() >= getWidth() - dp(330)) {
                    if (fileRequestListener != null) fileRequestListener.requestOpenStep();
                    return true;
                }
                if(e.getY()<=topBar()&&e.getX()<toolbarX()) {
                    showProjectMenu();return true;
                }
                if (e.getY() >= dp(8) && e.getY() <= dp(56)
                        && e.getX() >= toolbarX() && e.getX() <= toolbarX()+topToolWidth()*ToolbarCatalog.groups(inSketch()).length) {
                    float itemWidth = topToolWidth();
                    int tool = (int)((e.getX() - toolbarX()) / itemWidth);
                    ToolbarCatalog.Group[] groups=ToolbarCatalog.groups(inSketch());
                    if (tool >= 0 && tool < groups.length) openToolGroup(groups[tool],toolbarX()+tool*itemWidth);
                    return true;
                }
                if(e.getX()<leftWidth()&&e.getY()>topBar()&&(treeCollapsed||(e.getY()<treeTop()&&e.getX()>leftWidth()-dp(48)))) {
                    treeCollapsed=!treeCollapsed;dismissToolPopup();invalidate();return true;
                }
                if(e.getX()<leftWidth()&&e.getY()>=treeTop()) {treeTap(e.getX(),e.getY());return true;}

                
if (mode.equals("Szkic") && !sketchPlaneChosen && e.getX() > getWidth() - dp(234)) {
                    float py=e.getY()+propertiesShift();
                    if (py >= dp(374) && py <= dp(426)) chooseSketchPlane("XY");
                    else if (py >= dp(432) && py <= dp(484)) chooseSketchPlane("XZ");
                    else if (py >= dp(490) && py <= dp(542)) chooseSketchPlane("YZ");
                } else if (isInsideViewport(e.getX(), e.getY())) {
                    if(mode.equals("Profile wyciągnięcia")){if(extrusionFacePick!=null)pickExtrusionFace(e.getX(),e.getY());else toggleExtrusionProfile(e.getX(),e.getY());return true;}
                    if(mode.equals("Szkic")&&sketchPlaneChosen&&sketchOrbit)return true;
                    if(mode.equals("Szkic")&&sketchPlaneChosen&&(sketchTool.equals("Okrąg 3 pkt")||sketchTool.equals("Szczelina")||sketchTool.equals("Elipsa"))){threePointTap(e.getX(),e.getY());return true;}
                    if(mode.equals("Szkic")&&sketchPlaneChosen&&sketchTool.equals("Łuk")){arcTap(e.getX(),e.getY());return true;}
                    if(filletSelectionChanged!=null) {
                        selectFilletEdge(e.getX(),e.getY());return true;
                    }
                    if (mode.equals("Edytuj szkic")) {
                        if(cornerFilletPicking){cornerFilletTap(e.getX(),e.getY());return true;}
                        if(coincidenceMode){coincidenceTap(e.getX(),e.getY());return true;}
                        if(trimMode) {trimSketchAt(e.getX(),e.getY());return true;}
                        if(dimensionMode) {
                            SketchPrimitive entity=findSketchAt(e.getX(),e.getY());
                            if(entity!=null){selectedSketchId=entity.id;showSketchDimensionEditor(entity);}
                            else operationMessage="Dotknij linii, prostokąta lub okręgu do wymiarowania";
                            invalidate();return true;
                        }
                        SketchPrimitive entity = findSketchAt(e.getX(), e.getY());
                        if (entity != null) {
                            selectedSketchId = entity.id;
                            showSketchEntityEditor(entity);
                        } else {
                            clearViewportSelection();
                        }
                        return true;
                    }
                    if (mode.equals("Zmierz")) {
                        handleMeasureTap(e.getX(), e.getY());
                        return true;
                    }
                    if(mode.equals("Wybierz")&&editVisibleSketchAt(e.getX(),e.getY()))return true;
                    int face = findFaceAt(e.getX(), e.getY());
                    if(mode.equals("Gwint")) {threadTap(face);return true;}
                    if(mode.equals("Powłoka")) {shellTap(face);return true;}
                    if(mode.equals("Otwór")) {holeTap(face);return true;}
                    if(mode.equals("Pochyl ściany")) {draftTap(face);return true;}
                    if (face >= 0) {
                        selectedFace = face;project.selectedBody=faceBodies.getOrDefault(face,1);
                        if (mode.equals("Szkic") && !sketchPlaneChosen) {
                            if (isFacePlanar(face)) chooseSketchPlane("Ściana " + (face + 1));
                            else operationMessage = "Wybierz płaską ścianę — powierzchnia jest zakrzywiona";
                        } else if (mode.equals("Wybierz")) {
                            Integer origin=faceFeatures.get(face);highlightFeature=origin==null?Integer.MIN_VALUE:origin;highlightBody=0;highlightSketch=false;
                            operationMessage="Zaznaczono ścianę — Szkic utworzy na niej szkic. Przytrzymaj ścianę, aby edytować operację, która ją utworzyła.";
                        }
                        invalidate();
                    } else clearViewportSelection();
                }
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                float historyY=getHeight()-dp(62)-bottomSystemInset;
                if(e.getY()>=historyY&&e.getX()>=leftWidth()+dp(116)&&e.getX()<viewportRight()) {
                    int index=(int)((e.getX()-leftWidth()-dp(116)+timelineScroll)/dp(64));List<CadTimeline.Item> items=CadTimeline.entries(project);
                    if(index>=0&&index<items.size()&&items.get(index).kind==CadTimeline.Kind.FEATURE) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);selectedTimelineIndex=index;highlightFeature=items.get(index).featureIndex;showTimelineActions(items.get(index));invalidate();
                    }
                    return;
                }
                // Long press on a face edits the operation that created it; a tap only selects.
                if(!mode.equals("Wybierz")||!isInsideViewport(e.getX(),e.getY())||scaleGestures.isInProgress())return;
                int face=findFaceAt(e.getX(),e.getY());if(face<0)return;
                selectedFace=face;project.selectedBody=faceBodies.getOrDefault(face,1);
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                editFaceOrigin(face);invalidate();
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if(e1!=null&&!treeCollapsed&&e1.getX()<leftWidth()&&e1.getY()>treeTop()) {treeScroll=Math.max(0,Math.min(Math.max(0,modelTreeRows.size()*dp(36)-(getHeight()-treeTop()-dp(16)-bottomSystemInset)),treeScroll+dy));invalidate();return true;}
                if (e1 != null && e1.getX() >= leftWidth()+dp(104)
                        && e1.getY() >= getHeight()-dp(62)-bottomSystemInset) {
                    timelineScroll=Math.max(0,Math.min(maxTimelineScroll(),timelineScroll+dx));
                    invalidate();return true;
                }
                if(e1 == null || !isInsideViewport(e1.getX(),e1.getY()))return true;
                if (!scaleGestures.isInProgress() && e2.getPointerCount() > 1) {
                    panX -= dx;
                    panY -= dy;
                    invalidate();
                } else if (!scaleGestures.isInProgress() && orbitAllowed()) {
                    cameraYaw -= dx * 0.22f;
                    cameraPitch = Math.max(-89f, Math.min(89f, cameraPitch - dy * 0.22f));
                    invalidate();
                }
                return true;
            }
        });
        gestures.setOnDoubleTapListener(null);
        scaleGestures = new ScaleGestureDetector(context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    applyZoom(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());
                    invalidate();
                    return true;
                }
            });
    }

    private void applyZoom(float factor,float focusX,float focusY) {
        if(!Float.isFinite(factor)||factor<=0)return;
        float next=Math.max(.02f,Math.min(200f,zoom*factor)),ratio=next/zoom;
        float cx=(leftWidth()+viewportRight())/2,cy=(viewTop()+getHeight()-dp(68)-bottomSystemInset)/2;
        panX=focusX-cx-ratio*(focusX-cx-panX);panY=focusY-cy-ratio*(focusY-cy-panY);zoom=next;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(handleDimensionTouch(event))return true;
        if(handleExtrusionTouch(event))return true;
        if(event.getPointerCount()>1 || event.getActionMasked()==MotionEvent.ACTION_CANCEL) {
            sketching=false;sketchRect=null;snapMarker=null;
            polylineTouch=false;polylinePreview=null;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if(inSketch() && isInsideViewport(event.getX(),event.getY())) closePropertyEditor();
            touchDownX = event.getX();
            touchDownY = event.getY();
        }
        if(mode.equals("Szkic")&&sketchPlaneChosen&&sketchOrbit) {
            scaleGestures.onTouchEvent(event);gestures.onTouchEvent(event);return true;
        }
        if(mode.equals("Szkic")&&(sketchTool.equals("Polilinia")||sketchTool.equals("Splajn"))&&sketchPlaneChosen) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&isInsideViewport(event.getX(),event.getY())&&event.getY()>=dp(152)) {
                polylineTouch=true;polylinePreview=polylinePoint(event.getX(),event.getY());
                scaleGestures.onTouchEvent(event);gestures.onTouchEvent(event);invalidate();return true;
            }
            if(polylineTouch&&event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                polylinePreview=polylinePoint(event.getX(),event.getY());invalidate();return true;
            }
            if(polylineTouch&&event.getActionMasked()==MotionEvent.ACTION_UP) {
                polylineTouch=false;
                if(isInsideViewport(event.getX(),event.getY())) {
                    double[] point=polylinePoint(event.getX(),event.getY());
                    if(project.polylineDraft.size()>=3 && point==project.polylineDraft.get(0))finishPolyline(!project.splineDraft);
                    else try {
                        if(project.splineDraft&&project.polylineDraft.size()>=128)throw new IllegalArgumentException("Splajn może mieć najwyżej 128 punktów");
                        PolylineDraft.add(project.polylineDraft,point[0],point[1]);redoStack.clear();
                        projectRepository.save(project);operationMessage="Dodaj kolejny punkt; dotknij początku, aby zamknąć profil.";
                    }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                }
                polylinePreview=null;snapMarker=null;invalidate();return true;
            }
        }
        if (mode.equals("Edytuj szkic") && !trimMode && !dimensionMode && !coincidenceMode && !cornerFilletPicking) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && isInsideViewport(event.getX(), event.getY())) {
                draggedSketch = null;
                for (SketchPrimitive p : project.sketch) {
                    if (p.id.equals(selectedSketchId)
                            && sketchHandleAt(p,event.getX(),event.getY()) != 0) draggedSketch = p;
                }
                if (draggedSketch == null) draggedSketch = findSketchAt(event.getX(), event.getY());
                if (draggedSketch != null) {
                    dragWasSelected = draggedSketch.id.equals(selectedSketchId);
                    dragHandle = dragWasSelected
                        ? sketchHandleAt(draggedSketch, event.getX(), event.getY()) : 0;
                    double[] point = screenToSketch(event.getX(), event.getY());
                    dragStartU = point[0]; dragStartV = point[1];
                    selectedSketchId = draggedSketch.id;
                    dragPreview = null;
                    dragSolvedPreview=null;
                    dragConstraintRejected=false;
                    invalidate();
                    return true;
                }
            }
            if (draggedSketch != null) {
                if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    draggedSketch = dragPreview = null;
                    dragSolvedPreview=null;
                    invalidate();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (Math.hypot(event.getX()-touchDownX, event.getY()-touchDownY) > dp(4)) {
                        double[] point = screenToSketch(event.getX(),event.getY());
                        double du=point[0]-dragStartU,dv=point[1]-dragStartV;
                        PointF a=projectSketchPoint(0,0),b=projectSketchPoint(1,0);
                        double tolerance=dp(12)/Math.max(.01,Math.hypot(b.x-a.x,b.y-a.y));
                        if(dragHandle==0){double[] snapped=SketchSnapper.translation(draggedSketch,du,dv,snapGeometry(),tolerance);du=snapped[0];dv=snapped[1];}
                        else {
                            int index=dragHandle-1;double[] anchor;
                            if(draggedSketch.curves!=null){int offset=draggedSketch.closedGeometry()?(dragHandle>=1000?(dragHandle-1000)*7+3:(dragHandle-1)*7+1):dragHandle==1?1:dragHandle==2?5:3;anchor=new double[]{draggedSketch.curves[offset],draggedSketch.curves[offset+1]};}
                            else if(draggedSketch.type.equals("Okrąg")&&dragHandle==2)anchor=new double[]{draggedSketch.x2,draggedSketch.y2};
                            else anchor=SketchConstraints.anchor(draggedSketch,draggedSketch.type.equals("Prostokąt")?new int[]{0,2,1,3}[index]:index);
                            double[] hit=SketchSnapper.snap(anchor[0]+du,anchor[1]+dv,snapGeometry(),draggedSketch.id,tolerance,null,null);du=hit[0]-anchor[0];dv=hit[1]-anchor[1];
                        }
                        double x1 = draggedSketch.x1, y1 = draggedSketch.y1;
                        double x2 = draggedSketch.x2, y2 = draggedSketch.y2;
                        if (dragHandle == 0 || (draggedSketch.type.equals("Okrąg") && dragHandle == 1)) {
                            x1 += du; y1 += dv; x2 += du; y2 += dv;
                        } else {
                            if (dragHandle == 1 || dragHandle == 4) x1 += du;
                            if (dragHandle == 1 || dragHandle == 3) y1 += dv;
                            if (dragHandle == 2 || dragHandle == 3) x2 += du;
                            if (dragHandle == 2 || dragHandle == 4) y2 += dv;
                        }
                        if(draggedSketch.curves!=null) {
                            try{dragPreview=SketchCurves.drag(draggedSketch,dragHandle,du,dv);}
                            catch(IllegalArgumentException e){dragPreview=null;operationMessage=e.getMessage();}
                        }else if(draggedSketch.vertices!=null) {
                            double[] vertices=draggedSketch.vertices.clone();
                            for(int i=0;i<vertices.length;i+=2) if(dragHandle==0||dragHandle==i/2+1) {
                                vertices[i]+=du;vertices[i+1]+=dv;
                            }
                            try {dragPreview=SketchSpline.is(draggedSketch)?new SketchPrimitive(draggedSketch.id,vertices,"Splajn"):new SketchPrimitive(draggedSketch.id,vertices);}
                            catch(IllegalArgumentException e){dragPreview=null;}
                        } else {
                            try {dragPreview=SketchDimensions.constrainDrag(draggedSketch,
                                draggedSketch.withPoints(x1,y1,x2,y2,null),dragHandle);}
                            catch(IllegalArgumentException error){dragPreview=null;operationMessage=error.getMessage();}
                        }
                        dragSolvedPreview=null;
                        if(dragPreview!=null&&draggedSketch.curves==null&&!project.coincidences.isEmpty()) {
                            try{
                                dragPreview=dragPreview.withConstruction(draggedSketch.construction);
                                dragSolvedPreview=SketchConstraints.drag(project.sketch,project.coincidences,draggedSketch,dragPreview,dragHandle);
                                dragConstraintRejected=false;
                            }catch(IllegalArgumentException error){dragPreview=null;dragConstraintRejected=true;operationMessage=error.getMessage();}
                        }
                        invalidate();
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    SketchPrimitive original = draggedSketch, changed = dragPreview;
                    List<SketchPrimitive> solved=dragSolvedPreview;
                    draggedSketch = dragPreview = null;
                    dragSolvedPreview=null;
                    snapMarker=null;
                    if(changed==original)operationMessage="Wymiary są zablokowane. Przesuwaj krawędź albo zmień wartość narzędziem Wymiar.";
                    else if(changed!=null&&solved!=null)applySolvedSketch(solved,new ArrayList<>(project.coincidences));
                    else if (changed != null) replaceSketchEntity(original, changed);
                    else if(dragConstraintRejected)operationMessage="Ruch narusza więzy. Usuń blokadę punktu lub zmień wymiary zgodnie z więzami.";
                    else if (dragWasSelected) showSketchEntityEditor(original);
                    else operationMessage = "Przeciągnij uchwyt, aby zmienić rozmiar; krawędź, aby przesunąć";
                    invalidate();
                    return true;
                }
            }
        }
        if(mode.equals("Szkic")&&sketchTool.equals("Łuk")&&sketchPlaneChosen&&event.getActionMasked()==MotionEvent.ACTION_MOVE&&isInsideViewport(event.getX(),event.getY())&&arcDraft.size()==2) {
            double[] mid=snappedSketchPoint(event.getX(),event.getY(),null,null,null),a=arcDraft.get(0),b=arcDraft.get(1);
            try{arcPreview=SketchCurves.arc(a[0],a[1],mid[0],mid[1],b[0],b[1]);}catch(IllegalArgumentException e){arcPreview=null;}
            invalidate();
        }
        if(mode.equals("Szkic")&&(sketchTool.equals("Okrąg 3 pkt")||sketchTool.equals("Szczelina")||sketchTool.equals("Elipsa"))&&sketchPlaneChosen&&event.getActionMasked()==MotionEvent.ACTION_MOVE&&isInsideViewport(event.getX(),event.getY())&&project.threePointDraft.size()==2) {
            try{threePointPreview=SketchShapes.threePoint("preview",sketchTool,project.threePointDraft.get(0),project.threePointDraft.get(1),snappedSketchPoint(event.getX(),event.getY(),null,null,null));}
            catch(IllegalArgumentException error){threePointPreview=null;}invalidate();
        }
        if (mode.equals("Szkic") && !SketchShapes.threePointTool(sketchTool) && !(sketchTool.equals("Polilinia")||sketchTool.equals("Splajn")) && sketchPlaneChosen
                && (isInsideViewport(event.getX(), event.getY()) || sketching)) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if(event.getY()<dp(152)) return true;
                double[] start=snappedSketchPoint(event.getX(),event.getY(),null,null,null);
                PointF screen=projectSketchPoint(start[0],start[1]);
                sketchStartX = screen.x;
                sketchStartY = screen.y;
                sketchEndX=screen.x;sketchEndY=screen.y;
                sketchRect = new RectF(sketchStartX, sketchStartY, sketchStartX, sketchStartY);
                sketching = true;
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && sketching) {
                double[] start=screenToSketch(sketchStartX,sketchStartY);
                double[] end=snappedSketchPoint(event.getX(),event.getY(),null,start[0],start[1]);
                PointF screen=projectSketchPoint(end[0],end[1]);sketchEndX=screen.x;sketchEndY=screen.y;
                sketchRect.set(Math.min(sketchStartX, event.getX()), Math.min(sketchStartY, event.getY()),
                    Math.max(sketchStartX, event.getX()), Math.max(sketchStartY, event.getY()));
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && sketching) {
                sketching = false;
                if (Math.hypot(event.getX() - sketchStartX, event.getY() - sketchStartY) > dp(4)) {
                    sketchEntitiesAdd(sketchTool, sketchStartX, sketchStartY,
                        sketchEndX, sketchEndY);
                }
                sketchRect = null;
                snapMarker = null;
                invalidate();
                return true;
            }
        }
        scaleGestures.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        dimensionHits.clear();
        scheduleToolbarUpdate();
        float left = leftWidth();
        float right = viewportRight();
        float top = viewTop();
        float bottom = getHeight() - dp(68) - bottomSystemInset;
        workCenterX = (left + right) / 2f + panX;
        workCenterY = (top + bottom) / 2f + panY;

        drawTopBar(canvas);
        modelTreeRows=ModelTree.rows(project,collapsedBodies);
        drawLeftPanel(canvas, left, top, bottom);
        if(propertiesVisible() && propertyEditor==null)drawProperties(canvas, right, top, bottom);
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        drawGrid(canvas, left, top, right, bottom);
        if (mode.equals("Rysunek")) {
            drawTechnicalViews(canvas, left, top, right, bottom);
        } else {
            if (showSolid || !inSketch()) drawNativeSolid(canvas, (left + right) / 2f + panX, (top + bottom) / 2f + panY);
            if(inSketch()||showExtrusionSketch){if(sketchVisible)drawSketch(canvas,left,right);}
            else drawVisibleTreeSketches(canvas);
            if (mode.equals("Zmierz")) drawDimensions(canvas, left, top, right, bottom);
        }
        if(!mode.equals("Rysunek")) drawAxis(canvas, right - dp(70), bottom - dp(60));
        drawExtrusionManipulator(canvas,left,top,right,bottom);
        if(!propertiesVisible()&&!operationMessage.isEmpty())drawViewportMessage(canvas,left,right,bottom);
        canvas.restore();
        drawTimeline(canvas);
    }

    private void drawTopBar(Canvas canvas) {
        paint.setColor(CHROME);
        canvas.drawRect(0, 0, getWidth(), topBar(), paint);
        // Project block: product, mode badge and document name (tap opens the project menu).
        text(canvas, "TabletCAD", dp(18), dp(27), 15, Color.WHITE, true);
        boolean sketch = inSketch();
        float badgeX = dp(112);
        paint.setColor(sketch ? Color.rgb(196, 120, 24) : ACCENT);
        canvas.drawRoundRect(new RectF(badgeX, dp(12), badgeX + dp(70), dp(32)), dp(10), dp(10), paint);
        text(canvas, sketch ? "SZKIC 2D" : "BRYŁA 3D", badgeX + dp(35), dp(26), 9.5f, Color.WHITE, true, Paint.Align.CENTER);
        String projectLabel = project.name.length() > 17 ? project.name.substring(0, 15) + "…" : project.name;
        text(canvas, projectLabel + "  ▾", dp(18), dp(50), 12, CHROME_TEXT, false);
        text(canvas, "v" + appVersion(), toolbarX() - dp(12), dp(50), 10, Color.rgb(132, 146, 158), false, Paint.Align.RIGHT);
        paint.setColor(CHROME_RAISED);
        canvas.drawRect(toolbarX() - dp(1), dp(12), toolbarX(), topBar() - dp(12), paint);
        pill(canvas, getWidth() - dp(312), dp(13), dp(124), dp(38), "Otwórz plik", CHROME_RAISED, Color.WHITE);
        pill(canvas, getWidth() - dp(172), dp(13), dp(152), dp(38), "Eksportuj", ACCENT, Color.WHITE);
        float itemWidth = topToolWidth();
        ToolbarCatalog.Group[] groups = ToolbarCatalog.groups(sketch);
        for (int i = 0; i < groups.length; i++) {
            float x = toolbarX() + i * itemWidth;
            boolean active = groupActive(groups[i]);
            drawToolButton(canvas, x + dp(2), dp(8), itemWidth - dp(4), dp(48),
                groups[i].icon, groups[i].title+(groups[i].direct()?"":" ▾"), active);
        }
    }

    private boolean groupActive(ToolbarCatalog.Group group) {
        for(String command:group.commands())if(isToolActive(command))return true;
        return false;
    }

    private void scheduleToolbarUpdate() {
        String state=inSketch()+"|"+mode+"|"+sketchTool+"|"+trimMode+"|"+dimensionMode+"|"+coincidenceMode+"|"+showSolid+"|"+getWidth();
        if(state.equals(toolbarState)||toolbarUpdatePosted)return;
        toolbarUpdatePosted=true;
        post(()-> {
            toolbarUpdatePosted=false;
            if(!(getParent() instanceof android.widget.FrameLayout)||!isAttachedToWindow())return;
            boolean roleChanged=!toolbarState.startsWith(inSketch()+"|");
            if(roleChanged)dismissToolPopup();
            toolbarState=inSketch()+"|"+mode+"|"+sketchTool+"|"+trimMode+"|"+dimensionMode+"|"+coincidenceMode+"|"+showSolid+"|"+getWidth();
            android.widget.FrameLayout root=(android.widget.FrameLayout)getParent();
            if(nativeToolbar!=null)root.removeView(nativeToolbar);
            nativeToolbar=new android.widget.HorizontalScrollView(getContext());nativeToolbar.setHorizontalScrollBarEnabled(false);
            nativeToolbar.setBackgroundColor(CHROME);
            LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);
            for(ToolbarCatalog.Group group:ToolbarCatalog.groups(inSketch())) {
                View button=toolbarButton(group.title,group.icon,!group.direct(),groupActive(group),false);
                button.setContentDescription(group.direct()?group.title:group.title+" · otwórz grupę");
                button.setOnClickListener(v->{int[] position=new int[2];v.getLocationOnScreen(position);openToolGroup(group,position[0]);});
                row.addView(button,new LinearLayout.LayoutParams((int)topToolWidth(),(int)dp(48)));
            }
            nativeToolbar.addView(row);
            int toolbarWidth=(int)Math.min(topToolWidth()*ToolbarCatalog.groups(inSketch()).length,getWidth()-toolbarX()-dp(330));
            android.widget.FrameLayout.LayoutParams layout=new android.widget.FrameLayout.LayoutParams(Math.max(1,toolbarWidth),(int)dp(50));
            layout.leftMargin=(int)toolbarX();layout.topMargin=(int)dp(7);
            root.addView(nativeToolbar,layout);
        });
    }

    private View toolbarButton(String label,String icon,boolean dropdown,boolean active,boolean submenu) {
        View button=new View(getContext()) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setStyle(Paint.Style.FILL);paint.setColor(active?ACCENT:CHROME_RAISED);
                canvas.drawRoundRect(new RectF(dp(2),dp(2),getWidth()-dp(2),getHeight()-dp(2)),dp(6),dp(6),paint);
                canvas.save();canvas.translate(getWidth()/2f,dp(submenu?20:15));canvas.scale(.8f,.8f);
                drawToolIcon(canvas,icon,0,0,active?Color.WHITE:Color.rgb(210,220,228));canvas.restore();
                android.text.TextPaint labelPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);labelPaint.setTextSize(dp(submenu?9:8.5f));labelPaint.setColor(Color.WHITE);
                android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(label,0,label.length(),labelPaint,Math.max(1,getWidth()-(int)dp(8))).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(submenu?2:1).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
                canvas.save();canvas.translate(dp(4),dp(submenu?37:32));layout.draw(canvas);canvas.restore();
                if(dropdown)text(canvas,"▾",getWidth()-dp(9),dp(16),10,CHROME_TEXT,false,Paint.Align.CENTER);
            }
        };
        button.setFocusable(true);button.setClickable(true);button.setTooltipText(label);
        button.setContentDescription(label);return button;
    }

    private void dismissToolPopup() {if(toolbarPopup!=null){toolbarPopup.dismiss();toolbarPopup=null;}}

    private void openToolGroup(ToolbarCatalog.Group group,float anchorX) {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        dismissToolPopup();
        boolean role=inSketch();String[] commands=group.commands();
        for(String command:commands)if(!ToolbarCatalog.allows(role,command)){invalidate();return;}
        if(group.direct()){activateTopTool(commands[0]);return;}
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding((int)dp(8),(int)dp(6),(int)dp(8),(int)dp(8));body.setBackgroundColor(CHROME);
        android.widget.TextView heading=new android.widget.TextView(getContext());
        heading.setText((role?"Szkic · ":"Bryła · ")+group.title);heading.setTextColor(Color.WHITE);heading.setTextSize(13);
        heading.setPadding((int)dp(6),0,0,(int)dp(5));body.addView(heading);
        LinearLayout row=null;int commandIndex=0;
        int columns=Math.max(1,Math.min(4,(int)((getWidth()-toolbarX()-dp(16))/dp(88))));
        for(String command:commands) {
            if(commandIndex++%columns==0){row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row);}
            String label=command.equals("Ukryj bryłę")&&!showSolid?"Pokaż bryłę":command;
            View button=toolbarButton(label,command,false,isToolActive(command),true);
            button.setOnClickListener(v->{dismissToolPopup();
                if(inSketch()==role&&ToolbarCatalog.allows(role,command))activateTopTool(command);
            });
            row.addView(button,new LinearLayout.LayoutParams((int)dp(88),(int)dp(64)));
        }
        int width=(int)dp(Math.min(columns,commands.length)*88+16);
        toolbarPopup=new android.widget.PopupWindow(body,width,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);
        android.graphics.drawable.GradientDrawable popupBackground=new android.graphics.drawable.GradientDrawable();
        popupBackground.setColor(CHROME);popupBackground.setCornerRadius(dp(14));
        body.setBackground(popupBackground);body.setClipToOutline(true);
        toolbarPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        toolbarPopup.setOutsideTouchable(true);toolbarPopup.setElevation(dp(10));
        int x=(int)Math.max(toolbarX(),Math.min(anchorX,getWidth()-dp(252)-width));
        int[] origin=new int[2];getLocationOnScreen(origin);
        toolbarPopup.showAtLocation(this,android.view.Gravity.TOP|android.view.Gravity.LEFT,x+origin[0],(int)topBar()+origin[1]);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();previewViewDetached=false;
    }

    @Override protected void onDetachedFromWindow() {
        dismissToolPopup();
        if(libraryProgress!=null){libraryProgress.dismiss();libraryProgress=null;}
        if(projectChooser!=null){projectChooser.dismiss();projectChooser=null;}
        previewViewDetached=true;removeCallbacks(extrusionPreviewTask);extrusionPreviewScheduled=false;
        if(geometryPreviewWorker!=null){geometryPreviewWorker.close();geometryPreviewWorker=null;}
        if(modelCommitWorker!=null){modelCommitWorker.close();modelCommitWorker=null;}modelOperationPending=false;
        // Do not mutate siblings while FrameLayout is dispatching detachment.
        toolbarState="";super.onDetachedFromWindow();
    }

    private void drawLeftPanel(Canvas canvas,float left,float top,float bottom) {
        paint.setColor(PANEL);canvas.drawRect(0,topBar(),left,getHeight(),paint);
        paint.setColor(DIVIDER);canvas.drawRect(left-dp(1),topBar(),left,getHeight(),paint);
        if(treeCollapsed) {
            // Collapsed rail: one tap anywhere on it restores the model tree.
            text(canvas,"›",left/2,topBar()+dp(32),20,ACCENT,true,Paint.Align.CENTER);
            canvas.save();canvas.rotate(-90,left/2,topBar()+dp(110));text(canvas,"MODEL",left/2,topBar()+dp(114),11,MUTED,true,Paint.Align.CENTER);canvas.restore();
            return;
        }
        text(canvas,"MODEL",dp(18),topBar()+dp(29),12,MUTED,true);text(canvas,"‹",left-dp(24),topBar()+dp(31),20,ACCENT,true,Paint.Align.CENTER);
        row(canvas,"Dokument",dp(18),topBar()+dp(60),true);
        canvas.save();canvas.clipRect(0,treeTop(),left,getHeight()-bottomSystemInset);
        for(int i=0;i<modelTreeRows.size();i++) {
            ModelTree.Row entry=modelTreeRows.get(i);float y=treeTop()+i*dp(36)-treeScroll;if(y+dp(36)<treeTop()||y>getHeight())continue;
            if(entry.key.equals(selectedTreeKey)){paint.setColor(Color.rgb(226,242,240));canvas.drawRoundRect(new RectF(dp(8),y,dp(220),y+dp(34)),dp(6),dp(6),paint);}
            if(!entry.sketch()) {
                text(canvas,collapsedBodies.contains(entry.body)?"›":"⌄",dp(12),y+dp(23),15,MUTED,true);
                canvas.save();canvas.clipRect(dp(26),y,dp(194),y+dp(36));text(canvas,entry.name,dp(28),y+dp(23),12,INK,true);canvas.restore();
                if(entry.body>0)text(canvas,project.hiddenBodies.contains(entry.body)?"○":"●",dp(207),y+dp(23),13,ACCENT,true);
            } else {
                canvas.save();canvas.clipRect(dp(32),y,dp(143),y+dp(36));text(canvas,entry.name+" ("+entry.entities.size()+")",dp(34),y+dp(23),11.5f,INK,entry.key.equals(selectedTreeKey));canvas.restore();
                text(canvas,"✎",dp(153),y+dp(23),15,ACCENT,true);text(canvas,"Aa",dp(176),y+dp(23),11,ACCENT,true);text(canvas,project.hiddenSketchParts.contains(entry.key)?"○":"●",dp(207),y+dp(23),13,ACCENT,true);
            }
        }
        canvas.restore();
    }
    private void treeTap(float x,float y) {
        int index=(int)((y-treeTop()+treeScroll)/dp(36));if(index<0||index>=modelTreeRows.size())return;
        ModelTree.Row entry=modelTreeRows.get(index);long now=android.os.SystemClock.uptimeMillis();boolean edit=entry.key.equals(selectedTreeKey)&&now-treeLastTap<android.view.ViewConfiguration.getDoubleTapTimeout();treeLastTap=now;
        if(!entry.sketch()) {
            if(x<dp(26)){if(!collapsedBodies.add(entry.body))collapsedBodies.remove(entry.body);}
            else if(x>dp(196)&&entry.body>0){if(!project.hiddenBodies.add(entry.body))project.hiddenBodies.remove(entry.body);projectRepository.save(project);}
            else {selectedTreeKey=entry.key;project.selectedBody=entry.body>0?entry.body:1;highlightBody=entry.body;highlightFeature=Integer.MIN_VALUE;highlightSketch=false;}
        } else if(x>dp(196)) {if(!project.hiddenSketchParts.add(entry.key))project.hiddenSketchParts.remove(entry.key);else if(selectedTreeKey.equals(entry.key)){selectedTreeKey="";highlightSketch=false;}projectRepository.save(project);}
        else if(x>dp(167))renameTreeSketch(entry);
        else {selectedTreeKey=entry.key;highlightSketch=true;highlightBody=0;highlightFeature=Integer.MIN_VALUE;selectedSketchId=null;editTreeSketch(entry);}
        invalidate();
    }
    private void editTreeSketch(ModelTree.Row row) {
        if(SketchTransactions.active(project)){finishSketchWork();if(SketchTransactions.active(project))return;}
        if(project.sketchId.equals(row.sketchId)){enterSketchEditor();return;}
        if(commitModelChange(()->{
            preserveExtrudedBase();commitActiveFeature();
            try{project.archiveCurrentSketch();project.restoreSketch(row.snapshot);}catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
            project.featureOperation="none";project.featureProfileId=null;project.sketchExtruded=false;
        })){sketchPlane=project.sketchPlane;enterSketchEditor();}
    }
    private void renameTreeSketch(ModelTree.Row row) {
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setText(row.name);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
        new android.app.AlertDialog.Builder(getContext()).setTitle("Nazwa szkicu").setView(name).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
            String value=name.getText().toString().trim();if(value.isEmpty())return;
            commitModelChange(()->{if(project.sketchId.equals(row.sketchId))project.sketchName=value;for(org.json.JSONObject snapshot:project.savedSketches)if(row.sketchId.equals(snapshot.optString("id")))try{snapshot.put("name",value);}catch(org.json.JSONException error){throw new IllegalArgumentException(error);}});invalidate();
        }).show();
    }
    /**
     * Long press on a face edits the operation that created it; the face stays selected so the
     * Sketch tool can still start a sketch on it. A plain tap only selects the face.
     */
    private void editFaceOrigin(int face) {
        if(project.importedMeshSource!=null||SketchTransactions.active(project))return;
        Integer origin=faceFeatures.get(face);if(origin==null)return;
        highlightFeature=origin;highlightBody=0;highlightSketch=false;
        if(origin>=0&&origin<project.committedFeatures.size()) {
            String title="Operacja";for(CadTimeline.Item item:CadTimeline.entries(project))if(item.kind==CadTimeline.Kind.FEATURE&&item.featureIndex==origin)title=item.title;
            showCommittedFeatureEditor(origin);operationMessage="Ściana z operacji „"+title+"” (nr "+(origin+1)+"). Szkic użyje tej ściany.";
        } else if(origin==-1&&project.featureBase!=null&&!project.importedStep) {
            showDepthEditor(true);operationMessage="Ściana bryły bazowej. Szkic użyje tej ściany.";
        }
    }

    /**
     * Selecting a sketch element in the model view means editing it: the owning sketch opens
     * in the sketch editor with the touched element selected and its parameters shown.
     */
    private boolean editVisibleSketchAt(float x,float y) {
        CadProject model=project;ModelTree.Row hitRow=null;String hitId=null;
        try {
            List<ModelTree.Row> rows=ModelTree.rows(model,java.util.Collections.emptySet());
            // Prefer the highlighted sketch when several overlap.
            rows.sort((a,b)->Boolean.compare(!a.key.equals(selectedTreeKey),!b.key.equals(selectedTreeKey)));
            for(ModelTree.Row row:rows)if(row.sketch()&&(!model.hiddenSketchParts.contains(row.key)||row.key.equals(selectedTreeKey))) {
                project=ModelTree.display(model,row);SketchPrimitive entity=findSketchAt(x,y);
                if(entity!=null){hitRow=row;hitId=entity.id;break;}
            }
        }catch(IllegalArgumentException ignored){return false;}
        finally{project=model;}
        if(hitRow==null)return false;
        selectedTreeKey=hitRow.key;editTreeSketch(hitRow);
        if(!inSketch()){invalidate();return true;}
        for(SketchPrimitive entity:project.sketch)if(entity.id.equals(hitId)){selectedSketchId=entity.id;showSketchEntityEditor(entity);break;}
        operationMessage="Edycja szkicu: "+hitRow.name+". Zakończ zatwierdza zmiany.";
        invalidate();return true;
    }

    private void drawVisibleTreeSketches(Canvas canvas) {
        CadProject model=project;boolean previousHighlight=highlightSketch;
        try {
            for(ModelTree.Row row:ModelTree.rows(model,java.util.Collections.emptySet()))if(row.sketch()&&(!model.hiddenSketchParts.contains(row.key)||row.key.equals(selectedTreeKey))) {
                project=ModelTree.display(model,row);highlightSketch=row.key.equals(selectedTreeKey);
                for(SketchPrimitive entity:project.sketch)drawSketchEntity(canvas,entity);
            }
        }finally {project=model;highlightSketch=previousHighlight;}
    }

    private void drawProperties(Canvas canvas, float right, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(right, topBar(), getWidth(), getHeight(), paint);
        paint.setColor(DIVIDER);canvas.drawRect(right, topBar(), right+dp(1), getHeight(), paint);
        canvas.save();canvas.translate(0,-propertiesShift());
        try { drawPropertiesContent(canvas,right,bottom+propertiesShift()); } finally { canvas.restore(); }
    }

    /** The properties content keeps its original coordinates and is drawn shifted below the 64 dp bar. */
    private float propertiesShift() { return dp(108) - topBar(); }

    private void drawPropertiesContent(Canvas canvas, float right, float bottom) {
        text(canvas, "WŁAŚCIWOŚCI", right + dp(18), dp(137), 12, MUTED, true);
        if(!inSketch()&&!project.hasSolid()) {
            text(canvas,"Pusty dokument",right+dp(18),dp(185),18,INK,true);
            text(canvas,"Wybierz Szkic w górnym menu.",right+dp(18),dp(240),11,MUTED,false);
            text(canvas,"Wskaż płaszczyznę, narysuj",right+dp(18),dp(264),11,MUTED,false);
            text(canvas,"profil i wybierz Wyciągnij.",right+dp(18),dp(288),11,MUTED,false);
            drawPanelMessage(canvas,right,dp(380));return;
        }
        if(inSketch()) {
            text(canvas, sketchPlaneChosen ? "Szkic · "+sketchPlane : "Wybierz płaszczyznę",
                right+dp(18),dp(185),16,INK,true);
            if(!sketchPlaneChosen) {
                text(canvas,"Dotknij płaskiej ściany bryły",right+dp(18),dp(230),11,MUTED,false);
                pill(canvas,right+dp(18),dp(374),dp(202),dp(48),"Płaszczyzna XY",GRID,INK);
                pill(canvas,right+dp(18),dp(432),dp(202),dp(48),"Płaszczyzna XZ",GRID,INK);
                pill(canvas,right+dp(18),dp(490),dp(202),dp(48),"Płaszczyzna YZ",GRID,INK);
            } else {
                if(mode.equals("Szkic")&&(sketchTool.equals("Polilinia")||sketchTool.equals("Splajn"))) {
                    labelValue(canvas,right,project.splineDraft?"Punkty splajnu":"Punkty polilinii",String.valueOf(project.polylineDraft.size()),dp(230));
                    text(canvas,"Wskazuj kolejne punkty palcem.",right+dp(18),dp(330),11,MUTED,false);
                    pill(canvas,right+dp(18),dp(374),dp(202),dp(48),project.splineDraft?"Zapisz splajn":"Zamknij obwód",ACCENT,Color.WHITE);
                    pill(canvas,right+dp(18),dp(432),dp(202),dp(48),project.splineDraft?"Zapisz splajn":"Zapisz otwarty łańcuch",GRID,INK);
                    pill(canvas,right+dp(18),dp(490),dp(202),dp(48),"Cofnij ostatni punkt",GRID,INK);
                    pill(canvas,right+dp(18),dp(548),dp(202),dp(48),project.splineDraft?"Anuluj splajn":"Anuluj polilinię",GRID,INK);
                    drawPanelMessage(canvas,right,dp(620));return;
                }
                labelValue(canvas,right,"Narzędzie",coincidenceMode?constraintKind.equals("fixed")?"Unieruchom punkt":constraintKind.equals("horizontal")?"Poziomo":constraintKind.equals("vertical")?"Pionowo":"Połącz punkty":dimensionMode?"Wymiar sterujący":trimMode?"Przytnij fragment":mode.equals("Edytuj szkic")?"Zaznacz / przesuń":sketchTool,dp(220));
                labelValue(canvas,right,"Elementy szkicu",String.valueOf(project.sketch.size()),dp(295));
                text(canvas,"Przyciąganie: punkty, środki,",right+dp(18),dp(400),11,ACCENT,true);
                text(canvas,"przecięcia i linie pomocnicze",right+dp(18),dp(422),11,ACCENT,false);
                text(canvas,coincidenceMode?constraintKind.equals("coincident")?"Wskaż dwa punkty różnych":constraintKind.equals("fixed")?"Dotknij punktu do zablokowania.":"Dotknij odcinka lub boku konturu.":dimensionMode?"Dotknij elementu szkicu.":trimMode?"Dotknij zbędnego fragmentu":"Rysuj przeciągnięciem palca.",right+dp(18),dp(460),11,MUTED,false);
                text(canvas,coincidenceMode?constraintKind.equals("coincident")?"elementów. Pierwszy zostaje,":"Więzy zostaną zapisane.":dimensionMode?"Ustaw wymiar w prawym panelu.":trimMode?"pomiędzy przecięciami.":"Edytuj: zaznacz, potem przesuń",right+dp(18),dp(482),11,MUTED,false);
                text(canvas,coincidenceMode?constraintKind.equals("coincident")?"drugi zostanie dołączony.":"Usuwanie: Geometria → więzy.":dimensionMode?"Blokada zachowuje rozmiar.":trimMode?"Cofnij przywraca cały element.":"element lub jego uchwyt.",right+dp(18),dp(504),11,MUTED,false);
                text(canvas,showSolid?"Bryła widoczna":"Bryła ukryta",right+dp(18),dp(544),11,MUTED,false);
            }
            drawPanelMessage(canvas,right,dp(610));return;
        }
        text(canvas, mode.equals("Szkic") ? "Płaszczyzna szkicu"
            : (mode.equals("Edytuj szkic") ? "Edycja szkicu"
            : (project.importedMeshSource != null ? "Siatka STL" : project.importedStep ? "Zaimportowany STEP"
            : (Revolve.is(project.featureBase)?"Obrót profilu":project.sketchExtruded ? "Wyciągnięcie szkicu" : "Wyciągnięcie 1"))),
            right + dp(18), dp(191), 18, INK, true);
        double shownDepth = project.featureOperation.equals("none") ? modelDepth : Math.abs(project.featureDepth);
        if(project.importedMeshSource != null) {
            labelValue(canvas,right,"Trójkąty",String.valueOf(nativeMesh.length/13),dp(226));
            labelValue(canvas,right,"Typ modelu","Siatka STL",dp(290));
        } else {
        if(Revolve.is(project.featureBase)&&project.featureOperation.equals("none"))labelValue(canvas,right,"Kąt obrotu",String.format(java.util.Locale.US,"%.3f°",project.featureBase[6]),dp(226));
        else labelValue(canvas, right, project.featureOperation.equals("cut") ? "Głębokość wycięcia"
            : project.featureOperation.equals("add") ? "Wysokość dodania" : "Głębokość",
            formatMm(shownDepth), dp(226));
        String operationLabel = project.featureOperation.equals("cut") ? "Wytnij materiał"
            : project.featureOperation.equals("add") ? "Dodaj materiał" : "Nowa bryła";
        labelValue(canvas, right, "Operacja", operationLabel, dp(290));
        }
        if (mode.equals("Szkic")) {
            text(canvas, sketchPlane, right + dp(18), dp(354), 12,
                sketchPlaneChosen ? ACCENT : MUTED, true);
            pill(canvas, right + dp(18), dp(374), dp(202), dp(48), "Płaszczyzna XY", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(432), dp(202), dp(48), "Płaszczyzna XZ", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(490), dp(202), dp(48), "Płaszczyzna YZ", Color.rgb(226, 232, 236), INK);
            text(canvas, "lub dotknij płaskiej ściany bryły", right + dp(18), dp(562), 10, MUTED, false);
        } else if (mode.equals("Edytuj szkic")) {
            text(canvas, selectedSketchId == null ? "Dotknij elementu szkicu" : "Wybrano element szkicu",
                right + dp(18), dp(374), 12, selectedSketchId == null ? MUTED : ACCENT, true);
            text(canvas, "Współrzędne można wpisać numerycznie.", right + dp(18), dp(400), 10, MUTED, false);
        } else if (mode.equals("Zmierz")) {
            text(canvas,"Zmierz: krawędź lub dwa punkty",right+dp(18),dp(374),11,MUTED,true);
            text(canvas,"Ponownie wybierz ikonę Zmierz",right+dp(18),dp(398),11,MUTED,false);
        } else {
            labelValue(canvas, right, "Tryb", mode, dp(366));
        }
        if (!operationMessage.isEmpty()) drawPanelMessage(canvas,right,dp(614));
        text(canvas, "RDZEŃ GEOMETRII", right + dp(18), bottom - dp(106), 11, MUTED, true);
        text(canvas, kernelStatus, right + dp(18), bottom - dp(82), 12,
            nativePrism.length == 24 ? ACCENT : Color.rgb(205, 65, 65), true);
        text(canvas, modelInfo, right + dp(18), bottom - dp(60), 10, MUTED, false);
        text(canvas, stepStatus, right + dp(18), bottom - dp(38), 10, MUTED, false);
    }

    private void drawPanelMessage(Canvas canvas,float right,float y) {
        android.text.TextPaint messagePaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        messagePaint.setTextSize(dp(12));messagePaint.setColor(INK);
        android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(operationMessage,0,
            operationMessage.length(),messagePaint,(int)dp(212)).build();
        canvas.save();canvas.translate(right+dp(18),y);layout.draw(canvas);canvas.restore();
    }

    private void drawViewportMessage(Canvas canvas,float left,float right,float bottom) {
        android.text.TextPaint messagePaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        // Floating status toast: dark pill centred above the timeline, red accent for failures.
        messagePaint.setTextSize(dp(12.5f));messagePaint.setColor(Color.WHITE);
        int maxWidth=(int)Math.max(dp(40),Math.min(dp(620),right-left-dp(64)));
        float natural=messagePaint.measureText(operationMessage);int width=(int)Math.min(maxWidth,natural+dp(2));
        android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(operationMessage,0,
            operationMessage.length(),messagePaint,Math.max(1,width)).setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        float boxWidth=layout.getWidth()+dp(40),x=(left+right)/2-boxWidth/2,y=bottom-layout.getHeight()-dp(30);
        RectF box=new RectF(x,y-dp(10),x+boxWidth,bottom-dp(10));
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(235,20,26,33));canvas.drawRoundRect(box,dp(14),dp(14),paint);
        String lower=operationMessage.toLowerCase(java.util.Locale.ROOT);
        boolean failure=lower.startsWith("nie ")||lower.contains("nie zatwierdzono")||lower.contains("nie powiod")||lower.contains("błąd")||lower.contains("sprzeczne");
        paint.setColor(failure?Color.rgb(226,92,80):ACCENT);canvas.drawRoundRect(new RectF(box.left+dp(10),box.top+dp(10),box.left+dp(14),box.bottom-dp(10)),dp(2),dp(2),paint);
        canvas.save();canvas.translate(x+dp(26),y);layout.draw(canvas);canvas.restore();
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        paint.setStrokeWidth(1f);
        paint.setColor(GRID);
        if(inSketch() && sketchPlaneChosen) {
            double[] center=screenToSketch((left+right)/2,(top+bottom)/2);
            double nominal=10/zoom,power=Math.pow(10,Math.floor(Math.log10(nominal))),fraction=nominal/power;
            double step=(fraction<2?1:fraction<5?2:5)*power;
            double u=Math.floor(center[0]/step)*step,v=Math.floor(center[1]/step)*step;
            for(int i=-40;i<=40;i++) {
                PointF a=projectSketchPoint(u+i*step,v-400*step),b=projectSketchPoint(u+i*step,v+400*step);
                canvas.drawLine(a.x,a.y,b.x,b.y,paint);
                a=projectSketchPoint(u-400*step,v+i*step);b=projectSketchPoint(u+400*step,v+i*step);
                canvas.drawLine(a.x,a.y,b.x,b.y,paint);
            }
            paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(205,95,95));
            PointF a=projectSketchPoint(u-400*step,0),b=projectSketchPoint(u+400*step,0);
            canvas.drawLine(a.x,a.y,b.x,b.y,paint);paint.setColor(Color.rgb(85,155,105));
            a=projectSketchPoint(0,v-400*step);b=projectSketchPoint(0,v+400*step);
            canvas.drawLine(a.x,a.y,b.x,b.y,paint);return;
        }
        float step = dp(28) * zoom;
        float ox = (panX % step + step) % step;
        float oy = (panY % step + step) % step;
        for (float x = left + ox; x < right; x += step) canvas.drawLine(x, top, x, bottom, paint);
        for (float y = top + oy; y < bottom; y += step) canvas.drawLine(left, y, right, y, paint);
    }

    private void drawNativeSolid(Canvas canvas, float cx, float cy) {
        if(sectionPlane!=null&&sectionSource!=nativeMesh)requestSection();
        projectNativeMesh(cx, cy);
        projectedTriangles.sort((a, b) -> Float.compare(a.depth, b.depth));
        if(displayMode!=DISPLAY_SHADED){drawSeeThroughSolid(canvas);drawFilletSelection(canvas);return;}
        int vertexValues=projectedTriangles.size()*6;
        if(surfaceVertices.length<vertexValues){surfaceVertices=new float[vertexValues];surfaceColors=new int[vertexValues/2];}
        int position=0,colorIndex=0;
        for (ProjectedTriangle triangle : projectedTriangles) {
            int color;
            if (highlighted(triangle)) color=Color.rgb(255,190,70);
            else color=meshFaceColor(triangle);
            for(int i=0;i<3;i++){surfaceVertices[position++]=triangle.sx[i];surfaceVertices[position++]=triangle.sy[i];surfaceColors[colorIndex++]=color;}
        }
        // Submit ordered surfaces together; only true display boundaries are stroked below.
        // Disabling AA on adjacent filled patches prevents internal seam lines.
        boolean antiAlias=paint.isAntiAlias();paint.setAntiAlias(false);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        if(vertexValues>0)canvas.drawVertices(Canvas.VertexMode.TRIANGLES,vertexValues,surfaceVertices,0,null,0,surfaceColors,0,null,0,0,paint);
        paint.setAntiAlias(antiAlias);
        for (ProjectedTriangle triangle : projectedTriangles) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(triangle.faceId == selectedFace ? 2.6f : 1.35f));
            paint.setColor(triangle.faceId == selectedFace ? Color.rgb(225, 120, 10) : ACCENT);
            for (int edge = 0; edge < 3; edge++) {
                if (isVisibleCadEdge(triangle, edge)) {
                    int next = (edge + 1) % 3;
                    for(double[] interval:HiddenLineVisibility.segments(triangle.sx[edge],triangle.sy[edge],triangle.q[edge],
                            triangle.sx[next],triangle.sy[next],triangle.q[next],depthSurfaces)) {
                        float dx=triangle.sx[next]-triangle.sx[edge],dy=triangle.sy[next]-triangle.sy[edge];
                        canvas.drawLine(triangle.sx[edge]+dx*(float)interval[0],triangle.sy[edge]+dy*(float)interval[0],
                            triangle.sx[edge]+dx*(float)interval[1],triangle.sy[edge]+dy*(float)interval[1],paint);
                    }
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        drawFilletSelection(canvas);
        if (mode.equals("Szkic") && !sketchPlaneChosen) {
            text(canvas, "Wybierz płaszczyznę bazową lub dotknij ściany", cx, cy - dp(190),
                14, INK, true, Paint.Align.CENTER);
        }
    }

    private boolean highlighted(ProjectedTriangle triangle) {
        if(showingSection())return false;// section faces are display-only, not model faces
        return triangle.faceId == selectedFace || (mode.equals("Gwint")&&threadSelections.contains(triangle.faceId)) || (mode.equals("Powłoka")&&shellSelections.containsKey(triangle.faceId)) || (mode.equals("Pochyl ściany")&&draftSelections.containsKey(triangle.faceId)) || (featurePreview==null&&((highlightBody>0&&faceBodies.getOrDefault(triangle.faceId,0)==highlightBody)||(highlightFeature!=Integer.MIN_VALUE&&faceFeatures.getOrDefault(triangle.faceId,Integer.MIN_VALUE)==highlightFeature)));
    }
    /** Recomputes the display-only section for the current model in the background. */
    private void requestSection() {
        if(sectionPlane==null||nativeMesh==null||project.importedMeshSource!=null||!project.hasSolid())return;
        final double[] plane=sectionPlane.clone(),source=nativeMesh;final double[] base,features;
        try{base=evaluationBase()==null?null:evaluationBase().clone();features=kernelFeatures();}catch(IllegalArgumentException error){operationMessage="Przekrój: "+error.getMessage();return;}
        final double w=project.width,h=project.height,d=project.depth,r=project.importedStep?0:project.filletRadius;
        final String step=project.importedStep?importedSourceFile().getAbsolutePath():null;
        sectionSource=source;
        if(sectionWorker==null)sectionWorker=new GeometryPreviewWorker(task->post(task));
        sectionWorker.submit(()->{double[] packed=NativeCadEngine.sectionModel(w,h,d,r,base,features,step,plane);if(packed.length==0)throw NativeCadEngine.lastFailure();return packed;},(packed,error)->{
            if(sectionPlane==null||!java.util.Arrays.equals(plane,sectionPlane)||source!=nativeMesh)return;
            try{if(error!=null)throw error;sectionResult=new CadModelResult(packed);}
            catch(Exception failure){sectionResult=null;sectionPlane=null;operationMessage="Nie wyznaczono przekroju: "+failure.getMessage();}
            invalidate();
        });
    }
    private void showSectionDialog() {
        if(project.importedMeshSource!=null||!project.hasSolid()){operationMessage="Przekrój wymaga bryły B-Rep";invalidate();return;}
        final double[] face=selectedPlanarFace();
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText("Przekrój jest tylko widokiem: nie zmienia modelu, historii ani eksportu. Pokazywana jest część za płaszczyzną.");form.addView(info);
        List<String> choices=new ArrayList<>(java.util.Arrays.asList("Płaszczyzna YZ (prostopadła do X)","Płaszczyzna XZ (prostopadła do Y)","Płaszczyzna XY (prostopadła do Z)"));
        if(face!=null)choices.add("Płaszczyzna zaznaczonej ściany");
        android.widget.Spinner plane=new android.widget.Spinner(getContext());plane.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,choices));form.addView(plane);
        int current=sectionPlane==null?0:Math.abs(sectionPlane[3])>.5?0:Math.abs(sectionPlane[4])>.5?1:2;plane.setSelection(current);
        double[] centre={meshCenterX,meshCenterY,meshCenterZ};
        EditText offset=labeledCoordinate(form,"Położenie płaszczyzny [mm] (X, Y lub Z; dla ściany: odsunięcie)",sectionPlane!=null&&current<3?sectionPlane[current]:centre[current]);
        if(face!=null&&sectionPlane==null){plane.setSelection(3);setLengthText(offset,0);}
        final int[] shownPlane={plane.getSelectedItemPosition()};
        plane.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position==shownPlane[0])return;shownPlane[0]=position;setLengthText(offset,position<3?centre[position]:0);}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        android.widget.CheckBox flip=new android.widget.CheckBox(getContext());flip.setText("Pokaż drugą stronę");flip.setChecked(sectionPlane!=null&&sectionPlane[3+current]<0);form.addView(flip);
        new PropertyBuilder().setTitle("Przekrój").setView(form)
            .setNeutralButton("Wyłącz przekrój",(d,w)->{sectionPlane=null;sectionResult=null;sectionSource=null;if(sectionWorker!=null)sectionWorker.cancel();operationMessage="Przekrój wyłączony";invalidate();})
            .setNegativeButton("Anuluj",null).setPositiveButton("Pokaż przekrój",(d,w)->{
                try {
                    int choice=plane.getSelectedItemPosition();double value=parseCoordinate(offset);double sign=flip.isChecked()?-1:1;double[] next=new double[6];
                    if(choice<3){next[choice]=value;next[3+choice]=sign;}
                    else {for(int i=0;i<3;i++){next[i]=face[i]+face[3+i]*value;next[3+i]=face[3+i]*sign;}}
                    sectionPlane=next;sectionResult=null;sectionSource=null;requestSection();operationMessage="Obliczanie przekroju…";
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                invalidate();
            }).show();
    }

    /** Edge (wireframe) and transparent styles: all face boundaries, hidden ones dashed. */
    private void drawSeeThroughSolid(Canvas canvas) {
        boolean transparent=displayMode==DISPLAY_TRANSPARENT;
        List<ProjectedTriangle> all=new ArrayList<>(backTriangles.size()+projectedTriangles.size());all.addAll(backTriangles);all.addAll(projectedTriangles);
        all.sort((a,b)->Float.compare(a.depth,b.depth));
        int vertexValues=all.size()*6;
        if(surfaceVertices.length<vertexValues){surfaceVertices=new float[vertexValues];surfaceColors=new int[vertexValues/2];}
        int position=0,colorIndex=0;
        for(ProjectedTriangle triangle:all) {
            boolean picked=highlighted(triangle);
            if(!transparent&&!picked)continue;
            int base=picked?Color.rgb(255,190,70):meshFaceColor(triangle);
            int color=(base&0x00FFFFFF)|((picked?(transparent?150:110):70)<<24);
            for(int i=0;i<3;i++){surfaceVertices[position++]=triangle.sx[i];surfaceVertices[position++]=triangle.sy[i];surfaceColors[colorIndex++]=color;}
        }
        boolean antiAlias=paint.isAntiAlias();paint.setAntiAlias(false);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        if(position>0)canvas.drawVertices(Canvas.VertexMode.TRIANGLES,position,surfaceVertices,0,null,0,surfaceColors,0,null,0,0,paint);
        paint.setAntiAlias(antiAlias);paint.setStyle(Paint.Style.STROKE);
        // Hidden face boundaries first, dashed and lighter, then the visible ones on top.
        android.graphics.PathEffect dashed=new android.graphics.DashPathEffect(new float[]{dp(4),dp(3)},0);
        paint.setPathEffect(dashed);paint.setStrokeWidth(dp(1));paint.setColor(Color.argb(150,120,135,150));
        for(ProjectedTriangle triangle:backTriangles)for(int edge=0;edge<3;edge++)if(displayTopology.neighbours[triangle.meshIndex*3+edge]<0) {
            int next=(edge+1)%3;canvas.drawLine(triangle.sx[edge],triangle.sy[edge],triangle.sx[next],triangle.sy[next],paint);
        }
        paint.setPathEffect(null);
        for(ProjectedTriangle triangle:projectedTriangles) {
            paint.setStrokeWidth(dp(triangle.faceId==selectedFace?2.6f:1.35f));
            paint.setColor(triangle.faceId==selectedFace?Color.rgb(225,120,10):ACCENT);
            for(int edge=0;edge<3;edge++)if(isVisibleCadEdge(triangle,edge)) {
                int next=(edge+1)%3;canvas.drawLine(triangle.sx[edge],triangle.sy[edge],triangle.sx[next],triangle.sy[next],paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSketch(Canvas canvas, float left, float right) {
        if(!project.threePointDraft.isEmpty()) {
            if(threePointPreview!=null)drawSketchEntity(canvas,threePointPreview);
            paint.setColor(ACCENT);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.FILL);
            if(project.threePointDraft.size()==2){double[] a=project.threePointDraft.get(0),b=project.threePointDraft.get(1);drawSketchSegment(canvas,a[0],a[1],b[0],b[1]);}
            for(double[] point:project.threePointDraft){PointF screen=projectSketchPoint(point[0],point[1]);canvas.drawCircle(screen.x,screen.y,dp(4),paint);}
        }
        if(!arcDraft.isEmpty()) {
            paint.setColor(ACCENT);paint.setStrokeWidth(dp(2));
            if(arcPreview!=null)drawCurves(canvas,arcPreview);
            else if(arcDraft.size()==2){double[] a=arcDraft.get(0),b=arcDraft.get(1);drawSketchSegment(canvas,a[0],a[1],b[0],b[1]);}
            for(double[] p:arcDraft){PointF screen=projectSketchPoint(p[0],p[1]);canvas.drawCircle(screen.x,screen.y,dp(4),paint);}
        }
        if(!project.polylineDraft.isEmpty()) {
            paint.setStyle(Paint.Style.STROKE);paint.setColor(ACCENT);paint.setStrokeWidth(dp(2));
            Path path=new Path();
            for(int i=0;i<project.polylineDraft.size();i++) {
                double[] point=project.polylineDraft.get(i);PointF p=projectSketchPoint(point[0],point[1]);
                if(i==0)path.moveTo(p.x,p.y);else path.lineTo(p.x,p.y);
            }
            if(polylinePreview!=null) {PointF p=projectSketchPoint(polylinePreview[0],polylinePreview[1]);path.lineTo(p.x,p.y);}
            if(project.splineDraft && project.polylineDraft.size()>=3){
                try {double[] samples=SketchSpline.sample(SketchSpline.points(project.polylineDraft),24);path.reset();for(int i=0;i<samples.length;i+=2){PointF point=projectSketchPoint(samples[i],samples[i+1]);if(i==0)path.moveTo(point.x,point.y);else path.lineTo(point.x,point.y);}}
                catch(IllegalArgumentException ignored){}
            }
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            for(double[] point:project.polylineDraft) {PointF p=projectSketchPoint(point[0],point[1]);canvas.drawCircle(p.x,p.y,dp(4),paint);}
            double[] start=project.polylineDraft.get(0);PointF p=projectSketchPoint(start[0],start[1]);
            text(canvas,"Początek",p.x+dp(10),p.y-dp(10),10,ACCENT,true);
        }
        List<SketchPrimitive> displayedSketch=dragSolvedPreview==null?project.sketch:dragSolvedPreview;
        for (SketchPrimitive entity : displayedSketch) {
            SketchPrimitive shown=dragSolvedPreview==null&&dragPreview!=null&&dragPreview.id.equals(entity.id)?dragPreview:entity;
            drawSketchEntity(canvas,shown);
            if(inSketch() && showSketchDimensions)drawSketchDrivingDimensions(canvas,shown);
        }
        if(inSketch() && showSketchDimensions)drawPositionDimensions(canvas,displayedSketch);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(Color.rgb(117,70,160));
        if(inSketch())for(SketchConstraints.Link link:project.coincidences)for(SketchPrimitive p:displayedSketch)if(p.id.equals(link.a)) {
            double[] a=SketchConstraints.anchor(p,link.ai);
            if(sketchPointVisible(a[0],a[1])) {
                PointF point=projectSketchPoint(a[0],a[1]);
                if(link.kind.equals("fixed"))canvas.drawRect(point.x-dp(6),point.y-dp(6),point.x+dp(6),point.y+dp(6),paint);
                else if(link.kind.equals("coincident"))canvas.drawCircle(point.x,point.y,dp(6),paint);
                else {
                    SketchPrimitive second=p;
                    for(SketchPrimitive q:displayedSketch)if(q.id.equals(link.b))second=q;
                    double[] b=SketchConstraints.anchor(second,link.bi);
                    if(sketchPointVisible(b[0],b[1])) {
                        PointF other=projectSketchPoint(b[0],b[1]);paint.setStyle(Paint.Style.FILL);
                        text(canvas,NonlinearConstraints.relation(link.kind)?NonlinearConstraints.symbol(link.kind):link.kind.equals("horizontal")?"H":"V",(point.x+other.x)/2+dp(8),(point.y+other.y)/2-dp(8),10,Color.rgb(117,70,160),true);
                        paint.setStyle(Paint.Style.STROKE);
                    }
                }
            }
        }
        if(coincidenceMode)for(SketchPrimitive p:project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++) {
            double[] a=SketchConstraints.anchor(p,i);if(!sketchPointVisible(a[0],a[1]))continue;
            PointF point=projectSketchPoint(a[0],a[1]);
            paint.setColor(p.id.equals(coincidenceFirst)&&i==coincidenceAnchor?Color.rgb(245,145,20):ACCENT);
            canvas.drawCircle(point.x,point.y,dp(7),paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (sketchRect != null && sketching) {
            drawSketchPreview(canvas, sketchTool, sketchStartX, sketchStartY,
                sketchEndX, sketchEndY);
            double[] a=screenToSketch(sketchStartX,sketchStartY),b=screenToSketch(sketchEndX,sketchEndY);
            double angle=Math.toDegrees(Math.atan2(b[1]-a[1],b[0]-a[0]));
            text(canvas,formatMm(Math.hypot(b[0]-a[0],b[1]-a[1]))+String.format(java.util.Locale.US," · %.1f°",angle),
                sketchEndX+dp(14),sketchEndY-dp(20),13,ACCENT,true);
        }
        if(snapMarker!=null) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(ACCENT);
            canvas.drawCircle(snapMarker.x,snapMarker.y,dp(7),paint);paint.setStyle(Paint.Style.FILL);
        }

        if (mode.equals("Szkic") || mode.equals("Edytuj szkic")) {
            text(canvas, (mode.equals("Edytuj szkic") ? "Edytuj szkic na: " : "Szkic na: ") + sketchPlane,
                (left + right) / 2f, dp(151), 12, ACCENT, true, Paint.Align.CENTER);
        }
    }

    private void drawCurves(Canvas canvas,double[] curves) {
        double[] points=SketchCurves.samples(curves,96);
        for(int i=0;i<points.length-2;i+=2)drawSketchSegment(canvas,points[i],points[i+1],points[i+2],points[i+3]);
    }

    /** Persist a sketch draft without rebuilding the unchanged solid at each construction point. */
    private boolean commitSketchDraft(java.util.function.Consumer<CadProject> change) {
        try {
            String baseline=project.toJson().toString();CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));
            change.accept(candidate);projectRepository.save(candidate);project=candidate;
            undoStack.push(baseline);while(undoStack.size()>50)undoStack.removeLast();redoStack.clear();return true;
        }catch(Exception error){operationMessage="Nie zapisano zmiany szkicu: "+error.getMessage();return false;}
    }
    private void threePointTap(float x,float y) {
        double[] point=snappedSketchPoint(x,y,null,null,null);final SketchPrimitive[] created={null};
        if(commitSketchDraft(p->created[0]=ThreePointDraft.append(p,sketchTool,point,UUID.randomUUID().toString()))) {
            threePointPreview=null;
            if(created[0]!=null){selectedSketchId=created[0].id;operationMessage="Utworzono "+sketchTool+". Profil można wyciągnąć lub wyciąć po zakończeniu szkicu.";}
            else operationMessage=SketchShapes.instruction(sketchTool,project.threePointDraft.size());
        }
        invalidate();
    }
    private void showThreePointActions(String requested) {
        new PropertyBuilder().setTitle(project.threePointTool+" · "+project.threePointDraft.size()+"/3 punkty")
            .setItems(new String[]{"Kontynuuj konstrukcję","Cofnij ostatni punkt","Anuluj konstrukcję"},(d,index)->{
                if(index==0){mode="Szkic";sketchTool=project.threePointTool;return;}
                if(commitSketchDraft(p->{if(index==1)p.threePointDraft.remove(p.threePointDraft.size()-1);else p.threePointDraft.clear();if(p.threePointDraft.isEmpty())p.threePointTool="";})) {
                    threePointPreview=null;snapMarker=null;
                    if(index==2&&requested!=null)activateTopTool(requested);
                }invalidate();
            }).show();
    }

    private void arcTap(float x,float y) {
        double[] point=snappedSketchPoint(x,y,null,null,null);
        if(arcDraft.size()<2) {
            if(arcDraft.size()==1&&Math.hypot(point[0]-arcDraft.get(0)[0],point[1]-arcDraft.get(0)[1])<.01){operationMessage="Koniec musi być różny od początku";invalidate();return;}
            arcDraft.add(point);operationMessage=arcDraft.size()==1?"Łuk: wskaż koniec":"Łuk: wskaż punkt pośredni po właściwej stronie cięciwy";
        }else {
            double[] a=arcDraft.get(0),b=arcDraft.get(1);
            try {
                SketchPrimitive arc=new SketchPrimitive(UUID.randomUUID().toString(),SketchCurves.arc(a[0],a[1],point[0],point[1],b[0],b[1]),false);
                checkpoint();project.sketch.add(arc.withConstruction(project.drawingConstruction));selectedSketchId=arc.id;arcDraft.clear();arcPreview=null;
                projectRepository.save(project);operationMessage="Zapisano łuk. Edytuj pozwala przesuwać jego trzy punkty; Połącz kontur łączy linie i łuki.";
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        }
        invalidate();
    }

    private void showArcDraftActions(String requested) {
        new PropertyBuilder().setTitle("Niedokończony łuk · "+arcDraft.size()+"/3 punkty")
            .setItems(new String[]{"Kontynuuj łuk","Anuluj rozpoczęty łuk"},(d,index)-> {
                if(index==1){arcDraft.clear();arcPreview=null;snapMarker=null;if(requested!=null)activateTopTool(requested);}
            }).show();
    }

    private void showCurveEditor(SketchPrimitive entity) {
        if(!entity.closedGeometry()){showCurveSegmentEditor(entity,0);return;}
        String[] items=new String[entity.curves.length/7];
        for(int i=0;i<items.length;i++)items[i]="Segment "+(i+1)+" · "+(entity.curves[i*7]==1?"łuk":"linia");
        new PropertyBuilder().setTitle("Kontur krzywy · edytuj segment").setItems(items,(d,index)->showCurveSegmentEditor(entity,index))
            .setNegativeButton("Zamknij",null).show();
    }

    private void showCurveSegmentEditor(SketchPrimitive entity,int segment) {
        int offset=segment*7;LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText(entity.curves[offset]==1?"Łuk analityczny · R "+formatMm(SketchCurves.arc(entity.curves,offset).r)+". Przeciągaj końce i punkt pośredni w szkicu.":"Linia konturu. Zmiana końców przesuwa też końce sąsiednich segmentów.");form.addView(info);
        String[] labels={"Początek X [mm]","Początek Y [mm]","Pośredni X [mm]","Pośredni Y [mm]","Koniec X [mm]","Koniec Y [mm]"};EditText[] fields=new EditText[6];String[] originalText=new String[6];
        for(int i=0;i<6;i++){fields[i]=coordinateInput(labels[i],entity.curves[offset+1+i]);fields[i].setEnabled(entity.curves[offset]==1||i<2||i>3);
            originalText[i]=fields[i].getText().toString();
            android.widget.TextView label=new android.widget.TextView(getContext());label.setText(Units.label(labels[i],project.units));form.addView(label);form.addView(fields[i]);}
        new PropertyBuilder().setTitle(entity.closedGeometry()?"Segment "+(segment+1):"Łuk · trzy punkty").setView(form)
            .setNeutralButton("Usuń element",(d,w)->deleteSketchEntity(entity.id)).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double[] s=entity.curves.clone();for(int i=0;i<6;i++)
                        if(!fields[i].getText().toString().equals(originalText[i]))s[offset+1+i]=parseCoordinate(fields[i]);
                    if(entity.closedGeometry()) {
                        int previous=(offset-7+s.length)%s.length,next=(offset+7)%s.length;
                        s[previous+5]=s[offset+1];s[previous+6]=s[offset+2];s[next+1]=s[offset+5];s[next+2]=s[offset+6];
                    }
                    replaceSketchEntity(entity,new SketchPrimitive(entity.id,s,entity.closedGeometry()));
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    private void drawSketchEntity(Canvas canvas, SketchPrimitive entity) {
        boolean selected = entity.id.equals(selectedSketchId) || highlightSketch || extrusionSelections.contains(entity.id)&&mode.equals("Profile wyciągnięcia");
        PointF p1 = projectSketchPoint(entity.x1, entity.y1);
        PointF p2 = projectSketchPoint(entity.x2, entity.y2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(selected ? 3.5f : 2f));
        paint.setColor(selected ? Color.rgb(245, 145, 20) : Color.rgb(30, 105, 210));
        if(entity.construction) paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(8),dp(5)},0));
        if(SketchSpline.is(entity)) {double[] points=SketchSpline.sample(entity.vertices,24);for(int i=0;i<points.length-2;i+=2)drawSketchSegment(canvas,points[i],points[i+1],points[i+2],points[i+3]);}
        else if(entity.curves!=null)drawCurves(canvas,entity.curves);
        else if(entity.vertices!=null) {
            for(int i=0;i<entity.vertices.length;i+=2) {
                int next=(i+2)%entity.vertices.length;
                drawSketchSegment(canvas,entity.vertices[i],entity.vertices[i+1],entity.vertices[next],entity.vertices[next+1]);
            }
        } else if (entity.type.equals("Linia") || entity.type.equals("Pomocnicza")) {
            drawSketchSegment(canvas,entity.x1,entity.y1,entity.x2,entity.y2);
        } else if (entity.isEllipse()) {
            double[] e=ellipsePoints(entity,96);
            for(int i=0;i<e.length;i+=2){int j=(i+2)%e.length;drawSketchSegment(canvas,e[i],e[i+1],e[j],e[j+1]);}
        } else if (entity.type.equals("Prostokąt")) {
            drawSketchSegment(canvas,entity.x1,entity.y1,entity.x2,entity.y1);
            drawSketchSegment(canvas,entity.x2,entity.y1,entity.x2,entity.y2);
            drawSketchSegment(canvas,entity.x2,entity.y2,entity.x1,entity.y2);
            drawSketchSegment(canvas,entity.x1,entity.y2,entity.x1,entity.y1);
        } else {
            double radius = Math.hypot(entity.x2 - entity.x1, entity.y2 - entity.y1);
            for (int i = 0; i < 96; i++) {
                double angle = Math.PI * 2.0 * i / 96.0, next = Math.PI * 2.0 * (i+1) / 96.0;
                drawSketchSegment(canvas,entity.x1+radius*Math.cos(angle),entity.y1+radius*Math.sin(angle),
                    entity.x1+radius*Math.cos(next),entity.y1+radius*Math.sin(next));
            }
        }
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        if (selected && inSketch()) {
            paint.setColor(Color.rgb(245, 145, 20));
            if(entity.curves!=null) {
                for(int i=0;i<entity.curves.length;i+=7) {
                    drawSketchHandle(canvas,entity.curves[i+1],entity.curves[i+2]);
                    if(!entity.closedGeometry())drawSketchHandle(canvas,entity.curves[i+5],entity.curves[i+6]);
                    if(entity.curves[i]==1)drawSketchHandle(canvas,entity.curves[i+3],entity.curves[i+4]);
                }
            }else if(entity.vertices!=null) for(int i=0;i<entity.vertices.length;i+=2) {
                drawSketchHandle(canvas,entity.vertices[i],entity.vertices[i+1]);
            } else {
                drawSketchHandle(canvas,entity.x1,entity.y1);
                drawSketchHandle(canvas,entity.x2,entity.y2);
            }
            if (entity.type.equals("Prostokąt")) {
                PointF p3 = projectSketchPoint(entity.x2, entity.y1);
                PointF p4 = projectSketchPoint(entity.x1, entity.y2);
                drawSketchHandle(canvas,entity.x2,entity.y1);
                drawSketchHandle(canvas,entity.x1,entity.y2);
            }
            String size = entity.curves!=null?(entity.closedGeometry()?"Kontur · "+entity.curves.length/7+" segmentów":"Łuk · R "+formatMm(SketchCurves.arc(entity.curves,0).r)):entity.vertices!=null ? "Kontur · "+entity.vertices.length/2+" wierzchołków" : entity.type.equals("Okrąg")
                ? "R "+formatMm(Math.hypot(entity.x2-entity.x1,entity.y2-entity.y1))
                : entity.type.equals("Prostokąt")
                    ? Units.number(Math.abs(entity.x2-entity.x1),project.units,project.displayDecimals)+" × "+formatMm(Math.abs(entity.y2-entity.y1))
                    : formatMm(Math.hypot(entity.x2-entity.x1,entity.y2-entity.y1));
            if(showSketchDimensions&&entity.driving==null&&sketchPointVisible(entity.x2,entity.y2))text(canvas, size, p2.x + dp(12), p2.y - dp(10), 11, INK, true);
        }
    }

    /** Closed polyline approximation of an ellipse for display and picking only; B-Rep uses the exact curve. */
    private static double[] ellipsePoints(SketchPrimitive e,int count) {
        double a=e.major(),ux=(e.x2-e.x1)/a,uy=(e.y2-e.y1)/a;double[] r=new double[count*2];
        for(int i=0;i<count;i++){double t=2*Math.PI*i/count,c=Math.cos(t)*a,s=Math.sin(t)*e.minor;r[2*i]=e.x1+ux*c-uy*s;r[2*i+1]=e.y1+uy*c+ux*s;}
        return r;
    }
    /** Exact numeric ellipse: centre, semi-axes and rotation of the major axis. */
    private void showEllipseEditor(SketchPrimitive entity) {
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText cx=labeledCoordinate(form,"Środek X [mm]",entity.x1),cy=labeledCoordinate(form,"Środek Y [mm]",entity.y1);
        EditText major=labeledCoordinate(form,"Półoś wielka a [mm]",entity.major()),minor=labeledCoordinate(form,"Półoś mała b [mm]",entity.minor);
        EditText angle=labeledCoordinate(form,"Obrót osi wielkiej [°]",SketchDimensions.angle(entity));
        android.widget.CheckBox construction=new android.widget.CheckBox(getContext());construction.setText("Geometria pomocnicza");construction.setChecked(entity.construction);form.addView(construction);
        new PropertyBuilder().setTitle("Elipsa").setView(form).setNeutralButton("Usuń element",(d,w)->deleteSketchEntity(entity.id)).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zastosuj",(d,w)->{
                try {
                    double x=parseCoordinate(cx),y=parseCoordinate(cy),a=parseCoordinate(major),b=parseCoordinate(minor),t=Math.toRadians(parseCoordinate(angle));
                    SketchPrimitive changed=SketchPrimitive.ellipse(entity.id,x,y,x+a*Math.cos(t),y+a*Math.sin(t),b).withConstruction(construction.isChecked());
                    replaceSketchEntity(entity,changed,true);
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }
    private void drawEditableDimension(Canvas canvas,SketchPrimitive entity,String kind,int segment,PointF a,PointF b,PointF inside,String label) {
        drawingDimension=new DimensionHit(entity.id,kind,segment,null);
        drawSketchDimension(canvas,a,b,inside,label);drawingDimension=null;
    }
    private void drawAngleDimension(Canvas canvas,SketchPrimitive entity) {
        PointF a=projectSketchPoint(entity.x1,entity.y1),b=projectSketchPoint(entity.x2,entity.y2),axis=projectSketchPoint(entity.x1+1,entity.y1);
        float start=(float)Math.toDegrees(Math.atan2(axis.y-a.y,axis.x-a.x)),end=(float)Math.toDegrees(Math.atan2(b.y-a.y,b.x-a.x));
        float sweep=(float)Math.IEEEremainder(end-start,360),radius=dp(36);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.rgb(117,70,160));paint.setStrokeWidth(dp(1));
        canvas.drawArc(new RectF(a.x-radius,a.y-radius,a.x+radius,a.y+radius),start,sweep,false,paint);paint.setStyle(Paint.Style.FILL);
        double angle=Math.toRadians(start+sweep/2);float x=a.x+(float)Math.cos(angle)*dp(52),y=a.y+(float)Math.sin(angle)*dp(52);
        String label=String.format(java.util.Locale.US,"α %.2f°",SketchDimensions.angle(entity));paint.setTextSize(dp(10));float half=paint.measureText(label)/2+dp(10);
        RectF bounds=new RectF(x-half,y-dp(18),x+half,y+dp(8));
        for(int attempt=0;attempt<12;attempt++) {
            boolean collision=false;for(DimensionHit hit:dimensionHits)if(RectF.intersects(bounds,hit.bounds)){collision=true;break;}
            if(!collision&&bounds.left>leftWidth()&&bounds.right<viewportRight()&&bounds.top>viewTop()+dp(46))break;
            double candidate=angle+(attempt%4+1)*Math.PI/2;float distance=dp(60+attempt/4*24);x=a.x+(float)Math.cos(candidate)*distance;y=a.y+(float)Math.sin(candidate)*distance;bounds.set(x-half,y-dp(18),x+half,y+dp(8));
        }
        text(canvas,label,x,y,10,Color.rgb(117,70,160),true,Paint.Align.CENTER);dimensionHits.add(new DimensionHit(entity.id,"angle",0,bounds));
    }
    private boolean handleDimensionTouch(MotionEvent event) {
        if(!inSketch()||!showSketchDimensions)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN&&event.getPointerCount()==1&&isInsideViewport(event.getX(),event.getY())) {
            DimensionHit nearest=null;double best=Double.POSITIVE_INFINITY;
            for(DimensionHit hit:dimensionHits)if(hit.bounds.contains(event.getX(),event.getY())){
                double distance=Math.hypot(event.getX()-hit.bounds.centerX(),event.getY()-hit.bounds.centerY());if(distance<best){nearest=hit;best=distance;}
            }
            if(nearest!=null){dimensionTouch=nearest;dimensionTouchX=event.getX();dimensionTouchY=event.getY();return true;}
        }
        if(dimensionTouch==null)return false;
        if(event.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL||Math.hypot(event.getX()-dimensionTouchX,event.getY()-dimensionTouchY)>dp(12)){dimensionTouch=null;lastDimensionHit=null;return false;}
        if(action==MotionEvent.ACTION_UP) {
            DimensionHit hit=dimensionTouch;dimensionTouch=null;long now=android.os.SystemClock.uptimeMillis();selectedSketchId=hit.entityId;
            if(lastDimensionHit!=null&&lastDimensionHit.key().equals(hit.key())&&now-lastDimensionTime<android.view.ViewConfiguration.getDoubleTapTimeout()) {lastDimensionHit=null;showDimensionValueEditor(hit);}
            else {lastDimensionHit=hit;lastDimensionTime=now;invalidate();}
        }
        return true;
    }
    private void showDimensionValueEditor(DimensionHit hit) {
        if(hit.entityId.startsWith("link:")){showPositionDimensionEditor(Integer.parseInt(hit.entityId.substring(5)));return;}
        SketchPrimitive found=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(hit.entityId))found=p;
        if(found==null)return;final SketchPrimitive source=found;beginSketchWork();mode="Edytuj szkic";
        if(source.isEllipse()) {
            boolean majorAxis=hit.kind.equals("ellipseMajor");
            EditText axis=coordinateInput(majorAxis?"Półoś wielka a [mm]":"Półoś mała b [mm]",majorAxis?source.major():source.minor);
            new PropertyBuilder().setTitle(majorAxis?"Półoś wielka elipsy":"Półoś mała elipsy").setView(axis).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
                try {
                    double value=parseCoordinate(axis),a=source.major(),ux=(source.x2-source.x1)/a,uy=(source.y2-source.y1)/a;
                    SketchPrimitive changed=majorAxis?SketchPrimitive.ellipse(source.id,source.x1,source.y1,source.x1+ux*value,source.y1+uy*value,source.minor):SketchPrimitive.ellipse(source.id,source.x1,source.y1,source.x2,source.y2,value);
                    replaceSketchEntity(source,changed.withConstruction(source.construction),true);
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();return;
        }
        double value=hit.kind.equals("angle")?SketchDimensions.angle(source):hit.kind.equals("width")?Math.abs(source.x2-source.x1):hit.kind.equals("height")?Math.abs(source.y2-source.y1):SketchDimensions.length(source);
        if(hit.kind.equals("side")){int i=hit.segment*2,j=(i+2)%source.vertices.length;value=Math.hypot(source.vertices[j]-source.vertices[i],source.vertices[j+1]-source.vertices[i+1]);}
        if(hit.kind.equals("arcRadius"))value=SketchCurves.arc(source.curves,hit.segment*7).r;
        if(hit.kind.equals("diameter"))value=2*SketchDimensions.length(source);
        EditText input=coordinateInput(hit.kind.equals("angle")?"Kąt [°]":hit.kind.equals("diameter")?"Średnica Ø [mm]":"Wymiar [mm]",value);
        final int dimIndex=hit.kind.equals("angle")||hit.kind.equals("height")?1:0;
        final String dimKey=(hit.kind.equals("side")||hit.kind.equals("arcRadius"))?null:UserParameters.dimKey(project.sketchId,source.id,dimIndex);
        if(dimKey!=null&&!hit.kind.equals("diameter"))bound(input,dimKey);
        new PropertyBuilder().setTitle(hit.kind.equals("angle")?"Edytuj kąt [°]":hit.kind.equals("diameter")?"Edytuj średnicę Ø [mm]":"Edytuj wymiar [mm]").setView(input).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                if(dimKey!=null&&UserParameters.isExpression(input.getText().toString())) {
                    if(hit.kind.equals("diameter"))throw new IllegalArgumentException("Parametrem steruj promieniem (wymiar długości okręgu), nie średnicą");
                    // Driven by a parameter: store the binding and let the parameter pass recompute the sketch and model.
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));
                    UserParameters.bind(next,dimKey,input.getText().toString());UserParameters.apply(next);commitProjectAsync(next,baseline,"Wymiar powiązano z parametrem");return;
                }
                if(dimKey!=null)project.parameterBindings.remove(dimKey);
                replaceSketchEntity(source,dimensionChanged(source,hit.kind,hit.segment,parseCoordinate(input)));
            }
            catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
        input.requestFocus();input.post(()->{android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);keyboard.showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
    }
    private void showPositionDimensionEditor(int index) {
        if(index<0||index>=project.coincidences.size())return;
        final SketchConstraints.Link link=project.coincidences.get(index);if(!SketchConstraints.position(link.kind))return;
        beginSketchWork();mode="Edytuj szkic";
        double current=SketchConstraints.measure(project.sketch,link);
        EditText input=coordinateInput(link.kind.equals("dimX")?"Odległość X [mm]":"Odległość Y [mm]",Math.abs(current));
        new PropertyBuilder().setTitle(SketchConstraints.fromOrigin(link)?"Położenie od początku szkicu":"Odległość między punktami").setView(input).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                double value=parseCoordinate(input);if(!Double.isFinite(value)||value<0||value>10000)throw new IllegalArgumentException("Niepoprawny wymiar");
                double signed=current<0||(current==0&&link.x<0)?-value:value;
                List<SketchConstraints.Link> links=new ArrayList<>(project.coincidences);links.set(links.indexOf(link),SketchConstraints.withValue(link,signed));
                if(applySolvedSketch(SketchConstraints.resolve(project.sketch,links),links))operationMessage="Zmieniono wymiar położenia na "+formatMm(value);
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
            invalidate();
        }).show();
    }

    /** Adds driving X and Y position dimensions of the element's first point from the sketch origin. */
    private void addOriginPositionDimensions(SketchPrimitive source) {
        if(SketchConstraints.anchorCount(source)==0){operationMessage="Ten element nie ma punktu do zwymiarowania";invalidate();return;}
        double[] point=SketchConstraints.anchor(source,0);int added=0;
        for(String kind:new String[]{"dimX","dimY"}) {
            SketchConstraints.Link link=new SketchConstraints.Link(kind,source.id,0,source.id,0,kind.equals("dimX")?point[0]:point[1],0);
            boolean exists=false;for(SketchConstraints.Link l:project.coincidences)if(l.kind.equals(kind)&&l.a.equals(source.id)&&l.ai==0&&SketchConstraints.fromOrigin(l))exists=true;
            if(exists)continue;
            try {
                List<SketchPrimitive> solved=SketchConstraints.connect(project.sketch,project.coincidences,link);
                List<SketchConstraints.Link> links=new ArrayList<>(project.coincidences);links.add(link);
                if(applySolvedSketch(solved,links))added++;
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();return;}
        }
        operationMessage=added==0?"Położenie jest już zwymiarowane":"Dodano wymiary położenia X/Y od początku szkicu. Dwukrotnie dotknij wartości, aby ją zmienić.";
        invalidate();
    }

    private SketchPrimitive dimensionChanged(SketchPrimitive source,String kind,int segment,double value) {
        if(!Double.isFinite(value)||(!kind.equals("angle")&&(value<.01||value>10000)))throw new IllegalArgumentException("Niepoprawny wymiar");
        if(kind.equals("side")) {
            double[] points=source.vertices.clone();int i=segment*2,j=(i+2)%points.length;double length=Math.hypot(points[j]-points[i],points[j+1]-points[i+1]);
            points[j]=points[i]+(points[j]-points[i])*value/length;points[j+1]=points[i+1]+(points[j+1]-points[i+1])*value/length;SketchContours.validate(points);
            return new SketchPrimitive(source.id,points).withConstruction(source.construction);
        }
        if(kind.equals("arcRadius"))return SketchCurves.withRadius(source,segment,value);
        double first=source.type.equals("Prostokąt")?Math.abs(source.x2-source.x1):SketchDimensions.length(source);
        double second=source.type.equals("Prostokąt")?Math.abs(source.y2-source.y1):SketchDimensions.angle(source);
        if(kind.equals("diameter"))value/=2;
        if(kind.equals("height")||kind.equals("angle"))second=value;else first=value;
        return SketchDimensions.apply(source,first,second,true);
    }

    /** Driving X/Y position dimensions (to the sketch origin or between two points). */
    private void drawPositionDimensions(Canvas canvas,List<SketchPrimitive> geometry) {
        for(int index=0;index<project.coincidences.size();index++) {
            SketchConstraints.Link link=project.coincidences.get(index);if(!SketchConstraints.position(link.kind))continue;
            SketchPrimitive a=null,b=null;for(SketchPrimitive p:geometry){if(p.id.equals(link.a))a=p;if(p.id.equals(link.b))b=p;}
            if(a==null||b==null)continue;
            double[] pa=SketchConstraints.anchor(a,link.ai),pb=SketchConstraints.fromOrigin(link)?new double[]{0,0}:SketchConstraints.anchor(b,link.bi);
            boolean horizontal=link.kind.equals("dimX");
            double[] end=horizontal?new double[]{pb[0],pa[1]}:new double[]{pa[0],pb[1]};
            if(!sketchPointVisible(pa[0],pa[1])||!sketchPointVisible(end[0],end[1]))continue;
            PointF from=projectSketchPoint(end[0],end[1]),to=projectSketchPoint(pa[0],pa[1]),reference=projectSketchPoint(pb[0],pb[1]);
            if(Math.hypot(from.x-reference.x,from.y-reference.y)>dp(2)) {
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(117,70,160));
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4),dp(3)},0));canvas.drawLine(reference.x,reference.y,from.x,from.y,paint);paint.setPathEffect(null);
            }
            double value=SketchConstraints.measure(geometry,link);
            drawingDimension=new DimensionHit("link:"+index,link.kind,0,null);
            drawSketchDimension(canvas,from,to,null,(horizontal?"X ":"Y ")+formatMm(Math.abs(value)));drawingDimension=null;
        }
    }

    private void drawSketchDrivingDimensions(Canvas canvas,SketchPrimitive entity) {
        if(entity.vertices!=null) {
            double[] points=SketchSpline.is(entity)?null:entity.vertices;
            if(points!=null)for(int i=0;i<points.length;i+=2){int j=(i+2)%points.length;drawEditableDimension(canvas,entity,"side",i/2,projectSketchPoint(points[i],points[i+1]),projectSketchPoint(points[j],points[j+1]),null,formatMm(Math.hypot(points[j]-points[i],points[j+1]-points[i+1])));}
            return;
        }
        if(entity.curves!=null) {
            for(int i=0;i<entity.curves.length;i+=7)if(entity.curves[i]==1){SketchCurves.Arc arc=SketchCurves.arc(entity.curves,i);double[] mid=arc.point(.5);drawEditableDimension(canvas,entity,"arcRadius",i/7,projectSketchPoint(arc.x,arc.y),projectSketchPoint(mid[0],mid[1]),null,"R "+formatMm(arc.r));}
            return;
        }
        if(entity.isEllipse()) {
            if(!sketchPointVisible(entity.x1,entity.y1))return;
            double a=entity.major(),ux=(entity.x2-entity.x1)/a,uy=(entity.y2-entity.y1)/a;
            drawEditableDimension(canvas,entity,"ellipseMajor",0,projectSketchPoint(entity.x1,entity.y1),projectSketchPoint(entity.x2,entity.y2),null,"a "+formatMm(a));
            drawEditableDimension(canvas,entity,"ellipseMinor",0,projectSketchPoint(entity.x1,entity.y1),projectSketchPoint(entity.x1-uy*entity.minor,entity.y1+ux*entity.minor),null,"b "+formatMm(entity.minor));
            return;
        }
        double[] dimensions=entity.driving!=null?entity.driving:new double[]{entity.type.equals("Prostokąt")?Math.abs(entity.x2-entity.x1):SketchDimensions.length(entity),Math.abs(entity.y2-entity.y1)};
        if(!sketchPointVisible(entity.x1,entity.y1)||!sketchPointVisible(entity.x2,entity.y2))return;
        PointF a=projectSketchPoint(entity.x1,entity.y1),b=projectSketchPoint(entity.x2,entity.y2);
        if(entity.type.equals("Prostokąt")) {
            PointF center=projectSketchPoint((entity.x1+entity.x2)/2,(entity.y1+entity.y2)/2);
            drawEditableDimension(canvas,entity,"width",0,a,projectSketchPoint(entity.x2,entity.y1),center,"W "+formatMm(dimensions[0]));
            drawEditableDimension(canvas,entity,"height",0,a,projectSketchPoint(entity.x1,entity.y2),center,"H "+formatMm(dimensions[1]));
        } else {drawEditableDimension(canvas,entity,entity.type.equals("Okrąg")?"diameter":"length",0,entity.type.equals("Okrąg")?projectSketchPoint(2*entity.x1-entity.x2,2*entity.y1-entity.y2):a,b,null,entity.type.equals("Okrąg")?"Ø "+formatMm(2*dimensions[0]):"L "+formatMm(dimensions[0]));
            if(!entity.type.equals("Okrąg"))drawAngleDimension(canvas,entity);
        }
    }

    private void drawSketchDimension(Canvas canvas,PointF a,PointF b,PointF inside,String label) {
        float dx=b.x-a.x,dy=b.y-a.y,length=(float)Math.hypot(dx,dy);if(length<dp(8))return;
        float nx=-dy/length,ny=dx/length;
        if(inside!=null&&nx*(inside.x-(a.x+b.x)/2)+ny*(inside.y-(a.y+b.y)/2)>0){nx=-nx;ny=-ny;}
        float offset=dp(22),ax=a.x+nx*offset,ay=a.y+ny*offset,bx=b.x+nx*offset,by=b.y+ny*offset;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(117,70,160));
        canvas.drawLine(a.x,a.y,ax+nx*dp(4),ay+ny*dp(4),paint);canvas.drawLine(b.x,b.y,bx+nx*dp(4),by+ny*dp(4),paint);
        canvas.drawLine(ax,ay,bx,by,paint);
        canvas.drawLine(ax-nx*dp(3),ay-ny*dp(3),ax+nx*dp(3),ay+ny*dp(3),paint);
        canvas.drawLine(bx-nx*dp(3),by-ny*dp(3),bx+nx*dp(3),by+ny*dp(3),paint);
        float angle=(float)Math.toDegrees(Math.atan2(dy,dx));if(angle>90||angle<-90)angle+=180;
        canvas.save();canvas.translate((ax+bx)/2,(ay+by)/2);canvas.rotate(angle);
        text(canvas,label,0,-dp(5),10,Color.rgb(117,70,160),true,Paint.Align.CENTER);canvas.restore();
        if(drawingDimension!=null) {
            float width=paint.measureText(label);RectF bounds=new RectF(-width/2-dp(10),-dp(22),width/2+dp(10),dp(7));
            android.graphics.Matrix transform=new android.graphics.Matrix();transform.setRotate(angle);transform.postTranslate((ax+bx)/2,(ay+by)/2);transform.mapRect(bounds);
            dimensionHits.add(new DimensionHit(drawingDimension.entityId,drawingDimension.kind,drawingDimension.segment,bounds));
        }
    }

    private int sketchHandleAt(SketchPrimitive entity, float x, float y) {
        if(entity.curves!=null) {
            int handle=0;double best=dp(14);
            for(int i=0;i<entity.curves.length;i+=7)for(int j:new int[]{1,3,5}) {
                if(j==3&&entity.curves[i]==0||j==5&&entity.closedGeometry())continue;
                double u=entity.curves[i+j],v=entity.curves[i+j+1];if(!sketchPointVisible(u,v))continue;
                PointF p=projectSketchPoint(u,v);double d=Math.hypot(x-p.x,y-p.y);
                if(d<best){best=d;handle=entity.closedGeometry()?(j==3?1000+i/7:1+i/7):(j==1?1:j==5?2:3);}
            }
            return handle;
        }
        if(entity.vertices!=null) {
            int handle=0;double best=dp(14);
            for(int i=0;i<entity.vertices.length;i+=2) {
                if(!sketchPointVisible(entity.vertices[i],entity.vertices[i+1]))continue;
                PointF p=projectSketchPoint(entity.vertices[i],entity.vertices[i+1]);double d=Math.hypot(x-p.x,y-p.y);
                if(d<best) {best=d;handle=i/2+1;}
            }
            return handle;
        }
        double[][] points = {{entity.x1, entity.y1}, {entity.x2, entity.y2},
            {entity.x2, entity.y1}, {entity.x1, entity.y2}};
        int count = entity.type.equals("Prostokąt") ? 4 : 2;
        int handle = 0;
        double nearest = dp(14);
        for (int i = 0; i < count; i++) {
            if(!sketchPointVisible(points[i][0],points[i][1]))continue;
            PointF point = projectSketchPoint(points[i][0], points[i][1]);
            double distance = Math.hypot(x-point.x, y-point.y);
            if (distance < nearest) { nearest = distance; handle = i+1; }
        }
        return handle;
    }

    private PointF projectSketchPoint(double u, double v) {
        double[] projected=projectSketchDepth(u,v);
        return new PointF((float)projected[0],(float)projected[1]);
    }

    private boolean sketchPointVisible(double u,double v) {
        if(!showSolid||inSketch()||showExtrusionSketch)return true;
        double[] p=projectSketchDepth(u,v);
        return HiddenLineVisibility.visible(p[0],p[1],p[2],depthSurfaces);
    }

    private void drawSketchHandle(Canvas canvas,double u,double v) {
        if(!sketchPointVisible(u,v))return;
        PointF p=projectSketchPoint(u,v);canvas.drawCircle(p.x,p.y,dp(5),paint);
    }

    private double sketchSegmentDistance(float x,float y,double u1,double v1,double u2,double v2) {
        double[] a=projectSketchDepth(u1,v1),b=projectSketchDepth(u2,v2);
        return HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,showSolid&&!inSketch()&&!showExtrusionSketch?depthSurfaces:java.util.Collections.emptyList());
    }

    private void drawSketchSegment(Canvas canvas,double u1,double v1,double u2,double v2) {
        double[] a=projectSketchDepth(u1,v1),b=projectSketchDepth(u2,v2);
        if(!showSolid||inSketch()||showExtrusionSketch) {
            canvas.drawLine((float)a[0],(float)a[1],(float)b[0],(float)b[1],paint);
            return;
        }
        for(double[] interval:HiddenLineVisibility.segments(a[0],a[1],a[2],b[0],b[1],b[2],depthSurfaces)) {
            canvas.drawLine((float)(a[0]+(b[0]-a[0])*interval[0]),(float)(a[1]+(b[1]-a[1])*interval[0]),
                (float)(a[0]+(b[0]-a[0])*interval[1]),(float)(a[1]+(b[1]-a[1])*interval[1]),paint);
        }
    }

    private double[] projectSketchDepth(double u, double v) {
        double x = project.sketchOriginX + u * project.sketchUx + v * project.sketchVx;
        double y = project.sketchOriginY + u * project.sketchUy + v * project.sketchVy;
        double z = project.sketchOriginZ + u * project.sketchUz + v * project.sketchVz;
        double yaw = Math.toRadians(cameraYaw), pitch = Math.toRadians(cameraPitch);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch), spitch = Math.sin(pitch);
        x -= meshCenterX; y -= meshCenterY; z -= meshCenterZ;
        double rx = x * cyaw + z * syaw;
        double rz = -x * syaw + z * cyaw;
        double ry = y * cpitch - rz * spitch;
        double finalZ = y * spitch + rz * cpitch;
        double distance = viewDistance();
        double perspective = distance / (distance - finalZ);
        float scale = dp(3.2f) * zoom;
        return new double[]{workCenterX + (float)(rx * scale * perspective),
            workCenterY - (float)(ry * scale * perspective),1.0/(distance-finalZ)};
    }

    private void drawSketchPreview(Canvas canvas, String type, float x1, float y1, float x2, float y2) {
        drawSketchPreview(canvas, type, x1, y1, x2, y2, false);
    }

    private void drawSketchPreview(Canvas canvas, String type, float x1, float y1, float x2, float y2,
            boolean selected) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(selected ? 3.5f : 2f));
        paint.setColor(selected ? Color.rgb(245, 145, 20) : Color.rgb(30, 105, 210));
        if (type.equals("Linia") || type.equals("Pomocnicza")) {
            canvas.drawLine(x1, y1, x2, y2, paint);
        } else if (type.equals("Okrąg")) {
            float radius = (float)Math.hypot(x2 - x1, y2 - y1);
            canvas.drawCircle(x1, y1, radius, paint);
        } else {
            canvas.drawRect(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2), paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (selected) {
            paint.setColor(Color.rgb(245, 145, 20));
            canvas.drawCircle(x1, y1, dp(5), paint);
            canvas.drawCircle(x2, y2, dp(5), paint);
        }
    }

    private void sketchEntitiesAdd(String type, float screenX1, float screenY1,
                                   float screenX2, float screenY2) {
        // Reapply analytic snapping when committing: the float screen preview must
        // not turn a snapped endpoint into a microscopic gap in model coordinates.
        double[] first = snappedSketchPoint(screenX1, screenY1,null,null,null);
        double[] second = snappedSketchPoint(screenX2, screenY2,null,first[0],first[1]);
        double x1 = first[0], y1 = first[1], x2 = second[0], y2 = second[1];
        for(double value:new double[]{x1,y1,x2,y2}) if(!Double.isFinite(value)||Math.abs(value)>5000) {
            operationMessage="Szkic poza zakresem ±5000 mm";invalidate();return;
        }
        if(Math.hypot(x2-x1,y2-y1)<.01 || (type.equals("Prostokąt")
                && (Math.abs(x2-x1)<.01||Math.abs(y2-y1)<.01))) {
            operationMessage="Element jest zbyt mały. Rozsuń punkty szkicu.";invalidate();return;
        }
        checkpoint();
        preserveExtrudedBase();
        commitActiveFeature();
        SketchPrimitive created=SketchShapes.drag(UUID.randomUUID().toString(),type,x1,y1,x2,y2,project.polygonSides).withConstruction(project.drawingConstruction);
        project.sketch.add(created);
        if(type.equals("Linia")){selectedSketchId=created.id;post(()->showSketchDimensionEditor(created));}
        project.featureProfileId = null;
        project.sketchExtruded = false;
        project.featureOperation = "none";
        projectRepository.save(project);
    }

    private double[] screenToSketch(float sx, float sy) {
        PointF origin = projectSketchPoint(0.0, 0.0);
        PointF unitU = projectSketchPoint(1.0, 0.0);
        PointF unitV = projectSketchPoint(0.0, 1.0);
        double ax = unitU.x - origin.x, ay = unitU.y - origin.y;
        double bx = unitV.x - origin.x, by = unitV.y - origin.y;
        double dx = sx - origin.x, dy = sy - origin.y;
        double determinant = ax * by - ay * bx;
        if (Math.abs(determinant) < 1.0e-9) return new double[] {0.0, 0.0};
        double u=(dx * by - dy * bx) / determinant, v=(ax * dy - ay * dx) / determinant;
        // The affine guess is exact only for a view square to the sketch; in a rotated
        // perspective view refine with Newton steps on the real projection.
        for(int iteration=0;iteration<6;iteration++) {
            PointF at=projectSketchPoint(u,v);double ex=sx-at.x,ey=sy-at.y;
            if(!Float.isFinite(at.x)||!Float.isFinite(at.y))break;
            if(ex*ex+ey*ey<1e-6)break;
            double h=Math.max(1e-3,1e-4*Math.max(1,Math.hypot(u,v)));
            PointF pu=projectSketchPoint(u+h,v),pv=projectSketchPoint(u,v+h);
            double jax=(pu.x-at.x)/h,jay=(pu.y-at.y)/h,jbx=(pv.x-at.x)/h,jby=(pv.y-at.y)/h,det=jax*jby-jay*jbx;
            if(Math.abs(det)<1e-12)break;
            double du=(ex*jby-ey*jbx)/det,dv=(jax*ey-jay*ex)/det;
            if(!Double.isFinite(du)||!Double.isFinite(dv))break;
            u+=du;v+=dv;
        }
        return new double[] {u, v};
    }

    private double[] polylinePoint(float x,float y) {
        if(!project.splineDraft && project.polylineDraft.size()>=3) {
            double[] first=project.polylineDraft.get(0);PointF start=projectSketchPoint(first[0],first[1]);
            if(Math.hypot(x-start.x,y-start.y)<dp(14)){snapMarker=start;return first;}
        }
        double[] last=project.polylineDraft.isEmpty()?null:project.polylineDraft.get(project.polylineDraft.size()-1);
        return snappedSketchPoint(x,y,null,last==null?null:last[0],last==null?null:last[1]);
    }

    private boolean finishPolyline(boolean closed) {
        try {
            boolean spline=project.splineDraft;
            List<SketchPrimitive> entities=project.splineDraft?java.util.Collections.singletonList(new SketchPrimitive(UUID.randomUUID().toString(),SketchSpline.points(project.polylineDraft),"Splajn")):closed?java.util.Collections.singletonList(PolylineDraft.closed(project.polylineDraft))
                :PolylineDraft.open(project.polylineDraft);
            checkpoint();preserveExtrudedBase();commitActiveFeature();
            List<SketchPrimitive> marked=new ArrayList<>();for(SketchPrimitive p:entities)marked.add(p.withConstruction(project.drawingConstruction));entities=marked;
            project.sketch.addAll(entities);project.splineDraft=false;project.polylineDraft.clear();polylinePreview=null;snapMarker=null;
            selectedSketchId=entities.get(0).id;project.featureProfileId=null;
            project.featureOperation="none";project.sketchExtruded=false;projectRepository.save(project);
            operationMessage=spline?"Zapisano splajn. Edytuj pozwala przesuwać jego punkty.":closed?"Gotowe: zamknięty profil. Zakończ szkic i wyciągnij lub wytnij."
                :"Gotowe: zapisano krzywą lub otwarty łańcuch.";
            invalidate();return true;
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();return false;}
    }

    private void removePolylinePoint() {
        if(!project.polylineDraft.isEmpty()) {
            project.polylineDraft.remove(project.polylineDraft.size()-1);redoStack.clear();
            polylinePreview=null;snapMarker=null;projectRepository.save(project);
        }
        operationMessage="Cofnięto ostatni punkt polilinii";invalidate();
    }

    private void cancelPolyline() {
        if(!project.polylineDraft.isEmpty())checkpoint();
        project.polylineDraft.clear();project.splineDraft=false;polylinePreview=null;snapMarker=null;projectRepository.save(project);
        operationMessage="Anulowano polilinię. Cofnij przywraca rozpoczęty łańcuch.";invalidate();
    }

    private void showPolylineActions(String deferredTool) {
        new PropertyBuilder().setTitle("Zakończ rozpoczętą polilinię")
            .setItems(new String[]{project.splineDraft?"Zapisz splajn":"Zamknij obwód", project.splineDraft?"Zapisz splajn":"Zapisz otwarty łańcuch", "Anuluj"},(d,which)-> {
                boolean done;
                if(which==2){cancelPolyline();done=true;}else done=finishPolyline(which==0);
                if(done&&deferredTool!=null)activateTopTool(deferredTool);
            }).setNegativeButton("Kontynuuj rysowanie",null).show();
    }

    private List<SketchPrimitive> snapGeometry() {
        List<SketchPrimitive> result=new ArrayList<>(project.sketch);
        if(modelSnapGeometry==null) {
            modelSnapGeometry=new ArrayList<>();
            try {
                CadProject model=SketchTransactions.active(project)?SketchTransactions.baseline(project):project;
                double[] edges=model.hasSolid()&&model.importedMeshSource==null?NativeCadEngine.modelEdges(model.width,model.height,model.depth,model.filletRadius,model.featureBase,packedModelFeatures(model),model.importedStep?new File(projectRepository.importedStepFile().getParentFile(),model.importedStepSource).getAbsolutePath():null):new double[0];
                double[] normal={project.sketchUy*project.sketchVz-project.sketchUz*project.sketchVy,project.sketchUz*project.sketchVx-project.sketchUx*project.sketchVz,project.sketchUx*project.sketchVy-project.sketchUy*project.sketchVx};
                for(int i=0;i+15<edges.length;i+=16) {
                    double[] xy=new double[4];boolean coplanar=true;
                    for(int j=0;j<2;j++) {
                        double x=edges[i+9+3*j]-project.sketchOriginX,y=edges[i+10+3*j]-project.sketchOriginY,z=edges[i+11+3*j]-project.sketchOriginZ;
                        if(Math.abs(x*normal[0]+y*normal[1]+z*normal[2])>1e-4){coplanar=false;break;}
                        xy[2*j]=x*project.sketchUx+y*project.sketchUy+z*project.sketchUz;xy[2*j+1]=x*project.sketchVx+y*project.sketchVy+z*project.sketchVz;
                    }
                    if(coplanar&&Math.hypot(xy[2]-xy[0],xy[3]-xy[1])>.001)modelSnapGeometry.add(new SketchPrimitive("model-edge-"+i,"Linia",xy[0],xy[1],xy[2],xy[3]));
                }
            }catch(RuntimeException ignored){modelSnapGeometry.clear();}
        }
        result.addAll(modelSnapGeometry);return result;
    }
    private double[] packedModelFeatures(CadProject model) {
        CadProject previous=project;
        try {project=model;return kernelFeatures();}finally {project=previous;}
    }

    private double[] snappedSketchPoint(float x,float y,String excluded,Double startX,Double startY) {
        double[] raw=screenToSketch(x,y);
        PointF a=projectSketchPoint(raw[0],raw[1]),b=projectSketchPoint(raw[0]+1,raw[1]);
        double tolerance=dp(12)/Math.max(0.1,Math.hypot(a.x-b.x,a.y-b.y));
        double[] snapped=SketchSnapper.snap(raw[0],raw[1],snapGeometry(),excluded,tolerance,startX,startY);
        snapMarker=Math.hypot(raw[0]-snapped[0],raw[1]-snapped[1])>1e-7
            ? projectSketchPoint(snapped[0],snapped[1]):null;
        return snapped;
    }

    private boolean isProfileUsed(String id) {
        return id.equals(project.featureProfileId)||id.equals(project.featureBaseProfileId)
            ||project.committedProfileIds.contains(id);
    }

    private void trimSketchAt(float x,float y) {
        SketchPrimitive target=findSketchAt(x,y);
        if(target==null) {operationMessage="Dotknij fragmentu linii, łuku lub okręgu do przycięcia";invalidate();return;}
        if(project.hasCoincidence(target.id)){operationMessage="Przed przycinaniem usuń połączenia punktów elementu w menu Geometria";invalidate();return;}
        if(isProfileUsed(target.id)&&!SketchTransactions.active(project)) {
            operationMessage="Profil steruje bryłą. Przycinanie otworzyłoby obwód — najpierw usuń zależną cechę albo edytuj uchwyty.";
            invalidate();return;
        }
        try {
            double[] point=screenToSketch(x,y);
            List<SketchPrimitive> pieces=SketchTrimmer.trim(project.sketch,target,point[0],point[1]);
            checkpoint();SketchTransactions.replace(project,target.id,pieces);
            int index=project.sketch.indexOf(target);project.sketch.remove(index);project.sketch.addAll(index,pieces);
            selectedSketchId=null;projectRepository.save(project);
            operationMessage="Gotowe: usunięto fragment pomiędzy przecięciami. Możesz cofnąć zmianę.";
        } catch(IllegalArgumentException e){operationMessage=e.getMessage();}
        invalidate();
    }

    private SketchPrimitive findSketchAt(float sx, float sy) {
        float tolerance = dp(16);
        SketchPrimitive best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (SketchPrimitive entity : project.sketch) {
            PointF first = projectSketchPoint(entity.x1, entity.y1);
            PointF second = projectSketchPoint(entity.x2, entity.y2);
            float x1 = first.x, y1 = first.y, x2 = second.x, y2 = second.y;
            double distance;
            if(SketchSpline.is(entity)) {
                double[] points=SketchSpline.sample(entity.vertices,24);distance=Double.POSITIVE_INFINITY;
                for(int i=0;i<points.length-2;i+=2)distance=Math.min(distance,sketchSegmentDistance(sx,sy,points[i],points[i+1],points[i+2],points[i+3]));
            } else if(entity.curves!=null) {
                distance=Double.POSITIVE_INFINITY;double[] points=SketchCurves.samples(entity.curves,96);
                for(int i=0;i<points.length-2;i+=2)distance=Math.min(distance,sketchSegmentDistance(sx,sy,points[i],points[i+1],points[i+2],points[i+3]));
            }else if(entity.vertices!=null) {
                distance=Double.POSITIVE_INFINITY;
                for(int i=0;i<entity.vertices.length;i+=2) {
                    int j=(i+2)%entity.vertices.length;
                    distance=Math.min(distance,sketchSegmentDistance(sx,sy,entity.vertices[i],entity.vertices[i+1],entity.vertices[j],entity.vertices[j+1]));
                }
            } else if (entity.isEllipse()) {
                double[] e=ellipsePoints(entity,96);distance=Double.POSITIVE_INFINITY;
                for(int i=0;i<e.length;i+=2){int j=(i+2)%e.length;distance=Math.min(distance,sketchSegmentDistance(sx,sy,e[i],e[i+1],e[j],e[j+1]));}
            } else if (entity.type.equals("Okrąg")) {
                double radius = Math.hypot(entity.x2 - entity.x1, entity.y2 - entity.y1);
                distance = Double.POSITIVE_INFINITY;
                for (int segment = 0; segment < 96; segment++) {
                    double angle=Math.PI*2*segment/96,next=Math.PI*2*(segment+1)/96;
                    distance=Math.min(distance,sketchSegmentDistance(sx,sy,
                        entity.x1+radius*Math.cos(angle),entity.y1+radius*Math.sin(angle),
                        entity.x1+radius*Math.cos(next),entity.y1+radius*Math.sin(next)));
                }
            } else if (entity.type.equals("Prostokąt")) {
                PointF third = projectSketchPoint(entity.x2, entity.y1);
                PointF fourth = projectSketchPoint(entity.x1, entity.y2);
                distance = Math.min(Math.min(
                    sketchSegmentDistance(sx,sy,entity.x1,entity.y1,entity.x2,entity.y1),
                    sketchSegmentDistance(sx,sy,entity.x2,entity.y1,entity.x2,entity.y2)), Math.min(
                    sketchSegmentDistance(sx,sy,entity.x2,entity.y2,entity.x1,entity.y2),
                    sketchSegmentDistance(sx,sy,entity.x1,entity.y2,entity.x1,entity.y1)));
            } else {
                distance = sketchSegmentDistance(sx,sy,entity.x1,entity.y1,entity.x2,entity.y2);
            }
            if (distance <= tolerance && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private double distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        double dx = x2 - x1, dy = y2 - y1;
        if (dx * dx + dy * dy < 1.0e-9) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private void showSketchEntityEditor(SketchPrimitive entity) {
        if(entity.isEllipse()){showEllipseEditor(entity);return;}
        if(entity.curves!=null){showCurveEditor(entity);return;}
        if(entity.vertices!=null) {
            android.widget.TextView instructions=new android.widget.TextView(getContext());
            instructions.setText("Przeciągnij pomarańczowy punkt, aby zmienić kształt. Przeciągnij krawędź, aby przesunąć cały kontur. Zmiana przebudowuje powiązane operacje.");
            new PropertyBuilder().setTitle((SketchSpline.is(entity)?"Splajn · ":"Kontur · ")+entity.vertices.length/2+" punktów")
                .setView(instructions).setNeutralButton("Usuń kontur",(d,w)->deleteSketchEntity(entity.id))
                .setNegativeButton("Zamknij",null).show();return;
        }
        showSketchDimensionEditor(entity);
    }

    private void showDrawingOptions() {
        if(!mode.equals("Szkic"))return;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.CheckBox auxiliary=new android.widget.CheckBox(getContext());auxiliary.setText("Geometria pomocnicza");auxiliary.setChecked(project.drawingConstruction);form.addView(auxiliary);
        auxiliary.setOnCheckedChangeListener((button,checked)->{project.drawingConstruction=checked;projectRepository.save(project);invalidate();});
        if(sketchTool.equals("Wielokąt")) {
            EditText sides=coordinateInput("Liczba boków",project.polygonSides);form.addView(sides);
            new PropertyBuilder().setTitle("Rysuj · "+sketchTool).setView(form).setPositiveButton("Zastosuj",(d,w)->{
                try {double value=parseCoordinate(sides);if(value!=Math.floor(value)||value<3||value>128)throw new IllegalArgumentException();project.polygonSides=(int)value;projectRepository.save(project);}
                catch(IllegalArgumentException error){operationMessage="Liczba boków: 3–128";}
            }).show();
        } else new PropertyBuilder().setTitle("Rysuj · "+sketchTool).setView(form).setNegativeButton("Zamknij",null).show();
    }

    private void showSketchFillet() {
        new PropertyBuilder().setTitle("Zaokrąglij szkic").setItems(new String[]{"Wszystkie narożniki konturu","Dwie linie — wskaż na szkicu"},(d,w)->{
            if(w==0)showContourFillet();else {mode="Edytuj szkic";cornerFilletPicking=true;cornerFirst=null;operationMessage="Dotknij pierwszej i drugiej linii narożnika";invalidate();}
        }).setNegativeButton("Anuluj",null).show();
    }
    private void cornerFilletTap(float x,float y) {
        SketchPrimitive picked=findSketchAt(x,y);if(picked==null||(!picked.type.equals("Linia")&&!picked.type.equals("Pomocnicza"))){operationMessage="Wybierz pojedynczą linię szkicu";invalidate();return;}
        if(cornerFirst==null){cornerFirst=picked.id;selectedSketchId=picked.id;invalidate();return;}
        SketchPrimitive found=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(cornerFirst))found=p;final SketchPrimitive first=found,second=picked;cornerFirst=null;cornerFilletPicking=false;
        if(first==null||first.id.equals(second.id)){operationMessage="Wybierz dwie różne linie";invalidate();return;}
        if(project.hasCoincidence(first.id)||project.hasCoincidence(second.id)){operationMessage="Usuń więzy wybranych linii przed zastąpieniem narożnika łukiem";invalidate();return;}
        EditText radius=coordinateInput("Promień [mm]",1);
        new PropertyBuilder().setTitle("Zaokrąglij dwie linie [mm]").setView(radius).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try{List<SketchPrimitive> parts=SketchFillet.pair(first,second,parseCoordinate(radius));commitModelChange(()->{
                for(int i=0;i<project.sketch.size();i++){String id=project.sketch.get(i).id;if(id.equals(first.id))project.sketch.set(i,parts.get(0));else if(id.equals(second.id))project.sketch.set(i,parts.get(1));}project.sketch.add(parts.get(2));
            });}catch(IllegalArgumentException error){operationMessage=error.getMessage();}invalidate();
        }).show();
    }
    private void showContourFillet() {
        List<SketchPrimitive> profiles=new ArrayList<>();List<String> names=new ArrayList<>();
        for(SketchPrimitive p:project.sketch)if(p.closedProfile()&&p.curves==null&&!p.type.equals("Okrąg")&&!p.isEllipse()){profiles.add(p);names.add(p.type);}
        SketchPrimitive selected=null;for(SketchPrimitive p:profiles)if(p.id.equals(selectedSketchId))selected=p;
        if(selected!=null){showSketchFilletRadius(selected);return;}
        if(profiles.size()==1){showSketchFilletRadius(profiles.get(0));return;}
        if(profiles.isEmpty()){operationMessage="Wybierz prostokąt lub zamknięty kontur odcinków";invalidate();return;}
        new PropertyBuilder().setTitle("Zaokrąglij narożniki szkicu").setItems(names.toArray(new String[0]),(d,w)->showSketchFilletRadius(profiles.get(w))).setNegativeButton("Anuluj",null).show();
    }
    private void showSketchFilletRadius(SketchPrimitive source) {
        EditText radius=coordinateInput("Promień narożników [mm]",1);
        new PropertyBuilder().setTitle("Zaokrąglij wszystkie narożniki [mm]").setView(radius).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try{replaceSketchEntity(source,SketchFillet.apply(source,parseCoordinate(radius)));}
            catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    private SketchPrimitive selectedSketchEntity() {
        for(SketchPrimitive entity:project.sketch)if(entity.id.equals(selectedSketchId))return entity;
        return null;
    }
    private void showSketchTransform(String tool) {
        SketchPrimitive source=selectedSketchEntity();
        if(source==null){operationMessage="Zaznacz element szkicu, a następnie wybierz przekształcenie";invalidate();return;}
        final String id=source.id;boolean move=tool.equals("Przesuń szkic"),scale=tool.equals("Skaluj szkic");
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText first=coordinateInput(move?"Przesunięcie X [mm]":scale?"Współczynnik skali":"Kąt [°]",move?10:scale?1:45);
        EditText second=coordinateInput(move?"Przesunięcie Y [mm]":"Środek X [mm]",0),third=coordinateInput("Środek Y [mm]",0);
        form.addView(first);form.addView(second);if(!move)form.addView(third);
        android.widget.TextView hint=new android.widget.TextView(getContext());hint.setText("Zmienia zaznaczony element. Więzy są sprawdzane; Zakończ przebuduje wszystkie operacje korzystające z tego profilu.");form.addView(hint);
        new PropertyBuilder().setTitle(tool+" · "+source.type).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                SketchPrimitive current=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(id))current=p;
                if(current==null)throw new IllegalArgumentException("Element został usunięty lub zmieniono szkic");
                double a=parseCoordinate(first),b=parseCoordinate(second);
                SketchPrimitive changed=move?SketchTransform.move(current,a,b):scale?SketchTransform.scale(current,a,b,parseCoordinate(third)):SketchTransform.rotate(current,a,b,parseCoordinate(third));
                replaceSketchEntity(current,changed);
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }
    private String selectedSourceProfile() {
        if(selectedSketchId!=null)return selectedSketchId;
        if(highlightFeature==-1)return project.featureBaseProfileId;
        if(highlightFeature>=0&&highlightFeature<project.committedProfileIds.size())return project.committedProfileIds.get(highlightFeature);
        return project.featureProfileId;
    }
    private void showProfileDependencies() {
        String id=selectedSourceProfile();
        if(id==null||id.isEmpty()){operationMessage="Zaznacz element szkicu lub operację opartą na profilu";invalidate();return;}
        List<String> uses=ProfileDependencies.consumers(project,id);
        android.widget.TextView text=new android.widget.TextView(getContext());
        text.setText(uses.isEmpty()?"Ten profil nie steruje jeszcze żadną operacją bryłową.":"Bezpośrednio korzystają z profilu:\n\n"+android.text.TextUtils.join("\n",uses)+"\n\nZakończ przelicza również dalszą historię. Niepoprawna przebudowa zachowuje poprzednią bryłę.");
        new PropertyBuilder().setTitle("Powiązania profilu").setView(text).setNeutralButton("Edytuj profil źródłowy",(d,w)->editSourceProfile(id)).setNegativeButton("Zamknij",null).show();
    }
    private void editSourceProfile(String id) {
        if(id==null||id.isEmpty()){operationMessage="Wybrana operacja nie ma profilu źródłowego";invalidate();return;}
        ModelTree.Row owner=null;
        for(ModelTree.Row row:ModelTree.rows(project,new java.util.HashSet<>()))if(row.sketch()&&row.entities.contains(id)) {
            if(owner!=null&&!owner.sketchId.equals(row.sketchId)){operationMessage="Niejednoznaczny identyfikator profilu w kilku szkicach";invalidate();return;}
            owner=row;
        }
        if(owner==null){operationMessage="Brak zapisanego szkicu źródłowego; profil operacji pozostaje zachowany";invalidate();return;}
        if(project.sketchId.equals(owner.sketchId))enterSketchEditor();else editTreeSketch(owner);
        if(!project.sketchId.equals(owner.sketchId))return;
        selectedSketchId=id;highlightSketch=false;SketchPrimitive entity=selectedSketchEntity();
        if(entity!=null)showSketchEntityEditor(entity);invalidate();
    }

    /** Linear/rectangular or circular pattern of the selected sketch element (independent copies). */
    private void showSketchPattern(boolean circular) {
        SketchPrimitive source=null;
        for(SketchPrimitive entity:project.sketch)if(entity.id.equals(selectedSketchId))source=entity;
        if(source==null){operationMessage="Zaznacz element szkicu (Edytuj), a następnie wybierz szyk";return;}
        final SketchPrimitive selected=source;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        final EditText first,second,third,fourth;
        if(circular) {
            first=coordinateInput("Liczba elementów (z oryginałem)",6);second=coordinateInput("Kąt całkowity [°]",360);
            third=coordinateInput("Środek X [mm]",0);fourth=coordinateInput("Środek Y [mm]",0);
        }else {
            first=coordinateInput("Liczba w kierunku X",3);second=coordinateInput("Odstęp X [mm]",20);
            third=coordinateInput("Liczba w kierunku Y",1);fourth=coordinateInput("Odstęp Y [mm]",20);
        }
        form.addView(first);form.addView(second);form.addView(third);form.addView(fourth);
        android.widget.TextView status=new android.widget.TextView(getContext());
        status.setText(circular?"Kąt 360° dzieli okrąg równo. Środek w układzie szkicu.":"Liczby obejmują oryginał. Ujemny odstęp zmienia kierunek.");form.addView(status);
        new PropertyBuilder().setTitle((circular?"Szyk kołowy · ":"Szyk liniowy · ")+selected.type).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                List<SketchPrimitive> copies;
                double a=parseCoordinate(first),b=parseCoordinate(second),c=parseCoordinate(third),e=parseCoordinate(fourth);
                if(a!=Math.rint(a)||(!circular&&c!=Math.rint(c)))throw new IllegalArgumentException("Liczba elementów musi być całkowita");
                copies=circular?SketchPattern.circular(selected,(int)a,c,e,b):SketchPattern.grid(selected,(int)a,(int)c,b,e);
                checkpoint();project.sketch.addAll(copies);mode="Edytuj szkic";
                projectRepository.save(project);
                operationMessage="Utworzono szyk: "+(copies.size()+1)+" elementów. Kopie są niezależne. Zakończ zatwierdza szkic.";
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
            invalidate();
        }).show();
    }

    /** Extends the selected line to the nearest sketch geometry along its direction. */
    private void extendSelectedLine() {
        SketchPrimitive source=null;
        for(SketchPrimitive entity:project.sketch)if(entity.id.equals(selectedSketchId))source=entity;
        if(source==null){operationMessage="Zaznacz linię (Edytuj), a następnie wybierz Przedłuż";return;}
        try {
            SketchPrimitive extended=SketchExtend.extend(source,project.sketch,SketchExtend.End.AUTO);
            double added=Math.hypot(extended.x2-extended.x1,extended.y2-extended.y1)-Math.hypot(source.x2-source.x1,source.y2-source.y1);
            replaceSketchEntity(source,extended);
            if(selectedSketchId!=null&&selectedSketchId.equals(source.id))operationMessage="Przedłużono linię o "+formatMm(added)+" do najbliższej geometrii.";
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
    }

    /** Offset copy of the selected sketch element; the copy is independent and editable. */
    private void showSketchOffset() {
        SketchPrimitive source=null;
        for(SketchPrimitive entity:project.sketch)if(entity.id.equals(selectedSketchId))source=entity;
        if(source==null){operationMessage="Zaznacz element szkicu (Edytuj), a następnie wybierz Odsunięcie";return;}
        final SketchPrimitive selected=source;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText distance=coordinateInput("Odległość [mm]",2);form.addView(distance);
        android.widget.CheckBox inward=new android.widget.CheckBox(getContext());
        inward.setText(selected.closedGeometry()?"Do wewnątrz":selected.type.equals("Linia")||selected.type.equals("Pomocnicza")?"Na drugą stronę linii":"Zmniejsz promień");
        form.addView(inward);
        android.widget.TextView status=new android.widget.TextView(getContext());status.setText("Kopia odsunięta o stałą odległość. Narożniki ostre, łuki współśrodkowe.");form.addView(status);
        new PropertyBuilder().setTitle("Odsunięcie · "+selected.type).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                double value=parseCoordinate(distance);if(inward.isChecked())value=-value;
                SketchPrimitive copy=SketchOffset.offset(selected,UUID.randomUUID().toString(),value);
                checkpoint();project.sketch.add(copy);selectedSketchId=copy.id;mode="Edytuj szkic";
                projectRepository.save(project);
                operationMessage="Utworzono odsunięcie "+formatMm(Math.abs(value))+". Zakończ zatwierdza szkic.";
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
            invalidate();
        }).show();
    }

    private void showSketchMirror() {
        List<SketchPrimitive> sources=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchPrimitive entity:project.sketch) {
            if(entity.id.equals(selectedSketchId)){showSketchMirrorAxes(entity);return;}
            sources.add(entity);labels.add("Element "+sources.size()+" · "+entity.type);
        }
        if(sources.isEmpty()){operationMessage="Najpierw narysuj element szkicu do odbicia";invalidate();return;}
        if(sources.size()==1){showSketchMirrorAxes(sources.get(0));return;}
        new PropertyBuilder().setTitle("Lustro · wybierz element do skopiowania")
            .setItems(labels.toArray(new String[0]),(d,w)->showSketchMirrorAxes(sources.get(w)))
            .setNegativeButton("Anuluj",null).show();
    }

    private void showSketchMirrorAxes(SketchPrimitive source) {
        List<double[]> axes=new ArrayList<>();List<String> labels=new ArrayList<>();
        axes.add(new double[]{0,0,1,0});labels.add("Oś X szkicu");
        axes.add(new double[]{0,0,0,1});labels.add("Oś Y szkicu");
        for(SketchPrimitive p:project.sketch)if((p.type.equals("Pomocnicza")||p.type.equals("Linia"))&&!p.id.equals(source.id)) {
            axes.add(new double[]{p.x1,p.y1,p.x2,p.y2});labels.add("Oś z linii "+(axes.size()-2));
        }
        new PropertyBuilder().setTitle("Kopia lustrzana · "+source.type)
            .setItems(labels.toArray(new String[0]),(d,w)-> {
                try {
                    double[] axis=axes.get(w);
                    SketchPrimitive copy=SketchMirror.copy(source,UUID.randomUUID().toString(),axis[0],axis[1],axis[2],axis[3]);
                    checkpoint();project.sketch.add(copy);selectedSketchId=copy.id;mode="Edytuj szkic";
                    projectRepository.save(project);
                    operationMessage="Utworzono niezależną kopię lustrzaną. Więzy źródła nie są kopiowane. Zakończ zatwierdza szkic.";
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                invalidate();
            }).setNegativeButton("Anuluj",null).show();
    }

    private void showSketchGeometryTools() {
        SketchPrimitive selected=null;
        for(SketchPrimitive p:project.sketch)if(p.id.equals(selectedSketchId))selected=p;
        if(selected==null) {
            operationMessage="Zaznacz element szkicu, a następnie wybierz Geometria";
            invalidate();return;
        }
        SketchPrimitive source=selected;
        List<String> labels=new ArrayList<>();
        List<Runnable> actions=new ArrayList<>();
        if(project.hasCoincidence(source.id)) {
            labels.add("Zarządzaj więzami tego elementu");
            actions.add(()->showEntityConstraints(source));
        }
        {
            labels.add(source.construction?"Użyj jako zwykła geometria":"Ustaw jako geometrię pomocniczą");
            actions.add(()->{
                if(isProfileUsed(source.id)&&!SketchTransactions.active(project)){operationMessage="Element jest używany przez bryłę";invalidate();return;}
                replaceSketchEntity(source,SketchEditing.toggleConstruction(source),true);
            });
        }
        if(source.vertices!=null||source.type.equals("Prostokąt")||source.type.equals("Kontur krzywy")) {
            labels.add("Rozbij na osobne odcinki");
            actions.add(()->{
                if(project.hasCoincidence(source.id)){operationMessage="Najpierw usuń połączenia punktów tej figury";invalidate();return;}
                if(isProfileUsed(source.id)&&!SketchTransactions.active(project)) {
                    operationMessage="Profil jest używany przez bryłę — edytuj jego wierzchołki zamiast rozbijać";
                    invalidate();return;
                }
                try {
                    List<SketchPrimitive> edges=SketchEditing.explode(source);
                    int index=project.sketch.indexOf(source);if(index<0)return;
                    checkpoint();SketchTransactions.replace(project,source.id,edges);
                    project.sketch.remove(index);project.sketch.addAll(index,edges);
                    selectedSketchId=edges.get(0).id;
                    projectRepository.save(project);
                    operationMessage="Rozbito figurę: "+edges.size()+" odcinków. Możesz je edytować, przycinać i usuwać.";
                    invalidate();
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            });
        }
        labels.add("Edytuj wymiary / kształt");actions.add(()->showSketchEntityEditor(source));
        if(SketchConstraints.anchorCount(source)>0){labels.add(source.type.equals("Okrąg")?"Wymiaruj położenie środka (X/Y od początku)":"Wymiaruj położenie punktu 1 (X/Y od początku)");actions.add(()->addOriginPositionDimensions(source));}
        labels.add("Usuń element");actions.add(()->deleteSketchEntity(source.id));
        new PropertyBuilder().setTitle("Geometria · "+source.type)
            .setItems(labels.toArray(new String[0]),(dialog,which)->actions.get(which).run())
            .setNegativeButton("Zamknij",null).show();
    }

    private void coincidenceTap(float x,float y) {
        if(NonlinearConstraints.relation(constraintKind)){relationTap(x,y);return;}
        if(constraintKind.equals("horizontal")||constraintKind.equals("vertical")) {axisConstraintTap(x,y);return;}
        SketchPrimitive picked=null;int anchor=-1;double best=dp(18);
        for(SketchPrimitive p:project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++) {
            if(p.id.equals(coincidenceFirst))continue;
            double[] a=SketchConstraints.anchor(p,i);if(!sketchPointVisible(a[0],a[1]))continue;
            PointF point=projectSketchPoint(a[0],a[1]);double distance=Math.hypot(x-point.x,y-point.y);
            if(distance<best){best=distance;picked=p;anchor=i;}
        }
        if(SketchConstraints.position(constraintKind)&&(picked!=null||coincidenceFirst!=null)){positionDimensionTap(x,y,picked,anchor);return;}
        if(picked==null){operationMessage="Dotknij punktu innego elementu; punkty są oznaczone kółkami";invalidate();return;}
        if(constraintKind.equals("fixed")) {
            addSketchConstraint(SketchConstraints.Link.fixed(picked,anchor));return;
        }
        if(coincidenceFirst==null) {
            coincidenceFirst=picked.id;coincidenceAnchor=anchor;
            operationMessage="Wybrano pierwszy punkt. Dotknij punktu drugiego elementu — zostanie dołączony do pierwszego.";
        } else {
            try {
                SketchConstraints.Link link=new SketchConstraints.Link(coincidenceFirst,coincidenceAnchor,picked.id,anchor);
                List<SketchPrimitive> solved=SketchConstraints.connect(project.sketch,project.coincidences,link);
                List<SketchConstraints.Link> links=new ArrayList<>(project.coincidences);links.add(link);
                if(applySolvedSketch(solved,links))operationMessage="Połączono punkty. Wybierz kolejną parę lub Edytuj, aby przesuwać.";
            }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
            coincidenceFirst=null;
        }
        invalidate();
    }

    /** Position dimension picking: first point, then a second point or the sketch origin. */
    private void positionDimensionTap(float x,float y,SketchPrimitive picked,int anchor) {
        PointF origin=projectSketchPoint(0,0);boolean nearOrigin=Math.hypot(x-origin.x,y-origin.y)<dp(18);
        int axis=constraintKind.equals("dimX")?0:1;
        if(coincidenceFirst==null) {
            coincidenceFirst=picked.id;coincidenceAnchor=anchor;selectedSketchId=picked.id;
            operationMessage="Wskaż drugi punkt albo początek układu szkicu (przecięcie osi)";invalidate();return;
        }
        if(picked==null&&!nearOrigin){operationMessage="Wskaż punkt innego elementu albo początek układu szkicu";invalidate();return;}
        SketchPrimitive first=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(coincidenceFirst))first=p;
        String firstId=coincidenceFirst;int firstAnchor=coincidenceAnchor;coincidenceFirst=null;
        if(first==null){invalidate();return;}
        SketchConstraints.Link link;
        if(nearOrigin||picked==null||(picked.id.equals(firstId)&&anchor==firstAnchor))link=new SketchConstraints.Link(constraintKind,firstId,firstAnchor,firstId,firstAnchor,SketchConstraints.anchor(first,firstAnchor)[axis],0);
        else link=new SketchConstraints.Link(constraintKind,firstId,firstAnchor,picked.id,anchor,SketchConstraints.anchor(picked,anchor)[axis]-SketchConstraints.anchor(first,firstAnchor)[axis],0);
        addSketchConstraint(link);
        if(project.coincidences.contains(link))operationMessage="Dodano wymiar położenia. Dwukrotnie dotknij wartości, aby ją zmienić.";
    }

    private void addSketchConstraint(SketchConstraints.Link rule) {
        try {
            List<SketchPrimitive> solved=SketchConstraints.connect(project.sketch,project.coincidences,rule);
            List<SketchConstraints.Link> links=new ArrayList<>(project.coincidences);links.add(rule);
            if(applySolvedSketch(solved,links))operationMessage=NonlinearConstraints.relation(rule.kind)?"Zapisano relację "+NonlinearConstraints.symbol(rule.kind):rule.kind.equals("fixed")?"Punkt unieruchomiony. Aby odblokować: Geometria → Usuń więzy.":"Zapisano kierunek odcinka. Edytuj pozwala zmienić długość bez zmiany kierunku.";
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        invalidate();
    }

    private void axisConstraintTap(float x,float y) {
        SketchPrimitive p=findSketchAt(x,y);
        if(p==null){operationMessage="Dotknij odcinka szkicu";invalidate();return;}
        int first=0,second=1;
        if(p.vertices!=null) {
            double best=Double.POSITIVE_INFINITY;
            for(int i=0;i<p.vertices.length;i+=2) {
                int j=(i+2)%p.vertices.length;
                double d=sketchSegmentDistance(x,y,p.vertices[i],p.vertices[i+1],p.vertices[j],p.vertices[j+1]);
                if(d<best){best=d;first=i/2;second=j/2;}
            }
        } else if(!p.type.equals("Linia")&&!p.type.equals("Pomocnicza")) {
            operationMessage="Wybierz linię lub bok polilinii. Prostokąt ma już boki poziome i pionowe.";invalidate();return;
        }
        addSketchConstraint(SketchConstraints.Link.axis(p.id,first,second,constraintKind.equals("horizontal")));
    }

    private void relationTap(float x,float y) {
        SketchPrimitive picked=findSketchAt(x,y);int anchor=0;
        if(constraintKind.equals("pointOn")&&coincidenceFirst==null) {
            double best=dp(18);picked=null;
            for(SketchPrimitive p:project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++){double[] point=SketchConstraints.anchor(p,i);PointF screen=projectSketchPoint(point[0],point[1]);double distance=Math.hypot(screen.x-x,screen.y-y);if(distance<best){best=distance;picked=p;anchor=i;}}
        }
        if(picked==null){operationMessage="Wskaż geometrię szkicu; przy Punkcie na obiekcie najpierw wskaż punkt";invalidate();return;}
        if(coincidenceFirst==null){coincidenceFirst=picked.id;coincidenceAnchor=anchor;selectedSketchId=picked.id;operationMessage="Wskaż drugi element relacji "+NonlinearConstraints.symbol(constraintKind);invalidate();return;}
        if(coincidenceFirst.equals(picked.id)){operationMessage="Wybierz drugi, różny element";invalidate();return;}
        SketchPrimitive first=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(coincidenceFirst))first=p;
        if(first==null){coincidenceFirst=null;return;}final SketchPrimitive a=first,b=picked;final int ai=coincidenceAnchor;final String kind=constraintKind;coincidenceFirst=null;
        if(kind.equals("symmetric")) {
            List<String> axes=new ArrayList<>(),labels=new ArrayList<>();axes.add("@X");labels.add("Oś X szkicu");axes.add("@Y");labels.add("Oś Y szkicu");
            for(SketchPrimitive p:project.sketch)if((p.type.equals("Linia")||p.type.equals("Pomocnicza"))&&!p.id.equals(a.id)&&!p.id.equals(b.id)){axes.add(p.id);labels.add("Linia osi · "+p.id.substring(0,Math.min(6,p.id.length())));}
            new PropertyBuilder().setTitle("Oś symetrii dwóch linii").setItems(labels.toArray(new String[0]),(d,w)->addSketchConstraint(new SketchConstraints.Link(kind,a.id,0,b.id,0,0,0,axes.get(w)))).setNegativeButton("Anuluj",null).show();
        } else if(kind.equals("tangent")&&a.type.equals("Okrąg")&&b.type.equals("Okrąg")) {
            new PropertyBuilder().setTitle("Styczność okręgów").setItems(new String[]{"Zewnętrzna","Wewnętrzna"},(d,w)->addSketchConstraint(new SketchConstraints.Link(kind,a.id,0,b.id,0,w==0?1:-1,0))).setNegativeButton("Anuluj",null).show();
        } else {
            double side=1;
            if(kind.equals("tangent")){SketchPrimitive circle=a.type.equals("Okrąg")?a:b,line=a.type.equals("Okrąg")?b:a;side=Math.copySign(1,(circle.x1-line.x1)*(line.y2-line.y1)-(circle.y1-line.y1)*(line.x2-line.x1));}
            addSketchConstraint(new SketchConstraints.Link(kind,a.id,ai,b.id,0,side,0));
        }
        invalidate();
    }
    private void showConstraintMenu() {
        new PropertyBuilder().setTitle("Więzy szkicu")
            .setItems(new String[]{"Połącz dwa punkty","Odcinek poziomy (H)","Odcinek pionowy (V)","Unieruchom punkt","Równoległość ∥","Prostopadłość ⊥","Równa długość / promień =","Styczność T","Współśrodkowość ◎","Symetria względem osi ↔","Punkt na obiekcie","Wymiar położenia X ↔","Wymiar położenia Y ↕"},(d,which)-> {
                constraintKind=new String[]{"coincident","horizontal","vertical","fixed","parallel","perpendicular","equal","tangent","concentric","symmetric","pointOn","dimX","dimY"}[which];
                coincidenceMode=true;coincidenceFirst=null;
                operationMessage=NonlinearConstraints.relation(constraintKind)?"Wskaż dwa elementy relacji "+NonlinearConstraints.symbol(constraintKind):which==0?"Wskaż dwa punkty różnych elementów":which==3?"Dotknij punktu, który ma pozostać na miejscu":"Dotknij odcinka lub boku konturu";
                invalidate();
            }).setNegativeButton("Zamknij",null).show();
    }

    private void showEntityConstraints(SketchPrimitive source) {
        List<SketchConstraints.Link> rules=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchConstraints.Link rule:project.coincidences)if(rule.uses(source.id)) {
            rules.add(rule);
            String label=NonlinearConstraints.relation(rule.kind)?"Relacja "+NonlinearConstraints.symbol(rule.kind):rule.kind.equals("fixed")?"Punkt stały "+(rule.ai+1):rule.kind.equals("horizontal")?"Poziomo H · "+(rule.ai+1)+"–"+(rule.bi+1)
                :rule.kind.equals("vertical")?"Pionowo V · "+(rule.ai+1)+"–"+(rule.bi+1):"Połączenie punktu "+(rule.a.equals(source.id)?rule.ai+1:rule.bi+1);
            labels.add("Usuń: "+label);
        }
        labels.add("Usuń wszystkie więzy tego elementu");
        new PropertyBuilder().setTitle("Więzy · "+source.type+" · "+rules.size())
            .setItems(labels.toArray(new String[0]),(d,which)-> {
                checkpoint();
                if(which==rules.size())project.coincidences.removeIf(rule->rule.uses(source.id));
                else project.coincidences.remove(rules.get(which));
                projectRepository.save(project);operationMessage="Usunięto wybrany więz. Położenie geometrii nie zmieniło się.";invalidate();
            }).setNegativeButton("Zamknij",null).show();
    }

    private void showSketchCoordinateEditor(SketchPrimitive entity) {
        if(entity.curves!=null){showCurveEditor(entity);return;}
        if(entity.driving!=null) {operationMessage="Najpierw odblokuj wymiary elementu";invalidate();return;}
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)dp(18);
        form.setPadding(padding, 0, padding, 0);
        EditText x1 = coordinateInput("X1 [mm]", entity.x1);
        EditText y1 = coordinateInput("Y1 [mm]", entity.y1);
        EditText x2 = coordinateInput(entity.type.equals("Okrąg") ? "X na promieniu [mm]" : "X2 [mm]", entity.x2);
        EditText y2 = coordinateInput(entity.type.equals("Okrąg") ? "Y na promieniu [mm]" : "Y2 [mm]", entity.y2);
        for(EditText input:new EditText[]{x1,y1,x2,y2}) {
            android.widget.TextView label=new android.widget.TextView(getContext());
            label.setText(input.getHint());label.setTextSize(12);form.addView(label);form.addView(input);
        }
        new PropertyBuilder()
            .setTitle("Edytuj: " + entity.type)
            .setView(form)
            .setNeutralButton("Usuń", (dialog, which) -> deleteSketchEntity(entity.id))
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    SketchPrimitive changed = new SketchPrimitive(entity.id, entity.type,
                        parseCoordinate(x1), parseCoordinate(y1), parseCoordinate(x2), parseCoordinate(y2));
                    replaceSketchEntity(entity, changed);
                } catch (NumberFormatException ignored) {
                    operationMessage = "Niepoprawne współrzędne szkicu";
                    invalidate();
                }
            }).show();
    }

    private void showSketchDimensionEditor(SketchPrimitive entity) {
        if(entity.curves!=null){showCurveEditor(entity);return;}
        if(entity.vertices!=null){showSketchEntityEditor(entity);return;}
        boolean rectangle=entity.type.equals("Prostokąt"),circle=entity.type.equals("Okrąg");
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)dp(8),0,(int)dp(8),0);
        android.widget.TextView firstLabel=new android.widget.TextView(getContext());
        firstLabel.setText(rectangle?"Szerokość [mm]":circle?"Promień R [mm]":"Długość [mm]");form.addView(firstLabel);
        EditText first=coordinateInput(firstLabel.getText().toString(),rectangle?Math.abs(entity.x2-entity.x1):SketchDimensions.length(entity));form.addView(first);
        EditText second=coordinateInput(rectangle?"Wysokość [mm]":"Kąt do osi X [°]",rectangle?Math.abs(entity.y2-entity.y1):SketchDimensions.angle(entity));
        if(!circle) {
            android.widget.TextView label=new android.widget.TextView(getContext());label.setText(second.getHint());form.addView(label);form.addView(second);
        }
        android.widget.CheckBox diameter=new android.widget.CheckBox(getContext());
        if(circle) {
            diameter.setText("Podawaj średnicę Ø");form.addView(diameter);
            diameter.setOnCheckedChangeListener((button,checked)-> {
                try{double value=parseCoordinate(first);setLengthText(first,checked?value*2:value/2);}
                catch(NumberFormatException ignored){}
                firstLabel.setText(checked?"Średnica Ø [mm]":"Promień R [mm]");
            });
        }
        android.widget.CheckBox construction=new android.widget.CheckBox(getContext());
        construction.setText("Geometria pomocnicza");construction.setChecked(entity.construction);form.addView(construction);
        android.widget.CheckBox lock=new android.widget.CheckBox(getContext());
        lock.setText("Zablokuj wymiary");lock.setChecked(true);form.addView(lock);
        android.widget.TextView hint=new android.widget.TextView(getContext());
        hint.setText("Początek elementu pozostaje na miejscu. Blokada zachowuje rozmiar przy przesuwaniu. Zakończ zatwierdza szkic i przebudowuje powiązaną bryłę.");form.addView(hint);
        new PropertyBuilder().setTitle("Wymiary · "+entity.type).setView(form)
            .setNeutralButton(entity.driving==null?"Pozycja (opcjonalnie)":"Odblokuj",(d,w)-> {
                if(entity.driving!=null)replaceSketchEntity(entity,SketchDimensions.unlocked(entity));
                else showSketchCoordinateEditor(entity);
            }).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double a=parseCoordinate(first);if(circle&&diameter.isChecked())a/=2;
                    SketchPrimitive changed=SketchDimensions.apply(entity,a,circle?0:parseCoordinate(second),lock.isChecked()).withConstruction(construction.isChecked());
                    replaceSketchEntity(entity,changed,true);
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    private EditText labeledCoordinate(LinearLayout form,String label,double value) {
        android.widget.TextView heading=new android.widget.TextView(getContext());heading.setText(Units.label(label,project.units));form.addView(heading);EditText input=coordinateInput(label,value);form.addView(input);return input;
    }

    /** Tag of inputs whose text is a length in project units (stored geometry stays in mm). */
    private static final String LENGTH_INPUT="length-input";
    private EditText coordinateInput(String hint, double value) {
        EditText input = new EditText(getContext());
        boolean length=hint!=null&&hint.contains("[mm]");
        input.setHint(Units.label(hint,project.units));
        // Text keyboard: values may be expressions with units, e.g. 100/3, 1in+2mm, 2*(10+5).
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        if(length){input.setTag(LENGTH_INPUT);input.setText(Units.input(value,project.units));}
        else input.setText(value==Math.rint(value)&&Math.abs(value)<1e9?String.valueOf((long)value):String.format(java.util.Locale.US, "%.3f", value));
        input.setSelectAllOnFocus(true);
        return input;
    }
    /** Shows the bound parameter expression instead of the value, when the field is driven by one. */
    private EditText bound(EditText field,String key){String expression=project.parameterBindings.get(key);if(expression!=null)field.setText(expression);return field;}
    /** Binds (expression with parameter names) or unbinds (plain value) a driven value in the given document. */
    private static void bindField(CadProject target,String key,EditText field){String text=field.getText().toString().trim();if(UserParameters.isExpression(text))UserParameters.bind(target,key,text);else target.parameterBindings.remove(key);}
    /** Length input (project units, expressions allowed) for forms built outside coordinateInput. */
    private EditText lengthInput(double mm) {return coordinateInput("Wartość [mm]",mm);}
    private void setLengthText(EditText input,double mm){input.setText(LENGTH_INPUT.equals(input.getTag())?Units.input(mm,project.units):String.format(java.util.Locale.US,"%.3f",mm));}

    /** Exact value of a field: lengths are returned in mm, other fields as plain numbers. */
    private double parseCoordinate(EditText input) {
        String text=input.getText().toString();
        java.util.Map<String,double[]> variables=project.parameters.isEmpty()||!UserParameters.isExpression(text)?null:UserParameters.evaluate(project);
        return LENGTH_INPUT.equals(input.getTag())?Units.parseLength(text,project.units,variables):Units.parseNumber(text,variables);
    }

    private void replaceSketchEntity(SketchPrimitive oldEntity, SketchPrimitive changed) {
        replaceSketchEntity(oldEntity,changed.withConstruction(oldEntity.construction),false);
    }
    private void replaceSketchEntity(SketchPrimitive oldEntity, SketchPrimitive changed,boolean explicitConstruction) {
        int index = project.sketch.indexOf(oldEntity);
        if (index < 0) return;
        double[] coordinates = {changed.x1, changed.y1, changed.x2, changed.y2};
        for (double value : coordinates) {
            if (!Double.isFinite(value) || Math.abs(value) > 5000) {
                operationMessage = "Współrzędne muszą mieścić się w zakresie ±5000 mm";
                invalidate();
                return;
            }
        }
        if(Math.hypot(changed.x2-changed.x1,changed.y2-changed.y1)<.01
                || (changed.type.equals("Prostokąt") && (Math.abs(changed.x2-changed.x1)<.01
                    || Math.abs(changed.y2-changed.y1)<.01))) {
            operationMessage="Element nie może mieć zerowej długości, szerokości ani promienia";
            invalidate();return;
        }
        if(changed.vertices!=null&&!SketchSpline.is(changed)) {
            try{SketchContours.validate(changed.vertices);}
            catch(IllegalArgumentException e){operationMessage=e.getMessage();invalidate();return;}
        }
        try {
            List<SketchPrimitive> solved=SketchConstraints.edit(project.sketch,project.coincidences,oldEntity.id,changed);
            if(applySolvedSketch(solved,new ArrayList<>(project.coincidences)))selectedSketchId=changed.id;
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        invalidate();
    }

    private boolean applySolvedSketch(List<SketchPrimitive> solved,List<SketchConstraints.Link> links) {
        boolean committed = commitModelChange(() -> {
        if(!SketchTransactions.active(project))for(SketchPrimitive changed:solved)
            ProfileDependencies.propagate(project,changed.id,changed);
        project.sketch.clear();project.sketch.addAll(solved);
        project.coincidences.clear();project.coincidences.addAll(links);
        });
        if (committed) {
            operationMessage = SketchTransactions.active(project)?"Zmieniono szkic roboczy. Zakończ przebuduje bryłę; Anuluj przywróci szkic.":"Gotowe: zaktualizowano szkic i model";
        }
        return committed;
    }

    private void deleteSketchEntity(String id) {
        if (!SketchTransactions.active(project)&&(id.equals(project.featureProfileId) || id.equals(project.featureBaseProfileId)
                || project.committedProfileIds.contains(id))) {
            operationMessage = "Profil jest używany przez operację — edytuj go zamiast usuwać";
            invalidate();
            return;
        }
        for (int i = 0; i < project.sketch.size(); i++) {
            if (!project.sketch.get(i).id.equals(id)) continue;
            final int index = i;
            if (commitModelChange(() -> {
                project.sketch.remove(index);
                SketchTransactions.prune(project);
                project.pruneCoincidences();
                if (latestClosedProfile() == null) project.sketchExtruded = false;
            })) {
                selectedSketchId = null;
                operationMessage = "Gotowe: usunięto element szkicu";
            }
            invalidate();
            return;
        }
    }

    private void projectNativeMesh(float cx, float cy) {
        projectedTriangles.clear();backTriangles.clear();
        depthSurfaces.clear();
        double[] displayedMesh=featurePreview!=null?featurePreview:showingSection()?sectionResult.mesh:nativeMesh;
        java.util.Map<Integer,Integer> displayedBodies=showingSection()?sectionResult.bodies:faceBodies;
        if(displayTopology==null||displayTopology.mesh!=displayedMesh){displayTopology=new DisplayMeshTopology(displayedMesh);projectionPool.clear();}
        java.util.Arrays.fill(displayTopology.visible,false);
        double yaw = Math.toRadians(cameraYaw);
        double pitch = Math.toRadians(cameraPitch);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch), spitch = Math.sin(pitch);
        float scale = dp(3.2f) * zoom;
        final double distance = viewDistance();
        for (int offset = 0; offset + 12 < displayedMesh.length; offset += 13) {
            int meshIndex=offset/13;
            if(projectionPool.size()<=meshIndex)projectionPool.add(new ProjectedTriangle());
            ProjectedTriangle triangle=projectionPool.get(meshIndex);triangle.depth=0;
            triangle.meshIndex=offset/13;
            triangle.faceId = (int)Math.round(displayedMesh[offset + 12]) - 1;
            if(featurePreview==null&&project.hiddenBodies.contains(displayedBodies.getOrDefault(triangle.faceId,1)))continue;
            triangle.nx = displayedMesh[offset + 9];
            triangle.ny = displayedMesh[offset + 10];
            triangle.nz = displayedMesh[offset + 11];
            double normalRx = triangle.nx * cyaw + triangle.nz * syaw;
            double normalRz = -triangle.nx * syaw + triangle.nz * cyaw;
            double normalRy = triangle.ny * cpitch - normalRz * spitch;
            double cameraNormalZ = triangle.ny * spitch + normalRz * cpitch;
            // Perspective back-face test against the actual eye point (0,0,distance) in camera
            // space; the former orthographic test dropped visible faces seen at a grazing angle.
            double facing = Double.NaN;
            for (int vertex = 0; vertex < 3; vertex++) {
                double x = displayedMesh[offset + vertex * 3] - meshCenterX;
                double y = displayedMesh[offset + vertex * 3 + 1] - meshCenterY;
                double z = displayedMesh[offset + vertex * 3 + 2] - meshCenterZ;
                triangle.wx[vertex] = x;
                triangle.wy[vertex] = y;
                triangle.wz[vertex] = z;
                double rx = x * cyaw + z * syaw;
                double rz = -x * syaw + z * cyaw;
                double ry = y * cpitch - rz * spitch;
                double finalZ = y * spitch + rz * cpitch;
                double perspective = distance / (distance - finalZ);
                triangle.q[vertex]=1.0/(distance-finalZ);
                triangle.sx[vertex] = cx + (float)(rx * scale * perspective);
                triangle.sy[vertex] = cy - (float)(ry * scale * perspective);
                triangle.depth += (float)finalZ / 3f;
                if (vertex == 0) facing = -normalRx * rx - normalRy * ry + cameraNormalZ * (distance - finalZ);
            }
            if (!(facing > 1.0e-9 * distance)) {if(displayMode!=DISPLAY_SHADED)backTriangles.add(triangle);continue;}
            projectedTriangles.add(triangle);
            displayTopology.visible[triangle.meshIndex]=true;
            if(triangle.surface==null)triangle.surface=new HiddenLineVisibility.Triangle(new double[3],new double[3],new double[3]);
            triangle.surface.update(triangle.sx,triangle.sy,triangle.q);
            depthSurfaces.add(triangle.surface);
        }
        projectMeasurePoint(measureA, cx, cy, scale, cyaw, syaw, cpitch, spitch);
        projectMeasurePoint(measureB, cx, cy, scale, cyaw, syaw, cpitch, spitch);
    }

    private void projectMeasurePoint(MeasurePoint point, float cx, float cy, float scale,
            double cyaw, double syaw, double cpitch, double spitch) {
        if (point == null) return;
        double rx = point.x * cyaw + point.z * syaw;
        double rz = -point.x * syaw + point.z * cyaw;
        double ry = point.y * cpitch - rz * spitch;
        double finalZ = point.y * spitch + rz * cpitch;
        double distance = viewDistance();
        double perspective = distance / (distance - finalZ);
        point.sx = cx + (float)(rx * scale * perspective);
        point.sy = cy - (float)(ry * scale * perspective);
    }

    private int meshFaceColor(ProjectedTriangle triangle) {
        int shade = 207 + (int)Math.round(22.0 * Math.abs(triangle.nz)
            + 9.0 * Math.abs(triangle.ny));
        return Color.rgb(Math.min(238, shade), Math.min(246, shade + 18), Math.min(244, shade + 16));
    }

    private int findFaceAt(float x, float y) {
        if(showingSection()){operationMessage="Przekrój jest tylko widokiem. Wyłącz go (Widok → Przekrój), aby wybierać ściany modelu.";return -1;}
        ProjectedTriangle best = null;
        double bestDepth=-Double.MAX_VALUE;
        for (ProjectedTriangle triangle : projectedTriangles) {
            double depth=triangle.surface.depthAt(x,y);
            if(Double.isFinite(depth)&&depth>bestDepth) {best=triangle;bestDepth=depth;}
        }
        if (best != null) {
            selectedNormalX = best.nx;
            selectedNormalY = best.ny;
            selectedNormalZ = best.nz;
            selectedPointX = (best.wx[0] + best.wx[1] + best.wx[2]) / 3.0 + meshCenterX;
            selectedPointY = (best.wy[0] + best.wy[1] + best.wy[2]) / 3.0 + meshCenterY;
            selectedPointZ = (best.wz[0] + best.wz[1] + best.wz[2]) / 3.0 + meshCenterZ;
            return best.faceId;
        }
        return -1;
    }

    private boolean isFacePlanar(int faceId) {
        ProjectedTriangle reference = null;
        for (ProjectedTriangle triangle : projectedTriangles) {
            if (triangle.faceId != faceId) continue;
            if (reference == null) reference = triangle;
            else {
                double dot = reference.nx * triangle.nx + reference.ny * triangle.ny
                    + reference.nz * triangle.nz;
                if (dot < 0.9995) return false;
            }
        }
        return reference != null;
    }

    private boolean isVisibleCadEdge(ProjectedTriangle source, int edge) {
        int neighbour=displayTopology.neighbours[source.meshIndex*3+edge];
        return neighbour<0||!displayTopology.visible[neighbour];
    }

    private boolean samePoint(ProjectedTriangle a, int ai, ProjectedTriangle b, int bi) {
        return Math.abs(a.wx[ai] - b.wx[bi]) < 1.0e-7
            && Math.abs(a.wy[ai] - b.wy[bi]) < 1.0e-7
            && Math.abs(a.wz[ai] - b.wz[bi]) < 1.0e-7;
    }

    private void chooseSketchPlane(String plane) {
        // A newly picked model face is a new geometric reference even if its
        // displayed face number happens to match the previous sketch plane.
        // Mesh face IDs are not stable after B-Rep rebuilds.
        boolean pickedModelFace = plane.startsWith("Ściana ") && selectedFace >= 0;
        boolean newSketch = !plane.equals(project.sketchPlane)
            || (!sketchPlaneChosen && !project.sketch.isEmpty())
            || (pickedModelFace && !project.sketch.isEmpty()
                && !SketchTransactions.active(project));
        if (newSketch || !sketchPlaneChosen) checkpoint();
        sketchPlane = plane;
        sketchPlaneChosen = true;
        sketchRect = null;
        if (newSketch) {
            preserveExtrudedBase();
            commitActiveFeature();
            try {
                project.archiveCurrentSketch();
            } catch (org.json.JSONException error) {
                operationMessage = "Nie można zachować szkicu";
                invalidate();
                return;
            }
            project.sketchId = UUID.randomUUID().toString();project.sketchName="Szkic";
            project.sketch.clear();
            project.coincidences.clear();
            project.featureProfileId = null;
            project.sketchExtruded = false;
            project.featureOperation = "none";
        }
        project.sketchPlane = plane;
        if (plane.equals("XY")) {
            project.setPrincipalSketchFrame("XY");
            cameraYaw = 0f; cameraPitch = 0f; selectedFace = -1;
        } else if (plane.equals("XZ")) {
            project.setPrincipalSketchFrame("XZ");
            cameraYaw = 0f; cameraPitch = 90f; selectedFace = -1;
        } else if (plane.equals("YZ")) {
            project.setPrincipalSketchFrame("YZ");
            cameraYaw = 90f; cameraPitch = 0f; selectedFace = -1;
        } else {
            setSelectedFaceSketchFrame();
            double yaw = Math.atan2(-selectedNormalX, selectedNormalZ);
            double rz = -selectedNormalX * Math.sin(yaw) + selectedNormalZ * Math.cos(yaw);
            cameraYaw = (float)Math.toDegrees(yaw);
            cameraPitch = (float)Math.toDegrees(Math.atan2(selectedNormalY, rz));
        }
        selectedFace=-1;
        operationMessage="Płaszczyzna gotowa. Wybierz narzędzie i rysuj; linie pomocnicze nie tworzą bryły.";
        beginSketchWork();
        isolateCoveredSketch();
        projectRepository.save(project);
        invalidate();
    }

    private void setSelectedFaceSketchFrame() {
        double nx = selectedNormalX, ny = selectedNormalY, nz = selectedNormalZ;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-9) return;
        nx /= length; ny /= length; nz /= length;
        double rx = Math.abs(nz) < 0.9 ? 0.0 : 0.0;
        double ry = Math.abs(nz) < 0.9 ? 0.0 : 1.0;
        double rz = Math.abs(nz) < 0.9 ? 1.0 : 0.0;
        double ux = ry * nz - rz * ny;
        double uy = rz * nx - rx * nz;
        double uz = rx * ny - ry * nx;
        double uLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLength < 1.0e-9) return;
        ux /= uLength; uy /= uLength; uz /= uLength;
        double vx = ny * uz - nz * uy;
        double vy = nz * ux - nx * uz;
        double vz = nx * uy - ny * ux;
        // Origin = projection of the model origin onto the face plane (not the tapped point), so
        // face-sketch coordinates and position dimensions are reproducible and match the base
        // sketch axes, e.g. a hole on the top face is dimensioned from the same X/Y origin.
        double offset = selectedPointX * nx + selectedPointY * ny + selectedPointZ * nz;
        project.sketchOriginX = nx * offset;
        project.sketchOriginY = ny * offset;
        project.sketchOriginZ = nz * offset;
        project.sketchUx = ux; project.sketchUy = uy; project.sketchUz = uz;
        project.sketchVx = vx; project.sketchVy = vy; project.sketchVz = vz;
    }

    private void showDepthEditor() {
        showDepthEditor(false);
    }

    private void showDepthEditor(boolean baseOnly) {
        if(!project.hasSolid()){operationMessage="Najpierw utwórz szkic i wyciągnij zamknięty profil";invalidate();return;}
        if (!baseOnly&&!project.featureOperation.equals("none")) {
            SketchPrimitive profile = featureProfile();
            if (profile != null) {if(project.activeBodyTarget>0)project.selectedBody=project.activeBodyTarget;showFaceFeatureDepth(profile, project.featureOperation);}
            return;
        }
        if(project.importedStep) {
            if(!project.committedFeatures.isEmpty()) showCommittedFeatureEditor(project.committedFeatures.size()-1);
            else operationMessage="Import STEP nie ma głębokości bazowej. Najpierw utwórz operację ze szkicu.";
            invalidate();return;
        }
        if(project.featureBase!=null) {
            if(Revolve.is(project.featureBase)){showRevolveEditor(Revolve.profile(project.featureBaseProfileId,project.featureBase),project.featureBase,-1,true);return;}
            showBaseExtrusionEditor(project.featureBase,project.featureBaseProfileId);return;
        }
        if(project.sketchExtruded&&featureProfile()!=null) {
            SketchPrimitive p=featureProfile();
            showBaseExtrusionEditor(p.appendVertices(new double[]{p.featureType(),p.x1,p.y1,p.x2,p.y2,
                project.sketchPlane.equals("XZ")?1:project.sketchPlane.equals("YZ")?2:0,modelDepth}),p.id);return;
        }
        EditText input = lengthInput(modelDepth);
        new PropertyBuilder()
            .setTitle("Głębokość wyciągnięcia [mm]")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = parseCoordinate(input);
                    if (value >= 0.1 && value <= 5000.0) {
                        if (commitModelChange(() -> {
                            project.depth = value;
                            if(project.featureBase!=null) project.featureBase[6]=value;
                        })) operationMessage="Gotowe: zmieniono głębokość wyciągnięcia";
                        invalidate();
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid input leaves the last valid parameter unchanged.
                }
            })
            .show();
    }

    private double[] evaluationBase() {
        if(project.featureBase!=null)return project.featureBase;
        SketchPrimitive profile=featureProfile();
        if(!project.importedStep&&project.sketchExtruded&&project.featureOperation.equals("none")&&profile!=null)
            return profile.appendVertices(new double[]{profile.featureType(),profile.x1,profile.y1,profile.x2,profile.y2,
                project.sketchPlane.equals("XZ")?1:project.sketchPlane.equals("YZ")?2:0,modelDepth});
        return null;
    }

    private double[] evaluateFillet(double[] candidate,int index) {
        return evaluateQuery(filletPreviewQuery(candidate,index));
    }

    private java.util.concurrent.Callable<double[]> filletPreviewQuery(double[] candidate,int index) {
        double[] packed=packedFeatures();
        if(index<0) {
            int length=packed.length;packed=java.util.Arrays.copyOf(packed,length+candidate.length);
            System.arraycopy(candidate,0,packed,length,candidate.length);
        } else {
            int offset=0;for(int i=0;i<index;i++)offset+=project.committedFeatures.get(i).length;
            int previousLength=project.committedFeatures.get(index).length;
            double[] replacement=new double[packed.length-previousLength+candidate.length];
            System.arraycopy(packed,0,replacement,0,offset);
            System.arraycopy(candidate,0,replacement,offset,candidate.length);
            System.arraycopy(packed,offset+previousLength,replacement,offset+candidate.length,packed.length-offset-previousLength);
            packed=replacement;
        }
        return meshPreviewQuery(evaluationBase(),packed,project.importedStep?0:project.filletRadius,modelDepth);
    }

    private void chooseRevolveProfile() {
        if(project.importedMeshSource!=null){operationMessage="Obrót wymaga modelu B-Rep, nie siatki STL";invalidate();return;}
        List<SketchPrimitive> profiles=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchPrimitive p:project.sketch)if(p.closedProfile()) {
            if(p.id.equals(selectedSketchId)){showRevolveEditor(p,null,-1,!project.hasSolid());return;}
            profiles.add(p);labels.add("Profil "+profiles.size()+" · "+p.type);
        }
        if(profiles.isEmpty()){operationMessage="Najpierw narysuj i zakończ zamknięty profil szkicu";invalidate();return;}
        if(profiles.size()==1){showRevolveEditor(profiles.get(0),null,-1,!project.hasSolid());return;}
        new PropertyBuilder().setTitle("Obrót · wybierz profil").setItems(labels.toArray(new String[0]),
            (d,w)->showRevolveEditor(profiles.get(w),null,-1,!project.hasSolid())).setNegativeButton("Anuluj",null).show();
    }

    private double[] evaluateRevolve(double[] f,int index,boolean root) {
        return evaluateQuery(revolvePreviewQuery(f,index,root));
    }

    private java.util.concurrent.Callable<double[]> revolvePreviewQuery(double[] f,int index,boolean root) {
        double[] base=root?f:evaluationBase(),packed=packedFeatures();
        if(!root) {
            if(index<0){int offset=packed.length;packed=java.util.Arrays.copyOf(packed,offset+f.length);System.arraycopy(f,0,packed,offset,f.length);}
            else {int offset=0;for(int i=0;i<index;i++)offset+=project.committedFeatures.get(i).length;System.arraycopy(f,0,packed,offset,f.length);}
        }
        return meshPreviewQuery(base,packed,project.importedStep?0:project.filletRadius,modelDepth);
    }

    private java.util.concurrent.Callable<double[]> meshPreviewQuery(double[] base,double[] features,double radius,double depth) {
        final double[] savedBase=base==null?null:base.clone(),savedFeatures=BodyGraph.pack(project,features);
        final double width=modelWidth,height=modelHeight;
        final String source=project.importedStep?importedSourceFile().getAbsolutePath():null;
        return () -> {
            double[] result=NativeCadEngine.evaluateFeatures(width,height,depth,radius,savedBase,savedFeatures,null,source);
            // Read on the evaluating thread: kernel diagnostics are thread-local.
            previewFailureReason=result.length==0?NativeCadEngine.lastFailure().reason:null;
            return result;
        };
    }

    /** Last preview failure reason from the kernel, or the given fallback text. */
    private volatile String previewFailureReason;
    private String previewFailure(String fallback) {
        String reason=previewFailureReason;
        return reason==null||reason.isEmpty()?fallback:reason+". Model nie został zmieniony.";
    }

    private double[] evaluateQuery(java.util.concurrent.Callable<double[]> query) {
        try{return query.call();}catch(RuntimeException error){throw error;}
        catch(Exception error){throw new IllegalStateException(error);}
    }

    private void invalidateGeometryPreview() {
        // Keep the last successfully calculated geometry visible while a new
        // (possibly expensive) B-Rep preview is computed during arrow dragging.
        // Closing the editor explicitly discards this temporary geometry.
        if(geometryPreviewWorker!=null)geometryPreviewWorker.cancel();
    }

    private void submitGeometryPreview(android.widget.ScrollView owner,java.util.concurrent.Callable<double[]> query,
            java.util.function.Consumer<double[]> display) {
        if(owner==null||propertyEditor!=owner||previewViewDetached)return;
        if(geometryPreviewWorker==null) {
            android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
            geometryPreviewWorker=new GeometryPreviewWorker(task->ui.post(task));
        }
        geometryPreviewWorker.submit(query,(mesh,error)-> {
            if(propertyEditor!=owner)return;
            // An invalid/intermediate solid must never replace the last valid
            // preview: it would otherwise make the model flash/disappear.
            boolean valid=error==null&&CadMesh.isValid(mesh,false);
            if(valid)featurePreview=mesh;
            display.accept(valid?mesh:new double[0]);invalidate();
        });
    }

    private void showRevolveEditor(SketchPrimitive profile,double[] original,int index,boolean root) {
        mode="Wybierz";
        if(original==null){cameraYaw=-28;cameraPitch=22;}
        List<double[]> axes=new ArrayList<>();List<String> labels=new ArrayList<>();
        if(original!=null){axes.add(java.util.Arrays.copyOfRange(original,16,20));labels.add("Zapisana oś operacji");}
        axes.add(new double[]{0,0,1,0});labels.add("Oś X szkicu");axes.add(new double[]{0,0,0,1});labels.add("Oś Y szkicu");
        boolean sameFrame=true;
        if(original!=null){double[] frame={project.sketchOriginX,project.sketchOriginY,project.sketchOriginZ,project.sketchUx,project.sketchUy,project.sketchUz,project.sketchVx,project.sketchVy,project.sketchVz};
            for(int i=0;i<9;i++)if(Math.abs(frame[i]-original[7+i])>1e-8)sameFrame=false;}
        int guides=0;
        if(sameFrame)for(SketchPrimitive p:project.sketch)if(p.type.equals("Pomocnicza")) {
            axes.add(new double[]{p.x1,p.y1,p.x2,p.y2});labels.add("Linia pomocnicza "+(++guides));
        }
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView axisLabel=new android.widget.TextView(getContext());axisLabel.setText("Oś w płaszczyźnie profilu");form.addView(axisLabel);
        android.widget.Spinner axis=new android.widget.Spinner(getContext());
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);axis.setAdapter(adapter);axis.setContentDescription("Oś obrotu profilu");form.addView(axis);
        android.widget.TextView angleLabel=new android.widget.TextView(getContext());angleLabel.setText("Kąt obrotu [°] · 0,1–360");form.addView(angleLabel);
        EditText angle=coordinateInput("Kąt obrotu [°]",original==null?360:Math.abs(original[6]));form.addView(angle);
        android.widget.CheckBox reverse=directionCheck(original==null?360:original[6]);form.addView(reverse);
        android.widget.CheckBox cut=new android.widget.CheckBox(getContext());cut.setText("Wytnij materiał obrotem");cut.setChecked(original!=null&&original[1]==1);
        if(!root)form.addView(cut);
        android.widget.TextView status=new android.widget.TextView(getContext());form.addView(status);
        java.util.function.Supplier<double[]> candidate=()-> {
            double value=parseCoordinate(angle);if(!Double.isFinite(value)||value<.1||value>360)throw new IllegalArgumentException("Kąt: 0,1–360 stopni");
            value=reverse.isChecked()?-value:value;double[] selected=axes.get(axis.getSelectedItemPosition());
            if(original==null)return Revolve.create(profile,project,selected,value,!root&&cut.isChecked());
            double[] f=original.clone();f[6]=value;f[1]=!root&&cut.isChecked()?1:0;System.arraycopy(selected,0,f,16,4);Revolve.validate(f);return f;
        };
        new PropertyBuilder().setTitle(root?"Obrót profilu · bryła bazowa":"Obrót profilu · dodanie / wycięcie").setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double[] f=candidate.get();if(evaluateRevolve(f,index,root).length==0)throw new IllegalArgumentException(previewFailure("Nie można zbudować obrotu lub odtworzyć dalszych operacji"));
                    if (commitModelChange(() -> {
                        if(root){project.featureBase=f;project.featureBaseProfileId=profile.id;}
                        else if(index<0){preserveExtrudedBase();commitActiveFeature();project.committedFeatures.add(f);project.committedProfileIds.add(profile.id);}
                        else project.committedFeatures.set(index,f);
                    })) {
                        selectedSketchId=null;
                        operationMessage="Zapisano obrót profilu "+String.format(java.util.Locale.US,"%.3f°",f[6])+" w historii modelu";
                    }
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                invalidate();
            }).show();
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(propertyEditor!=owner)return;invalidateGeometryPreview();
            try {
                status.setText("Obliczanie podglądu…");
                submitGeometryPreview(owner,revolvePreviewQuery(candidate.get(),index,root),mesh->{
                    if(mesh.length==0)status.setText(previewFailure("Brak poprawnej bryły. Sprawdź oś, profil i przecięcie z modelem. Dokument pozostaje bez zmian."));
                    else status.setText("Podgląd obrotu. Zastosuj zapisuje edytowalną operację w historii.");
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}invalidate();
        };
        angle.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}
            public void onTextChanged(CharSequence s,int a,int b,int c){invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,180);}public void afterTextChanged(android.text.Editable s){} });
        reverse.setOnCheckedChangeListener((button,checked)->preview.run());cut.setOnCheckedChangeListener((button,checked)->preview.run());
        axis.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){preview.run();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){} });
        preview.run();
    }

    private void showFilletEditor(){showEdgeFilletEditor(-1);}

    private void showEdgeFilletEditor(int index) {
        showEdgeTreatmentEditor(index,index>=0&&EdgeChamfer.is(project.committedFeatures.get(index)));
    }

    private void showEdgeTreatmentEditor(int index,boolean chamfer) {
        if(!project.hasSolid()||project.importedMeshSource!=null) {
            operationMessage="Obróbka krawędzi wymaga bryły B-Rep, nie siatki STL";invalidate();return;
        }
        mode="Wybierz";
        final double[][] candidate={index>=0?project.committedFeatures.get(index).clone():null};
        double[] root=evaluationBase();
        final double[] rootReference=root==null?new double[]{0,0,0,modelWidth,modelHeight,0,modelDepth}:root.clone();
        final double[] rootEdges=(index<0||candidate[0].length==16)&&!project.importedStep&&RootEdgeReference.supports(rootReference)
            ?NativeCadEngine.modelEdges(modelWidth,modelHeight,modelDepth,0,rootReference,new double[0],null):new double[0];
        if(index>=0&&candidate[0].length==16)candidate[0]=RootEdgeReference.anchor(candidate[0],rootReference,rootEdges);
        EditText radius=coordinateInput(chamfer?"Wielkość fazy [mm]":"Promień [mm]",index>=0?candidate[0][11]:3);
        android.widget.TextView status=new android.widget.TextView(getContext());status.setTextSize(13);
        android.widget.TextView selectionCount=new android.widget.TextView(getContext());
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(radius);form.addView(selectionCount);form.addView(status);
        new PropertyBuilder().setTitle(chamfer?(index<0?"Fazuj · wskaż krawędź":"Fazowanie · równe odległości [mm]"):(index<0?"Zaokrąglij · wskaż krawędź":"Zaokrąglenie · promień [mm]")).setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    if(candidate[0]==null)throw new IllegalArgumentException("Najpierw dotknij widocznej krawędzi bryły");
                    double[] value=EdgeGroup.withRadius(candidate[0],parseCoordinate(radius));
                    if(chamfer)EdgeChamfer.validate(value);else EdgeFillet.validate(value);
                    if(evaluateFillet(value,index).length==0)throw new IllegalArgumentException(previewFailure("Nie można wykonać operacji na tej krawędzi z podaną wartością lub przebudować kolejnych cech"));
                    if (commitModelChange(() -> {
                        if(index<0) {
                            preserveExtrudedBase();commitActiveFeature();
                            project.committedFeatures.add(value);project.committedProfileIds.add("");
                        } else project.committedFeatures.set(index,value);
                    })) operationMessage=chamfer?"Gotowe: fazowanie wskazanej krawędzi "+formatMm(value[11]):"Gotowe: zaokrąglenie wskazanej krawędzi R"+formatMm(value[11]);
                }catch(IllegalArgumentException e){operationMessage=e.getMessage();}
                invalidate();
            }).show();
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(propertyEditor!=owner)return;
            invalidateGeometryPreview();
            try {
                double dimension=parseCoordinate(radius);
                if(index<0) {
                    candidate[0]=null;
                    selectionCount.setText("Wybrane krawędzie: "+filletSelections.size());
                    java.util.List<double[]> selected=new java.util.ArrayList<>();
                    for(int segment:filletSelections.values())selected.add(RootEdgeReference.anchor(
                        chamfer?EdgeChamfer.create(filletEdges,segment,dimension):EdgeFillet.create(filletEdges,segment,dimension),rootReference,rootEdges));
                    candidate[0]=selected.isEmpty()?null:selected.size()==1?selected.get(0):EdgeGroup.create(selected,dimension,chamfer);
                }
                selectionCount.setText("Wybrane krawędzie: "+(candidate[0]==null?0:EdgeGroup.is(candidate[0])?(int)candidate[0][1]:1));
                if(candidate[0]==null){status.setText("Dotykaj kolejnych krawędzi. Ponowne dotknięcie odznacza; przeciągnięcie obraca model.");invalidate();return;}
                candidate[0]=EdgeGroup.withRadius(candidate[0],dimension);
                if(chamfer)EdgeChamfer.validate(candidate[0]);else EdgeFillet.validate(candidate[0]);
                double proposedRadius=candidate[0][11];status.setText("Obliczanie podglądu…");
                submitGeometryPreview(owner,filletPreviewQuery(candidate[0],index),mesh->{
                    if(mesh.length==0)status.setText(previewFailure("Brak poprawnego wyniku. Zmniejsz wartość lub wskaż inną krawędź. Model nie został zmieniony."));
                    else status.setText("Podgląd "+(chamfer?"fazy ":"R")+formatMm(proposedRadius)+". "+(chamfer?"Równe odsunięcia na obu ścianach. ":"")+"Zastosuj zapisuje operację w historii.");
                });
            }catch(IllegalArgumentException e){status.setText(chamfer?"Wielkość fazy: 0,1–1000 mm":"Promień: 0,1–1000 mm");}
            invalidate();
        };
        if(index<0) {
            filletEdges=NativeCadEngine.modelEdges(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
                evaluationBase(),kernelFeatures(),project.importedStep?importedSourceFile().getAbsolutePath():null);
            filletSelectionChanged=preview;
            if(filletEdges.length==0)status.setText("Nie można odczytać krawędzi bryły");
        }
        radius.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,180);}
            public void afterTextChanged(android.text.Editable s){}
        });
        preview.run();
    }

    private double[] projectWorldPoint(double x,double y,double z) {
        double yaw=Math.toRadians(cameraYaw),pitch=Math.toRadians(cameraPitch);
        x-=meshCenterX;y-=meshCenterY;z-=meshCenterZ;
        double rx=x*Math.cos(yaw)+z*Math.sin(yaw),rz=-x*Math.sin(yaw)+z*Math.cos(yaw);
        double distance=viewDistance();
        double ry=y*Math.cos(pitch)-rz*Math.sin(pitch),q=1/(distance-y*Math.sin(pitch)-rz*Math.cos(pitch));
        return new double[]{workCenterX+rx*distance*q*dp(3.2f)*zoom,workCenterY-ry*distance*q*dp(3.2f)*zoom,q};
    }

    private void bindExtrusionManipulator(EditText dimension,android.widget.CheckBox reverse,
            android.widget.CheckBox symmetric,double[] origin,double[] axis,Runnable preview) {
        if(propertyEditor==null)return;
        extrusionDimension=dimension;extrusionReverse=reverse;extrusionSymmetric=symmetric;
        extrusionOrigin=origin;extrusionAxis=axis;extrusionPreview=preview;
        double length=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);
        if(!Double.isFinite(length)||length<1e-10){extrusionDimension=null;return;}
        for(int i=0;i<3;i++)axis[i]/=length;
        double[] a=projectWorldPoint(origin[0],origin[1],origin[2]);
        double[] b=projectWorldPoint(origin[0]+axis[0]*20,origin[1]+axis[1]*20,origin[2]+axis[2]*20);
        if(Math.hypot(b[0]-a[0],b[1]-a[1])<dp(16)) {
            cameraPitch=cameraPitch>45?cameraPitch-25:cameraPitch<-45?cameraPitch+25:cameraPitch+25;
            operationMessage="Widok odchylono, aby odsłonić oś wyciągania. Przeciągnij strzałkę lub dotknij wymiaru.";
        }
        invalidate();
    }

    private double extrusionSignedDepth() {
        return FeatureDirection.signed(parseCoordinate(extrusionDimension),extrusionReverse.isChecked(),extrusionSymmetric.isChecked());
    }

    private void drawExtrusionManipulator(Canvas canvas,float left,float top,float right,float bottom) {
        extrusionHandle=null;extrusionValueBounds.setEmpty();
        if(extrusionDimension==null)return;
        try {
            double depth=extrusionSignedDepth(),factor=extrusionSymmetric.isChecked()?.5:1;
            double[] a=projectWorldPoint(extrusionOrigin[0],extrusionOrigin[1],extrusionOrigin[2]);
            double[] b=projectWorldPoint(extrusionOrigin[0]+extrusionAxis[0]*depth*factor,
                extrusionOrigin[1]+extrusionAxis[1]*depth*factor,extrusionOrigin[2]+extrusionAxis[2]*depth*factor);
            if(a[2]<=0||b[2]<=0)return;
            double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);
            if(length<dp(.2f))return;
            double ux=dx/length,uy=dy/length,display=length+dp(36);
            if(ux>1e-8)display=Math.min(display,(right-dp(28)-a[0])/ux);
            if(ux< -1e-8)display=Math.min(display,(left+dp(28)-a[0])/ux);
            if(uy>1e-8)display=Math.min(display,(bottom-dp(72)-a[1])/uy);
            if(uy< -1e-8)display=Math.min(display,(top+dp(28)-a[1])/uy);
            if(display<dp(24))return;
            float hx=(float)(a[0]+ux*display),hy=(float)(a[1]+uy*display);
            extrusionHandle=new PointF(hx,hy);
            paint.setColor(ACCENT);paint.setStrokeWidth(dp(3));
            canvas.drawLine((float)a[0],(float)a[1],hx,hy,paint);
            canvas.drawCircle((float)a[0],(float)a[1],dp(4),paint);
            android.graphics.Path head=new android.graphics.Path();head.moveTo(hx,hy);
            head.lineTo((float)(hx-ux*dp(17)-uy*dp(9)),(float)(hy-uy*dp(17)+ux*dp(9)));
            head.lineTo((float)(hx-ux*dp(17)+uy*dp(9)),(float)(hy-uy*dp(17)-ux*dp(9)));head.close();canvas.drawPath(head,paint);
            float x=Math.max(left+dp(8),Math.min(right-dp(126),hx+dp(18))),y=Math.max(top+dp(8),Math.min(bottom-dp(60),hy-dp(48)));
            extrusionValueBounds.set(x,y,x+dp(116),y+dp(40));
            paint.setColor(PANEL);canvas.drawRoundRect(extrusionValueBounds,dp(8),dp(8),paint);
            text(canvas,formatMm(depth),x+dp(58),y+dp(26),13,ACCENT,true,Paint.Align.CENTER);
        }catch(IllegalArgumentException ignored){ /* Invalid typed values keep the model unchanged. */ }
    }

    private double extrusionParameter(float x,float y) {
        return AxisDrag.parameter(extrusionOrigin,extrusionAxis,new double[]{meshCenterX,meshCenterY,meshCenterZ},
            cameraYaw,cameraPitch,dp(3.2f)*zoom,workCenterX,workCenterY,x,y,viewDistance());
    }

    private void setExtrusionDepth(double depth) {
        double value=Math.max(.1,Math.min(5000,Math.abs(depth)));
        extrusionReverse.setChecked(!extrusionSymmetric.isChecked()&&depth<0);
        setLengthText(extrusionDimension,value);
        if(extrusionPreview!=null)removeCallbacks(extrusionPreview);
        if(!extrusionPreviewScheduled){extrusionPreviewScheduled=true;postDelayed(extrusionPreviewTask,40);}
        invalidate();
    }

    private boolean handleExtrusionTouch(MotionEvent event) {
        if(extrusionDimension==null)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            if(extrusionHandle!=null&&Math.hypot(event.getX()-extrusionHandle.x,event.getY()-extrusionHandle.y)<=dp(28)) {
                double parameter=extrusionParameter(event.getX(),event.getY());
                if(!Double.isFinite(parameter)){operationMessage="Obróć widok, aby odsłonić oś wyciągania.";invalidate();return true;}
                try{extrusionStartDepth=extrusionSignedDepth();}catch(IllegalArgumentException error){return true;}
                extrusionStartParameter=parameter;extrusionTouch=true;extrusionCancelled=false;return true;
            }
            if(extrusionValueBounds.contains(event.getX(),event.getY())) {
                extrusionDimension.requestFocus();extrusionDimension.selectAll();
                ((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(extrusionDimension,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                return true;
            }
        }
        if(!extrusionTouch)return false;
        if(event.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL) {
            if(!extrusionCancelled)setExtrusionDepth(extrusionStartDepth);
            extrusionCancelled=true;
        }
        if(action==MotionEvent.ACTION_MOVE&&!extrusionCancelled) {
            double parameter=extrusionParameter(event.getX(),event.getY());
            if(Double.isFinite(parameter))setExtrusionDepth(extrusionStartDepth+(parameter-extrusionStartParameter)*(extrusionSymmetric.isChecked()?2:1));
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) {
            extrusionTouch=false;removeCallbacks(extrusionPreviewTask);extrusionPreviewTask.run();
        }
        return true;
    }

    private void selectFilletEdge(float x,float y) {
        // Pick against the unmodified model, never through the temporary fillet preview.
        double[] preview=featurePreview;featurePreview=null;projectNativeMesh(workCenterX,workCenterY);featurePreview=preview;
        double distance=dp(18);int selected=-1;
        for(int i=0;i+15<filletEdges.length;i+=16) {
            double[] a=projectWorldPoint(filletEdges[i+9],filletEdges[i+10],filletEdges[i+11]);
            double[] b=projectWorldPoint(filletEdges[i+12],filletEdges[i+13],filletEdges[i+14]);
            double d=HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,depthSurfaces);
            if(d<distance){distance=d;selected=i;}
        }
        if(selected<0){if(findFaceAt(x,y)<0)clearViewportSelection();else {operationMessage="Dotknij bliżej widocznej krawędzi";invalidate();}return;}
        int id=(int)filletEdges[selected+15];
        if(!filletSelections.containsKey(id)&&filletSelections.size()>=256){operationMessage="Limit jednej operacji: 256 krawędzi";invalidate();return;}
        if(filletSelections.containsKey(id))filletSelections.remove(id);else filletSelections.put(id,selected);
        filletSelectionChanged.run();
    }

    private void drawFilletSelection(Canvas canvas) {
        if(filletSelections.isEmpty())return;
        paint.setColor(Color.rgb(235,130,15));paint.setStrokeWidth(dp(3));
        for(int i=0;i+15<filletEdges.length;i+=16)if(filletSelections.containsKey((int)filletEdges[i+15])) {
            double[] a=projectWorldPoint(filletEdges[i+9],filletEdges[i+10],filletEdges[i+11]);
            double[] b=projectWorldPoint(filletEdges[i+12],filletEdges[i+13],filletEdges[i+14]);
            for(double[] range:HiddenLineVisibility.segments(a[0],a[1],a[2],b[0],b[1],b[2],depthSurfaces))
                canvas.drawLine((float)(a[0]+(b[0]-a[0])*range[0]),(float)(a[1]+(b[1]-a[1])*range[0]),
                    (float)(a[0]+(b[0]-a[0])*range[1]),(float)(a[1]+(b[1]-a[1])*range[1]),paint);
        }
    }

    private void showAllEdgesFilletEditor() {
        if (project.importedMeshSource != null) {
            operationMessage = "STL jest siatką — zaokrąglanie wymaga bryły STEP";
            invalidate(); return;
        }
        if (project.importedStep) {
            operationMessage = "Zaokrąglanie importu STEP: kolejny etap";
            invalidate();
            return;
        }
        EditText input = lengthInput(project.filletRadius > 0.0 ? project.filletRadius : 3.0);
        new PropertyBuilder()
            .setTitle("Promień zaokrąglenia wszystkich krawędzi [mm]")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = parseCoordinate(input);
                    if (value < 0.1 || value > 1000.0) {
                        operationMessage = "Promień musi mieścić się w zakresie 0,1–1000 mm";
                        invalidate();
                        return;
                    }
                    if (commitModelChange(() -> project.filletRadius = value)) {
                        operationMessage = String.format(java.util.Locale.US,
                            "Gotowe: zaokrąglenie R%s", formatMm(value));
                    }
                    invalidate();
                } catch (NumberFormatException ignored) {
                    operationMessage = "Niepoprawna wartość promienia";
                    invalidate();
                }
            })
            .show();
    }

    private void drawDimensions(Canvas canvas, float left, float top, float right, float bottom) {
        if(!measurePointMode&&measuredCurve!=null){drawMeasuredCurve(canvas);return;}
        if (measureA == null) {
            text(canvas, measurePointMode?"Wskaż dwa punkty / wierzchołki":"Dotknij widocznej krawędzi", (left + right) / 2f,
                top + dp(68), 14, INK, true, Paint.Align.CENTER);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2f));
        paint.setColor(Color.rgb(45, 90, 190));
        canvas.drawCircle(measureA.sx, measureA.sy, dp(7), paint);
        if (measureB != null) {
            canvas.drawCircle(measureB.sx, measureB.sy, dp(7), paint);
            canvas.drawLine(measureA.sx, measureA.sy, measureB.sx, measureB.sy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (measureB != null) {
            float mx = (measureA.sx + measureB.sx) / 2f;
            float my = (measureA.sy + measureB.sy) / 2f - dp(12);
            text(canvas, formatMm(measureA.distanceTo(measureB)), mx, my,
                12, Color.rgb(45, 90, 190), true, Paint.Align.CENTER);
        }
    }

    private void handleMeasureTap(float x, float y) {
        if(measurementOutput==null)showMeasurementPanel();
        if(findFaceAt(x,y)<0&&nearestBrepVertex(x,y)==null&&nearestMeasurePoint(x,y)==null){clearViewportSelection();return;}
        if(!measurePointMode&&project.importedMeshSource==null) {
            double distance=dp(18);int selected=-1;
            for(int i=0;i+15<measureEdges.length;i+=16) {
                double[] a=projectWorldPoint(measureEdges[i+9],measureEdges[i+10],measureEdges[i+11]);
                double[] b=projectWorldPoint(measureEdges[i+12],measureEdges[i+13],measureEdges[i+14]);
                double d=HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,depthSurfaces);
                if(d<distance){distance=d;selected=i;}
            }
            if(selected>=0)measuredCurve=CadMeasurements.edge(measureProperties,measureEdges[selected+15]);
            else operationMessage="Dotknij bliżej widocznej krawędzi";
            updateMeasurementOutput();invalidate();return;
        }
        MeasurePoint picked = project.importedMeshSource==null?nearestBrepVertex(x,y):nearestMeasurePoint(x, y);
        if (picked == null) {
            measuredEdgeText = "Brak krawędzi w zasięgu";
            operationMessage="Dotknij widocznego wierzchołka bryły";
            updateMeasurementOutput();
            invalidate();
            return;
        }
        if (measureA == null || measureB != null) {
            measureA = picked;
            measureB = null;
        } else {
            measureB = picked;
        }
        measuredEdgeText = picked.edgeLength > 0.0 ? formatMm(picked.edgeLength) : "";
        updateMeasurementOutput();
        invalidate();
    }

    private MeasurePoint nearestBrepVertex(float x,float y) {
        MeasurePoint best=null;double distance=dp(20);
        for(int i=0;i+15<measureProperties.length;i+=16)for(int endpoint:new int[]{0,6}) {
            double wx=measureProperties[i+endpoint],wy=measureProperties[i+endpoint+1],wz=measureProperties[i+endpoint+2];
            double[] p=projectWorldPoint(wx,wy,wz);double d=Math.hypot(p[0]-x,p[1]-y);
            if(d<distance&&HiddenLineVisibility.visible(p[0],p[1],p[2],depthSurfaces)) {
                distance=d;best=new MeasurePoint(wx-meshCenterX,wy-meshCenterY,wz-meshCenterZ,(float)p[0],(float)p[1],"Punkt B-Rep",0);
            }
        }
        return best;
    }

    private void loadMeasurementGeometry() {
        measureEdges=measureProperties=new double[0];measuredCurve=null;measureA=measureB=null;
        if(!project.hasSolid()||project.importedMeshSource!=null)return;
        String source=project.importedStep?importedSourceFile().getAbsolutePath():null;
        double radius=project.importedStep?0:project.filletRadius;
        measureEdges=NativeCadEngine.modelEdges(modelWidth,modelHeight,modelDepth,radius,evaluationBase(),kernelFeatures(),source);
        measureProperties=NativeCadEngine.edgeMeasurements(modelWidth,modelHeight,modelDepth,radius,evaluationBase(),kernelFeatures(),source);
    }

    private void showMeasurementPanel() {
        android.widget.RadioGroup types=new android.widget.RadioGroup(getContext());
        android.widget.RadioButton edge=new android.widget.RadioButton(getContext()),points=new android.widget.RadioButton(getContext());
        edge.setId(View.generateViewId());points.setId(View.generateViewId());
        edge.setText("Krawędź · długość / R / Ø");points.setText("Punkt–punkt · wierzchołki");
        edge.setEnabled(project.importedMeshSource==null);types.addView(edge);types.addView(points);
        types.check(measurePointMode?points.getId():edge.getId());
        android.widget.TextView output=new android.widget.TextView(getContext());output.setTextSize(15);
        output.setTextColor(INK);output.setPadding(0,(int)dp(12),0,(int)dp(16));
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);form.addView(types);form.addView(output);
        new PropertyBuilder().setTitle(project.importedMeshSource==null?"Pomiar · geometria OCCT":"Pomiar · siatka STL (przybliżony)")
            .setView(form).setNeutralButton("Wyczyść pomiar",(d,w)-> {
                measureA=measureB=null;measuredCurve=null;showMeasurementPanel();invalidate();
            }).setNegativeButton("Zamknij pomiar",(d,w)->{mode="Wybierz";measureA=measureB=null;measuredCurve=null;invalidate();}).show();
        measurementOutput=output;
        types.setOnCheckedChangeListener((group,id)-> {
            measurePointMode=id==points.getId();measureA=measureB=null;measuredCurve=null;
            updateMeasurementOutput();invalidate();
        });
        updateMeasurementOutput();
    }

    private void updateMeasurementOutput() {
        if(measurementOutput==null)return;
        if(!measurePointMode) {
            if(measuredCurve==null){measurementOutput.setText(measureEdges.length==0?"Brak krawędzi B-Rep do pomiaru":"Dotknij widocznej krawędzi bryły. Przeciągnięcie obraca model.");return;}
            String value=(measuredCurve[14]==0?"Krawędź prosta":measuredCurve[14]==1?"Krawędź kołowa / łuk":"Krzywa B-Rep")+"\n\nDługość: "+formatMm(measuredCurve[9]);
            if(measuredCurve[14]==1)value+="\nPromień R: "+formatMm(measuredCurve[10])+"\nŚrednica Ø: "+formatMm(measuredCurve[10]*2);
            measurementOutput.setText(value+"\n\nWartości z geometrii OCCT, nie z siatki ekranu.");
        }else if(measureA==null)measurementOutput.setText(project.importedMeshSource==null?"Wskaż pierwszy widoczny wierzchołek. Punkty są przyciągane do końców krawędzi B-Rep.":"Wskaż dwa punkty siatki STL. Pomiar zależy od jej dokładności.");
        else if(measureB==null)measurementOutput.setText("Punkt A wybrany. Wskaż drugi wierzchołek.");
        else measurementOutput.setText("Odległość 3D: "+formatMm(measureA.distanceTo(measureB))
            +"\nΔX: "+formatMm(Math.abs(measureB.x-measureA.x))+"\nΔY: "+formatMm(Math.abs(measureB.y-measureA.y))+"\nΔZ: "+formatMm(Math.abs(measureB.z-measureA.z))
            +"\n\nKolejne dotknięcie rozpoczyna nowy pomiar.");
    }

    private void drawMeasuredCurve(Canvas canvas) {
        boolean visible=false;paint.setStrokeWidth(dp(3));paint.setColor(Color.rgb(45,90,190));
        for(int i=0;i+15<measureEdges.length;i+=16)if(measureEdges[i+15]==measuredCurve[15]) {
            double[] a=projectWorldPoint(measureEdges[i+9],measureEdges[i+10],measureEdges[i+11]);
            double[] b=projectWorldPoint(measureEdges[i+12],measureEdges[i+13],measureEdges[i+14]);
            for(double[] r:HiddenLineVisibility.segments(a[0],a[1],a[2],b[0],b[1],b[2],depthSurfaces)) {
                visible=true;canvas.drawLine((float)(a[0]+(b[0]-a[0])*r[0]),(float)(a[1]+(b[1]-a[1])*r[0]),
                    (float)(a[0]+(b[0]-a[0])*r[1]),(float)(a[1]+(b[1]-a[1])*r[1]),paint);
            }
        }
        if(!visible)return;
        double[] a=projectWorldPoint(measuredCurve[0],measuredCurve[1],measuredCurve[2]);
        double[] b=projectWorldPoint(measuredCurve[6],measuredCurve[7],measuredCurve[8]);
        String label=formatMm(measuredCurve[9]);
        if(measuredCurve[14]==1) {b=a;a=projectWorldPoint(measuredCurve[11],measuredCurve[12],measuredCurve[13]);label="R "+formatMm(measuredCurve[10]);}
        else if(measuredCurve[14]!=0)return;
        double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length<dp(3))return;
        float nx=(float)(-dy/length*dp(22)),ny=(float)(dx/length*dp(22));
        float ax=(float)a[0],ay=(float)a[1],bx=(float)b[0],by=(float)b[1];
        paint.setStrokeWidth(dp(1));canvas.drawLine(ax,ay,ax+nx*1.3f,ay+ny*1.3f,paint);canvas.drawLine(bx,by,bx+nx*1.3f,by+ny*1.3f,paint);
        canvas.drawLine(ax+nx,ay+ny,bx+nx,by+ny,paint);
        canvas.drawCircle(ax+nx,ay+ny,dp(2.5f),paint);canvas.drawCircle(bx+nx,by+ny,dp(2.5f),paint);
        text(canvas,label,(ax+bx)/2+nx,(ay+by)/2+ny-dp(8),12,Color.rgb(45,90,190),true,Paint.Align.CENTER);
    }

    private MeasurePoint nearestMeasurePoint(float x, float y) {
        MeasurePoint bestVertex = null;
        double bestVertexDistance = dp(15);
        for (ProjectedTriangle triangle : projectedTriangles) {
            for (int i = 0; i < 3; i++) {
                double screenDistance = Math.hypot(x - triangle.sx[i], y - triangle.sy[i]);
                if (screenDistance < bestVertexDistance) {
                    if(!HiddenLineVisibility.visible(triangle.sx[i],triangle.sy[i],triangle.q[i],depthSurfaces))continue;
                    bestVertexDistance = screenDistance;
                    bestVertex = new MeasurePoint(triangle.wx[i], triangle.wy[i], triangle.wz[i],
                        triangle.sx[i], triangle.sy[i], "Wierzchołek", 0.0);
                }
            }
        }
        if (bestVertex != null) return bestVertex;

        MeasurePoint bestEdge = null;
        double bestEdgeDistance = dp(13);
        for (ProjectedTriangle triangle : projectedTriangles) {
            for (int edge = 0; edge < 3; edge++) {
                if (!isVisibleCadEdge(triangle, edge)) continue;
                int next = (edge + 1) % 3;
                double dx = triangle.sx[next] - triangle.sx[edge];
                double dy = triangle.sy[next] - triangle.sy[edge];
                double lengthSquared = dx * dx + dy * dy;
                if (lengthSquared < 1.0e-8) continue;
                double t = ((x - triangle.sx[edge]) * dx + (y - triangle.sy[edge]) * dy) / lengthSquared;
                t = Math.max(0.0, Math.min(1.0, t));
                float sx = triangle.sx[edge] + (float)(t * dx);
                float sy = triangle.sy[edge] + (float)(t * dy);
                double screenDistance = Math.hypot(x - sx, y - sy);
                if (screenDistance < bestEdgeDistance) {
                    if(!HiddenLineVisibility.visible(sx,sy,triangle.q[edge]+t*(triangle.q[next]-triangle.q[edge]),depthSurfaces))continue;
                    double wx = triangle.wx[edge] + t * (triangle.wx[next] - triangle.wx[edge]);
                    double wy = triangle.wy[edge] + t * (triangle.wy[next] - triangle.wy[edge]);
                    double wz = triangle.wz[edge] + t * (triangle.wz[next] - triangle.wz[edge]);
                    double edgeLength = Math.sqrt(
                        square(triangle.wx[next] - triangle.wx[edge])
                        + square(triangle.wy[next] - triangle.wy[edge])
                        + square(triangle.wz[next] - triangle.wz[edge]));
                    bestEdgeDistance = screenDistance;
                    bestEdge = new MeasurePoint(wx, wy, wz, sx, sy, "Krawędź", edgeLength);
                }
            }
        }
        return bestEdge;
    }

    private static double square(double value) { return value * value; }

    /** Display text of a length in the project unit and display precision. */
    private String formatMm(double value) {
        return Units.length(value,project.units,project.displayDecimals);
    }

    private void drawTechnicalViews(Canvas canvas, float left, float top, float right, float bottom) {
        if(technicalSheet!=null) technicalSheet.draw(canvas,
            new RectF(left+dp(10),top+dp(10),right-dp(10),bottom-dp(10)),project);
        else text(canvas,"Nie można utworzyć arkusza z bieżącego modelu",left+dp(20),top+dp(50),14,INK,true);
    }

    private void showBodyProperties() {
        if(!project.hasSolid()||project.importedMeshSource!=null){operationMessage="Dokładne właściwości wymagają bryły B-Rep, nie siatki STL";invalidate();return;}
        double[] properties=NativeCadEngine.bodyProperties(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
            evaluationBase(),kernelFeatures(),project.importedStep?importedSourceFile().getAbsolutePath():null);
        if(properties.length!=7){operationMessage="Nie można obliczyć właściwości aktualnej bryły";invalidate();return;}
        android.widget.TextView values=new android.widget.TextView(getContext());values.setTextSize(16);
        String mass="Masa: przypisz gęstość materiału";
        if(project.materialDensityKgM3>0) {
            try { mass=String.format(java.util.Locale.US,"Masa: %.9g kg\nGęstość: %.9g kg/m³",
                MaterialDensity.massKg(properties[0],project.materialDensityKgM3),project.materialDensityKgM3); }
            catch(IllegalArgumentException error){mass="Masa: "+error.getMessage();}
        }
        String u=project.units;int digits=Math.max(project.displayDecimals,u.equals("mm")?6:8);
        values.setText(mass+"\n\n"+String.format(java.util.Locale.US,
            "Objętość: %s\n\nPole powierzchni: %s\n\nŚrodek objętości [%s]\nX: %s\nY: %s\nZ: %s\n\nBryły: %.0f\nŚciany: %.0f\n\nWynik dla całego modelu B-Rep OCCT. Masa zakłada jednakową gęstość wszystkich brył. Środek objętości odpowiada wtedy środkowi masy. Wartości z chwili otwarcia panelu.",
            Units.volume(properties[0],u,digits),Units.area(properties[1],u,digits),u,Units.number(properties[2],u,digits),Units.number(properties[3],u,digits),Units.number(properties[4],u,digits),properties[5],properties[6]));
        new PropertyBuilder().setTitle("Właściwości bryły · OCCT").setView(values)
            .setNeutralButton("Gęstość materiału",(d,w)->showMaterialDensityEditor()).setNegativeButton("Zamknij",null).show();
    }

    private void showMaterialDensityEditor() {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(SketchTransactions.active(project)){operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)dp(20),(int)dp(8),(int)dp(20),0);
        android.widget.TextView hint=new android.widget.TextView(getContext());
        hint.setText("Jednakowa gęstość wszystkich brył [kg/m³]. Wpisz wartość dla używanego materiału. Puste pole usuwa przypisanie.");form.addView(hint);
        EditText density=new EditText(getContext());density.setSingleLine(true);density.setSelectAllOnFocus(true);
        density.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        density.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        density.setHint("Gęstość [kg/m³]");
        density.setText(project.materialDensityKgM3==0?"":java.math.BigDecimal.valueOf(project.materialDensityKgM3).stripTrailingZeros().toPlainString());form.addView(density);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(getContext()).setTitle("Gęstość materiału")
            .setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                double value=MaterialDensity.parse(density.getText().toString());
                if(value!=project.materialDensityKgM3) {
                    String baseline=project.toJson().toString();
                    CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));candidate.materialDensityKgM3=value;
                    projectRepository.save(candidate);
                    project=candidate;undoStack.push(baseline);while(undoStack.size()>50)undoStack.removeLast();redoStack.clear();
                }
                dialog.dismiss();showBodyProperties();invalidate();
            }catch(IllegalArgumentException error){density.setError(error.getMessage());}
            catch(Exception error){android.widget.Toast.makeText(getContext(),"Nie zapisano gęstości; poprzedni stan zachowano",android.widget.Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

    private boolean prepareTechnicalSheet() {
        technicalSheet=null;
        if(project.importedMeshSource!=null) {
            operationMessage="Arkusz OCCT wymaga bryły STEP, a nie siatki STL";
            return false;
        }
        File source=new File(getContext().getCacheDir(),"drawing-source.step");
        try {
            if(!exportStep(source.getAbsolutePath())) {operationMessage="Nie można przeliczyć modelu arkusza";return false;}
            double[] segments=NativeCadEngine.technicalDrawing(source.getAbsolutePath(),project.drawingSectionFraction);
            if(segments.length==0) {operationMessage="Nie można wygenerować rzutów OCCT";return false;}
            technicalSheet=new TechnicalSheet(segments,project.drawingSectionFraction);
            operationMessage="Gotowe: rzuty, linie niewidoczne i przekrój OCCT";
            return true;
        } finally {source.delete();}
    }

    public void writeTechnicalPdf(OutputStream output) throws IOException {
        if(!prepareTechnicalSheet()) throw new IOException(operationMessage);
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        try {
            android.graphics.pdf.PdfDocument.Page page=pdf.startPage(
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(842,595,1).create());
            technicalSheet.draw(page.getCanvas(),new RectF(0,0,842,595),project);
            pdf.finishPage(page);pdf.writeTo(output);
        } finally {pdf.close();}
    }

    private boolean isInsideViewport(float x, float y) {
        return x >= leftWidth() && x <= viewportRight()
            && y >= viewTop() && y <= getHeight() - dp(68) - bottomSystemInset;
    }

    private boolean updateNativeModel() {
        if(SketchTransactions.active(project)) {
            CadProject draft=project;double w=modelWidth,h=modelHeight,d=modelDepth;
            try{project=SketchTransactions.baseline(draft);modelWidth=project.width;modelHeight=project.height;modelDepth=project.depth;return updateNativeModel();}
            finally{project=draft;modelWidth=w;modelHeight=h;modelDepth=d;}
        }
        SketchPrimitive profile = featureProfile();
        if ((!project.featureOperation.equals("none") && profile == null)
                || (project.sketchExtruded && profile == null && project.featureBase == null
                    && project.committedFeatures.isEmpty() && !project.importedStep
                    && project.importedMeshSource == null)) {
            operationMessage = "Brak profilu źródłowego. Poprzednia geometria została zachowana.";
            return false;
        }
        double[] candidateMesh;
        if(!project.hasSolid()) {
            candidateMesh=new double[0];holeCylinders=new ArrayList<>();faceBodies=new java.util.HashMap<>();faceFeatures=new java.util.HashMap<>();
        } else if (project.importedMeshSource != null) {
            try (FileInputStream input = new FileInputStream(new File(
                    projectRepository.importedStepFile().getParentFile(),project.importedMeshSource))) {
                candidateMesh = CadExchange.readStl(input);
            } catch (IOException error) { operationMessage = error.getMessage(); return false; }
        } else {
            CadModelResult result;
            try {result=new CadModelResult(NativeCadEngine.evaluateModelChecked(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,evaluationBase(),kernelFeatures(),project.importedStep?importedSourceFile().getAbsolutePath():null));}
            catch(IllegalArgumentException error){operationMessage=kernelFailureMessage(project,error);return false;}
            candidateMesh=result.mesh;faceBodies=result.bodies;faceFeatures=result.features;holeCylinders=result.holes;
        }
        if (!CadMesh.isValid(candidateMesh, !project.hasSolid())) {
            operationMessage = "Nie można przebudować bryły. Poprzednia geometria została zachowana.";
            return false;
        }
        double[] candidatePrism = NativeCadEngine.createPrism(modelWidth, modelHeight, modelDepth);
        nativeMesh = candidateMesh;modelSnapGeometry=null;
        nativePrism = candidatePrism;
        updateMeshCenter();
        java.util.Set<Integer> meshFaces=new java.util.HashSet<>();
        for(int i=12;i<nativeMesh.length;i+=13)meshFaces.add((int)Math.round(nativeMesh[i]));
        modelInfo = !project.hasSolid()?"Pusty dokument — utwórz szkic":project.importedMeshSource != null ? "Siatka STL · trójkąty " + nativeMesh.length/13
            : nativeMesh.length==0?"Nie można przebudować bryły"
            : "Bryła B-Rep · ściany "+meshFaces.size()+" · trójkąty "+nativeMesh.length/13;
        technicalSheet=null;
        if(mode.equals("Zmierz")){loadMeasurementGeometry();updateMeasurementOutput();}
        if(mode.equals("Rysunek")) prepareTechnicalSheet();
        scheduleThumbnail();
        return true;
    }

    /** Debounced background PNG preview of the current solid for the project library. */
    private final Runnable thumbnailTask=this::writeCurrentThumbnail;
    private void writeCurrentThumbnail() {
        if(project==null||nativeMesh.length<13||projectRepository.readOnly())return;
        final String id=project.id;final double[] mesh=nativeMesh;final int w=(int)dp(160),h=(int)dp(110);
        new Thread(()->{try{writeThumbnail(id,mesh,w,h);}catch(Exception ignored){}},"TabletCAD-thumbnail").start();
    }
    private void scheduleThumbnail(){removeCallbacks(thumbnailTask);postDelayed(thumbnailTask,1200);}
    private void writeThumbnail(String id,double[] mesh,int w,int h)throws IOException {
        android.graphics.Bitmap bitmap=ThumbnailRenderer.render(mesh,w,h);
        java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png);bitmap.recycle();
        projectRepository.saveThumbnail(id,png.toByteArray());
    }

    private SketchPrimitive latestClosedProfile() {
        for (int i = project.sketch.size() - 1; i >= 0; i--) {
            SketchPrimitive primitive = project.sketch.get(i);
            if (primitive.closedProfile()) return primitive;
        }
        return null;
    }

    private SketchPrimitive selectedClosedProfile() {
        if (selectedSketchId != null) {
            for (SketchPrimitive primitive : project.sketch) {
                if (primitive.id.equals(selectedSketchId)) return primitive.closedProfile()?primitive:null;
            }
        }
        return latestClosedProfile();
    }

    private SketchPrimitive featureProfile() {
        if (project.featureProfileId != null) {
            for (SketchPrimitive primitive : project.sketch) {
                if (primitive.id.equals(project.featureProfileId)) return primitive;
            }
            return null;
        }
        return latestClosedProfile();
    }

    private double[] activeFeatureValues() {
        SketchPrimitive p = featureProfile();
        if (p == null || project.featureOperation.equals("none")) return null;
        return p.appendVertices(new double[] {p.featureType(),
            FeatureDirection.featureCode(project.featureOperation,project.featureSymmetric), p.x1, p.y1, p.x2, p.y2,
            project.featureDepth, project.sketchOriginX, project.sketchOriginY,
            project.sketchOriginZ, project.sketchUx, project.sketchUy, project.sketchUz,
            project.sketchVx, project.sketchVy, project.sketchVz});
    }

    private void commitActiveFeature() {
        double[] active = activeFeatureValues();
        if (active != null) {
            if(project.bodyGraphStart>=0) {
                project.featureBodyTargets.add(project.featureOperation.equals("new")?0:project.activeBodyTarget>0?project.activeBodyTarget:project.selectedBody);
                project.featureBodyOutputs.add(project.featureOperation.equals("new")?(project.activeBodyOutput>0?project.activeBodyOutput:BodyGraph.allocate(project)):project.activeBodyTarget>0?project.activeBodyTarget:project.selectedBody);
            }
            project.committedFeatures.add(active);
            project.committedProfileIds.add(project.featureProfileId == null ? "" : project.featureProfileId);
        }
        project.featureOperation = "none";
        project.featureSymmetric=false;project.activeBodyTarget=project.activeBodyOutput=0;
    }

    private void preserveExtrudedBase() {
        if (project.featureBase != null || project.importedStep || !project.sketchExtruded
                || !project.featureOperation.equals("none")) return;
        SketchPrimitive p = featureProfile();
        if (p == null) return;
        project.featureBase = p.appendVertices(new double[] {p.featureType(),
            p.x1, p.y1, p.x2, p.y2, project.sketchPlane.equals("XZ") ? 1
                : project.sketchPlane.equals("YZ") ? 2 : 0, modelDepth});
        project.featureBaseProfileId = p.id;
    }

    private double[] kernelFeatures(){return BodyGraph.pack(project,packedFeatures());}

    private double[] packedFeatures() {
        double[] active = activeFeatureValues();
        int length=active==null?0:active.length;
        for(double[] feature:project.committedFeatures) length+=feature.length;
        double[] packed = new double[length];
        int offset = 0;
        for (double[] feature : project.committedFeatures) {
            System.arraycopy(feature, 0, packed, offset, feature.length);
            offset += feature.length;
        }
        if (active != null) System.arraycopy(active, 0, packed, offset, active.length);
        return packed;
    }

    private void showCommittedFeatureEditor(int index) {
        double[] feature = project.committedFeatures.get(index);
        if(project.extrusionExtents.containsKey(index)){showExtrusionExtentEditor(index);return;}
        if(HoleThread.is(feature)){showThreadFeatureEditor(index);return;}
        if(FeaturePattern.is(feature)){showPatternEditor(index);return;}
        if(ShellFeature.is(feature)){showShellEditor(index);return;}
        if(BodyCombine.is(feature)){beginBodyCombine((int)feature[1],index);return;}
        if(BodySplit.is(feature)){showBodySplit(index);return;}
        if(SweepLoft.bodyPattern(feature)){showBodyPatternDialog((int)feature[1],index);return;}
        if(SweepLoft.loft(feature)||SweepLoft.sweep(feature)){showLoftSweepEditor(index);return;}
        if(DraftFeature.is(feature)){showDraftEditor(index);return;}
        if(BodyTransform.is(feature)){showBodyTransform((int)feature[1],index);return;}
        if(HoleFeature.is(feature)){holeEditIndex=index;double[] f=feature;holeReference=new double[]{f[2],f[3],f[4],f[5],f[6],f[7],f[8]};mode="Otwór";showHolePanel();return;}
        if(Revolve.is(feature)){String id=project.committedProfileIds.get(index);showRevolveEditor(Revolve.profile(id,feature),feature,index,false);return;}
        if(EdgeFillet.is(feature)||EdgeChamfer.is(feature)){showEdgeFilletEditor(index);return;}
        String linkedProfileId=index<project.committedProfileIds.size()?project.committedProfileIds.get(index):null;
        if(feature[0]>=5 || project.isProfileDimensioned(linkedProfileId) || ProfileDependencies.consumers(project,linkedProfileId).size()>1) {
            EditText depth=bound(coordinateInput("Głębokość [mm]",Math.abs(feature[6])),UserParameters.depthKey(index));
            android.widget.CheckBox reverse=directionCheck(feature[6]);
            android.widget.CheckBox symmetric=symmetricCheck(FeatureDirection.isSymmetric(feature[1]),reverse);
            LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);form.addView(depth);form.addView(reverse);
            form.addView(symmetric);
            android.widget.TextView status=new android.widget.TextView(getContext());form.addView(status);
            new PropertyBuilder().setTitle((feature[0]>=5?"Kontur":"Profil źródłowy")+" · głębokość [mm]").setView(form)
                .setNeutralButton("Edytuj profil źródłowy",(d,w)->editSourceProfile(linkedProfileId))
                .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                    try {
                        double value=FeatureDirection.signed(parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                        double[] candidate=feature.clone();candidate[6]=value;
                        candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());
                        if(commitModelChange(() -> {project.committedFeatures.set(index,candidate);bindField(project,UserParameters.depthKey(index),depth);}))
                            operationMessage="Gotowe: przeliczono operację konturu";
                    }catch(IllegalArgumentException e){operationMessage="Głębokość: 0,1–5000 mm";}
                    invalidate();
                }).show();
            bindCommittedPreview(index,new EditText[]{depth},reverse,symmetric,status,()-> {
                double[] candidate=feature.clone();candidate[6]=FeatureDirection.signed(parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());return candidate;
            });return;
        }
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding((int)dp(16), 0, (int)dp(16), 0);
        String[] labels = {"X1 / środek X [mm]", "Y1 / środek Y [mm]",
            feature[0] == 1 ? "Punkt promienia X [mm]" : "X2 [mm]",
            feature[0] == 1 ? "Punkt promienia Y [mm]" : "Y2 [mm]", "Głębokość [mm]"};
        EditText[] inputs = new EditText[5];
        for (int i = 0; i < inputs.length; i++) {
            android.widget.TextView label = new android.widget.TextView(getContext());
            label.setText(Units.label(labels[i],project.units));
            label.setTextColor(MUTED);
            label.setTextSize(12);
            fields.addView(label);
            inputs[i] = coordinateInput(labels[i], i==4?Math.abs(feature[6]):feature[i + 2]);
            if(i==4)bound(inputs[i],UserParameters.depthKey(index));
            fields.addView(inputs[i]);
        }
        android.widget.CheckBox reverse=directionCheck(feature[6]);fields.addView(reverse);
        android.widget.CheckBox symmetric=symmetricCheck(FeatureDirection.isSymmetric(feature[1]),reverse);fields.addView(symmetric);
        android.widget.TextView status=new android.widget.TextView(getContext());fields.addView(status);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        scroll.addView(fields);
        new PropertyBuilder()
            .setTitle((FeatureDirection.isCut(feature[1]) ? "Wycięcie " : "Dodanie ") + (index + 1) + " — profil i głębokość")
            .setView(scroll)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double[] candidate = feature.clone();
                    boolean valid = true;
                    for (int i = 0; i < inputs.length; i++) {
                        candidate[i + 2] = parseCoordinate(inputs[i]);
                        valid &= Double.isFinite(candidate[i + 2])
                            && Math.abs(candidate[i + 2]) <= 5000;
                    }
                    valid &= candidate[6] >= 0.1;
                    if(valid)candidate[6]=FeatureDirection.signed(candidate[6],reverse.isChecked(),symmetric.isChecked());
                    candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());
                    if (!valid) {
                        operationMessage = "Współrzędne: ±5000 mm, głębokość: 0,1–5000 mm";
                    } else {
                        if (commitModelChange(() -> {
                            project.committedFeatures.set(index, candidate);
                            bindField(project,UserParameters.depthKey(index),inputs[4]);
                            String profileId = index < project.committedProfileIds.size()
                                ? project.committedProfileIds.get(index) : "";
                            SketchPrimitive changed=SketchPrimitive.fromFeature(profileId,candidate[0]==1?"Okrąg":"Prostokąt",candidate,2,16);
                            ProfileDependencies.propagate(project,profileId,changed);
                            for (int i = 0; i < project.sketch.size(); i++) {
                                SketchPrimitive p = project.sketch.get(i);
                                if (p.id.equals(profileId)) project.sketch.set(i,changed.withConstruction(p.construction));
                            }
                        })) operationMessage = "Gotowe: przeliczono historię modelu";
                    }
                } catch (NumberFormatException error) {
                    operationMessage = "Niepoprawna głębokość";
                }
                invalidate();
            }).show();
        bindCommittedPreview(index,inputs,reverse,symmetric,status,()-> {
            double[] candidate=feature.clone();
            for(int i=0;i<4;i++) {
                candidate[i+2]=parseCoordinate(inputs[i]);
                if(!Double.isFinite(candidate[i+2])||Math.abs(candidate[i+2])>5000)throw new IllegalArgumentException("Współrzędne: ±5000 mm");
            }
            candidate[6]=FeatureDirection.signed(parseCoordinate(inputs[4]),reverse.isChecked(),symmetric.isChecked());
            candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());return candidate;
        });
    }

    private void bindCommittedPreview(int index,EditText[] inputs,android.widget.CheckBox reverse,android.widget.CheckBox symmetric,
            android.widget.TextView status,java.util.function.Supplier<double[]> proposed) {
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(owner==null||propertyEditor!=owner)return;
            invalidateGeometryPreview();
            try {
                double[] candidate=proposed.get(),active=activeFeatureValues();
                int length=active==null?0:active.length;
                for(int i=0;i<project.committedFeatures.size();i++)length+=(i==index?candidate:project.committedFeatures.get(i)).length;
                double[] packed=new double[length];int offset=0;
                for(int i=0;i<project.committedFeatures.size();i++) {
                    double[] record=i==index?candidate:project.committedFeatures.get(i);
                    System.arraycopy(record,0,packed,offset,record.length);offset+=record.length;
                }
                if(active!=null)System.arraycopy(active,0,packed,offset,active.length);
                status.setText("Obliczanie podglądu…");
                submitGeometryPreview(owner,meshPreviewQuery(project.featureBase,packed,project.filletRadius,modelDepth),mesh->{
                    if(mesh.length>0)status.setText("Podgląd · "+Math.abs(candidate[6])+" mm"+(FeatureDirection.isSymmetric(candidate[1])?" · symetrycznie":candidate[6]<0?" · odwrotnie":"")+". Zastosuj zapisuje zmianę.");
                    else status.setText(previewFailure("Brak poprawnego wyniku. Zmień głębokość lub kierunek."));
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            invalidate();
        };
        for(EditText input:inputs)input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
    }

    private void extrudeSketchProfile() {
        if(project.importedMeshSource!=null){operationMessage="Wyciąganie wymaga bryły B-Rep albo pustego dokumentu";invalidate();return;}
        extrusionSelections.clear();for(SketchPrimitive p:project.sketch)if(p.id.equals(selectedSketchId)&&p.closedProfile())extrusionSelections.add(p.id);
        boolean any=false;for(SketchPrimitive p:project.sketch)if(p.closedProfile())any=true;
        if(!any){operationMessage="Narysuj zamknięte profile zwykłej geometrii";invalidate();return;}
        mode="Profile wyciągnięcia";alignToCurrentSketchPlane();showExtrusionSelection();invalidate();
    }
    private static boolean polygonContains(double[] points,double x,double y) {
        boolean inside=false;int n=points.length/2;
        for(int i=0,j=n-1;i<n;j=i++){double ax=points[2*i],ay=points[2*i+1],bx=points[2*j],by=points[2*j+1];if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;}return inside;
    }
    private void toggleExtrusionProfile(float x,float y) {
        double[] point=screenToSketch(x,y);SketchPrimitive chosen=null;double area=Double.POSITIVE_INFINITY;boolean overConstruction=false;
        for(SketchPrimitive p:project.sketch)if(p.closedGeometry()) {
            if(p.isEllipse()) {
                double a=p.major(),ux=(p.x2-p.x1)/a,uy=(p.y2-p.y1)/a,dx=point[0]-p.x1,dy=point[1]-p.y1,s=(dx*ux+dy*uy)/a,t=(-dx*uy+dy*ux)/p.minor;
                boolean in=s*s+t*t<1;double size=Math.PI*a*p.minor;
                if(in&&p.construction)overConstruction=true;
                if(in&&!p.construction&&size<area){chosen=p;area=size;}
                continue;
            }
            boolean inside=p.type.equals("Okrąg")?Math.hypot(point[0]-p.x1,point[1]-p.y1)<Math.hypot(p.x2-p.x1,p.y2-p.y1):p.vertices!=null?polygonContains(p.vertices,point[0],point[1]):p.curves!=null?polygonContains(SketchCurves.samples(p.curves,96),point[0],point[1]):point[0]>=Math.min(p.x1,p.x2)&&point[0]<=Math.max(p.x1,p.x2)&&point[1]>=Math.min(p.y1,p.y2)&&point[1]<=Math.max(p.y1,p.y2);
            double size=p.type.equals("Okrąg")?Math.PI*Math.pow(Math.hypot(p.x2-p.x1,p.y2-p.y1),2):Math.abs((p.x2-p.x1)*(p.y2-p.y1));
            if(inside&&p.construction)overConstruction=true;
            if(inside&&!p.construction&&size<area){chosen=p;area=size;}
        }
        if(chosen!=null){if(!extrusionSelections.add(chosen.id))extrusionSelections.remove(chosen.id);if(extrusionSelectionChanged!=null)extrusionSelectionChanged.run();}
        else if(!overConstruction&&findFaceAt(x,y)<0)clearViewportSelection();
        else operationMessage="Dotknij wnętrza zamkniętego obszaru szkicu";
        invalidate();
    }
    private CadProject extrusionCandidate(List<String> profiles,String operation,double first,double second,boolean reverse,boolean symmetric,int target) {
        if(profiles.isEmpty())throw new IllegalArgumentException("Zaznacz obszary szkicu bezpośrednio na rysunku");
        if(operation.equals("common")&&profiles.size()>1)throw new IllegalArgumentException("Część wspólna wymaga jednego profilu; dla wielu wybierz Dodaj, Połącz lub Wytnij");
        CadProject current=project;
        try {
            project=CadProject.fromJson(current.toJson());preserveExtrudedBase();commitActiveFeature();BodyGraph.begin(project);
            for(String id:profiles) {
                SketchPrimitive shape=null;for(SketchPrimitive p:project.sketch)if(p.id.equals(id)&&p.closedProfile())shape=p;if(shape==null)throw new IllegalArgumentException("Profil źródłowy nie jest dostępny");
                if(!project.hasSolid()) {
                    if(!operation.equals("new"))throw new IllegalArgumentException("Pierwsze wyciągnięcie musi tworzyć nową bryłę");
                    project.featureBase=ExtrusionPlan.base(project,shape,first,second,reverse,symmetric);project.featureBaseProfileId=shape.id;project.featureProfileId=shape.id;project.sketchExtruded=true;project.depth=first+second;project.filletRadius=0;
                    project.extrusionExtents.put(-1,new double[]{FeatureDirection.signed(first,reverse,symmetric),second,symmetric?1:0});
                } else {
                    int index=project.committedFeatures.size();project.committedFeatures.add(ExtrusionPlan.feature(project,shape,operation,first,second,reverse,symmetric));project.committedProfileIds.add(shape.id);
                    project.featureBodyTargets.add(operation.equals("new")?0:target);project.featureBodyOutputs.add(operation.equals("new")?BodyGraph.allocate(project):target);
                    project.extrusionExtents.put(index,new double[]{FeatureDirection.signed(first,reverse,symmetric),second,symmetric?1:0});
                }
            }
            BodyGraph.reconcile(project);return project;
        }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
        finally {project=current;}
    }
    private java.util.concurrent.Callable<double[]> candidateQuery(CadProject next) {
        CadProject current=project;try {project=next;return meshPreviewQuery(evaluationBase(),packedFeatures(),next.importedStep?0:next.filletRadius,next.depth);}finally {project=current;}
    }
    private void showExtrusionSelection() {
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView selected=new android.widget.TextView(getContext());form.addView(selected);
        android.widget.Spinner operation=new android.widget.Spinner(getContext());operation.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Dodaj — nowa bryła","Połącz z bryłą","Wytnij","Część wspólna"}));form.addView(operation);
        List<Integer> bodies=new ArrayList<>(BodyGraph.bodies(project));if(bodies.isEmpty())bodies.add(1);
        android.widget.Spinner target=new android.widget.Spinner(getContext());List<String> labels=new ArrayList<>();for(Integer id:bodies)labels.add(BodyGraph.name(project,id));target.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,labels));form.addView(target);
        int selectedBody=bodies.indexOf(project.selectedBody);if(selectedBody>=0)target.setSelection(selectedBody);
        EditText first=labeledCoordinate(form,"Pierwszy kierunek [mm]",10),second=coordinateInput("Drugi kierunek [mm]",5);
        android.widget.CheckBox reverse=directionCheck(1),symmetric=symmetricCheck(false,reverse),both=new android.widget.CheckBox(getContext());both.setText("Dwa kierunki · niezależne długości");form.addView(reverse);form.addView(symmetric);form.addView(both);android.widget.TextView secondLabel=new android.widget.TextView(getContext());secondLabel.setText("Drugi kierunek [mm]");form.addView(secondLabel);form.addView(second);second.setEnabled(false);
        android.widget.TextView status=new android.widget.TextView(getContext());status.setText("Dotknij wnętrza jednego lub kilku profili. Pomarańczowy oznacza wybrany obszar.");form.addView(status);
        android.widget.Button toFace=new android.widget.Button(getContext());toFace.setText("Do powierzchni…");toFace.setAllCaps(false);form.addView(toFace);
        new PropertyBuilder().setTitle("Wyciąganie · wybór na szkicu").setView(form).setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";extrusionSelections.clear();}).setPositiveButton("Zastosuj",(d,w)->{
            try {String baseline=project.toJson().toString();CadProject next=extrusionCandidate(new ArrayList<>(extrusionSelections),new String[]{"new","add","cut","common"}[operation.getSelectedItemPosition()],parseCoordinate(first),both.isChecked()?parseCoordinate(second):0,reverse.isChecked(),symmetric.isChecked(),bodies.get(target.getSelectedItemPosition()));commitProjectAsync(next,baseline,"Wyciągnięto profile: "+extrusionSelections.size());}
            catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()->{
            if(propertyEditor!=owner)return;selected.setText("Wybrane profile: "+extrusionSelections.size());target.setEnabled(operation.getSelectedItemPosition()!=0);if(extrusionSelections.isEmpty()){invalidateGeometryPreview();featurePreview=null;extrusionDimension=null;extrusionHandle=null;invalidate();return;}
            for(SketchPrimitive shape:project.sketch)if(extrusionSelections.contains(shape.id)){
                double[] center=profileCenter(shape),origin=sketchToWorld(center[0],center[1]);double sign=operation.getSelectedItemPosition()==2?-1:1;
                // Principal XZ base extrusions follow native +Y, whereas placed features follow U×V.
                if(!project.hasSolid()&&project.sketchPlane.equals("XZ"))sign=-sign;
                double[] axis={sign*(project.sketchUy*project.sketchVz-project.sketchUz*project.sketchVy),sign*(project.sketchUz*project.sketchVx-project.sketchUx*project.sketchVz),sign*(project.sketchUx*project.sketchVy-project.sketchUy*project.sketchVx)};
                if(extrusionDimension!=first)bindExtrusionManipulator(first,reverse,symmetric,origin,axis,extrusionSelectionChanged);
                else {extrusionOrigin=origin;extrusionAxis=axis;}
                break;
            }
            try {CadProject next=extrusionCandidate(new ArrayList<>(extrusionSelections),new String[]{"new","add","cut","common"}[operation.getSelectedItemPosition()],parseCoordinate(first),both.isChecked()?parseCoordinate(second):0,reverse.isChecked(),symmetric.isChecked(),bodies.get(target.getSelectedItemPosition()));status.setText("Obliczanie podglądu…");submitGeometryPreview(owner,candidateQuery(next),mesh->status.setText(mesh.length>0?"Podgląd gotowy. Zastosuj tworzy jedną transakcję Cofnij.":previewFailure("Brak poprawnego wyniku; sprawdź zakres i cel operacji.")));}
            catch(IllegalArgumentException error){status.setText(error.getMessage());}invalidate();
        };
        extrusionSelectionChanged=preview;showExtrusionSketch=true;selected.setText("Wybrane profile: "+extrusionSelections.size());
        toFace.setOnClickListener(v->{
            if(!project.hasSolid()){status.setText("Brak bryły — wyciągnięcie do powierzchni wymaga istniejącej ściany");return;}
            extrusionFacePick=distance->{
                symmetric.setChecked(false);both.setChecked(false);
                first.setText(ExtrudeToFace.format(distance));reverse.setChecked(ExtrudeToFace.reverse(distance,operation.getSelectedItemPosition()==2));
                status.setText("Głębokość do wskazanej ściany: "+ExtrudeToFace.format(distance)+" mm");operationMessage="Długość pobrana ze ściany: "+ExtrudeToFace.format(distance)+" mm";preview.run();
            };
            status.setText("Dotknij ściany bryły, do której ma sięgać wyciągnięcie. Jednym palcem obrócisz widok.");operationMessage="Wskaż ścianę docelową";invalidate();
        });
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int c,int d){}public void onTextChanged(CharSequence t,int a,int b,int c){removeCallbacks(preview);postDelayed(preview,300);}public void afterTextChanged(android.text.Editable text){}};first.addTextChangedListener(watcher);second.addTextChangedListener(watcher);
        symmetric.setOnCheckedChangeListener((button,checked)->{if(checked)both.setChecked(false);reverse.setEnabled(!checked);preview.run();});both.setOnCheckedChangeListener((button,checked)->{if(checked)symmetric.setChecked(false);second.setEnabled(checked);preview.run();});reverse.setOnCheckedChangeListener((button,checked)->preview.run());
        android.widget.AdapterView.OnItemSelectedListener changed=new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){preview.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}};operation.setOnItemSelectedListener(changed);target.setOnItemSelectedListener(changed);
        post(preview);
    }

    private void showExtrusionExtentEditor(int index) {
        double[] feature=index<0?project.featureBase:project.committedFeatures.get(index),range=project.extrusionExtents.get(index);
        if(range==null)range=new double[]{feature[1],feature[2],0};final double[] oldRange=range.clone();
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);EditText first=labeledCoordinate(form,"Pierwszy kierunek [mm]",Math.abs(range[0])),second=labeledCoordinate(form,"Drugi kierunek [mm]",range[1]);
        android.widget.CheckBox reverse=directionCheck(range[0]),symmetric=symmetricCheck(range[2]!=0,reverse);form.addView(reverse);form.addView(symmetric);
        android.widget.TextView status=new android.widget.TextView(getContext());form.addView(status);
        java.util.function.Supplier<CadProject> candidate=()->{
            try {
                double a=parseCoordinate(first),b=symmetric.isChecked()?0:parseCoordinate(second);String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));double[] updated;
                if(index<0) {
                    if(ExtrusionPlan.placedBase(feature)) {double[] inner=java.util.Arrays.copyOfRange(feature,7,feature.length);boolean flip=Math.signum(inner[6])!=Math.signum(oldRange[0]);inner=ExtrusionPlan.resizeFeature(inner,oldRange[1],a,b,reverse.isChecked()^flip,symmetric.isChecked());updated=java.util.Arrays.copyOf(feature,7+inner.length);updated[1]=FeatureDirection.signed(a,reverse.isChecked(),symmetric.isChecked());updated[2]=b;updated[6]=Math.copySign(a+b,updated[1]);System.arraycopy(inner,0,updated,7,inner.length);}
                    else {CadProject plane=CadProject.blank();plane.sketchPlane=FeatureDirection.basePlane(feature[5])==1?"XZ":FeatureDirection.basePlane(feature[5])==2?"YZ":"XY";plane.setPrincipalSketchFrame(plane.sketchPlane);updated=ExtrusionPlan.base(plane,SketchPrimitive.fromFeature(next.featureBaseProfileId,feature[0]==1?"Okrąg":"Prostokąt",feature,1,7),a,b,reverse.isChecked(),symmetric.isChecked());}
                    next.featureBase=updated;next.depth=a+b;
                } else {updated=ExtrusionPlan.resizeFeature(feature,oldRange[1],a,b,reverse.isChecked(),symmetric.isChecked());next.committedFeatures.set(index,updated);}
                next.extrusionExtents.put(index,new double[]{FeatureDirection.signed(a,reverse.isChecked(),symmetric.isChecked()),b,symmetric.isChecked()?1:0});return next;
            }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
        };
        new PropertyBuilder().setTitle("Zakres wyciągania · historia").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try{String baseline=project.toJson().toString();commitProjectAsync(candidate.get(),baseline,"Zaktualizowano zakres wyciągania");}
            catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()->{if(propertyEditor!=owner)return;try{status.setText("Obliczanie podglądu…");submitGeometryPreview(owner,candidateQuery(candidate.get()),mesh->status.setText(mesh.length>0?"Przeciągnij strzałkę lub wpisz długość. Zastosuj zapisuje zmianę.":previewFailure("Niepoprawny zakres wyciągania")));}catch(IllegalArgumentException error){status.setText(error.getMessage());}invalidate();};
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,300);}public void afterTextChanged(android.text.Editable e){}};
        first.addTextChangedListener(watcher);second.addTextChangedListener(watcher);reverse.setOnCheckedChangeListener((button,checked)->preview.run());symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);preview.run();});
        double[] origin,axis;
        if(index<0&&!ExtrusionPlan.placedBase(feature)) {
            double[] c=profileCenter(SketchPrimitive.fromFeature("manipulator",feature[0]==1?"Okrąg":"Prostokąt",feature,1,7));int plane=FeatureDirection.basePlane(feature[5]);
            origin=plane==1?new double[]{c[0],0,c[1]}:plane==2?new double[]{0,c[0],c[1]}:new double[]{c[0],c[1],0};axis=plane==1?new double[]{0,1,0}:plane==2?new double[]{1,0,0}:new double[]{0,0,1};
        } else {
            double[] f=index<0?java.util.Arrays.copyOfRange(feature,7,feature.length):feature;
            double[] c=profileCenter(SketchPrimitive.fromFeature("manipulator",f[0]==1?"Okrąg":"Prostokąt",f,2,16));double cut=FeatureDirection.isCut(f[1])?-1:1;
            double[] n={f[11]*f[15]-f[12]*f[14],f[12]*f[13]-f[10]*f[15],f[10]*f[14]-f[11]*f[13]};
            origin=new double[3];axis=new double[3];double flip=index<0?Math.signum(f[6])*Math.signum(oldRange[0]):1;
            for(int i=0;i<3;i++){origin[i]=f[7+i]+c[0]*f[10+i]+c[1]*f[13+i]+n[i]*cut*Math.signum(f[6])*oldRange[1];axis[i]=n[i]*cut*flip;}
        }
        bindExtrusionManipulator(first,reverse,symmetric,origin,axis,preview);post(preview);
    }

    private static double[] profileCenter(SketchPrimitive shape) {
        if(shape.type.equals("Okrąg")||shape.isEllipse())return new double[]{shape.x1,shape.y1};
        if(shape.curves!=null){double[] b=SketchCurves.bounds(shape.curves);return new double[]{(b[0]+b[2])*.5,(b[1]+b[3])*.5};}
        if(shape.vertices!=null){double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;for(int i=0;i<shape.vertices.length;i+=2){minX=Math.min(minX,shape.vertices[i]);maxX=Math.max(maxX,shape.vertices[i]);minY=Math.min(minY,shape.vertices[i+1]);maxY=Math.max(maxY,shape.vertices[i+1]);}return new double[]{(minX+maxX)*.5,(minY+maxY)*.5};}
        return new double[]{(shape.x1+shape.x2)*.5,(shape.y1+shape.y2)*.5};
    }

    private boolean holeFromMaximum(HoleThread.Cylinder hole) {
        double[] a=hole.end(false),b=hole.end(true),pa=projectWorldPoint(a[0],a[1],a[2]),pb=projectWorldPoint(b[0],b[1],b[2]);return pb[2]>pa[2];
    }
    private void beginThreadTool() {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(inSketch()){finishSketchWork();if(inSketch())return;}
        if(project.importedMeshSource!=null||!project.hasSolid()){operationMessage="Gwint wymaga otworu w bryle B-Rep";invalidate();return;}
        closePropertyEditor();preserveExtrudedBase();commitActiveFeature();BodyGraph.begin(project);mode="Gwint";threadSelections.clear();
        for(HoleThread.Cylinder hole:holeCylinders)if(hole.face==selectedFace)threadSelections.add(hole.face);showThreadPanel();
    }
    private void threadTap(int face) {
        if(face<0){clearViewportSelection();return;}
        for(HoleThread.Cylinder hole:holeCylinders)if(hole.face==face){if(!threadSelections.add(face))threadSelections.remove(face);showThreadPanel();invalidate();return;}
        operationMessage="Dotknij wewnętrznej, walcowej ściany otworu. Obróć model, jeśli nie jest widoczna.";invalidate();
    }
    private void showThreadPanel() {
        List<HoleThread.Cylinder> selected=new ArrayList<>();for(HoleThread.Cylinder hole:holeCylinders)if(threadSelections.contains(hole.face))selected.add(hole);
        if(selected.isEmpty()) {android.widget.TextView message=new android.widget.TextView(getContext());message.setText("Dotknij walcowych ścian jednego lub kilku otworów. Ponowne dotknięcie usuwa wybór.");new PropertyBuilder().setTitle("Gwint · wybierz otwory").setView(message).setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";threadSelections.clear();}).show();return;}
        List<HoleThread.Preset> presets=HoleThread.nearest(selected.get(0).radius*2);
        if(presets.isEmpty()){operationMessage="Brak pasującego presetu dla średnicy wskazanego otworu";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText(String.format(java.util.Locale.US,"Wybrane otwory: %d · pierwszy Ø %s. Rzeczywisty profil V 60°.",selected.size(),formatMm(selected.get(0).radius*2)));form.addView(info);
        android.widget.Spinner preset=new android.widget.Spinner(getContext());List<String> labels=new ArrayList<>();for(HoleThread.Preset p:presets)labels.add(p.label());preset.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,labels));form.addView(preset);
        android.widget.CheckBox automatic=new android.widget.CheckBox(getContext());automatic.setText("Dobierz preset osobno do każdego otworu");automatic.setChecked(selected.size()>1);form.addView(automatic);
        double min=Double.POSITIVE_INFINITY;for(HoleThread.Cylinder hole:selected)min=Math.min(min,hole.max-hole.min);
        EditText depth=labeledCoordinate(form,"Głębokość gwintu [mm]",min);
        android.widget.CheckBox full=new android.widget.CheckBox(getContext());full.setText("Pełna długość każdego otworu");full.setChecked(true);form.addView(full);depth.setEnabled(false);full.setOnCheckedChangeListener((button,checked)->depth.setEnabled(!checked));
        android.widget.CheckBox left=new android.widget.CheckBox(getContext());left.setText("Gwint lewy");form.addView(left);
        android.widget.CheckBox otherEnd=new android.widget.CheckBox(getContext());otherEnd.setText("Od przeciwnego końca otworu");form.addView(otherEnd);
        new PropertyBuilder().setTitle("Gwint · "+selected.size()+" otworów").setView(form).setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";threadSelections.clear();})
            .setPositiveButton("Utwórz gwinty",(d,w)-> {
                try {
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    for(HoleThread.Cylinder hole:selected) {
                        List<HoleThread.Preset> choices=automatic.isChecked()?HoleThread.nearest(hole.radius*2):presets;if(choices.isEmpty())throw new IllegalArgumentException("Brak presetu dla jednego z otworów");
                        HoleThread.Preset chosen=choices.get(automatic.isChecked()?0:preset.getSelectedItemPosition());
                        double[] feature=HoleThread.create(hole,chosen,full.isChecked()?hole.max-hole.min:parseCoordinate(depth),holeFromMaximum(hole)^otherEnd.isChecked(),left.isChecked());
                        next.committedFeatures.add(feature);next.committedProfileIds.add("");next.featureBodyTargets.add(hole.body);next.featureBodyOutputs.add(hole.body);
                    }
                    commitProjectAsync(next,baseline,"Utworzono gwinty: "+selected.size());
                }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }
    /** Human-readable kernel failure naming the history operation that could not be rebuilt. */
    private String kernelFailureMessage(CadProject model,Exception error) {
        if(!(error instanceof NativeCadEngine.KernelException))return error==null?"Nieznany błąd":error.getMessage();
        NativeCadEngine.KernelException failure=(NativeCadEngine.KernelException)error;
        String where="";
        if(failure.feature==-1)where="Bryła bazowa: ";
        else if(failure.feature>=0&&failure.feature<model.committedFeatures.size()) {
            for(CadTimeline.Item item:CadTimeline.entries(model))if(item.kind==CadTimeline.Kind.FEATURE&&item.featureIndex==failure.feature){where="Operacja „"+item.title+"” (nr "+(failure.feature+1)+"): ";break;}
            if(where.isEmpty())where="Operacja nr "+(failure.feature+1)+": ";
        }else if(failure.feature>=model.committedFeatures.size())where="Bieżąca operacja: ";
        return where+failure.reason+". Poprzednia geometria została zachowana.";
    }
    private void commitProjectAsync(CadProject next,String baseline,String success) {
        if(modelOperationPending){operationMessage="Trwa już obliczanie operacji";invalidate();return;}
        BodyGraph.reconcile(next);CadProject current=project;final double[] base,features;
        try {project=next;base=evaluationBase()==null?null:evaluationBase().clone();features=kernelFeatures();}finally {project=current;}
        final String source=next.importedStep?new File(projectRepository.importedStepFile().getParentFile(),next.importedStepSource).getAbsolutePath():null;
        if(modelCommitWorker==null)modelCommitWorker=new GeometryPreviewWorker(task->post(task));modelOperationPending=true;operationMessage="Obliczanie geometrii…";invalidate();
        modelCommitWorker.submit(()->NativeCadEngine.evaluateModelChecked(next.width,next.height,next.depth,next.importedStep?0:next.filletRadius,base,features,source),(packed,error)->{
            modelOperationPending=false;
            try {
                if(error!=null)throw new IllegalArgumentException(error.getMessage());CadModelResult result=new CadModelResult(packed);
                if(!project.toJson().toString().equals(baseline))throw new IllegalArgumentException("Dokument zmienił się podczas obliczania. Wynik nie został zastosowany.");
                projectRepository.save(next);project=next;nativeMesh=result.mesh;faceBodies=result.bodies;faceFeatures=result.features;holeCylinders=result.holes;
                modelWidth=next.width;modelHeight=next.height;modelDepth=next.depth;nativePrism=NativeCadEngine.createPrism(modelWidth,modelHeight,modelDepth);modelSnapGeometry=null;updateMeshCenter();technicalSheet=null;scheduleThumbnail();
                undoStack.push(baseline);while(undoStack.size()>50)undoStack.removeLast();redoStack.clear();mode="Wybierz";selectedFace=-1;threadSelections.clear();highlightBody=0;highlightFeature=next.committedFeatures.size()-1;operationMessage=success;
            }catch(Exception failure){operationMessage="Nie zatwierdzono operacji: "+kernelFailureMessage(next,error!=null?error:failure);}
            invalidate();
        });
    }
    private void showThreadFeatureEditor(int index) {
        double[] original=project.committedFeatures.get(index);LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText diameter=coordinateInput("Średnica nominalna [mm]",original[9]*2),pitch=coordinateInput("Skok [mm]",original[10]),depth=coordinateInput("Głębokość [mm]",original[11]);form.addView(diameter);form.addView(pitch);form.addView(depth);
        android.widget.CheckBox left=new android.widget.CheckBox(getContext());left.setText("Gwint lewy");left.setChecked(original[1]<0);form.addView(left);
        new PropertyBuilder().setTitle("Edytuj gwint").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {double[] f=original.clone();f[1]=left.isChecked()?-1:1;f[9]=parseCoordinate(diameter)/2;f[10]=parseCoordinate(pitch);f[11]=parseCoordinate(depth);HoleThread.validate(f);
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));next.committedFeatures.set(index,f);commitProjectAsync(next,baseline,"Zaktualizowano gwint");
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    /** Context menu of a history operation (long press on the timeline). */
    private void showTimelineActions(CadTimeline.Item item) {
        new PropertyBuilder().setTitle((item.featureIndex+1)+". "+item.title).setItems(new String[]{"Edytuj operację","Usuń operację…"},(d,which)->{
            if(which==0){showCommittedFeatureEditor(item.featureIndex);return;}
            confirmFeatureRemoval(item);
        }).setNegativeButton("Zamknij",null).show();
    }
    private void confirmFeatureRemoval(CadTimeline.Item item) {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(SketchTransactions.active(project)){operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";invalidate();return;}
        final FeatureRemoval.Plan plan;
        try{plan=FeatureRemoval.plan(project,item.featureIndex);}catch(IllegalArgumentException error){operationMessage="Nie można usunąć: "+error.getMessage();invalidate();return;}
        android.widget.TextView text=new android.widget.TextView(getContext());
        StringBuilder message=new StringBuilder("Operacja zostanie usunięta z historii, a model przeliczony. Jeśli przeliczenie się nie powiedzie, dokument pozostanie bez zmian. Można cofnąć.");
        if(!plan.consequences.isEmpty()){message.append("\n\nKonsekwencje:");for(String c:plan.consequences)message.append("\n· ").append(c);}
        text.setText(message);
        new PropertyBuilder().setTitle("Usuń: "+item.title).setView(text).setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->{
            try {
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));
                FeatureRemoval.apply(next,plan);BodyGraph.validate(next);
                commitProjectAsync(next,baseline,"Usunięto operację „"+item.title+"”"+(plan.removed.size()>1?" i zależne ("+(plan.removed.size()-1)+")":""));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage="Nie usunięto: "+error.getMessage();invalidate();}
        }).show();
    }

    // ---- Parametric mirror / linear / circular patterns of history operations ----
    private boolean prepareSolidTool(String name) {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return false;}
        if(inSketch()){finishSketchWork();if(inSketch())return false;}
        if(project.importedMeshSource!=null||!project.hasSolid()){operationMessage=name+" wymaga bryły B-Rep";invalidate();return false;}
        if(modelOperationPending){operationMessage="Trwa już obliczanie operacji";invalidate();return false;}
        closePropertyEditor();preserveExtrudedBase();commitActiveFeature();BodyGraph.begin(project);return true;
    }
    private int featureBody(CadProject model,int index){return index<model.featureBodyOutputs.size()&&model.featureBodyOutputs.get(index)>0?model.featureBodyOutputs.get(index):1;}
    private void beginPatternTool(String tool) {
        final int kind=tool.startsWith("Lustro")?FeaturePattern.MIRROR:tool.startsWith("Szyk liniowy")?FeaturePattern.LINEAR:FeaturePattern.CIRCULAR;
        String name=kind==FeaturePattern.MIRROR?"Lustro":kind==FeaturePattern.LINEAR?"Szyk liniowy":"Szyk kołowy";
        if(!prepareSolidTool(name))return;
        List<Integer> eligible=new ArrayList<>();List<String> titles=new ArrayList<>();
        for(CadTimeline.Item item:CadTimeline.entries(project))if(item.kind==CadTimeline.Kind.FEATURE&&FeaturePattern.eligible(project.committedFeatures.get(item.featureIndex))) {
            eligible.add(item.featureIndex);titles.add((item.featureIndex+1)+". "+item.title+" · "+BodyGraph.name(project,featureBody(project,item.featureIndex)));
        }
        if(eligible.isEmpty()){operationMessage=name+": brak operacji do powielenia. Powielać można wyciągnięcia, wycięcia, obroty profilu i gwinty z historii.";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText("Zaznacz operacje z historii. Kopie powstają jako jedna operacja i śledzą późniejsze zmiany źródeł.");form.addView(info);
        List<android.widget.CheckBox> boxes=new ArrayList<>();
        for(int i=0;i<eligible.size();i++){android.widget.CheckBox box=new android.widget.CheckBox(getContext());box.setText(titles.get(i));box.setChecked(eligible.get(i)==highlightFeature||(eligible.size()==1));form.addView(box);boxes.add(box);}
        new PropertyBuilder().setTitle(name+" · operacje źródłowe").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Dalej",(d,w)->{
            List<Integer> chosen=new ArrayList<>();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isChecked())chosen.add(eligible.get(i));
            if(chosen.isEmpty()){operationMessage="Zaznacz co najmniej jedną operację";invalidate();return;}
            int body=featureBody(project,chosen.get(0));for(int source:chosen)if(featureBody(project,source)!=body){operationMessage="Wybrane operacje należą do różnych brył – utwórz osobne szyki";invalidate();return;}
            int[] sources=new int[chosen.size()];for(int i=0;i<sources.length;i++)sources[i]=chosen.get(i);
            showPatternParameters(kind,sources,-1);
        }).show();
    }
    /** Planar face currently selected in the viewport, as point + outward normal, or null. */
    private double[] selectedPlanarFace() {
        if(selectedFace<0||!isFacePlanar(selectedFace))return null;
        double l=Math.sqrt(selectedNormalX*selectedNormalX+selectedNormalY*selectedNormalY+selectedNormalZ*selectedNormalZ);if(l<1e-9)return null;
        return new double[]{selectedPointX,selectedPointY,selectedPointZ,selectedNormalX/l,selectedNormalY/l,selectedNormalZ/l};
    }
    private HoleThread.Cylinder selectedCylinder() {
        for(HoleThread.Cylinder hole:holeCylinders)if(hole.face==selectedFace)return hole;return null;
    }
    private void showPatternParameters(int kind,int[] sources,int editIndex) {
        double[] original=editIndex>=0?project.committedFeatures.get(editIndex):null;
        final double[] face=selectedPlanarFace();final HoleThread.Cylinder cylinder=selectedCylinder();
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        List<String> choices=new ArrayList<>();List<double[]> frames=new ArrayList<>();
        if(original!=null){choices.add("Bieżące odniesienie");frames.add(new double[]{original[3],original[4],original[5],original[6],original[7],original[8]});}
        if(kind==FeaturePattern.MIRROR) {
            choices.add("Płaszczyzna YZ (normalna X)");frames.add(new double[]{0,0,0,1,0,0});
            choices.add("Płaszczyzna XZ (normalna Y)");frames.add(new double[]{0,0,0,0,1,0});
            choices.add("Płaszczyzna XY (normalna Z)");frames.add(new double[]{0,0,0,0,0,1});
            if(face!=null){choices.add("Płaszczyzna zaznaczonej ściany (stała)");frames.add(face);}
        } else {
            String noun=kind==FeaturePattern.LINEAR?"Kierunek":"Oś";
            choices.add(noun+" X");frames.add(new double[]{0,0,0,1,0,0});
            choices.add(noun+" Y");frames.add(new double[]{0,0,0,0,1,0});
            choices.add(noun+" Z");frames.add(new double[]{0,0,0,0,0,1});
            if(kind==FeaturePattern.LINEAR&&face!=null){choices.add("Normalna zaznaczonej ściany");frames.add(face);}
            if(kind==FeaturePattern.CIRCULAR&&cylinder!=null){choices.add("Oś zaznaczonej ściany walcowej");frames.add(new double[]{cylinder.origin[0],cylinder.origin[1],cylinder.origin[2],cylinder.axis[0],cylinder.axis[1],cylinder.axis[2]});}
        }
        android.widget.TextView reference=new android.widget.TextView(getContext());reference.setText(kind==FeaturePattern.MIRROR?"Płaszczyzna lustra":kind==FeaturePattern.LINEAR?"Kierunek szyku":"Oś obrotu");form.addView(reference);
        android.widget.Spinner frame=new android.widget.Spinner(getContext());frame.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,choices));form.addView(frame);
        if(original==null&&((kind==FeaturePattern.MIRROR&&face!=null)||(kind==FeaturePattern.LINEAR&&face!=null)||(kind==FeaturePattern.CIRCULAR&&cylinder!=null)))frame.setSelection(choices.size()-1);
        EditText offset=null,px=null,py=null,pz=null,count=null,value=null;android.widget.CheckBox reverse=null;
        if(kind==FeaturePattern.MIRROR) offset=labeledCoordinate(form,"Odsunięcie płaszczyzny wzdłuż normalnej [mm]",0);
        else {
            if(kind==FeaturePattern.CIRCULAR) {
                double[] o=original!=null?new double[]{original[3],original[4],original[5]}:new double[3];
                px=labeledCoordinate(form,"Punkt osi X [mm] (dla osi X/Y/Z)",o[0]);py=labeledCoordinate(form,"Punkt osi Y [mm]",o[1]);pz=labeledCoordinate(form,"Punkt osi Z [mm]",o[2]);
            }
            count=labeledCoordinate(form,"Liczba wystąpień (z oryginałem)",original!=null?original[9]:kind==FeaturePattern.LINEAR?3:6);
            value=labeledCoordinate(form,kind==FeaturePattern.LINEAR?"Odstęp [mm]":"Kąt całkowity [°] (360 = równo na obwodzie)",original!=null?Math.abs(original[10]):kind==FeaturePattern.LINEAR?20:360);
            reverse=new android.widget.CheckBox(getContext());reverse.setText(kind==FeaturePattern.LINEAR?"Przeciwny kierunek":"Przeciwny kierunek obrotu");reverse.setChecked(original!=null&&original[10]<0);
            if(kind==FeaturePattern.LINEAR)form.addView(reverse);
        }
        final EditText fOffset=offset,fx=px,fy=py,fz=pz,fCount=count,fValue=value;final android.widget.CheckBox fReverse=reverse;
        final android.widget.CheckBox flipAxis=new android.widget.CheckBox(getContext());
        if(kind==FeaturePattern.CIRCULAR){flipAxis.setText("Przeciwny kierunek obrotu");form.addView(flipAxis);}
        String title=(kind==FeaturePattern.MIRROR?"Lustro":kind==FeaturePattern.LINEAR?"Szyk liniowy":"Szyk kołowy")+" · "+sources.length+(sources.length==1?" operacja":" operacje");
        new PropertyBuilder().setTitle(title).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Utwórz",(d,w)->{
            try {
                double[] selected=frames.get(frame.getSelectedItemPosition()).clone();
                double[] origin={selected[0],selected[1],selected[2]},direction={selected[3],selected[4],selected[5]};
                int n=0;double v=0;
                if(kind==FeaturePattern.MIRROR) {
                    double shift=parseCoordinate(fOffset),l=Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);
                    for(int i=0;i<3;i++)origin[i]+=direction[i]/l*shift;
                } else {
                    double countValue=parseCoordinate(fCount);if(countValue!=Math.floor(countValue))throw new IllegalArgumentException("Liczba wystąpień musi być całkowita");n=(int)countValue;
                    v=parseCoordinate(fValue);
                    if(kind==FeaturePattern.LINEAR){if(fReverse.isChecked())v=-Math.abs(v);else v=Math.abs(v);}
                    else {
                        // The typed point defines the axis unless the axis of a picked cylinder is used.
                        if(!choices.get(frame.getSelectedItemPosition()).startsWith("Oś zaznaczonej")){origin[0]=parseCoordinate(fx);origin[1]=parseCoordinate(fy);origin[2]=parseCoordinate(fz);}
                        if(flipAxis.isChecked())for(int i=0;i<3;i++)direction[i]=-direction[i];
                    }
                }
                double[] record=FeaturePattern.create(kind,sources,origin,direction,n,v);
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                int body=featureBody(next,sources[0]);
                if(editIndex>=0)next.committedFeatures.set(editIndex,record);
                else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
                FeaturePattern.validateSources(next.committedFeatures,editIndex>=0?editIndex:next.committedFeatures.size()-1);
                commitProjectAsync(next,baseline,(editIndex>=0?"Zaktualizowano: ":"Utworzono: ")+FeaturePattern.describe(record));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }
    private void showPatternEditor(int index) {
        double[] f=project.committedFeatures.get(index);
        StringBuilder info=new StringBuilder(FeaturePattern.describe(f)).append("\nŹródła:");
        for(int source:FeaturePattern.sources(f))for(CadTimeline.Item item:CadTimeline.entries(project))if(item.kind==CadTimeline.Kind.FEATURE&&item.featureIndex==source)info.append("\n· ").append(source+1).append(". ").append(item.title);
        android.widget.TextView text=new android.widget.TextView(getContext());text.setText(info);
        new PropertyBuilder().setTitle(FeaturePattern.label(f)).setView(text).setNegativeButton("Zamknij",null)
            .setNeutralButton("Pokaż źródła",(d,w)->{highlightFeature=FeaturePattern.sources(f)[0];operationMessage="Podświetlono pierwszą operację źródłową";invalidate();})
            .setPositiveButton("Edytuj parametry",(d,w)->showPatternParameters(FeaturePattern.kind(f),FeaturePattern.sources(f),index)).show();
    }

    // ---- Boolean between document bodies ----
    private void beginBodyCombine(int operation,int editIndex) {
        String name=operation==BodyCombine.JOIN?"Połącz bryły":operation==BodyCombine.CUT?"Odejmij bryły":"Część wspólna brył";
        if(editIndex<0&&!prepareSolidTool(name))return;
        CadProject view=project;
        if(editIndex>=0) {
            // Bodies available at that point of the history: evaluate the routes before the operation.
            try{view=CadProject.fromJson(project.toJson());FeatureRemoval.truncate(view,editIndex);}catch(Exception error){operationMessage=error.getMessage();invalidate();return;}
        }
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(view));
        if(ids.size()<2){operationMessage=name+": potrzebne są co najmniej dwie bryły. Utwórz drugą bryłę: Wyciągnij → Dodaj — nowa bryła.";invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(project,ids.get(i));
        double[] original=editIndex>=0?project.committedFeatures.get(editIndex):null;
        int defaultTarget=original!=null?featureBody(project,editIndex):project.selectedBody;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView t1=new android.widget.TextView(getContext());t1.setText("Bryła docelowa (zostaje zmieniona)");form.addView(t1);
        android.widget.Spinner target=new android.widget.Spinner(getContext());target.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(target);
        android.widget.TextView t2=new android.widget.TextView(getContext());t2.setText(operation==BodyCombine.CUT?"Bryła odejmowana (narzędzie)":"Bryła narzędzia");form.addView(t2);
        android.widget.Spinner tool=new android.widget.Spinner(getContext());tool.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(tool);
        int ti=Math.max(0,ids.indexOf(defaultTarget));target.setSelection(ti);
        int tool0=original!=null?ids.indexOf(BodyCombine.tool(original)):(ti==0?1:0);tool.setSelection(Math.max(0,tool0));
        android.widget.CheckBox keep=new android.widget.CheckBox(getContext());keep.setText("Zachowaj bryłę narzędzia");keep.setChecked(original!=null&&original[3]==1);form.addView(keep);
        if(editIndex>=0)target.setEnabled(false);
        new PropertyBuilder().setTitle(editIndex>=0?"Edytuj: "+name:name).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Wykonaj",(d,w)->{
            try {
                int a=ids.get(target.getSelectedItemPosition()),b=ids.get(tool.getSelectedItemPosition());
                if(a==b)throw new IllegalArgumentException("Wybierz dwie różne bryły");
                double[] record=BodyCombine.create(operation,b,keep.isChecked());
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                if(editIndex>=0)next.committedFeatures.set(editIndex,record);
                else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(a);next.featureBodyOutputs.add(a);next.selectedBody=a;}
                BodyGraph.validate(next);
                commitProjectAsync(next,baseline,BodyCombine.describe(record,BodyGraph.name(next,b)));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    // ---- Hole on a planar face ----
    private void beginHoleTool() {
        if(!prepareSolidTool("Otwór"))return;
        mode="Otwór";holeEditIndex=-1;holeReference=null;
        if(selectedFace>=0)holeTap(selectedFace);
        else {operationMessage="Otwór: dotknij płaskiej ściany w miejscu środka otworu. Położenie można potem wpisać dokładnie.";closePropertyEditor();invalidate();}
    }
    private void holeTap(int face) {
        if(face<0){clearViewportSelection();if(holeEditIndex<0){holeReference=null;}return;}
        if(!isFacePlanar(face)){operationMessage="Otwór można umieścić tylko na płaskiej ścianie";invalidate();return;}
        Integer origin=faceFeatures.get(face);if(origin==null){operationMessage="Nie można ustalić operacji, która utworzyła tę ścianę";invalidate();return;}
        selectedFace=face;
        double l=Math.sqrt(selectedNormalX*selectedNormalX+selectedNormalY*selectedNormalY+selectedNormalZ*selectedNormalZ);
        holeReference=new double[]{selectedPointX,selectedPointY,selectedPointZ,selectedNormalX/l,selectedNormalY/l,selectedNormalZ/l,origin};
        showHolePanel();invalidate();
    }
    private void showHolePanel() {
        if(holeReference==null)return;
        double[] original=holeEditIndex>=0?project.committedFeatures.get(holeEditIndex):null;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText("Środek leży na wskazanej ścianie i przesuwa się razem z nią przy zmianie parametrów. Dotknij innej płaskiej ściany, aby ją zmienić.");form.addView(info);
        EditText x=labeledCoordinate(form,"Środek X [mm]",holeReference[0]),y=labeledCoordinate(form,"Środek Y [mm]",holeReference[1]),z=labeledCoordinate(form,"Środek Z [mm]",holeReference[2]);
        EditText diameter=labeledCoordinate(form,"Średnica [mm]",original!=null?original[9]:6.5);if(holeEditIndex>=0)bound(diameter,"hole:"+holeEditIndex+":d");
        android.widget.CheckBox through=new android.widget.CheckBox(getContext());through.setText("Na wylot (przez całą bryłę)");through.setChecked(original==null||original[1]==1);form.addView(through);
        EditText depth=labeledCoordinate(form,"Głębokość [mm]",original!=null&&original[1]==0?original[10]:10);if(holeEditIndex>=0)bound(depth,"hole:"+holeEditIndex+":h");
        EditText tip=labeledCoordinate(form,"Kąt wierzchołkowy wiertła [°] (0 = płaskie dno)",original!=null&&original[1]==0?original[14]:118);
        android.widget.TextView headLabel=new android.widget.TextView(getContext());headLabel.setText("Pogłębienie");form.addView(headLabel);
        android.widget.Spinner head=new android.widget.Spinner(getContext());head.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Brak (otwór prosty)","Walcowe (pod łeb walcowy)","Stożkowe (pod łeb stożkowy)"}));
        if(original!=null)head.setSelection((int)original[11]);form.addView(head);
        EditText headDiameter=labeledCoordinate(form,"Średnica pogłębienia [mm]",original!=null&&original[11]!=0?original[12]:11);
        EditText headValue=labeledCoordinate(form,"Głębokość pogłębienia [mm] / kąt stożka [°]",original!=null&&original[11]!=0?original[13]:6);
        Runnable states=()->{depth.setEnabled(!through.isChecked());tip.setEnabled(!through.isChecked());boolean headed=head.getSelectedItemPosition()!=0;headDiameter.setEnabled(headed);headValue.setEnabled(headed);};
        through.setOnCheckedChangeListener((b,c)->states.run());
        // The second head value is a length for a counterbore and an angle for a countersink.
        if(original!=null&&original[11]==HoleFeature.COUNTERSINK){headValue.setTag(null);headValue.setText(Units.input(original[13],"mm"));}
        final int[] shownHead={original!=null?(int)original[11]:0};
        head.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position!=shownHead[0]) {
                    shownHead[0]=position;
                    if(position==HoleFeature.COUNTERSINK){headValue.setTag(null);headValue.setText("90");}
                    else {headValue.setTag(LENGTH_INPUT);setLengthText(headValue,6);}
                }
                states.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        states.run();
        new PropertyBuilder().setTitle(holeEditIndex>=0?"Edytuj otwór":"Otwór").setView(form)
            .setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";holeReference=null;holeEditIndex=-1;})
            .setPositiveButton(holeEditIndex>=0?"Zastosuj":"Utwórz otwór",(d,w)->{
                try {
                    int kind=head.getSelectedItemPosition();
                    double[] point={parseCoordinate(x),parseCoordinate(y),parseCoordinate(z)};
                    double headSecond=kind==HoleFeature.COUNTERSINK?Units.parseNumber(headValue.getText().toString()):kind==HoleFeature.COUNTERBORE?parseCoordinate(headValue):0;
                    double[] record=HoleFeature.create(point,new double[]{holeReference[3],holeReference[4],holeReference[5]},(int)holeReference[6],parseCoordinate(diameter),through.isChecked(),
                        through.isChecked()?0:parseCoordinate(depth),kind,kind==0?0:parseCoordinate(headDiameter),headSecond,through.isChecked()?0:parseCoordinate(tip));
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    int body=selectedFace>=0?faceBodies.getOrDefault(selectedFace,1):project.selectedBody>0?project.selectedBody:1;
                    if(holeEditIndex>=0)next.committedFeatures.set(holeEditIndex,record);
                    else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
                    int at=holeEditIndex>=0?holeEditIndex:next.committedFeatures.size()-1;bindField(next,"hole:"+at+":d",diameter);if(through.isChecked())next.parameterBindings.remove("hole:"+at+":h");else bindField(next,"hole:"+at+":h",depth);
                    int edited=holeEditIndex;holeEditIndex=-1;holeReference=null;
                    commitProjectAsync(next,baseline,(edited>=0?"Zaktualizowano: ":"Utworzono: ")+HoleFeature.describe(record));
                }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    // ---- Draft (taper) of planar faces ----
    private void beginDraftTool(int editIndex) {
        if(!prepareSolidTool("Pochylenie ścian"))return;
        mode="Pochyl ściany";draftEditIndex=editIndex;draftSelections.clear();
        if(selectedFace>=0)draftTap(selectedFace);else showDraftPanel();
    }
    private void draftTap(int face) {
        if(face<0){clearViewportSelection();return;}
        if(draftSelections.remove(face)!=null){showDraftPanel();invalidate();return;}
        if(!isFacePlanar(face)){operationMessage="Pochylić można płaską ścianę";invalidate();return;}
        Integer origin=faceFeatures.get(face);if(origin==null){operationMessage="Nie można ustalić operacji, która utworzyła tę ścianę";invalidate();return;}
        try{draftSelections.put(face,new ShellFeature.Face(new double[]{selectedPointX,selectedPointY,selectedPointZ},new double[]{selectedNormalX,selectedNormalY,selectedNormalZ},origin));}
        catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        showDraftPanel();invalidate();
    }
    private void showDraftPanel() {
        double[] original=draftEditIndex>=0&&draftEditIndex<project.committedFeatures.size()?project.committedFeatures.get(draftEditIndex):null;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText(draftSelections.isEmpty()?"Dotknij bocznych, płaskich ścian do pochylenia (np. ścianek formy). Ponowne dotknięcie usuwa wybór.":"Pochylane ściany: "+draftSelections.size());form.addView(info);
        EditText angle=labeledCoordinate(form,"Kąt pochylenia [°] (ujemny odwraca kierunek)",original!=null?original[2]:3);
        android.widget.TextView planeLabel=new android.widget.TextView(getContext());planeLabel.setText("Płaszczyzna neutralna (kierunek wyjmowania = jej normalna)");form.addView(planeLabel);
        android.widget.Spinner plane=new android.widget.Spinner(getContext());plane.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"XY (wyjmowanie wzdłuż Z)","XZ (wzdłuż Y)","YZ (wzdłuż X)"}));form.addView(plane);
        int axis=original==null?2:Math.abs(original[8])>.5?2:Math.abs(original[7])>.5?1:0;plane.setSelection(axis==2?0:axis==1?1:2);
        EditText level=labeledCoordinate(form,"Położenie płaszczyzny neutralnej [mm]",original!=null?original[3+axis]:0);
        android.widget.CheckBox flip=new android.widget.CheckBox(getContext());flip.setText("Wyjmowanie w kierunku ujemnym");flip.setChecked(original!=null&&original[6+axis]<0);form.addView(flip);
        new PropertyBuilder().setTitle(draftEditIndex>=0?"Pochylenie · wskaż ściany ponownie":"Pochylenie ścian").setView(form)
            .setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";draftSelections.clear();draftEditIndex=-1;})
            .setPositiveButton(draftEditIndex>=0?"Zastosuj":"Pochyl",(d,w)->{
                try {
                    int a=new int[]{2,1,0}[plane.getSelectedItemPosition()];double[] point=new double[3],pull=new double[3];point[a]=parseCoordinate(level);pull[a]=flip.isChecked()?-1:1;
                    double[] record=DraftFeature.create(new ArrayList<>(draftSelections.values()),parseCoordinate(angle),point,pull);
                    Integer first=draftSelections.keySet().iterator().next();int body=faceBodies.getOrDefault(first,1);
                    for(Integer face:draftSelections.keySet())if(faceBodies.getOrDefault(face,1)!=body)throw new IllegalArgumentException("Wskazane ściany należą do różnych brył");
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    if(draftEditIndex>=0)next.committedFeatures.set(draftEditIndex,record);
                    else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
                    boolean edited=draftEditIndex>=0;draftEditIndex=-1;commitProjectAsync(next,baseline,(edited?"Zaktualizowano: ":"Utworzono: ")+DraftFeature.describe(record));
                }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }
    private void showDraftEditor(int index) {
        double[] f=project.committedFeatures.get(index);LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText angle=labeledCoordinate(form,"Kąt pochylenia [°]",f[2]);
        new PropertyBuilder().setTitle("Edytuj pochylenie").setView(form).setNegativeButton("Anuluj",null)
            .setNeutralButton("Wskaż ściany ponownie",(d,w)->beginDraftTool(index))
            .setPositiveButton("Zastosuj",(d,w)->{
                try{double[] updated=DraftFeature.withAngle(f,parseCoordinate(angle));String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));next.committedFeatures.set(index,updated);commitProjectAsync(next,baseline,"Zaktualizowano: "+DraftFeature.describe(updated));}
                catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    // ---- Loft, sweep and whole-body patterns ----
    /** Like prepareSolidTool, but an empty document is allowed: the operation may create the first body. */
    private boolean prepareProfileTool(String name) {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return false;}
        if(inSketch()){finishSketchWork();if(inSketch())return false;}
        if(project.importedMeshSource!=null){operationMessage=name+" wymaga modelu B-Rep";invalidate();return false;}
        if(!project.hasSolid()){operationMessage=name+": najpierw utwórz pierwszą bryłę (np. wyciągnięcie). Kolejne mogą powstać jako „Nowa bryła”.";invalidate();return false;}
        if(modelOperationPending){operationMessage="Trwa już obliczanie operacji";invalidate();return false;}
        closePropertyEditor();preserveExtrudedBase();commitActiveFeature();BodyGraph.begin(project);return true;
    }
    private String placedLabel(SweepLoft.Placed e){return e.sketchName+" · "+(e.shape.isEllipse()?"Elipsa":e.shape.type)+(e.shape.construction?" (pomocnicza)":"");}
    private android.widget.Spinner operationSpinner(LinearLayout form,int flags) {
        android.widget.TextView label=new android.widget.TextView(getContext());label.setText("Operacja");form.addView(label);
        android.widget.Spinner operation=new android.widget.Spinner(getContext());operation.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Nowa bryła","Połącz z bryłą","Wytnij z bryły"}));
        operation.setSelection(flags==4?0:flags==1?2:project.hasSolid()?1:0);form.addView(operation);return operation;
    }
    private static int flagsOf(int position){return position==0?4:position==2?1:0;}
    private void commitProfileOperation(double[] record,String ref,int editIndex,String success) {
        try {
            String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
            boolean create=((int)record[1]&4)!=0;
            if(editIndex>=0) {
                boolean wasCreate=next.featureBodyTargets.get(editIndex)==0;
                if(wasCreate!=create)throw new IllegalArgumentException("Zmiana między „Nowa bryła” a łączeniem wymaga usunięcia i ponownego utworzenia operacji");
                next.committedFeatures.set(editIndex,record);next.committedProfileIds.set(editIndex,ref);
            } else {
                if(!create&&!next.hasSolid())throw new IllegalArgumentException("Brak bryły do połączenia lub wycięcia – wybierz „Nowa bryła”");
                next.committedFeatures.add(record);next.committedProfileIds.add(ref);
                if(create){next.featureBodyTargets.add(0);next.featureBodyOutputs.add(BodyGraph.allocate(next));}
                else {int body=next.selectedBody>0?next.selectedBody:1;next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
            }
            BodyGraph.validate(next);commitProjectAsync(next,baseline,success);
        }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
    }
    private void showLoftDialog() {
        List<SweepLoft.Placed> profiles=new ArrayList<>();for(SweepLoft.Placed e:SweepLoft.elements(project))if(e.shape.closedProfile())profiles.add(e);
        if(profiles.size()<2){operationMessage="Przejście: potrzebne są co najmniej dwa zamknięte profile w różnych szkicach (np. na płaszczyźnie odsuniętej).";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText("Zaznacz profile w kolejności przejścia (kolejność zaznaczania). Każdy profil z innego szkicu.");form.addView(info);
        List<SweepLoft.Placed> order=new ArrayList<>();
        for(SweepLoft.Placed e:profiles){android.widget.CheckBox box=new android.widget.CheckBox(getContext());box.setText(placedLabel(e));box.setOnCheckedChangeListener((b,c)->{if(c)order.add(e);else order.remove(e);});form.addView(box);}
        android.widget.CheckBox ruled=new android.widget.CheckBox(getContext());ruled.setText("Ściany prostokreślne (bez wygładzania)");form.addView(ruled);
        android.widget.Spinner operation=operationSpinner(form,project.hasSolid()?0:4);
        new PropertyBuilder().setTitle("Przejście między profilami").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Utwórz",(d,w)->{
            try{double[] record=SweepLoft.loftRecord(order,flagsOf(operation.getSelectedItemPosition()),ruled.isChecked());commitProfileOperation(record,SweepLoft.loftRef(order),-1,"Utworzono przejście przez "+order.size()+" profile");}
            catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }
    private void showSweepDialog() {
        List<SweepLoft.Placed> all=SweepLoft.elements(project),profiles=new ArrayList<>(),paths=new ArrayList<>();
        for(SweepLoft.Placed e:all){if(e.shape.closedProfile())profiles.add(e);if(!e.shape.isEllipse()&&!SketchSpline.is(e.shape)&&!e.shape.type.equals("Okrąg")&&!e.shape.type.equals("Prostokąt"))paths.add(e);}
        if(profiles.isEmpty()||paths.isEmpty()){operationMessage="Przeciągnięcie: potrzebny zamknięty profil i ścieżka z linii/łuków (zwykle w szkicu prostopadłym do profilu).";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView t1=new android.widget.TextView(getContext());t1.setText("Profil");form.addView(t1);
        List<String> names=new ArrayList<>();for(SweepLoft.Placed e:profiles)names.add(placedLabel(e));
        android.widget.Spinner profile=new android.widget.Spinner(getContext());profile.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(profile);
        android.widget.TextView t2=new android.widget.TextView(getContext());t2.setText("Ścieżka – zaznacz kolejne elementy (od początku ścieżki, przy profilu)");form.addView(t2);
        List<SweepLoft.Placed> order=new ArrayList<>();
        for(SweepLoft.Placed e:paths){android.widget.CheckBox box=new android.widget.CheckBox(getContext());box.setText(placedLabel(e));box.setOnCheckedChangeListener((b,c)->{if(c)order.add(e);else order.remove(e);});form.addView(box);}
        android.widget.Spinner operation=operationSpinner(form,project.hasSolid()?0:4);
        new PropertyBuilder().setTitle("Przeciągnięcie po ścieżce").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Utwórz",(d,w)->{
            try{SweepLoft.Placed chosen=profiles.get(profile.getSelectedItemPosition());double[] record=SweepLoft.sweepRecord(chosen,order,flagsOf(operation.getSelectedItemPosition()));commitProfileOperation(record,SweepLoft.sweepRef(chosen,order),-1,"Utworzono przeciągnięcie po ścieżce ("+order.size()+" el.)");}
            catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }
    private void showLoftSweepEditor(int index) {
        double[] f=project.committedFeatures.get(index);LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText(SweepLoft.label(f)+": kształt pochodzi ze wskazanych elementów szkiców – edytuj szkic, a operacja zostanie przeliczona.");form.addView(info);
        android.widget.CheckBox ruled=new android.widget.CheckBox(getContext());ruled.setText("Ściany prostokreślne");ruled.setChecked(f[3]==1);if(SweepLoft.loft(f))form.addView(ruled);
        boolean create=index<project.featureBodyTargets.size()&&project.featureBodyTargets.get(index)==0;
        android.widget.Spinner operation=new android.widget.Spinner(getContext());operation.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,create?new String[]{"Nowa bryła"}:new String[]{"Połącz z bryłą","Wytnij z bryły"}));
        if(!create)operation.setSelection(((int)f[1]&1)!=0?1:0);form.addView(operation);
        new PropertyBuilder().setTitle("Edytuj: "+SweepLoft.label(f)).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try{double[] r=f.clone();if(!create)r[1]=operation.getSelectedItemPosition()==1?1:0;if(SweepLoft.loft(r))r[3]=ruled.isChecked()?1:0;SweepLoft.validate(r);commitProfileOperation(r,project.committedProfileIds.get(index),index,"Zaktualizowano: "+SweepLoft.label(r));}
            catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }
    private void showBodyPatternDialog(int kind,int editIndex) {
        double[] original=editIndex>=0?project.committedFeatures.get(editIndex):null;
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(project));if(ids.isEmpty()){operationMessage="Brak bryły do powielenia";invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(project,ids.get(i));
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.Spinner body=new android.widget.Spinner(getContext());body.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(body);
        body.setSelection(Math.max(0,ids.indexOf(original!=null?featureBody(project,editIndex):project.selectedBody)));if(original!=null)body.setEnabled(false);
        android.widget.Spinner axis=new android.widget.Spinner(getContext());axis.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,kind==1?new String[]{"Płaszczyzna YZ (normalna X)","Płaszczyzna XZ (normalna Y)","Płaszczyzna XY (normalna Z)"}:new String[]{kind==2?"Kierunek X":"Oś X","Kierunek Y".replace("Kierunek",kind==2?"Kierunek":"Oś"),"Kierunek Z".replace("Kierunek",kind==2?"Kierunek":"Oś")}));form.addView(axis);
        int a0=original==null?(kind==3?2:0):Math.abs(original[6])>.5?0:Math.abs(original[7])>.5?1:2;axis.setSelection(a0);
        double[] o=original!=null?original:new double[16];
        EditText px=labeledCoordinate(form,kind==1?"Punkt płaszczyzny X [mm]":"Punkt osi X [mm]",o[3]),py=labeledCoordinate(form,kind==1?"Punkt płaszczyzny Y [mm]":"Punkt osi Y [mm]",o[4]),pz=labeledCoordinate(form,kind==1?"Punkt płaszczyzny Z [mm]":"Punkt osi Z [mm]",o[5]);
        EditText count=kind==1?null:labeledCoordinate(form,"Liczba wystąpień (z oryginałem)",original!=null?original[9]:3);
        EditText value=kind==1?null:labeledCoordinate(form,kind==2?"Odstęp [mm] (ujemny = przeciwny kierunek)":"Kąt całkowity [°]",original!=null?original[10]:kind==2?20:360);
        String title=kind==1?"Lustro bryły":kind==2?"Szyk liniowy bryły":"Szyk kołowy bryły";
        new PropertyBuilder().setTitle(editIndex>=0?"Edytuj: "+title:title).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Utwórz",(d,w)->{
            try {
                double[] dir=new double[3];dir[axis.getSelectedItemPosition()]=1;int n=0;double v=0;
                if(kind!=1){double c=parseCoordinate(count);if(c!=Math.floor(c))throw new IllegalArgumentException("Liczba wystąpień musi być całkowita");n=(int)c;v=parseCoordinate(value);}
                double[] record=SweepLoft.bodyPatternRecord(kind,new double[]{parseCoordinate(px),parseCoordinate(py),parseCoordinate(pz)},dir,n,v);
                int target=ids.get(body.getSelectedItemPosition());
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                if(editIndex>=0)next.committedFeatures.set(editIndex,record);
                else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                commitProjectAsync(next,baseline,title+" · "+BodyGraph.name(next,target));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    // ---- Split a body by a plane ----
    private void showBodySplit(int editIndex) {
        double[] original=editIndex>=0?project.committedFeatures.get(editIndex):null;final double[] face=selectedPlanarFace();
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(project));if(original!=null)ids.remove(Integer.valueOf(BodySplit.created(original)));
        if(ids.isEmpty()){operationMessage="Brak bryły do podziału";invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(project,ids.get(i));
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText("Część za płaszczyzną pozostaje w wybranej bryle, część przed nią (po stronie normalnej) staje się nową bryłą.");form.addView(info);
        android.widget.Spinner body=new android.widget.Spinner(getContext());body.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(body);
        body.setSelection(Math.max(0,ids.indexOf(original!=null?featureBody(project,editIndex):project.selectedBody)));if(original!=null)body.setEnabled(false);
        List<String> choices=new ArrayList<>(java.util.Arrays.asList("Płaszczyzna YZ (normalna X)","Płaszczyzna XZ (normalna Y)","Płaszczyzna XY (normalna Z)"));if(face!=null)choices.add("Płaszczyzna zaznaczonej ściany (stała)");
        android.widget.Spinner plane=new android.widget.Spinner(getContext());plane.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,choices));form.addView(plane);
        int axis=original==null?0:Math.abs(original[5])>.5?0:Math.abs(original[6])>.5?1:2;plane.setSelection(original==null&&face!=null?3:axis);
        double[] centre={meshCenterX,meshCenterY,meshCenterZ};
        EditText offset=labeledCoordinate(form,"Położenie płaszczyzny [mm] (dla ściany: odsunięcie)",original!=null?original[2+axis]:face!=null?0:centre[0]);
        android.widget.CheckBox flip=new android.widget.CheckBox(getContext());flip.setText("Odwróć strony podziału");flip.setChecked(original!=null&&original[5+axis]<0);form.addView(flip);
        new PropertyBuilder().setTitle(editIndex>=0?"Edytuj podział bryły":"Podziel bryłę").setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Podziel",(d,w)->{
            try {
                int choice=plane.getSelectedItemPosition();double value=parseCoordinate(offset),sign=flip.isChecked()?-1:1;double[] point=new double[3],normal=new double[3];
                if(choice<3){point[choice]=value;normal[choice]=sign;}else for(int i=0;i<3;i++){point[i]=face[i]+face[3+i]*value;normal[i]=face[3+i]*sign;}
                int target=ids.get(body.getSelectedItemPosition());
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                double[] record;
                if(editIndex>=0){record=BodySplit.create(BodySplit.created(original),point,normal);next.committedFeatures.set(editIndex,record);}
                else {record=BodySplit.create(BodyGraph.allocate(next),point,normal);next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                BodyGraph.validate(next);commitProjectAsync(next,baseline,BodySplit.describe(record)+" → nowa bryła: "+BodyGraph.name(next,BodySplit.created(record)));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    // ---- Move / rotate / scale a body ----
    private void showBodyTransform(int kind,int editIndex) {
        double[] original=editIndex>=0?project.committedFeatures.get(editIndex):null;
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(project));if(ids.isEmpty()){operationMessage="Brak bryły do przekształcenia";invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(project,ids.get(i));
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView bodyLabel=new android.widget.TextView(getContext());bodyLabel.setText("Bryła");form.addView(bodyLabel);
        android.widget.Spinner body=new android.widget.Spinner(getContext());body.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(body);
        body.setSelection(Math.max(0,ids.indexOf(original!=null?featureBody(project,editIndex):project.selectedBody)));if(original!=null)body.setEnabled(false);
        double[] o=original!=null?original:new double[16];
        EditText a=labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie X [mm]":kind==BodyTransform.ROTATE?"Punkt osi X [mm]":"Środek skalowania X [mm]",o[2]);
        EditText b=labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie Y [mm]":kind==BodyTransform.ROTATE?"Punkt osi Y [mm]":"Środek skalowania Y [mm]",o[3]);
        EditText c=labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie Z [mm]":kind==BodyTransform.ROTATE?"Punkt osi Z [mm]":"Środek skalowania Z [mm]",o[4]);
        android.widget.Spinner axis=new android.widget.Spinner(getContext());
        EditText value=null;
        if(kind==BodyTransform.ROTATE) {
            android.widget.TextView axisLabel=new android.widget.TextView(getContext());axisLabel.setText("Oś obrotu");form.addView(axisLabel);
            axis.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"X","Y","Z"}));form.addView(axis);
            axis.setSelection(original==null?2:Math.abs(o[5])>.5?0:Math.abs(o[6])>.5?1:2);
            value=labeledCoordinate(form,"Kąt [°]",original!=null?(o[5]+o[6]+o[7]<0?-o[8]:o[8]):90);
        } else if(kind==BodyTransform.SCALE)value=labeledCoordinate(form,"Współczynnik skali",original!=null?o[8]:2);
        final EditText fValue=value;
        String title=kind==BodyTransform.MOVE?"Przesuń bryłę":kind==BodyTransform.ROTATE?"Obróć bryłę":"Skaluj bryłę";
        new PropertyBuilder().setTitle(editIndex>=0?"Edytuj: "+title:title).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Wykonaj",(d,w)->{
            try {
                double x=parseCoordinate(a),y=parseCoordinate(b),z=parseCoordinate(c);double[] record;
                if(kind==BodyTransform.MOVE)record=BodyTransform.move(x,y,z);
                else if(kind==BodyTransform.ROTATE){double[] dir=new double[3];dir[axis.getSelectedItemPosition()]=1;record=BodyTransform.rotate(new double[]{x,y,z},dir,parseCoordinate(fValue));}
                else record=BodyTransform.scale(new double[]{x,y,z},parseCoordinate(fValue));
                int target=ids.get(body.getSelectedItemPosition());
                String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                if(editIndex>=0)next.committedFeatures.set(editIndex,record);
                else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                commitProjectAsync(next,baseline,BodyTransform.describe(record)+" · "+BodyGraph.name(next,target));
            }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
        }).show();
    }

    // ---- Shell (hollow body) ----
    private void beginShellTool(int editIndex) {
        if(!prepareSolidTool("Powłoka"))return;
        mode="Powłoka";shellEditIndex=editIndex;shellSelections.clear();
        if(selectedFace>=0)shellTap(selectedFace);else showShellPanel();
    }
    private void shellTap(int face) {
        if(face<0){clearViewportSelection();return;}
        if(shellSelections.remove(face)!=null){showShellPanel();invalidate();return;}
        if(!isFacePlanar(face)){operationMessage="Powłoka usuwa tylko płaskie ściany. Dotknij płaskiej ściany.";invalidate();return;}
        Integer origin=faceFeatures.get(face);if(origin==null){operationMessage="Nie można ustalić operacji, która utworzyła tę ścianę";invalidate();return;}
        try {
            shellSelections.put(face,new ShellFeature.Face(new double[]{selectedPointX,selectedPointY,selectedPointZ},new double[]{selectedNormalX,selectedNormalY,selectedNormalZ},origin));
        }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
        showShellPanel();invalidate();
    }
    private void showShellPanel() {
        double[] original=shellEditIndex>=0&&shellEditIndex<project.committedFeatures.size()?project.committedFeatures.get(shellEditIndex):null;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText(shellSelections.isEmpty()?"Dotknij płaskich ścian, które mają zostać otwarte. Bez wskazanych ścian bryła zostanie wydrążona z zamkniętą pustką.":"Usuwane ściany: "+shellSelections.size()+". Ponowne dotknięcie usuwa wybór.");form.addView(info);
        EditText thickness=labeledCoordinate(form,"Grubość ścianki [mm]",original!=null?original[2]:2);
        android.widget.CheckBox outside=new android.widget.CheckBox(getContext());outside.setText("Grubość na zewnątrz bryły");outside.setChecked(original!=null&&original[3]==1);form.addView(outside);
        new PropertyBuilder().setTitle(shellEditIndex>=0?"Powłoka · wskaż ściany ponownie":"Powłoka").setView(form)
            .setNegativeButton("Anuluj",(d,w)->{mode="Wybierz";shellSelections.clear();shellEditIndex=-1;})
            .setPositiveButton(shellEditIndex>=0?"Zastosuj":"Utwórz powłokę",(d,w)->{
                try {
                    double[] record=ShellFeature.create(new ArrayList<>(shellSelections.values()),parseCoordinate(thickness),outside.isChecked());
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    int body=1;
                    if(!shellSelections.isEmpty()){Integer first=shellSelections.keySet().iterator().next();body=faceBodies.getOrDefault(first,1);for(Integer face:shellSelections.keySet())if(faceBodies.getOrDefault(face,1)!=body)throw new IllegalArgumentException("Wskazane ściany należą do różnych brył");}
                    else body=project.selectedBody>0?project.selectedBody:1;
                    if(shellEditIndex>=0){next.committedFeatures.set(shellEditIndex,record);if(!shellSelections.isEmpty()&&featureBody(next,shellEditIndex)!=body)throw new IllegalArgumentException("Ściany należą do innej bryły niż edytowana powłoka");}
                    else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
                    shellEditIndex=-1;commitProjectAsync(next,baseline,(original!=null?"Zaktualizowano: ":"Utworzono: ")+ShellFeature.describe(record));
                }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }
    private void showShellEditor(int index) {
        double[] f=project.committedFeatures.get(index);LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());info.setText(ShellFeature.describe(f));form.addView(info);
        EditText thickness=labeledCoordinate(form,"Grubość ścianki [mm]",f[2]);
        android.widget.CheckBox outside=new android.widget.CheckBox(getContext());outside.setText("Grubość na zewnątrz bryły");outside.setChecked(f[3]==1);form.addView(outside);
        new PropertyBuilder().setTitle("Edytuj powłokę").setView(form).setNegativeButton("Anuluj",null)
            .setNeutralButton("Wskaż ściany ponownie",(d,w)->beginShellTool(index))
            .setPositiveButton("Zastosuj",(d,w)->{
                try {
                    double[] updated=ShellFeature.withThickness(f,parseCoordinate(thickness),outside.isChecked());
                    String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));next.committedFeatures.set(index,updated);
                    commitProjectAsync(next,baseline,"Zaktualizowano: "+ShellFeature.describe(updated));
                }catch(org.json.JSONException|IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    private void showFaceFeatureDialog(SketchPrimitive profile) {
        new PropertyBuilder().setTitle("Wyciągnięcie · operacja").setItems(new String[]{"Dodaj — nowa bryła","Połącz z bryłą","Wytnij","Część wspólna"},(d,index)->chooseExtrusionBody(profile,new String[]{"new","add","cut","common"}[index])).setNegativeButton("Anuluj",null).show();showExtrusionSketch=true;
    }
    private void chooseExtrusionBody(SketchPrimitive profile,String operation) {
        List<Integer> bodies=new ArrayList<>(BodyGraph.bodies(project));
        if(operation.equals("new")||bodies.size()<2){showFaceFeatureDepth(profile,operation);return;}
        String[] names=new String[bodies.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(project,bodies.get(i));
        new PropertyBuilder().setTitle("Bryła docelowa").setItems(names,(d,w)->{project.selectedBody=bodies.get(w);showFaceFeatureDepth(profile,operation);}).setNegativeButton("Anuluj",null).show();showExtrusionSketch=true;
    }

    private void showFaceFeatureDepth(SketchPrimitive profile, String operation) {
        BodyGraph.begin(project);final int targetBody=project.selectedBody;final boolean editActive=operation.equals(project.featureOperation)&&profile.id.equals(project.featureProfileId);
        if(project.importedMeshSource!=null) {
            operationMessage="Operacje bryłowe wymagają B-Rep, nie siatki STL";invalidate();return;
        }
        selectedSketchId=profile.id;
        double initialDepth=operation.equals(project.featureOperation)&&profile.id.equals(project.featureProfileId)
            ?project.featureDepth:Math.abs(project.featureDepth);
        EditText input = lengthInput(Math.abs(project.featureDepth));
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(input);
        android.widget.CheckBox reverse=directionCheck(initialDepth);form.addView(reverse);
        android.widget.CheckBox symmetric=symmetricCheck(operation.equals(project.featureOperation)&&profile.id.equals(project.featureProfileId)&&project.featureSymmetric,reverse);form.addView(symmetric);
        android.widget.TextView previewStatus=new android.widget.TextView(getContext());
        previewStatus.setText("Podgląd nie zmienia dokumentu. Zastosuj zapisuje operację.");form.addView(previewStatus);
        new PropertyBuilder()
            .setTitle(FeatureDirection.label(FeatureDirection.featureCode(operation,false))+" · długość [mm]")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = parseCoordinate(input);
                    if (!Double.isFinite(value) || value < 0.1 || value > 5000.0) {
                        operationMessage = "Głębokość musi mieścić się w zakresie 0,1–5000 mm";
                        invalidate();
                        return;
                    }
                    final double signedDepth=FeatureDirection.signed(value,reverse.isChecked(),symmetric.isChecked());
                    if (commitModelChange(() -> {
                        if(!editActive){preserveExtrudedBase();commitActiveFeature();}
                        project.featureOperation = operation;project.activeBodyTarget=targetBody;
                        project.featureProfileId = profile.id;
                        project.featureDepth = signedDepth;
                        project.featureSymmetric=symmetric.isChecked();
                        project.sketchExtruded = true;
                    })) {
                        treeSelection = "Model";
                        mode = "Wybierz";
                        operationMessage = "Gotowe: "+FeatureDirection.label(FeatureDirection.featureCode(operation,false));
                    }
                    invalidate();
                } catch (NumberFormatException ignored) {
                    operationMessage = "Niepoprawna wartość głębokości";
                    invalidate();
                }
            }).show();
        final android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(owner==null || propertyEditor!=owner) return;
            invalidateGeometryPreview();
            try {
                double depth=parseCoordinate(input);
                if(!Double.isFinite(depth)||depth<.1||depth>5000) throw new NumberFormatException();
                depth=FeatureDirection.signed(depth,reverse.isChecked(),symmetric.isChecked());
                double[] candidate=profile.appendVertices(new double[]{profile.featureType(),FeatureDirection.featureCode(operation,symmetric.isChecked()),
                    profile.x1,profile.y1,profile.x2,profile.y2,depth,
                    project.sketchOriginX,project.sketchOriginY,project.sketchOriginZ,
                    project.sketchUx,project.sketchUy,project.sketchUz,project.sketchVx,project.sketchVy,project.sketchVz});
                int length=candidate.length;for(double[] feature:project.committedFeatures) length+=feature.length;
                double[] values=new double[length];int offset=0;
                for(double[] feature:project.committedFeatures) {System.arraycopy(feature,0,values,offset,feature.length);offset+=feature.length;}
                System.arraycopy(candidate,0,values,offset,candidate.length);
                final double proposedDepth=depth;final boolean proposedSymmetric=symmetric.isChecked();
                previewStatus.setText("Obliczanie podglądu…");
                submitGeometryPreview(owner,meshPreviewQuery(project.featureBase,values,project.filletRadius,modelDepth),mesh->{
                    if(mesh.length>0)previewStatus.setText("Podgląd · "+Math.abs(proposedDepth)+" mm"+(proposedSymmetric?" · symetrycznie":proposedDepth<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");
                    else previewStatus.setText(previewFailure("Brak poprawnego wyniku. Zmień głębokość lub profil."));
                });
            } catch(NumberFormatException error) {previewStatus.setText("Głębokość: 0,1–5000 mm");}
            invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,350);
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
        double u=profile.type.equals("Okrąg")||profile.isEllipse()?profile.x1:(profile.x1+profile.x2)*.5;
        double v=profile.type.equals("Okrąg")||profile.isEllipse()?profile.y1:(profile.y1+profile.y2)*.5;
        double sign=operation.equals("cut")?-1:1;
        double[] origin={project.sketchOriginX+u*project.sketchUx+v*project.sketchVx,
            project.sketchOriginY+u*project.sketchUy+v*project.sketchVy,
            project.sketchOriginZ+u*project.sketchUz+v*project.sketchVz};
        double[] axis={sign*(project.sketchUy*project.sketchVz-project.sketchUz*project.sketchVy),
            sign*(project.sketchUz*project.sketchVx-project.sketchUx*project.sketchVz),
            sign*(project.sketchUx*project.sketchVy-project.sketchUy*project.sketchVx)};
        bindExtrusionManipulator(input,reverse,symmetric,origin,axis,preview);showExtrusionSketch=true;
    }

    private android.widget.CheckBox directionCheck(double depth) {
        android.widget.CheckBox result=new android.widget.CheckBox(getContext());
        result.setText("Odwróć kierunek");result.setChecked(depth<0);return result;
    }

    private android.widget.CheckBox symmetricCheck(boolean checked,android.widget.CheckBox reverse) {
        android.widget.CheckBox result=new android.widget.CheckBox(getContext());
        result.setText("Symetrycznie (długość łączna)");result.setChecked(checked);reverse.setEnabled(!checked);return result;
    }

    private void showBaseExtrusionEditor(double[] base,String profileId) {
        if(ExtrusionPlan.placedBase(base)||project.extrusionExtents.containsKey(-1)){showExtrusionExtentEditor(-1);return;}
        EditText input=coordinateInput("Głębokość [mm]",Math.abs(base[6]));
        android.widget.CheckBox reverse=directionCheck(base[6]);
        android.widget.CheckBox symmetric=symmetricCheck(FeatureDirection.baseSymmetric(base[5]),reverse);
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(input);form.addView(reverse);form.addView(symmetric);
        android.widget.TextView status=new android.widget.TextView(getContext());
        status.setText("Podgląd nie zmienia dokumentu.");form.addView(status);
        boolean initial=!project.sketchExtruded&&project.featureBase==null;
        new PropertyBuilder().setTitle("Wyciągnięcie bazowe [mm]").setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(dialog,which)-> {
                try {
                    double signed=FeatureDirection.signed(parseCoordinate(input),reverse.isChecked(),symmetric.isChecked());
                    double[] candidate=base.clone();candidate[6]=signed;
                    candidate[5]=FeatureDirection.baseCode(base[5],symmetric.isChecked());
                    if (commitModelChange(() -> {
                        project.featureBase=candidate;project.featureBaseProfileId=profileId;
                        if(initial){project.featureProfileId=profileId;project.featureOperation="none";project.featureSymmetric=false;project.sketchExtruded=true;}
                        project.depth=Math.abs(signed);
                        if(initial)project.filletRadius=0;
                    })) {
                        mode="Wybierz";
                        operationMessage="Gotowe: wyciągnięcie "+Math.abs(signed)+" mm"+(symmetric.isChecked()?" · symetrycznie":signed<0?" · kierunek odwrócony":"");
                    }
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                invalidate();
            }).show();
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(owner==null||propertyEditor!=owner)return;
            invalidateGeometryPreview();
            try {
                double signed=FeatureDirection.signed(parseCoordinate(input),reverse.isChecked(),symmetric.isChecked());
                double[] candidate=base.clone();candidate[6]=signed;
                candidate[5]=FeatureDirection.baseCode(base[5],symmetric.isChecked());
                final boolean proposedSymmetric=symmetric.isChecked();status.setText("Obliczanie podglądu…");
                submitGeometryPreview(owner,meshPreviewQuery(candidate,kernelFeatures(),initial?0:project.filletRadius,Math.abs(signed)),mesh->{
                    if(mesh.length>0)status.setText("Podgląd · "+Math.abs(signed)+" mm"+(proposedSymmetric?" · symetrycznie":signed<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");
                    else status.setText(previewFailure("Brak poprawnego wyniku. Zmień głębokość lub kierunek."));
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);invalidateGeometryPreview();removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
        double u=base[0]==1?base[1]:(base[1]+base[3])*.5,v=base[0]==1?base[2]:(base[2]+base[4])*.5;
        int plane=FeatureDirection.basePlane(base[5]);
        double[] origin=plane==1?new double[]{u,0,v}:plane==2?new double[]{0,u,v}:new double[]{u,v,0};
        double[] axis=plane==1?new double[]{0,1,0}:plane==2?new double[]{1,0,0}:new double[]{0,0,1};
        bindExtrusionManipulator(input,reverse,symmetric,origin,axis,preview);showExtrusionSketch=true;
    }

    /**
     * Perspective camera distance in model millimetres. 420 mm keeps the familiar look of small
     * parts; larger models move the camera back so no vertex can reach or pass the eye point.
     */
    private double cameraDistance = 420.0;
    private boolean orthographicView;
    private static final double ORTHOGRAPHIC_DISTANCE = 1.0e6;
    private double modelRadius;
    private double viewDistance() { return orthographicView ? ORTHOGRAPHIC_DISTANCE : cameraDistance; }

    /** Standard named orientation (model Y is screen-up in the front view). */
    private void applyStandardView(String view) {
        switch (view) {
            case "Przód": cameraYaw = 0f; cameraPitch = 0f; break;
            case "Tył": cameraYaw = 180f; cameraPitch = 0f; break;
            case "Góra": cameraYaw = 0f; cameraPitch = 90f; break;
            case "Dół": cameraYaw = 0f; cameraPitch = -90f; break;
            case "Prawy bok": cameraYaw = -90f; cameraPitch = 0f; break;
            case "Lewy bok": cameraYaw = 90f; cameraPitch = 0f; break;
            default: cameraYaw = -45f; cameraPitch = (float)Math.toDegrees(Math.atan(1 / Math.sqrt(2))); break;
        }
        fitView();
        operationMessage = "Widok: " + view + (orthographicView ? " · rzut prostokątny" : "");
    }

    /** Zoom and pan so that the whole model (or sketch) fits the free viewport. */
    private void fitView() {
        panX = panY = 0;
        List<double[]> sketchPoints = fitSketchPoints();
        double radius = modelRadius;
        // With a solid, the view pivots on the mesh centre; a sketch-only document is panned to
        // its own centre below, so its size is measured from the centre of its extents.
        double ox = meshCenterX, oy = meshCenterY, oz = meshCenterZ;
        if (modelRadius <= 1e-6 && !sketchPoints.isEmpty()) {
            double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, hi = {-lo[0], -lo[0], -lo[0]};
            for (double[] p : sketchPoints) { double[] w = sketchToWorld(p[0], p[1]); for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], w[k]); hi[k] = Math.max(hi[k], w[k]); } }
            ox = (lo[0] + hi[0]) / 2; oy = (lo[1] + hi[1]) / 2; oz = (lo[2] + hi[2]) / 2;
        }
        for (double[] p : sketchPoints) {
            double[] w = sketchToWorld(p[0], p[1]);
            radius = Math.max(radius, Math.sqrt((w[0]-ox)*(w[0]-ox)+(w[1]-oy)*(w[1]-oy)+(w[2]-oz)*(w[2]-oz)));
        }
        if (!(radius > 1e-6)) { zoom = 1; return; }
        float width = viewportRight() - leftWidth(), height = getHeight() - viewTop() - dp(68) - bottomSystemInset;
        float available = Math.max(dp(80), Math.min(width, height)) * 0.42f;
        double perspective = orthographicView ? 1 : viewDistance() / Math.max(1e-6, viewDistance() - radius);
        zoom = (float)Math.max(0.02, Math.min(200, available / (radius * perspective * dp(3.2f))));
        if (modelRadius <= 1e-6 && !sketchPoints.isEmpty()) {
            // Sketch-only document: centre the sketch extents in the free viewport.
            double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
            for (double[] p : sketchPoints) {
                double[] screen = projectSketchDepth(p[0], p[1]);
                minX = Math.min(minX, screen[0]); maxX = Math.max(maxX, screen[0]);
                minY = Math.min(minY, screen[1]); maxY = Math.max(maxY, screen[1]);
            }
            panX = (float)(workCenterX - (minX + maxX) / 2);
            panY = (float)(workCenterY - (minY + maxY) / 2);
        }
    }

    private double[] sketchToWorld(double u, double v) {
        return new double[]{project.sketchOriginX + u * project.sketchUx + v * project.sketchVx,
            project.sketchOriginY + u * project.sketchUy + v * project.sketchVy,
            project.sketchOriginZ + u * project.sketchUz + v * project.sketchVz};
    }

    /** Extent points of the active sketch when it is what the user works on (sketch mode or no solid). */
    private List<double[]> fitSketchPoints() {
        List<double[]> points = new ArrayList<>();
        if (project.sketch.isEmpty() || (!inSketch() && project.hasSolid())) return points;
        for (SketchPrimitive p : project.sketch) {
            if (p.isEllipse()) {
                double r = p.major();points.add(new double[]{p.x1 - r, p.y1 - r}); points.add(new double[]{p.x1 + r, p.y1 + r});
            } else if (p.type.equals("Okrąg")) {
                double r = Math.hypot(p.x2 - p.x1, p.y2 - p.y1);
                points.add(new double[]{p.x1 - r, p.y1 - r}); points.add(new double[]{p.x1 + r, p.y1 + r});
                points.add(new double[]{p.x1 - r, p.y1 + r}); points.add(new double[]{p.x1 + r, p.y1 - r});
            } else if (p.curves != null) {
                double[] b = SketchCurves.bounds(p.curves);
                points.add(new double[]{b[0], b[1]}); points.add(new double[]{b[2], b[3]});
                points.add(new double[]{b[0], b[3]}); points.add(new double[]{b[2], b[1]});
            } else if (p.vertices != null) {
                for (int i = 0; i + 1 < p.vertices.length; i += 2) points.add(new double[]{p.vertices[i], p.vertices[i + 1]});
            } else {
                points.add(new double[]{p.x1, p.y1}); points.add(new double[]{p.x2, p.y2});
                if (p.type.equals("Prostokąt")) { points.add(new double[]{p.x1, p.y2}); points.add(new double[]{p.x2, p.y1}); }
            }
        }
        return points;
    }

    private void updateMeshCenter() {
        if (nativeMesh.length < 13) {
            meshCenterX = meshCenterY = meshCenterZ = 0.0;
            modelRadius = 0; cameraDistance = 420.0;
            return;
        }
        double xmin = Double.POSITIVE_INFINITY, ymin = Double.POSITIVE_INFINITY, zmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY, ymax = Double.NEGATIVE_INFINITY, zmax = Double.NEGATIVE_INFINITY;
        for (int offset = 0; offset + 12 < nativeMesh.length; offset += 13) {
            for (int vertex = 0; vertex < 3; vertex++) {
                double x = nativeMesh[offset + vertex * 3];
                double y = nativeMesh[offset + vertex * 3 + 1];
                double z = nativeMesh[offset + vertex * 3 + 2];
                xmin = Math.min(xmin, x); xmax = Math.max(xmax, x);
                ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
                zmin = Math.min(zmin, z); zmax = Math.max(zmax, z);
            }
        }
        meshCenterX = (xmin + xmax) / 2.0;
        meshCenterY = (ymin + ymax) / 2.0;
        meshCenterZ = (zmin + zmax) / 2.0;
        modelRadius = 0.5 * Math.sqrt((xmax - xmin) * (xmax - xmin) + (ymax - ymin) * (ymax - ymin) + (zmax - zmin) * (zmax - zmin));
        cameraDistance = Math.max(420.0, modelRadius * 4.0);
    }

    private void showExportPicker() {
        String[] formats = {"STEP — precyzyjna bryła", "STL — siatka binarna", "STL — tekst ASCII", "DXF — szkic 2D", "PDF — arkusz techniczny A4", "OBJ — siatka, jednostki mm"};
        new PropertyBuilder()
            .setTitle("Eksportuj projekt")
            .setItems(formats, (dialog, which) -> {
                if((which==0||which==4) && project.importedMeshSource != null) {
                    operationMessage="Eksport STEP wymaga bryły B-Rep; siatkę zapisz jako STL";
                    invalidate(); return;
                }
                if (fileRequestListener != null) {
                    fileRequestListener.requestExport(which == 0 ? "step" : which == 1 ? "stl" : which == 2 ? "stla" : which==3 ? "dxf" : which==4 ? "pdf" : "obj");
                }
            })
            .setNegativeButton("Anuluj", null)
            .show();
    }

    public boolean exportStep(String path) {
        if(SketchTransactions.active(project)){operationMessage="Zakończ albo anuluj edycję szkicu przed eksportem";return false;}
        if(!project.hasSolid()){operationMessage="Brak bryły do eksportu STEP";return false;}
        if(project.importedMeshSource != null) return false;
        if (project.importedStep && packedFeatures().length > 0) {
            return NativeCadEngine.evaluateFeatures(modelWidth, modelHeight, modelDepth,
                0, null, kernelFeatures(), path,
                importedSourceFile().getAbsolutePath()).length > 0;
        }
        if (!project.importedStep && (project.featureBase != null || !project.committedFeatures.isEmpty()||!project.featureOperation.equals("none"))) {
            return NativeCadEngine.evaluateFeatures(modelWidth, modelHeight, modelDepth,
                project.filletRadius, project.featureBase, kernelFeatures(), path).length > 0;
        }
        if (project.importedStep && importedSourceFile().isFile()) {
            try (FileInputStream input = new FileInputStream(importedSourceFile());
                 FileOutputStream output = new FileOutputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                output.getFD().sync();
                return true;
            } catch (IOException error) {
                return false;
            }
        }
        SketchPrimitive profile = featureProfile();
        String type = profile == null ? "Prostokąt" : profile.type;
        double x1 = profile == null ? 0.0 : profile.x1;
        double y1 = profile == null ? 0.0 : profile.y1;
        double x2 = profile == null ? modelWidth : profile.x2;
        double y2 = profile == null ? modelHeight : profile.y2;
        return NativeCadEngine.exportStep(path, modelWidth, modelHeight, modelDepth,
            project.sketchExtruded && profile != null, type, x1, y1, x2, y2, project.sketchPlane,
            project.filletRadius, project.featureOperation, project.featureDepth,
            project.sketchOriginX, project.sketchOriginY, project.sketchOriginZ,
            project.sketchUx, project.sketchUy, project.sketchUz,
            project.sketchVx, project.sketchVy, project.sketchVz);
    }

    public File importedStepFile() { return projectRepository.importedStepFile(); }

    private File importedSourceFile() {
        return new File(projectRepository.importedStepFile().getParentFile(), project.importedStepSource);
    }

    public boolean activateImportedStep() {
        try { importStep(projectRepository.importedStepFile()); return true; }
        catch (IOException error) { operationMessage=error.getMessage(); return false; }
    }

    public void importStep(File staged) throws IOException {
        double[] imported = NativeCadEngine.tessellateStep(staged.getAbsolutePath());
        if (!CadMesh.isValid(imported,false)) throw new IOException("Plik nie zawiera poprawnej bryły STEP");
        String sourceName = "source-" + UUID.randomUUID() + ".step";
        File retained = new File(projectRepository.importedStepFile().getParentFile(), sourceName);
        java.nio.file.Files.copy(staged.toPath(), retained.toPath());
        if (!commitModelChange(() -> {
            resetImportedModel();
            project.importedStep=true;project.importedStepSource=sourceName;
        })) {
            retained.delete();
            throw new IOException(operationMessage);
        }
        finishModelImport("Gotowe: otwarto STEP");
    }

    private void resetImportedModel() {
        project.importedStep=false;project.importedStepSource="imported.step";project.importedMeshSource=null;
        project.committedFeatures.clear();project.extrusionExtents.clear();project.featureBodyTargets.clear();project.featureBodyOutputs.clear();project.bodyGraphStart=-1;project.activeBodyTarget=project.activeBodyOutput=0;project.selectedBody=1;
        project.committedProfileIds.clear();
        project.savedSketches.clear();
        project.sketchId = UUID.randomUUID().toString();project.sketchName="Szkic";
        project.featureBase = null;
        project.featureBaseProfileId = null;
        project.featureOperation = "none";
        project.featureProfileId = null;
        project.sketch.clear();
        project.coincidences.clear();
        project.sketchEditBaseline=null;project.sketchLineage.clear();project.sketchBindings.clear();
        project.polylineDraft.clear();project.threePointDraft.clear();project.threePointTool="";
        project.sketchPlane="XY";project.setPrincipalSketchFrame("XY");
        project.sketchExtruded = false;
        project.featureSymmetric=false;
        project.filletRadius = 0.0;
    }

    private void finishModelImport(String message) {
        closePropertyEditor();
        mode="Wybierz";sketchPlane=project.sketchPlane;sketchPlaneChosen=false;
        cameraYaw=-28;cameraPitch=22;zoom=1;panX=panY=0;showSolid=true;treeSelection="Model";
        selectedFace=-1;selectedSketchId=null;arcDraft.clear();polylinePreview=null;
        draggedSketch=dragPreview=null;dragSolvedPreview=null;sketching=false;sketchRect=null;
        trimMode=dimensionMode=coincidenceMode=false;coincidenceFirst=null;snapMarker=null;
        measureA=measureB=null;measuredEdgeText="";polylineTouch=false;
        operationMessage=message;invalidate();
    }

    private void checkpoint() {
        try {
            undoStack.push(project.toJson().toString());
            while (undoStack.size() > 50) undoStack.removeLast();
            redoStack.clear();
        } catch (Exception ignored) {
            // A failed snapshot must not corrupt the current document.
        }
    }

    /** Isolated document edit: geometry and persistence must succeed before history advances. */
    private boolean commitModelChange(Runnable change) {
        CadProject previous = project;
        double[] previousMesh = nativeMesh, previousPrism = nativePrism;
        java.util.Map<Integer,Integer> previousFaceBodies=faceBodies,previousFaceFeatures=faceFeatures;
        List<HoleThread.Cylinder> previousHoles=holeCylinders;
        String previousInfo = modelInfo, previousMode = mode;
        TechnicalSheet previousSheet = technicalSheet;
        double w = modelWidth, h = modelHeight, d = modelDepth;
        try {
            String snapshot = previous.toJson().toString();
            project = CadProject.fromJson(new org.json.JSONObject(snapshot));
            change.run();
            BodyGraph.reconcile(project);
            modelWidth = project.width; modelHeight = project.height; modelDepth = project.depth;
            mode = "Wybierz";
            if (!updateNativeModel()) throw new IllegalArgumentException(operationMessage);
            projectRepository.save(project);
            undoStack.push(snapshot);
            while (undoStack.size() > 50) undoStack.removeLast();
            redoStack.clear();
            return true;
        } catch (Exception failure) {
            project = previous;
            nativeMesh = previousMesh; nativePrism = previousPrism;faceBodies=previousFaceBodies;faceFeatures=previousFaceFeatures;holeCylinders=previousHoles; modelInfo = previousInfo;
            modelWidth = w; modelHeight = h; modelDepth = d;
            technicalSheet = previousSheet; updateMeshCenter();
            operationMessage = "Nie zatwierdzono zmiany: " + failure.getMessage();
            return false;
        } finally {
            mode = previousMode;
        }
    }

    private void undo() {
        closePropertyEditor();
        if(!project.polylineDraft.isEmpty()){removePolylinePoint();return;}
        if (undoStack.isEmpty()) return;
        try {
            SnapshotHistory.move(undoStack, redoStack, project.toJson().toString(), this::restoreSnapshot);
        } catch (Exception ignored) {
            operationMessage = "Nie udało się cofnąć operacji";
        }
    }

    private void redo() {
        closePropertyEditor();
        if (redoStack.isEmpty()) return;
        try {
            SnapshotHistory.move(redoStack, undoStack, project.toJson().toString(), this::restoreSnapshot);
        } catch (Exception ignored) {
            operationMessage = "Nie udało się ponowić operacji";
        }
    }

    private void restoreSnapshot(String snapshot) throws Exception {
        CadProject candidate = CadProject.fromJson(new org.json.JSONObject(snapshot));
        CadProject previous = project;
        double[] previousMesh = nativeMesh;
        java.util.Map<Integer,Integer> previousFaceBodies=faceBodies,previousFaceFeatures=faceFeatures;
        List<HoleThread.Cylinder> previousHoles=holeCylinders;
        double[] previousPrism = nativePrism;
        String previousInfo = modelInfo;
        String previousMode = mode;
        double previousWidth = modelWidth, previousHeight = modelHeight, previousDepth = modelDepth;
        try {
            project = candidate;
            modelWidth = candidate.width;
            modelHeight = candidate.height;
            modelDepth = candidate.depth;
            mode = "Wybierz";
            if (!updateNativeModel())
                throw new IllegalStateException("Nie można odtworzyć geometrii historii");
            projectRepository.save(candidate);
        } catch (Exception failure) {
            project = previous;
            modelWidth = previousWidth; modelHeight = previousHeight; modelDepth = previousDepth;
            nativeMesh = previousMesh; nativePrism = previousPrism;faceBodies=previousFaceBodies;faceFeatures=previousFaceFeatures;holeCylinders=previousHoles; modelInfo = previousInfo;
            updateMeshCenter();
            technicalSheet = null;
            throw failure;
        } finally {
            mode = previousMode;
        }
        coincidenceMode=false;coincidenceFirst=null;
        modelWidth = project.width;
        modelHeight = project.height;
        modelDepth = project.depth;
        sketchPlane = project.sketchPlane;
        sketchPlaneChosen = true;
        mode="Wybierz";arcDraft.clear();threePointPreview=null;resumeSketchWork();
        selectedFace = -1;
        operationMessage = "Gotowe: historia przywrócona";
        invalidate();
    }

    public void importDxf(java.io.InputStream input) throws IOException {
        if(SketchTransactions.active(project))throw new IOException("Zakończ albo anuluj edycję szkicu przed importem DXF");
        List<SketchPrimitive> entities = CadExchange.readDxf(input);
        if(!commitModelChange(() -> {
            preserveExtrudedBase(); commitActiveFeature();
            try { project.archiveCurrentSketch(); }
            catch(org.json.JSONException error) { throw new IllegalArgumentException("Nie można zachować szkicu",error); }
            project.sketchId = UUID.randomUUID().toString();project.sketchName="Szkic";
            project.sketch.clear(); project.sketch.addAll(entities);project.coincidences.clear();
            project.polylineDraft.clear();project.threePointDraft.clear();project.threePointTool="";
            project.sketchPlane = "XY"; project.setPrincipalSketchFrame("XY");
            project.featureProfileId = null; project.sketchExtruded = false;
            SketchTransactions.begin(project);
        }))throw new IOException(operationMessage);
        sketchPlane = "XY";
        enterSketchEditor();
        operationMessage = "Gotowe: wczytano DXF · " + entities.size() + " elementów";
        invalidate();
    }

    public void importStl(File staged) throws IOException {
        double[] mesh;
        try(FileInputStream input=new FileInputStream(staged)) { mesh=CadExchange.readStl(input); }
        String sourceName="mesh-"+UUID.randomUUID()+".stl";
        if(!CadMesh.isValid(mesh,false))throw new IOException("Niepoprawna siatka STL");
        File retained=new File(projectRepository.importedStepFile().getParentFile(),sourceName);
        java.nio.file.Files.copy(staged.toPath(),retained.toPath());
        if(!commitModelChange(() -> {resetImportedModel();project.importedMeshSource=sourceName;})) {
            retained.delete();throw new IOException(operationMessage);
        }
        finishModelImport("Gotowe: wczytano siatkę STL");
    }

    public void writeObj(OutputStream output) throws IOException {
        if(SketchTransactions.active(project)) throw new IOException("Zakończ albo anuluj edycję szkicu przed eksportem");
        ObjExporter.write(output,nativeMesh);
    }

    public void writeBinaryStl(OutputStream output) throws IOException {
        int count = nativeMesh.length / 13;
        byte[] header = new byte[80];
        byte[] title = "TabletCAD binary STL".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, header, 0, title.length);
        output.write(header);
        ByteBuffer countBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        countBuffer.putInt(count);
        output.write(countBuffer.array());
        ByteBuffer triangle = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset + 12 < nativeMesh.length; offset += 13) {
            triangle.clear();
            triangle.putFloat((float)nativeMesh[offset + 9]);
            triangle.putFloat((float)nativeMesh[offset + 10]);
            triangle.putFloat((float)nativeMesh[offset + 11]);
            for (int value = 0; value < 9; value++) triangle.putFloat((float)nativeMesh[offset + value]);
            triangle.putShort((short)0);
            output.write(triangle.array());
        }
    }

    public void writeAsciiStl(OutputStream output) throws IOException {
        Writer writer=new OutputStreamWriter(output,StandardCharsets.US_ASCII);
        writer.write("solid TabletCAD\n");
        for(int i=0;i+12<nativeMesh.length;i+=13) {
            writer.write(" facet normal "+nativeMesh[i+9]+" "+nativeMesh[i+10]+" "+nativeMesh[i+11]+"\n  outer loop\n");
            for(int j=0;j<9;j+=3) writer.write("   vertex "+nativeMesh[i+j]+" "+nativeMesh[i+j+1]+" "+nativeMesh[i+j+2]+"\n");
            writer.write("  endloop\n endfacet\n");
        }
        writer.write("endsolid TabletCAD\n"); writer.flush();
    }

    public void writeDxf(OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.US_ASCII);
        writer.write("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1015\n9\n$INSUNITS\n70\n4\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        for (SketchPrimitive primitive : project.sketch) {
            if(SketchSpline.is(primitive))SketchSpline.writeDxf(writer,primitive.vertices);
            else if(primitive.curves!=null)SketchCurves.writeDxf(writer,primitive.curves);
            else if(primitive.vertices!=null) {
                writer.write("0\nLWPOLYLINE\n8\nSKETCH\n100\nAcDbEntity\n100\nAcDbPolyline\n90\n"+primitive.vertices.length/2+"\n70\n1\n");
                for(int i=0;i<primitive.vertices.length;i+=2)
                    writer.write("10\n"+primitive.vertices[i]+"\n20\n"+primitive.vertices[i+1]+"\n");
            } else if (primitive.isEllipse()) {
                // DXF ELLIPSE: centre, major-axis endpoint relative to the centre, minor/major ratio, full parameter range.
                writer.write("0\nELLIPSE\n8\nSKETCH\n100\nAcDbEntity\n100\nAcDbEllipse\n10\n"+primitive.x1+"\n20\n"+primitive.y1+"\n30\n0\n11\n"+(primitive.x2-primitive.x1)+"\n21\n"+(primitive.y2-primitive.y1)
                    +"\n31\n0\n40\n"+(primitive.minor/primitive.major())+"\n41\n0\n42\n"+(2*Math.PI)+"\n");
            } else if (primitive.type.equals("Okrąg")) {
                double radius = Math.hypot(primitive.x2 - primitive.x1, primitive.y2 - primitive.y1);
                writer.write("0\nCIRCLE\n8\nSKETCH\n10\n" + primitive.x1 + "\n20\n" + primitive.y1
                    + "\n30\n0\n40\n" + radius + "\n");
            } else if (primitive.type.equals("Prostokąt")) {
                writer.write("0\nLWPOLYLINE\n8\nSKETCH\n100\nAcDbEntity\n100\nAcDbPolyline\n90\n4\n70\n1\n");
                double[][] points={{primitive.x1,primitive.y1},{primitive.x2,primitive.y1},
                    {primitive.x2,primitive.y2},{primitive.x1,primitive.y2}};
                for(double[] p:points) writer.write("10\n"+p[0]+"\n20\n"+p[1]+"\n");
            } else {
                writeDxfLine(writer, primitive.x1, primitive.y1, primitive.x2, primitive.y2);
            }
        }
        writer.write("0\nENDSEC\n0\nEOF\n");
        writer.flush();
    }

    private void writeDxfLine(Writer writer, double x1, double y1, double x2, double y2) throws IOException {
        writer.write("0\nLINE\n8\nSKETCH\n10\n" + x1 + "\n20\n" + y1 + "\n30\n0\n11\n"
            + x2 + "\n21\n" + y2 + "\n31\n0\n");
    }

    private static final class ProjectedTriangle {
        int meshIndex;
        final double[] q = new double[3];
        HiddenLineVisibility.Triangle surface;
        final double[] wx = new double[3];
        final double[] wy = new double[3];
        final double[] wz = new double[3];
        final float[] sx = new float[3];
        final float[] sy = new float[3];
        double nx, ny, nz;
        float depth;
        int faceId;
        final Path facePath=new Path();

        Path path() {
            Path path=facePath;path.rewind();
            path.moveTo(sx[0], sy[0]);
            path.lineTo(sx[1], sy[1]);
            path.lineTo(sx[2], sy[2]);
            path.close();
            return path;
        }

        boolean contains(float x, float y) {
            float d1 = sign(x, y, sx[0], sy[0], sx[1], sy[1]);
            float d2 = sign(x, y, sx[1], sy[1], sx[2], sy[2]);
            float d3 = sign(x, y, sx[2], sy[2], sx[0], sy[0]);
            boolean negative = d1 < 0f || d2 < 0f || d3 < 0f;
            boolean positive = d1 > 0f || d2 > 0f || d3 > 0f;
            return !(negative && positive);
        }

        private static float sign(float px, float py, float ax, float ay, float bx, float by) {
            return (px - bx) * (ay - by) - (ax - bx) * (py - by);
        }
    }

    private static final class MeasurePoint {
        final double x, y, z;
        float sx, sy;
        final String kind;
        final double edgeLength;

        MeasurePoint(double x, double y, double z, float sx, float sy, String kind, double edgeLength) {
            this.x = x; this.y = y; this.z = z;
            this.sx = sx; this.sy = sy;
            this.kind = kind;
            this.edgeLength = edgeLength;
        }

        double distanceTo(MeasurePoint other) {
            return Math.sqrt(square(other.x - x) + square(other.y - y) + square(other.z - z));
        }
    }

    private void drawAxis(Canvas canvas, float x, float y) {
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(220, 60, 60)); canvas.drawLine(x, y, x + dp(30), y, paint);
        paint.setColor(Color.rgb(50, 155, 75)); canvas.drawLine(x, y, x, y - dp(30), paint);
        paint.setColor(Color.rgb(55, 105, 210)); canvas.drawLine(x, y, x - dp(18), y + dp(18), paint);
        text(canvas, "X", x + dp(34), y + dp(5), 11, Color.rgb(220, 60, 60), true);
        text(canvas, "Y", x - dp(4), y - dp(36), 11, Color.rgb(50, 155, 75), true);
        text(canvas, "Z", x - dp(29), y + dp(27), 11, Color.rgb(55, 105, 210), true);
    }

    private void drawTimeline(Canvas canvas) {
        float top = getHeight() - dp(62) - bottomSystemInset;
        float left = leftWidth();
        float right = viewportRight();
        canvas.save();canvas.clipRect(left,top,right,getHeight());
        // Light strip under the dock keeps the system navigation area visually quiet.
        paint.setStyle(Paint.Style.FILL);paint.setColor(BG);
        canvas.drawRect(left, top, right, getHeight(), paint);
        paint.setColor(CHROME);
        canvas.drawRoundRect(new RectF(left + dp(8), top + dp(3), right - dp(8), top + dp(59)), dp(14), dp(14), paint);
        text(canvas, "↶", left + dp(30), top + dp(36), 22, undoStack.isEmpty() ? Color.rgb(84, 98, 110) : Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "↷", left + dp(76), top + dp(36), 22, redoStack.isEmpty() ? Color.rgb(84, 98, 110) : Color.WHITE, true, Paint.Align.CENTER);
        paint.setColor(CHROME_RAISED);canvas.drawRect(left + dp(104), top + dp(14), left + dp(105), top + dp(48), paint);
        List<CadTimeline.Item> features=CadTimeline.entries(project);
        float startX = left + dp(116);
        float gap = dp(64);
        timelineScroll=Math.max(0,Math.min(maxTimelineScroll(),timelineScroll));
        canvas.save();canvas.clipRect(startX-dp(8),top+dp(3),right-dp(10),top+dp(59));
        canvas.translate(-timelineScroll,0);
        paint.setStrokeWidth(dp(2));
        paint.setColor(CHROME_RAISED);
        if (features.size() > 1) {
            canvas.drawLine(startX + dp(19), top + dp(24),
                startX + (features.size() - 1) * gap + dp(19), top + dp(24), paint);
        }
        for (int i = 0; i < features.size(); i++) {
            float x = startX + i * gap;
            boolean selected = i == selectedTimelineIndex, last = i == features.size() - 1;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(225,120,10) : last ? ACCENT : CHROME_RAISED);
            canvas.drawRoundRect(new RectF(x + dp(2), top + dp(8), x + dp(36), top + dp(40)), dp(9), dp(9), paint);
            drawFeatureIcon(canvas, features.get(i).title, x + dp(19), top + dp(24), Color.WHITE);
            text(canvas, features.get(i).title, x + dp(19), top + dp(53), 8,
                selected || last ? Color.WHITE : CHROME_TEXT, selected, Paint.Align.CENTER);
        }
        canvas.restore();canvas.restore();
    }

    private boolean isToolActive(String tool) {
        if(tool.equals("Więzy"))return inSketch()&&coincidenceMode;
        if(tool.equals("Wymiar"))return mode.equals("Edytuj szkic")&&dimensionMode;
        if(tool.equals("Przytnij"))return mode.equals("Edytuj szkic")&&trimMode;
        if (SketchShapes.drawingTool(tool)) {
            return mode.equals("Szkic") && sketchTool.equals(tool);
        }
        if (tool.equals("Edytuj")) return mode.equals("Edytuj szkic")&&!trimMode&&!dimensionMode&&!coincidenceMode;
        if (tool.equals("Zmierz")) return mode.equals("Zmierz");
        if (tool.equals("Obróć widok")) return inSketch()&&sketchOrbit;
        if (tool.equals("Rysunek")) return mode.equals("Rysunek");
        if (tool.equals("Przekrój")) return sectionPlane!=null;
        if (tool.equals("Cieniowanie")) return displayMode==DISPLAY_SHADED;
        if (tool.equals("Krawędzie")) return displayMode==DISPLAY_EDGES;
        if (tool.equals("Przezroczystość")) return displayMode==DISPLAY_TRANSPARENT;
        if (tool.equals("Powłoka")) return mode.equals("Powłoka");
        if (tool.equals("Otwór")) return mode.equals("Otwór");
        if (tool.equals("Pochyl ściany")) return mode.equals("Pochyl ściany");
        return tool.equals("Wybierz") && mode.equals("Wybierz")
            || tool.equals("Szkic") && mode.equals("Szkic") && !sketchPlaneChosen;
    }

    private void activateTopTool(String tool) {
        if(tool.equals("Gwint")){beginThreadTool();return;}
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(tool.equals("Powłoka")){beginShellTool(-1);return;}
        if(tool.equals("Otwór")){beginHoleTool();return;}
        if(tool.equals("Pochyl ściany")){beginDraftTool(-1);return;}
        if(tool.equals("Przejście")){if(prepareProfileTool("Przejście"))showLoftDialog();return;}
        if(tool.equals("Przeciągnij")){if(prepareProfileTool("Przeciągnięcie"))showSweepDialog();return;}
        if(tool.equals("Lustro bryły")||tool.equals("Szyk liniowy bryły")||tool.equals("Szyk kołowy bryły")){if(prepareSolidTool(tool))showBodyPatternDialog(tool.startsWith("Lustro")?1:tool.startsWith("Szyk liniowy")?2:3,-1);return;}
        if(tool.equals("Podziel bryłę")){if(prepareSolidTool(tool))showBodySplit(-1);return;}
        if(tool.equals("Przesuń bryłę")||tool.equals("Obróć bryłę")||tool.equals("Skaluj bryłę")){if(prepareSolidTool(tool))showBodyTransform(tool.startsWith("Przesuń")?BodyTransform.MOVE:tool.startsWith("Obróć")?BodyTransform.ROTATE:BodyTransform.SCALE,-1);return;}
        if(tool.equals("Połącz bryły")||tool.equals("Odejmij bryły")||tool.equals("Część wspólna brył")){beginBodyCombine(tool.equals("Połącz bryły")?BodyCombine.JOIN:tool.equals("Odejmij bryły")?BodyCombine.CUT:BodyCombine.INTERSECT,-1);return;}
        if(tool.equals("Lustro operacji")||tool.equals("Szyk liniowy operacji")||tool.equals("Szyk kołowy operacji")){beginPatternTool(tool);return;}
        if(!project.threePointDraft.isEmpty()&&!tool.equals(project.threePointTool)&&!tool.equals("Anuluj szkic")){showThreePointActions(tool);return;}
        if(!arcDraft.isEmpty()&&!tool.equals("Łuk")){showArcDraftActions(tool);return;}
        if(!project.polylineDraft.isEmpty()) {showPolylineActions(tool);return;}
        if(inSketch()&&!SketchTransactions.active(project))beginSketchWork();
        boolean hadSketchPlane=inSketch() && sketchPlaneChosen;
        closePropertyEditor();
        trimMode=false;cornerFilletPicking=false;cornerFirst=null;
        dimensionMode=false;
        coincidenceMode=false;coincidenceFirst=null;
        if(tool.equals("Przesuń szkic")||tool.equals("Obróć szkic")||tool.equals("Skaluj szkic")){mode="Edytuj szkic";showSketchTransform(tool);invalidate();return;}
        if(tool.equals("Powiązania profilu")){showProfileDependencies();return;}
        if(tool.equals("Edytuj profil źródłowy")){editSourceProfile(selectedSourceProfile());return;}
        if(tool.equals("Wymiary")) {showSketchDimensions=!showSketchDimensions;invalidate();return;}
        if(tool.equals("Więzy")) {
            mode="Edytuj szkic";showConstraintMenu();
            invalidate();return;
        }
        if(tool.equals("Geometria")) {
            mode="Edytuj szkic";showSketchGeometryTools();invalidate();return;
        }
        if(tool.equals("Zaokrąglij szkic")){showSketchFillet();return;}
        if(tool.equals("Lustro")){mode="Edytuj szkic";showSketchMirror();invalidate();return;}
        if(tool.equals("Odsunięcie")){mode="Edytuj szkic";showSketchOffset();invalidate();return;}
        if(tool.equals("Przedłuż")){mode="Edytuj szkic";extendSelectedLine();invalidate();return;}
        if(tool.equals("Szyk liniowy")||tool.equals("Szyk kołowy")){mode="Edytuj szkic";showSketchPattern(tool.equals("Szyk kołowy"));invalidate();return;}
        if(tool.equals("Wymiar")) {
            if(!hadSketchPlane)operationMessage="Najpierw wybierz płaszczyznę szkicu";
            else {mode="Edytuj szkic";dimensionMode=true;operationMessage="Dotknij elementu, aby nadać mu wymiar sterujący";}
            invalidate();return;
        }
        if(tool.equals("Przytnij")) {
            if(!hadSketchPlane) operationMessage="Najpierw wybierz płaszczyznę szkicu";
            else {
                mode="Edytuj szkic";trimMode=true;selectedSketchId=null;
                operationMessage="Dotknij fragmentu linii, łuku lub okręgu między przecięciami. Pozostałe krzywe i linie pomocnicze wyznaczają granice cięcia.";
            }
            invalidate();return;
        }
        if(tool.equals("Połącz kontur")) {
            try {
                SketchContours.Result joined=SketchContours.join(project.sketch,selectedSketchId);
                if(joined.profile.curves!=null)for(SketchConstraints.Link link:project.coincidences)
                    if(joined.consumed.contains(link.a)||joined.consumed.contains(link.b))throw new IllegalArgumentException("Usuń więzy łączonych linii przed utworzeniem konturu krzywego");
                List<SketchConstraints.Link> remapped=SketchConstraints.rebindContour(project.sketch,project.coincidences,joined);
                checkpoint();SketchTransactions.join(project,joined);preserveExtrudedBase();commitActiveFeature();
                project.sketch.removeIf(p->joined.consumed.contains(p.id));project.sketch.add(joined.profile);
                project.coincidences.clear();project.coincidences.addAll(remapped);
                project.featureProfileId=null;project.sketchExtruded=false;
                selectedSketchId=joined.profile.id;mode="Edytuj szkic";
                projectRepository.save(project);operationMessage="Gotowe: zamknięty kontur. Możesz edytować wierzchołki lub zakończyć szkic i wyciągnąć.";
            } catch(IllegalArgumentException e){operationMessage=e.getMessage();}
            invalidate();return;
        }
        if(tool.equals("Usuń cechę")) {
            boolean active=!project.featureOperation.equals("none");
            if(!active && project.committedFeatures.isEmpty() && project.filletRadius<=0) {
                operationMessage="Brak dodanych cech. Bryła bazowa pozostaje podstawą dokumentu.";invalidate();return;
            }
            new PropertyBuilder().setTitle("Usuń ostatnią cechę modelu")
                .setItems(new String[]{"Usuń ostatnie dodanie / wycięcie / zaokrąglenie"},(dialog,which)-> {
                    if (commitModelChange(() -> {
                        if(project.filletRadius>0) project.filletRadius=0;
                        else {
                            if(!active) {
                                int index=project.committedFeatures.size()-1;
                                BodyGraph.removeRoute(project,index);project.committedFeatures.remove(index);UserParameters.removed(project,index);
                                if(index<project.committedProfileIds.size()) project.committedProfileIds.remove(index);
                            }
                            project.featureOperation="none";project.featureProfileId=null;project.sketchExtruded=false;
                        }
                    })) {
                        operationMessage="Gotowe: usunięto ostatnią cechę; szkic zachowano. Możesz cofnąć zmianę.";
                        selectedSketchId=null;
                    }
                    invalidate();
                }).setNegativeButton("Anuluj",null).show();return;
        }
        if(tool.equals("Wytnij")) {
            List<SketchPrimitive> profiles=new ArrayList<>();List<String> labels=new ArrayList<>();
            for(SketchPrimitive p:project.sketch) if(p.closedProfile()) {
                profiles.add(p);labels.add("Profil "+profiles.size()+" · "+p.type);
            }
            if(profiles.isEmpty()) operationMessage="Najpierw utwórz zamknięty profil na ścianie bryły";
            else if(profiles.size()==1) showFaceFeatureDepth(profiles.get(0),"cut");
            else new PropertyBuilder().setTitle("Profil wycięcia").setItems(labels.toArray(new String[0]),
                (dialog,which)->{selectedSketchId=profiles.get(which).id;showFaceFeatureDepth(profiles.get(which),"cut");})
                .setNegativeButton("Anuluj",null).show();
            invalidate();return;
        }
        if(tool.equals("Obrót profilu")){chooseRevolveProfile();return;}
        if(tool.equals("Właściwości bryły")){showBodyProperties();return;}
        if (tool.equals("Zakończ")) {
            finishSketchWork();return;
        }
        if(tool.equals("Anuluj szkic")){cancelSketchWork();return;}
        if(tool.equals("Izometryczny")||tool.equals("Przód")||tool.equals("Góra")||tool.equals("Prawy bok")) {
            applyStandardView(tool);invalidate();return;
        }
        if(tool.equals("Przekrój")){showSectionDialog();return;}
        if(tool.equals("Cieniowanie")||tool.equals("Krawędzie")||tool.equals("Przezroczystość")) {
            displayMode=tool.equals("Cieniowanie")?DISPLAY_SHADED:tool.equals("Krawędzie")?DISPLAY_EDGES:DISPLAY_TRANSPARENT;
            operationMessage=tool.equals("Cieniowanie")?"Widok cieniowany":tool.equals("Krawędzie")?"Tryb krawędzi: ukryte krawędzie przerywane":"Tryb przezroczysty: widoczne ukryte krawędzie i ściany tylne";
            invalidate();return;
        }
        if(tool.equals("Rzut prostokątny")) {
            orthographicView=!orthographicView;fitView();
            operationMessage=orthographicView?"Rzut prostokątny (bez perspektywy)":"Rzut perspektywiczny";invalidate();return;
        }
        if(tool.equals("Dopasuj") || tool.equals("Wyczyść widok")) {
            fitView();selectedFace=-1;selectedSketchId=null;measureA=measureB=null;
            measuredCurve=null;
            measuredEdgeText="";operationMessage="";
            if(tool.equals("Wyczyść widok")) {if(!SketchTransactions.active(project))mode="Wybierz";showSolid=true;}
            invalidate();return;
        }
        if(tool.equals("Ukryj bryłę")) {showSolid=!showSolid;invalidate();return;}
        if(tool.equals("Obróć widok")) {
            sketchOrbit=!sketchOrbit;
            operationMessage=sketchOrbit?"Obracanie widoku: przeciągnij jednym palcem. Rysowanie wstrzymane — wyłącz Obróć widok lub wybierz Widok na szkic.":"Rysowanie wznowione";invalidate();return;
        }
        if(tool.equals("Widok na szkic")) {sketchOrbit=false;alignToCurrentSketchPlane();fitView();operationMessage="Widok prostopadły do szkicu";invalidate();return;}
        if(tool.equals("Usuń")) {
            if(selectedSketchId!=null) deleteSketchEntity(selectedSketchId);
            else operationMessage="Zaznacz element narzędziem Edytuj, potem wybierz Usuń";
            invalidate();return;
        }
        if(tool.equals("Wyczyść szkic")) {
            new PropertyBuilder().setTitle("Usuń szkic / jego zawartość")
                .setItems(new String[]{"Usuń nieużywane elementy", "Usuń bieżący szkic"},(dialog,which)-> {
                    boolean used=false;
                    for(SketchPrimitive p:project.sketch) if(isProfileUsed(p.id)) used=true;
                    if(which==1 && used&&!SketchTransactions.active(project)) {operationMessage="Szkic jest podstawą bryły — najpierw usuń zależne operacje";invalidate();return;}
                    checkpoint();project.sketch.removeIf(p->which==1||!isProfileUsed(p.id));
                    SketchTransactions.prune(project);
                    project.pruneCoincidences();
                    if(which==1&&!SketchTransactions.active(project)) {
                        String id=project.sketchId;project.savedSketches.removeIf(s->id.equals(s.optString("id")));
                        project.sketchId=UUID.randomUUID().toString();mode="Wybierz";
                    }
                    selectedSketchId=null;projectRepository.save(project);
                    operationMessage=used ? "Usunięto nieużywane elementy; profile bryły zachowano" : "Usunięto geometrię szkicu";
                    invalidate();
                }).setNegativeButton("Anuluj",null).show();return;
        }
        if (!tool.equals("Wyciągnij")) selectedSketchId = null;
        if (tool.equals("Wybierz")) mode = "Wybierz";
        else if (tool.equals("Szkic")) {
            sketchOrbit = false;
            mode = "Szkic";
            sketchPlaneChosen = false;
            sketchPlane = "Wybierz płaszczyznę";
            operationMessage = "Wybierz płaszczyznę lub ścianę modelu";
            // Respect the face selected before invoking Sketch. Previously the
            // app silently kept the old sketch frame until the user tapped again.
            if (selectedFace >= 0) {
                if (isFacePlanar(selectedFace)) {
                    chooseSketchPlane("Ściana " + (selectedFace + 1));
                } else {
                    operationMessage = "Wybrana ściana nie jest płaska. Wskaż inną.";
                }
            }
        } else if (SketchShapes.drawingTool(tool)) {
            sketchOrbit = false;
            mode = "Szkic";
            sketchTool = tool;
            project.splineDraft=tool.equals("Splajn");
            sketchPlaneChosen = hadSketchPlane;
            if (sketchPlaneChosen) alignToCurrentSketchPlane();
            post(this::showDrawingOptions);
            if(tool.equals("Łuk"))operationMessage="Łuk: dotknij początku, końca, a następnie punktu pośredniego łuku.";
            if(tool.equals("Okrąg 3 pkt")||tool.equals("Szczelina")||tool.equals("Elipsa"))operationMessage=SketchShapes.instruction(tool,project.threePointDraft.size());
        } else if (tool.equals("Edytuj")) {
            if(inSketch()){mode="Edytuj szkic";sketchPlaneChosen=true;alignToCurrentSketchPlane();invalidate();}
            else showSavedSketchPicker();
        } else if (tool.equals("Wyciągnij")) {
            // Zatwierdz szkic automatycznie, bez ukrywania profilu przed operacja.
            if (inSketch() && SketchTransactions.active(project)) {
                finishSketchWork();
                if (inSketch()) return; // Blad walidacji lub nierozwiazane powiazania.
            } else if (inSketch()) {
                mode = "Wybierz";
            }
            extrudeSketchProfile();
        } else if (tool.equals("Głębokość")) showDepthEditor();
        else if (tool.equals("Zaokrąglij")) showFilletEditor();
        else if (tool.equals("Fazuj")) showEdgeTreatmentEditor(-1,true);
        else if (tool.equals("Zmierz")) {
            mode = "Zmierz";
            measurePointMode=project.importedMeshSource!=null;
            loadMeasurementGeometry();showMeasurementPanel();
        } else if (tool.equals("Rysunek")) {
            if(mode.equals("Rysunek")) {
                EditText value=coordinateInput("Położenie przekroju X [%]",project.drawingSectionFraction*100);
                new PropertyBuilder().setTitle("Przekrój A–A · położenie X [%]")
                    .setView(value).setNegativeButton("Anuluj",null)
                    .setNeutralButton("Tabliczka",(dialog,which)->showDrawingMetadataEditor())
                    .setPositiveButton("Zastosuj",(dialog,which)-> {
                        try {
                            double fraction=parseCoordinate(value)/100;
                            if(!Double.isFinite(fraction)||fraction<=0||fraction>=1) {
                                operationMessage="Podaj położenie pomiędzy 0 a 100%";
                            } else {
                                checkpoint(); project.drawingSectionFraction=fraction;
                                projectRepository.save(project); prepareTechnicalSheet();
                            }
                        } catch(NumberFormatException e) {operationMessage="Niepoprawne położenie przekroju";}
                        invalidate();
                    }).show();
            } else if(prepareTechnicalSheet()) mode = "Rysunek";
        }
        invalidate();
    }


    private String currentSketchName() {return project.sketchName;}

    private void showRenameSketch() {
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setText(currentSketchName());
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
        new android.app.AlertDialog.Builder(getContext()).setTitle("Nazwa szkicu").setView(name)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
                String value=name.getText().toString().trim();if(value.isEmpty())return;
                try {checkpoint();project.sketchName=value;project.archiveCurrentSketch();
                    for(org.json.JSONObject snapshot:project.savedSketches)if(project.sketchId.equals(snapshot.optString("id")))snapshot.put("name",value);
                    projectRepository.save(project);invalidate();
                }catch(org.json.JSONException|RuntimeException error){operationMessage="Nie można zapisać nazwy szkicu";}
            }).show();
    }

    private android.app.AlertDialog libraryProgress;
    private boolean libraryFileOperationPending;

    public void showStartupProjects() {
        if(projectChooser!=null&&projectChooser.isShowing())return;
        openProjectBrowser(true,null);
    }

    private void openProjectBrowser(boolean startup,CadProject snapshot) {
        closePropertyEditor();
        if(projectChooser!=null){projectChooser.dismiss();projectChooser=null;}
        ProjectLibraryBrowser browser=new ProjectLibraryBrowser(getContext(),projectRepository,project.id,new ProjectLibraryBrowser.Actions(){
            public void open(CadProject next){if(!activateProject(next))post(CadWorkspaceView.this::showStartupProjects);}
            public void create(){showNewProject();}
            public void trash(){showProjectTrash();}
            public void delete(CadProject target){confirmDeleteProject(target);}
        });
        projectChooser=browser.show(startup,snapshot);
    }

    private void confirmDeleteProject(CadProject target) {
        if(target.id.equals(project.id)){android.widget.Toast.makeText(getContext(),"Nie można usunąć otwartego projektu — najpierw otwórz inny",android.widget.Toast.LENGTH_LONG).show();return;}
        new android.app.AlertDialog.Builder(getContext()).setTitle("Usunąć projekt „"+target.name+"”?")
            .setMessage("Projekt zniknie z listy. Pliki zostaną przeniesione do kosza aplikacji, skąd można je przywrócić.")
            .setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->{
                if(projectChooser!=null){projectChooser.dismiss();projectChooser=null;}
                final String activeId=project.id;
                runProjectLibraryTask("Przenoszenie projektu do kosza",()->{projectRepository.deleteProject(target.id,activeId);return target;},"Przeniesiono do kosza: ");
            }).show();
    }

    private void showProjectMenu() {
        closePropertyEditor();
        new PropertyBuilder().setTitle("Projekt · "+project.name+" · TabletCAD v"+appVersion())
            .setItems(new String[]{"Nowy pusty projekt","Moje projekty","Utwórz kopię projektu","Kosz — przywróć projekt","Dane i nazwa dokumentu","Jednostki i dokładność","Parametry"},(d,which)-> {
                if(which==0)showNewProject();else if(which==1)showProjectLibrary();else if(which==2)showProjectCopy();else if(which==3)showProjectTrash();else if(which==4)showDrawingMetadataEditor();else if(which==5)showUnitSettings();else showParameters();
            }).setNegativeButton("Zamknij",null).show();
    }

    /** User parameters: list with current values, add/edit/delete; bound values are recomputed on change. */
    private void showParameters() {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(SketchTransactions.active(project)){operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";invalidate();return;}
        java.util.Map<String,double[]> values;
        try{values=UserParameters.evaluate(project);}catch(IllegalArgumentException error){values=new java.util.HashMap<>();operationMessage=error.getMessage();}
        List<String> rows=new ArrayList<>();
        for(UserParameters.Parameter x:project.parameters){double[] v=values.get(x.name);rows.add(x.name+" = "+x.expression+(v==null?"":"  →  "+(x.length?formatMm(v[0]):String.format(java.util.Locale.US,"%.6g",v[0]))));}
        rows.add("+ Dodaj parametr");
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText("Wpisz nazwę parametru w polu głębokości lub wymiaru (np. grubosc/2), aby nim sterować. Powiązań: "+project.parameterBindings.size()+".");form.addView(info);
        new PropertyBuilder().setTitle("Parametry").setView(form).setItems(rows.toArray(new String[0]),(d,which)->showParameterEditor(which<project.parameters.size()?which:-1)).setNegativeButton("Zamknij",null).show();
    }
    private void showParameterEditor(int index) {
        UserParameters.Parameter original=index>=0?project.parameters.get(index):null;
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView l1=new android.widget.TextView(getContext());l1.setText("Nazwa");form.addView(l1);
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setText(original!=null?original.name:"p"+(project.parameters.size()+1));form.addView(name);
        android.widget.TextView l2=new android.widget.TextView(getContext());l2.setText("Wyrażenie (liczby, jednostki, + − * / ( ), inne parametry)");form.addView(l2);
        EditText expression=new EditText(getContext());expression.setSingleLine(true);expression.setText(original!=null?original.expression:"10");form.addView(expression);
        android.widget.CheckBox length=new android.widget.CheckBox(getContext());length.setText("Długość (w jednostce projektu: "+project.units+")");length.setChecked(original==null||original.length);form.addView(length);
        PropertyBuilder builder=new PropertyBuilder().setTitle(original!=null?"Parametr "+original.name:"Nowy parametr").setView(form).setNegativeButton("Anuluj",null);
        if(original!=null)builder.setNeutralButton("Usuń",(d,w)->changeParameters(next->{
            for(String expr:next.parameterBindings.values())if(UserParameters.names(expr).contains(original.name))throw new IllegalArgumentException("Parametr jest używany w powiązaniach – najpierw je zmień");
            next.parameters.remove(index);
        },"Usunięto parametr "+original.name));
        builder.setPositiveButton("Zapisz",(d,w)->changeParameters(next->{
            String n=name.getText().toString().trim();UserParameters.validateName(n);
            UserParameters.Parameter value=new UserParameters.Parameter(n,expression.getText().toString().trim(),length.isChecked());
            if(index>=0){
                String old=next.parameters.get(index).name;next.parameters.set(index,value);
                if(!old.equals(n))for(java.util.Map.Entry<String,String> e:next.parameterBindings.entrySet())if(UserParameters.names(e.getValue()).contains(old))throw new IllegalArgumentException("Zmiana nazwy używanego parametru – najpierw zmień powiązania");
            }else next.parameters.add(value);
        },"Zapisano parametr")).show();
    }
    private interface DocumentChange{void apply(CadProject next);}
    private void changeParameters(DocumentChange change,String success) {
        try {
            String baseline=project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));
            change.apply(next);UserParameters.evaluate(next);UserParameters.apply(next);
            commitProjectAsync(next,baseline,success);
        }catch(org.json.JSONException|IllegalArgumentException error){operationMessage="Parametry: "+error.getMessage();invalidate();}
    }

    /** Project units and display precision; the B-Rep and all stored values stay in millimetres. */
    private void showUnitSettings() {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(SketchTransactions.active(project)){operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";invalidate();return;}
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(getContext());
        info.setText("Geometria jest zapisywana dokładnie w milimetrach. Jednostka zmienia wyświetlanie i interpretację wpisywanych liczb bez jednostki. W polach można wpisywać wyrażenia, np. 100/3, 2*(10+2,5), 1in+2mm.");form.addView(info);
        android.widget.Spinner unit=new android.widget.Spinner(getContext());unit.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,Units.NAMES));
        for(int i=0;i<Units.CODES.length;i++)if(Units.CODES[i].equals(project.units))unit.setSelection(i);form.addView(unit);
        android.widget.TextView decimalsLabel=new android.widget.TextView(getContext());decimalsLabel.setText("Miejsca po przecinku na rysunku (0–6)");form.addView(decimalsLabel);
        android.widget.Spinner decimals=new android.widget.Spinner(getContext());decimals.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"0","1","2","3","4","5","6"}));
        decimals.setSelection(project.displayDecimals);form.addView(decimals);
        new PropertyBuilder().setTitle("Jednostki i dokładność").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
            try {
                String code=Units.CODES[unit.getSelectedItemPosition()];int places=decimals.getSelectedItemPosition();
                if(code.equals(project.units)&&places==project.displayDecimals)return;
                String baseline=project.toJson().toString();CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));
                candidate.units=code;candidate.displayDecimals=places;projectRepository.save(candidate);
                project=candidate;undoStack.push(baseline);while(undoStack.size()>50)undoStack.removeLast();redoStack.clear();
                operationMessage="Jednostki projektu: "+code+", dokładność "+places+" miejsc. Geometria bez zmian.";
            }catch(Exception error){operationMessage="Nie zapisano ustawień jednostek: "+error.getMessage();}
            invalidate();
        }).show();
    }

    private void showProjectCopy() {
        if(projectRepository.readOnly()){operationMessage=projectRepository.loadNotice();invalidate();return;}
        if(SketchTransactions.active(project)){operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";invalidate();return;}
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setText(project.name+" — kopia");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new android.app.AlertDialog.Builder(getContext()).setTitle("Kopia projektu").setView(name).setNegativeButton("Anuluj",null).setPositiveButton("Utwórz kopię",(d,w)->{
            try{CadProject snapshot=CadProject.fromJson(project.toJson());String title=name.getText().toString();runProjectLibraryTask("Tworzenie kopii projektu",()->{projectRepository.save(snapshot);return projectRepository.duplicateProject(snapshot.id,title);},"Utworzono kopię: ");}
            catch(org.json.JSONException|RuntimeException error){operationMessage="Nie utworzono kopii: "+error.getMessage();invalidate();}
        }).show();
    }

    private void showProjectTrash() {
        closePropertyEditor();if(projectChooser!=null){projectChooser.dismiss();projectChooser=null;}
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding((int)dp(16),0,(int)dp(16),0);
        android.widget.TextView status=new android.widget.TextView(getContext());body.addView(status);
        android.widget.ProgressBar progress=new android.widget.ProgressBar(getContext());body.addView(progress);
        android.widget.Button retry=new android.widget.Button(getContext());retry.setText("Odśwież kosz");body.addView(retry);
        android.widget.ListView list=new android.widget.ListView(getContext());body.addView(list,new LinearLayout.LayoutParams(-1,(int)dp(240)));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(getContext()).setTitle("Kosz")
            .setView(body).setNegativeButton("Wróć do projektów",(d,w)->{d.dismiss();showStartupProjects();})
            .setOnCancelListener(d->post(this::showStartupProjects)).create();
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        LatestTaskRunner reader=new LatestTaskRunner(command->ui.post(command));
        dialog.setOnDismissListener(d->{reader.close();if(projectChooser==dialog)projectChooser=null;});
        Runnable refresh=()->{
            retry.setEnabled(false);list.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setText("Odczytywanie kosza…");
            reader.submit(()->{
                List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries=projectRepository.listDeletedProjects();
                java.text.DateFormat date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT);
                String[] names=new String[entries.size()];
                for(int i=0;i<names.length;i++){pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry e=entries.get(i);names[i]=e.name+"\n"+date.format(new java.util.Date(e.deletedAt))+(e.backup?" · kopia odzyskiwania":"")+(projectRepository.hasProjectFiles(e.id)?" · już w bibliotece":"");}
                return new TrashListing(entries,names);
            },result->{
                if(!dialog.isShowing())return;
                progress.setVisibility(View.GONE);retry.setEnabled(true);list.setEnabled(true);
                status.setText(result.entries.isEmpty()?"Brak projektów możliwych do przywrócenia. Nieczytelne zapisy pozostają zachowane w koszu.":"Projektów do przywrócenia: "+result.entries.size());
                list.setAdapter(new android.widget.ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,result.labels));
                list.setOnItemClickListener((parent,view,index,id)->{dialog.dismiss();confirmRestoreProject(result.entries.get(index));});
            },error->{if(!dialog.isShowing())return;progress.setVisibility(View.GONE);retry.setEnabled(true);status.setText("Nie można odczytać kosza: "+error.getMessage()+". Użyj Odśwież kosz, aby ponowić.");});
        };
        retry.setOnClickListener(v->refresh.run());projectChooser=dialog;dialog.show();refresh.run();
    }
    private static final class TrashListing {
        final List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries;final String[] labels;
        TrashListing(List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries,String[] labels){this.entries=entries;this.labels=labels;}
    }
    private void confirmRestoreProject(pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry entry) {
        boolean conflict=projectRepository.hasProjectFiles(entry.id);
        android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(getContext()).setTitle(entry.name)
            .setMessage(conflict?"Ten projekt jest już w bibliotece. Możesz odzyskać zapis z kosza jako osobną kopię.":"Przywrócić projekt do biblioteki?")
            .setNegativeButton("Wróć",(d,w)->showProjectTrash()).setOnCancelListener(d->showProjectTrash());
        if(!conflict)dialog.setPositiveButton("Przywróć",(d,w)->restoreProjectEntry(entry,false,null));
        dialog.setNeutralButton("Przywróć jako kopię",(d,w)->{
            EditText name=new EditText(getContext());name.setSingleLine(true);name.setText(entry.name+" — odzyskany");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
            new android.app.AlertDialog.Builder(getContext()).setTitle("Nazwa odzyskanego projektu").setView(name).setNegativeButton("Wróć",(a,b)->showProjectTrash()).setOnCancelListener(a->showProjectTrash())
                .setPositiveButton("Przywróć kopię",(a,b)->restoreProjectEntry(entry,true,name.getText().toString())).show();
        }).show();
    }
    private void restoreProjectEntry(pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry entry,boolean asCopy,String name) {
        runProjectLibraryTask("Przywracanie projektu",()->projectRepository.restoreProject(entry.token,asCopy,name),"Przywrócono projekt: ");
    }
    private void runProjectLibraryTask(String title,java.util.concurrent.Callable<CadProject> action,String success) {
        if(libraryFileOperationPending)return;libraryFileOperationPending=true;
        android.app.AlertDialog progress=new android.app.AlertDialog.Builder(getContext()).setTitle(title).setMessage("Proszę czekać…").setView(new android.widget.ProgressBar(getContext())).setCancelable(false).show();libraryProgress=progress;
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(()->{
            String result;try{result=success+action.call().name;}catch(Exception error){result="Operacja nie powiodła się: "+error.getMessage();}final String message=result;
            ui.post(()->{libraryFileOperationPending=false;if(libraryProgress==progress){progress.dismiss();libraryProgress=null;}if(!isAttachedToWindow())return;
                operationMessage=message;android.widget.Toast.makeText(getContext(),message,android.widget.Toast.LENGTH_LONG).show();showStartupProjects();invalidate();});
        },"TabletCAD-library-files").start();
    }

    private void showNewProject() {
        createNewProject("Nowy projekt");
    }

    private void createNewProject(String title) {
        try {
            projectRepository.save(project);
            CadProject next=CadProject.blank();next.name=title;if(!activateProject(next))post(this::showStartupProjects);
        }catch(RuntimeException error){operationMessage="Nie można utworzyć projektu; poprzedni dokument zachowano";post(this::showStartupProjects);invalidate();}
    }

    private void showProjectLibrary() {
        try {openProjectBrowser(false,CadProject.fromJson(project.toJson()));}
        catch(org.json.JSONException|RuntimeException error){operationMessage="Nie można przygotować biblioteki; bieżący dokument zachowano";invalidate();}
    }

    private boolean activateProject(CadProject next) {
        CadProject previous=project;String previousMode=mode;
        double[] previousMesh=nativeMesh,previousPrism=nativePrism;
        java.util.Map<Integer,Integer> previousFaceBodies=faceBodies,previousFaceFeatures=faceFeatures;
        List<HoleThread.Cylinder> previousHoles=holeCylinders;
        String previousInfo=modelInfo;TechnicalSheet previousSheet=technicalSheet;
        double w=modelWidth,h=modelHeight,d=modelDepth;
        try {
            project=next;mode="Wybierz";
            modelWidth=next.width;modelHeight=next.height;modelDepth=next.depth;
            if(!updateNativeModel())throw new IllegalStateException("Nie można odtworzyć geometrii lub źródła projektu");
            projectRepository.save(next);
        }catch(RuntimeException error) {
            project=previous;mode=previousMode;modelWidth=w;modelHeight=h;modelDepth=d;
            nativeMesh=previousMesh;nativePrism=previousPrism;faceBodies=previousFaceBodies;faceFeatures=previousFaceFeatures;holeCylinders=previousHoles;modelInfo=previousInfo;technicalSheet=previousSheet;updateMeshCenter();
            operationMessage="Nie można otworzyć projektu. Bieżący dokument pozostaje aktywny.";invalidate();return false;
        }
        closePropertyEditor();undoStack.clear();redoStack.clear();highlightSketch=false;selectedTimelineIndex=-1;sketchVisible=true;
        sketchPlane=next.sketchPlane;sketchPlaneChosen=false;
        sketching=false;sketchRect=null;draggedSketch=dragPreview=null;dragSolvedPreview=null;
        selectedFace=-1;selectedSketchId=null;measureA=measureB=null;measuredEdgeText="";
        coincidenceMode=trimMode=dimensionMode=false;coincidenceFirst=null;snapMarker=null;
        polylineTouch=false;polylinePreview=null;
        cameraYaw=-28;cameraPitch=22;zoom=1;panX=panY=0;showSolid=true;treeSelection="Model";
        operationMessage="Otwarto projekt: "+next.name;
        arcDraft.clear();threePointPreview=null;resumeSketchWork();
        invalidate();return true;
    }

    private void showDrawingMetadataEditor() {
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)dp(16),0,(int)dp(16),0);
        String[] labels={"Nazwa dokumentu", "Numer rysunku", "Autor", "Materiał", "Rewizja"};
        String[] values={project.name,project.drawingNumber,project.drawingAuthor,project.drawingMaterial,project.drawingRevision};
        int[] limits={48,32,32,32,8};EditText[] inputs=new EditText[5];
        for(int i=0;i<inputs.length;i++) {
            android.widget.TextView label=new android.widget.TextView(getContext());
            label.setText(labels[i]);form.addView(label);
            EditText input=new EditText(getContext());input.setSingleLine(true);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limits[i])});
            input.setText(values[i]);input.setSelectAllOnFocus(true);form.addView(input);inputs[i]=input;
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(getContext());scroll.addView(form);
        new PropertyBuilder().setTitle("Dane tabliczki rysunkowej")
            .setView(scroll).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",(dialog,which)-> {
                String name=inputs[0].getText().toString().trim();
                if(name.isEmpty()) {operationMessage="Nazwa dokumentu nie może być pusta";invalidate();return;}
                checkpoint();project.name=name;
                project.drawingNumber=inputs[1].getText().toString().trim();
                project.drawingAuthor=inputs[2].getText().toString().trim();
                project.drawingMaterial=inputs[3].getText().toString().trim();
                project.drawingRevision=inputs[4].getText().toString().trim();
                projectRepository.save(project);operationMessage="Gotowe: zapisano dane tabliczki";invalidate();
            }).show();
    }

    private void beginSketchWork() {
        if(SketchTransactions.active(project))return;
        preserveExtrudedBase();commitActiveFeature();
        SketchTransactions.begin(project);projectRepository.save(project);
    }

    private void resumeSketchWork() {
        if(!SketchTransactions.active(project)&&project.polylineDraft.isEmpty()&&project.threePointDraft.isEmpty())return;
        beginSketchWork();mode=project.polylineDraft.isEmpty()?"Edytuj szkic":"Szkic";
        if(!project.polylineDraft.isEmpty())sketchTool=project.splineDraft?"Splajn":"Polilinia";
        if(!project.threePointDraft.isEmpty()){mode="Szkic";sketchTool=project.threePointTool;threePointPreview=null;}
        sketchPlane=project.sketchPlane;sketchPlaneChosen=true;
        alignToCurrentSketchPlane();isolateCoveredSketch();
        operationMessage="Przywrócono roboczą edycję szkicu. Zakończ zatwierdza, Anuluj przywraca poprzedni stan.";
    }

    private void showSketchSessionActions() {
        new PropertyBuilder().setTitle("Trwa edycja szkicu")
            .setItems(new String[]{"Kontynuuj edycję", "Zakończ i przebuduj bryłę", "Anuluj zmiany szkicu"},(dialog,which)-> {
                if(which==1)finishSketchWork();else if(which==2)cancelSketchWork();
            }).setNegativeButton("Wróć",null).show();
    }

    private void finishSketchWork() {
        if(!project.threePointDraft.isEmpty()){showThreePointActions(null);return;}
        if(!arcDraft.isEmpty()){showArcDraftActions(null);return;}
        if(!project.polylineDraft.isEmpty()){showPolylineActions(null);return;}
        if(!SketchTransactions.active(project)){mode="Wybierz";invalidate();return;}
        List<String> missing=SketchTransactions.unresolved(project);
        if(!missing.isEmpty()) {
            List<SketchPrimitive> profiles=new ArrayList<>();List<String> labels=new ArrayList<>();
            for(SketchPrimitive p:project.sketch)if(p.closedProfile()){profiles.add(p);labels.add("Profil "+profiles.size()+" · "+p.type);}
            if(profiles.isEmpty()){operationMessage="Domknij i połącz profil źródłowy. Dotychczasowa bryła pozostaje bez zmian.";invalidate();return;}
            new PropertyBuilder().setTitle("Wskaż zamknięty profil zastępujący profil bryły")
                .setItems(labels.toArray(new String[0]),(dialog,which)-> {
                    SketchTransactions.bind(project,missing.get(0),profiles.get(which).id);
                    projectRepository.save(project);finishSketchWork();
                }).setNegativeButton("Kontynuuj szkic",null).show();return;
        }
        try {
            CadProject candidate=SketchTransactions.candidate(project);
            if (commitModelChange(() -> project=candidate)) {
                mode="Wybierz";showSolid=true;selectedSketchId=null;selectedFace=-1;
                trimMode=dimensionMode=coincidenceMode=false;closePropertyEditor();
                operationMessage="Zatwierdzono szkic i przebudowano bryłę";
            }
        }catch(RuntimeException error) {
            operationMessage="Nie zatwierdzono: "+error.getMessage()+". Popraw szkic albo anuluj edycję.";
        }
        invalidate();
    }

    private void cancelSketchWork() {
        if(!SketchTransactions.active(project))return;
        CadProject original=SketchTransactions.baseline(project);
        if(!commitModelChange(() -> project=original)){invalidate();return;}
        arcDraft.clear();threePointPreview=null;polylinePreview=null;selectedSketchId=null;selectedFace=-1;
        trimMode=dimensionMode=coincidenceMode=false;mode="Wybierz";showSolid=true;
        closePropertyEditor();
        operationMessage="Anulowano edycję szkicu; przywrócono poprzednią geometrię";invalidate();
    }

    private void enterSketchEditor() {
        trimMode=false;cornerFilletPicking=false;cornerFirst=null;
        dimensionMode=false;
        coincidenceMode=false;coincidenceFirst=null;
        mode = "Edytuj szkic";
        sketchPlaneChosen = true;
        selectedSketchId = null;
        treeSelection = "Szkic";
        beginSketchWork();
        alignToCurrentSketchPlane();
        operationMessage = "Wybierz element lub przeciągnij uchwyt szkicu";
        isolateCoveredSketch();
        invalidate();
    }

    private void isolateCoveredSketch() {
        // Never hide the body implicitly when a sketch starts. Sketch visibility
        // must not change model visibility; the user controls it explicitly.
        // Hiding here made the entire viewport appear empty until Cancel.
        showSolid=true;
    }

    private void showSavedSketchPicker() {
        List<org.json.JSONObject> snapshots = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        labels.add("Bieżący — " + project.sketchPlane + " · " + project.sketch.size() + " elementów");
        for (org.json.JSONObject snapshot : project.savedSketches) {
            if (project.sketchId.equals(snapshot.optString("id"))) continue;
            snapshots.add(snapshot);
            org.json.JSONArray entities = snapshot.optJSONArray("entities");
            labels.add("Szkic " + snapshots.size() + " — " + snapshot.optString("plane")
                + " · " + (entities == null ? 0 : entities.length()) + " elementów");
        }
        if (snapshots.isEmpty()) { enterSketchEditor(); return; }
        new PropertyBuilder().setTitle("Wybierz szkic do edycji")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                if (which == 0) { enterSketchEditor(); return; }
                checkpoint();
                try {
                    preserveExtrudedBase();
                    commitActiveFeature();
                    project.archiveCurrentSketch();
                    project.restoreSketch(snapshots.get(which-1));
                    project.featureOperation = "none";
                    project.featureProfileId = null;
                    project.sketchExtruded = false;
                    sketchPlane = project.sketchPlane;
                    updateNativeModel();
                    projectRepository.save(project);
                    enterSketchEditor();
                } catch (org.json.JSONException | IllegalArgumentException error) {
                    operationMessage = "Nie można odtworzyć wybranego szkicu";
                    invalidate();
                }
            }).setNegativeButton("Anuluj", null).show();
    }

    private void alignToCurrentSketchPlane() {
        modelSnapGeometry=null;
        String plane = project.sketchPlane;
        sketchPlane = plane;
        if ("XY".equals(plane)) { cameraYaw = 0f; cameraPitch = 0f; }
        else if ("XZ".equals(plane)) { cameraYaw = 0f; cameraPitch = 90f; }
        else if ("YZ".equals(plane)) { cameraYaw = 90f; cameraPitch = 0f; }
        else {
            double nx = project.sketchUy * project.sketchVz - project.sketchUz * project.sketchVy;
            double ny = project.sketchUz * project.sketchVx - project.sketchUx * project.sketchVz;
            double nz = project.sketchUx * project.sketchVy - project.sketchUy * project.sketchVx;
            double yaw = Math.atan2(-nx, nz);
            double rz = -nx * Math.sin(yaw) + nz * Math.cos(yaw);
            cameraYaw = (float)Math.toDegrees(yaw);
            cameraPitch = (float)Math.toDegrees(Math.atan2(ny, rz));
        }
    }

    private void drawToolButton(Canvas canvas, float x, float y, float w, float h,
            String id, String label, boolean active) {
        paint.setColor(active ? ACCENT : Color.rgb(28, 40, 51));
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), dp(7), dp(7), paint);
        drawToolIcon(canvas, id, x + w / 2f, y + dp(14), active ? Color.WHITE : Color.rgb(210, 220, 228));
        text(canvas, label, x + w / 2f, y + dp(38), 7.5f,
            active ? Color.WHITE : Color.rgb(190, 201, 210), false, Paint.Align.CENTER);
    }

    private void drawToolIcon(Canvas canvas, String id, float x, float y, int color) {
        if(SemanticIcons.draw(canvas,id,x,y,density,color))return;
        paint.setColor(color);
        paint.setStrokeWidth(dp(1.7f));
        paint.setStyle(Paint.Style.STROKE);
        if(id.equals("Łuk")) {canvas.drawArc(new RectF(x-dp(10),y-dp(10),x+dp(10),y+dp(10)),185,165,false,paint);
        }else if(id.equals("Polilinia")) {
            Path path=new Path();path.moveTo(x-dp(11),y+dp(7));path.lineTo(x-dp(3),y-dp(8));path.lineTo(x+dp(10),y+dp(3));canvas.drawPath(path,paint);
            canvas.drawCircle(x-dp(11),y+dp(7),dp(2),paint);canvas.drawCircle(x-dp(3),y-dp(8),dp(2),paint);canvas.drawCircle(x+dp(10),y+dp(3),dp(2),paint);
        } else if(id.equals("Wymiar")) {
            canvas.drawLine(x-dp(11),y,x+dp(11),y,paint);
            canvas.drawLine(x-dp(9),y-dp(6),x-dp(9),y+dp(6),paint);
            canvas.drawLine(x+dp(9),y-dp(6),x+dp(9),y+dp(6),paint);
            canvas.drawLine(x-dp(9),y,x-dp(5),y-dp(3),paint);canvas.drawLine(x+dp(9),y,x+dp(5),y+dp(3),paint);
        } else if(id.equals("Przytnij")) {
            canvas.drawCircle(x-dp(7),y+dp(6),dp(4),paint);canvas.drawCircle(x+dp(7),y+dp(6),dp(4),paint);
            canvas.drawLine(x-dp(7),y+dp(3),x+dp(9),y-dp(10),paint);
            canvas.drawLine(x+dp(7),y+dp(3),x-dp(9),y-dp(10),paint);
        } else if(id.equals("Więzy")) {
            canvas.drawCircle(x-dp(6),y,dp(7),paint);canvas.drawCircle(x+dp(6),y,dp(7),paint);
        } else if(id.equals("Geometria")) {
            canvas.drawRect(x-dp(10),y-dp(9),x+dp(10),y+dp(9),paint);
            canvas.drawCircle(x-dp(10),y-dp(9),dp(3),paint);
            canvas.drawCircle(x+dp(10),y+dp(9),dp(3),paint);
        } else if(id.equals("Lustro")) {
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3),dp(2)},0));
            canvas.drawLine(x,y-dp(12),x,y+dp(12),paint);paint.setPathEffect(null);
            canvas.drawRect(x-dp(12),y-dp(6),x-dp(4),y+dp(7),paint);
            canvas.drawRect(x+dp(4),y-dp(6),x+dp(12),y+dp(7),paint);
        } else if(id.equals("Połącz kontur")) {
            Path polygon=new Path();polygon.moveTo(x-dp(11),y+dp(8));polygon.lineTo(x+dp(10),y+dp(8));
            polygon.lineTo(x+dp(4),y-dp(10));polygon.close();canvas.drawPath(polygon,paint);
        } else if (id.equals("Pomocnicza")) {
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4),dp(3)},0));
            canvas.drawLine(x-dp(12),y+dp(8),x+dp(12),y-dp(8),paint);paint.setPathEffect(null);
        } else if(id.equals("Usuń") || id.equals("Wyczyść szkic") || id.equals("Usuń cechę")) {
            canvas.drawRect(x-dp(6),y-dp(5),x+dp(6),y+dp(9),paint);
            canvas.drawLine(x-dp(9),y-dp(8),x+dp(9),y-dp(8),paint);
        } else if(id.equals("Anuluj szkic")) {
            canvas.drawLine(x-dp(9),y-dp(9),x+dp(9),y+dp(9),paint);
            canvas.drawLine(x-dp(9),y+dp(9),x+dp(9),y-dp(9),paint);
        } else if(id.equals("Zakończ")) {
            canvas.drawLine(x-dp(9),y,x-dp(2),y+dp(7),paint);
            canvas.drawLine(x-dp(2),y+dp(7),x+dp(11),y-dp(9),paint);
        } else if(id.equals("Ukryj bryłę")) {
            canvas.drawOval(new RectF(x-dp(11),y-dp(6),x+dp(11),y+dp(6)),paint);
            canvas.drawLine(x-dp(12),y+dp(10),x+dp(12),y-dp(10),paint);
        } else if(id.equals("Dopasuj") || id.equals("Wyczyść widok")) {
            canvas.drawRect(x-dp(10),y-dp(8),x+dp(10),y+dp(8),paint);
            canvas.drawCircle(x,y,dp(3),paint);
        } else if (id.equals("Linia")) canvas.drawLine(x - dp(10), y + dp(7), x + dp(10), y - dp(7), paint);
        else if (id.equals("Prostokąt")) canvas.drawRect(x - dp(10), y - dp(7), x + dp(10), y + dp(7), paint);
        else if (id.equals("Okrąg")) canvas.drawCircle(x, y, dp(9), paint);
        else if (id.equals("Szkic")) {
            canvas.drawRect(x - dp(10), y - dp(8), x + dp(8), y + dp(8), paint);
            canvas.drawLine(x - dp(7), y + dp(4), x + dp(5), y - dp(5), paint);
        } else if (id.equals("Wyciągnij") || id.equals("Wytnij")) {
            canvas.drawRect(x - dp(9), y - dp(2), x + dp(5), y + dp(9), paint);
            canvas.drawLine(x - dp(2), y - dp(2), x - dp(2), y - dp(11), paint);
            canvas.drawLine(x - dp(6), y - dp(7), x - dp(2), y - dp(11), paint);
            canvas.drawLine(x + dp(2), y - dp(7), x - dp(2), y - dp(11), paint);
        } else if (id.equals("Głębokość")) {
            canvas.drawLine(x - dp(9), y, x + dp(9), y, paint);
            canvas.drawLine(x - dp(9), y, x - dp(4), y - dp(5), paint);
            canvas.drawLine(x + dp(9), y, x + dp(4), y + dp(5), paint);
        } else if(id.equals("Właściwości bryły")) {
            canvas.drawRect(x-dp(10),y-dp(10),x+dp(10),y+dp(10),paint);
            canvas.drawLine(x-dp(6),y-dp(4),x+dp(6),y-dp(4),paint);
            canvas.drawLine(x-dp(6),y+dp(3),x+dp(3),y+dp(3),paint);
        } else if(id.equals("Obrót profilu")) {
            canvas.drawOval(new RectF(x-dp(11),y-dp(9),x+dp(11),y+dp(9)),paint);
            canvas.drawLine(x,y-dp(13),x,y+dp(13),paint);
            canvas.drawLine(x+dp(11),y,x+dp(6),y-dp(5),paint);
        } else if(id.equals("Fazuj")) {
            canvas.drawLine(x-dp(11),y+dp(10),x-dp(11),y-dp(2),paint);
            canvas.drawLine(x-dp(11),y-dp(2),x+dp(1),y-dp(10),paint);
            canvas.drawLine(x+dp(1),y-dp(10),x+dp(12),y-dp(10),paint);
        } else if (id.equals("Zaokrąglij")) {
            Path p = new Path(); p.moveTo(x - dp(10), y + dp(8));
            p.quadTo(x - dp(10), y - dp(8), x + dp(8), y - dp(8)); canvas.drawPath(p, paint);
        } else if (id.equals("Zmierz")) {
            canvas.drawLine(x - dp(10), y + dp(7), x + dp(10), y - dp(7), paint);
            canvas.drawLine(x - dp(10), y + dp(2), x - dp(10), y + dp(11), paint);
            canvas.drawLine(x + dp(10), y - dp(11), x + dp(10), y - dp(2), paint);
        } else if (id.equals("Rysunek")) {
            canvas.drawRect(x - dp(9), y - dp(10), x + dp(9), y + dp(10), paint);
            canvas.drawLine(x - dp(6), y - dp(4), x + dp(6), y - dp(4), paint);
            canvas.drawLine(x - dp(6), y + dp(2), x + dp(4), y + dp(2), paint);
        } else if (id.equals("Edytuj")) {
            canvas.drawLine(x - dp(8), y + dp(8), x + dp(7), y - dp(7), paint);
            canvas.drawCircle(x - dp(9), y + dp(9), dp(2), paint);
        } else {
            Path p = new Path(); p.moveTo(x - dp(8), y - dp(10)); p.lineTo(x + dp(8), y);
            p.lineTo(x, y + dp(2)); p.lineTo(x + dp(4), y + dp(10)); p.lineTo(x, y + dp(12));
            p.lineTo(x - dp(4), y + dp(4)); p.lineTo(x - dp(8), y + dp(8)); p.close(); canvas.drawPath(p, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFeatureIcon(Canvas canvas, String feature, float x, float y, int color) {
        if(feature.equals("Gwint")){drawToolIcon(canvas,"Gwint",x,y,color);return;}
        if(feature.equals("Lustro")||feature.equals("Szyk liniowy")||feature.equals("Szyk kołowy")){drawToolIcon(canvas,feature+" operacji",x,y,color);return;}
        if(feature.equals("Powłoka")||feature.equals("Otwór")){drawToolIcon(canvas,feature,x,y,color);return;}
        if(feature.equals("Pochylenie")){drawToolIcon(canvas,"Pochyl ściany",x,y,color);return;}
        if(feature.equals("Podział bryły")){drawToolIcon(canvas,"Podziel bryłę",x,y,color);return;}
        if(feature.equals("Przejście")){drawToolIcon(canvas,"Przejście",x,y,color);return;}
        if(feature.equals("Przeciągnięcie")){drawToolIcon(canvas,"Przeciągnij",x,y,color);return;}
        if(feature.endsWith(" bryły")&&(feature.startsWith("Lustro")||feature.startsWith("Szyk"))){drawToolIcon(canvas,feature,x,y,color);return;}
        if(feature.equals("Przesunięcie bryły")||feature.equals("Obrót bryły")||feature.equals("Skalowanie bryły")){drawToolIcon(canvas,feature.startsWith("Przes")?"Przesuń bryłę":feature.startsWith("Obrót")?"Obróć bryłę":"Skaluj bryłę",x,y,color);return;}
        if(feature.equals("Połączenie brył")||feature.equals("Odjęcie brył")||feature.equals("Część wspólna brył")){drawToolIcon(canvas,feature.startsWith("Połą")?"Połącz bryły":feature.startsWith("Odję")?"Odejmij bryły":"Część wspólna brył",x,y,color);return;}
        if (feature.equals("Szkic")) drawToolIcon(canvas, "Szkic", x, y, color);
        else if (feature.startsWith("Wyciąg") || feature.equals("Dodanie") || feature.equals("Połączenie") || feature.equals("Nowa bryła") || feature.equals("Część wspólna") || feature.equals("Wycięcie")) {
            drawToolIcon(canvas, feature.startsWith("Wyciąg")?"Wyciągnij":feature.equals("Wycięcie")?"Wytnij":feature, x, y, color);
        }
        else if (feature.equals("Obrót profilu")||feature.equals("Wycięcie obrotowe")) drawToolIcon(canvas, "Obrót profilu", x, y, color);
        else if (feature.equals("Fazowanie")) drawToolIcon(canvas, "Fazuj", x, y, color);
        else if (feature.startsWith("R")||feature.equals("Zaokrąglenie")) drawToolIcon(canvas, "Zaokrąglij", x, y, color);
        else if (feature.startsWith("Import")) drawToolIcon(canvas, "Rysunek", x, y, color);
        else {
            paint.setColor(color); canvas.drawCircle(x, y, dp(5), paint);
        }
    }

    private void labelValue(Canvas canvas, float right, String label, String value, float y) {
        text(canvas, label, right + dp(18), y, 12, MUTED, false);
        paint.setColor(Color.rgb(244, 247, 249));
        canvas.drawRoundRect(new RectF(right + dp(18), y + dp(10), getWidth() - dp(18), y + dp(52)), dp(8), dp(8), paint);
        text(canvas, value, right + dp(32), y + dp(37), 14, INK, false);
    }

    private void row(Canvas canvas, String value, float x, float y, boolean strong) {
        text(canvas, value, x, y, 14, INK, strong);
    }

    private void treeRow(Canvas canvas, String id, String value, float top, float bottom) {
        if (id.equals(treeSelection)) {
            paint.setColor(Color.rgb(226, 242, 240));
            canvas.drawRoundRect(new RectF(dp(10), top, dp(214), bottom), dp(7), dp(7), paint);
            paint.setColor(ACCENT);
            canvas.drawRoundRect(new RectF(dp(10), top, dp(14), bottom), dp(2), dp(2), paint);
        }
        text(canvas, value, dp(26), top + dp(23), 12.5f, INK, id.equals(treeSelection));
        if(!id.equals("Szkic"))text(canvas, "›", dp(198), top + dp(23), 15, MUTED, true, Paint.Align.CENTER);
    }

    private void pill(Canvas canvas, float x, float y, float w, float h, String value, int color, int textColor) {
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), dp(9), dp(9), paint);
        text(canvas, value, x + w / 2, y + h / 2 + dp(5), 14, textColor, true, Paint.Align.CENTER);
    }

    private void text(Canvas canvas, String value, float x, float y, float sp, int color, boolean bold) {
        text(canvas, value, x, y, sp, color, bold, Paint.Align.LEFT);
    }

    private void text(Canvas canvas, String value, float x, float y, float sp, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(align);
        canvas.drawText(value, x, y, paint);
    }

    private float dp(float value) { return value * density; }
}
