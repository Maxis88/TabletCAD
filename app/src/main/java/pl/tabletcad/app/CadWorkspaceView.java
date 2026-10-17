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
    private final List<HiddenLineVisibility.Triangle> depthSurfaces = new ArrayList<>();
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
    private boolean dimensionMode;
    private boolean coincidenceMode;
    private String coincidenceFirst;
    private int coincidenceAnchor;
    private String constraintKind="coincident";
    private boolean dragConstraintRejected;
    private boolean polylineTouch;
    private double[] polylinePreview;
    private android.widget.ScrollView propertyEditor;
    private double[] featurePreview;
    private double[] filletEdges=new double[0];
    private int filletSegment=-1;
    private Runnable filletSelectionChanged;
    private android.widget.HorizontalScrollView nativeToolbar;
    private android.widget.PopupWindow toolbarPopup;
    private String toolbarState="";
    private boolean toolbarUpdatePosted;
    private final List<double[]> arcDraft=new ArrayList<>();
    private double[] arcPreview;

    private boolean inSketch() { return mode.equals("Szkic") || mode.equals("Edytuj szkic"); }

    private float topToolWidth() {return dp(64);}

    private void closePropertyEditor() {
        featurePreview=null;
        measurementOutput=null;
        filletEdges=new double[0];filletSegment=-1;filletSelectionChanged=null;
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
            propertyEditor=new android.widget.ScrollView(getContext());propertyEditor.setFillViewport(true);
            propertyEditor.addView(body);
            android.widget.FrameLayout.LayoutParams layout=new android.widget.FrameLayout.LayoutParams(
                (int)dp(252),Math.max((int)dp(100),getHeight()-(int)dp(110)-bottomSystemInset));
            layout.gravity=android.view.Gravity.TOP|android.view.Gravity.RIGHT;layout.topMargin=(int)dp(110);
            ((android.widget.FrameLayout)getParent()).addView(propertyEditor,layout);
        }
    }

    public CadWorkspaceView(Context context, FileRequestListener fileRequestListener) {
        super(context);
        this.fileRequestListener = fileRequestListener;
        density = getResources().getDisplayMetrics().density;
        projectRepository = new ProjectRepository(context);
        project = projectRepository.loadOrCreate();
        operationMessage=projectRepository.loadNotice();
        if (project.importedStep && project.importedStepSource.equals("imported.step")
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
        projectRepository.save(project);
        kernelStatus = NativeCadEngine.status();
        updateNativeModel();
        resumeSketchWork();
        // STEP is deliberately not exercised during Activity construction. A native
        // importer failure must never prevent the workspace from opening.
        stepStatus = "STEP: moduł zainstalowany";
        setBackgroundColor(BG);
        setFocusable(true);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if(SketchTransactions.active(project)&&(e.getY()<dp(54)||e.getX()<dp(224)
                        ||(e.getY()>=getHeight()-dp(78)-bottomSystemInset&&e.getX()>=dp(340)))) {
                    showSketchSessionActions();return true;
                }
                if(!arcDraft.isEmpty()&&(e.getX()<dp(224)||e.getY()<dp(54))) {showArcDraftActions(null);return true;}
                if(!project.polylineDraft.isEmpty() && ((e.getX()<dp(224)&&e.getY()>dp(108))
                        ||(e.getY()<dp(58)&&e.getX()>getWidth()-dp(330)))) {
                    showPolylineActions(null);return true;
                }
                if(mode.equals("Szkic")&&sketchTool.equals("Polilinia")&&sketchPlaneChosen
                        && e.getX()>getWidth()-dp(234)&&e.getY()>dp(108)) {
                    float y=e.getY();
                    if(y>=dp(374)&&y<=dp(422))finishPolyline(true);
                    else if(y>=dp(432)&&y<=dp(480))finishPolyline(false);
                    else if(y>=dp(490)&&y<=dp(538))removePolylinePoint();
                    else if(y>=dp(548)&&y<=dp(596))cancelPolyline();
                    return true;
                }
                if(e.getX()<dp(224) || e.getY()>getHeight()-dp(84)-bottomSystemInset) closePropertyEditor();
                float historyY = getHeight() - dp(78) - bottomSystemInset;
                if (e.getX() >= dp(224) && e.getX() <= dp(328) && e.getY() >= historyY) {
                    if(!arcDraft.isEmpty()){
                        if(e.getX()<dp(276)){arcDraft.remove(arcDraft.size()-1);arcPreview=null;operationMessage="Cofnięto punkt rozpoczętego łuku";}
                        else operationMessage="Zakończ lub anuluj rozpoczęty łuk przed Ponów";
                        invalidate();return true;
                    }
                    if (e.getX() < dp(276)) undo(); else redo();
                    return true;
                }
                if (e.getY() >= historyY && e.getX() >= dp(340)
                        && e.getX() < getWidth() - dp(250)) {
                    if(!arcDraft.isEmpty()){showArcDraftActions(null);return true;}
                    if(!project.polylineDraft.isEmpty()){showPolylineActions(null);return true;}
                    int index = (int)((e.getX() - dp(340)) / dp(82));
                    List<CadTimeline.Item> items=CadTimeline.entries(project);
                    if(index>=0&&index<items.size()) {
                        CadTimeline.Item item=items.get(index);
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
                if (e.getY() <= dp(58) && e.getX() >= getWidth() - dp(190)) {
                    showExportPicker();
                    return true;
                }
                if (e.getY() <= dp(58) && e.getX() >= getWidth() - dp(330)) {
                    if (fileRequestListener != null) fileRequestListener.requestOpenStep();
                    return true;
                }
                if(e.getY()<=dp(50)&&e.getX()>=dp(130)&&e.getX()<getWidth()-dp(340)) {
                    showProjectMenu();return true;
                }
                if (e.getY() >= dp(54) && e.getY() <= dp(104)
                        && e.getX() >= dp(224) && e.getX() <= dp(224)+topToolWidth()*ToolbarCatalog.groups(inSketch()).length) {
                    float itemWidth = topToolWidth();
                    int tool = (int)((e.getX() - dp(224)) / itemWidth);
                    ToolbarCatalog.Group[] groups=ToolbarCatalog.groups(inSketch());
                    if (tool >= 0 && tool < groups.length) openToolGroup(groups[tool],dp(224)+tool*itemWidth);
                    return true;
                }
                if (e.getX() < dp(224) && e.getY() >= dp(180) && e.getY() <= dp(216)) {
                    cameraYaw = -28f; cameraPitch = 22f; zoom = 1f; panX = panY = 0f;
                    treeSelection = "Początek";
                    mode = "Wybierz";
                    invalidate();
                    return true;
                }
                if (e.getX() < dp(224) && e.getY() >= dp(216) && e.getY() <= dp(252)) {
                    showSavedSketchPicker();
                    return true;
                }
                if (e.getX() < dp(224) && e.getY() >= dp(252) && e.getY() <= dp(286)) {
                    treeSelection = "Model";
                    mode = "Wybierz";
                    selectedSketchId = null;
                    invalidate();
                    return true;
                }
                if (project.filletRadius > 0.0 && e.getX() < dp(224)
                        && e.getY() >= dp(286) && e.getY() <= dp(322)) {
                    treeSelection = "Zaokrąglenie";
                    showAllEdgesFilletEditor();
                    return true;
                }
                if (mode.equals("Szkic") && !sketchPlaneChosen && e.getX() > getWidth() - dp(234)) {
                    if (e.getY() >= dp(374) && e.getY() <= dp(426)) chooseSketchPlane("XY");
                    else if (e.getY() >= dp(432) && e.getY() <= dp(484)) chooseSketchPlane("XZ");
                    else if (e.getY() >= dp(490) && e.getY() <= dp(542)) chooseSketchPlane("YZ");
                } else if (isInsideViewport(e.getX(), e.getY())) {
                    if(mode.equals("Szkic")&&sketchPlaneChosen&&sketchTool.equals("Łuk")){arcTap(e.getX(),e.getY());return true;}
                    if(filletSelectionChanged!=null) {
                        selectFilletEdge(e.getX(),e.getY());return true;
                    }
                    if (mode.equals("Edytuj szkic")) {
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
                            operationMessage = "Dotknij elementu szkicu do edycji";
                            invalidate();
                        }
                        return true;
                    }
                    if (mode.equals("Zmierz")) {
                        handleMeasureTap(e.getX(), e.getY());
                        return true;
                    }
                    int face = findFaceAt(e.getX(), e.getY());
                    if (face >= 0) {
                        selectedFace = face;
                        if (mode.equals("Szkic") && !sketchPlaneChosen) {
                            if (isFacePlanar(face)) chooseSketchPlane("Ściana " + (face + 1));
                            else operationMessage = "Wybierz płaską ścianę — powierzchnia jest zakrzywiona";
                        }
                        invalidate();
                    }
                }
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (!scaleGestures.isInProgress() && e2.getPointerCount() > 1) {
                    panX -= dx;
                    panY -= dy;
                    invalidate();
                } else if (!scaleGestures.isInProgress()
                        && (mode.equals("Wybierz") || mode.equals("Zmierz"))) {
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
                    zoom = Math.max(0.45f, Math.min(3.5f, zoom * detector.getScaleFactor()));
                    invalidate();
                    return true;
                }
            });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getPointerCount()>1 || event.getActionMasked()==MotionEvent.ACTION_CANCEL) {
            sketching=false;sketchRect=null;snapMarker=null;
            polylineTouch=false;polylinePreview=null;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if(inSketch() && isInsideViewport(event.getX(),event.getY())) closePropertyEditor();
            touchDownX = event.getX();
            touchDownY = event.getY();
        }
        if(mode.equals("Szkic")&&sketchTool.equals("Polilinia")&&sketchPlaneChosen) {
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
                    if(project.polylineDraft.size()>=3 && point==project.polylineDraft.get(0))finishPolyline(true);
                    else try {
                        PolylineDraft.add(project.polylineDraft,point[0],point[1]);redoStack.clear();
                        projectRepository.save(project);operationMessage="Dodaj kolejny punkt; dotknij początku, aby zamknąć profil.";
                    }catch(IllegalArgumentException error){operationMessage=error.getMessage();}
                }
                polylinePreview=null;snapMarker=null;invalidate();return true;
            }
        }
        if (mode.equals("Edytuj szkic") && !trimMode && !dimensionMode && !coincidenceMode) {
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
                        double[] point = snappedSketchPoint(event.getX(), event.getY(), draggedSketch.id, null, null);
                        double du = point[0]-dragStartU, dv = point[1]-dragStartV;
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
                            try {dragPreview=new SketchPrimitive(draggedSketch.id,vertices);}
                            catch(IllegalArgumentException e){dragPreview=null;}
                        } else {
                            try {dragPreview=SketchDimensions.constrainDrag(draggedSketch,
                                new SketchPrimitive(draggedSketch.id,draggedSketch.type,x1,y1,x2,y2),dragHandle);}
                            catch(IllegalArgumentException error){dragPreview=null;operationMessage=error.getMessage();}
                        }
                        dragSolvedPreview=null;
                        if(dragPreview!=null&&draggedSketch.curves==null&&!project.coincidences.isEmpty()) {
                            try{
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
        if (mode.equals("Szkic") && !sketchTool.equals("Łuk") && !sketchTool.equals("Polilinia") && sketchPlaneChosen
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
        scheduleToolbarUpdate();
        float left = dp(224);
        float right = getWidth() - dp(252);
        float top = dp(110);
        float bottom = getHeight() - dp(84) - bottomSystemInset;
        workCenterX = (left + right) / 2f + panX;
        workCenterY = (top + bottom) / 2f + panY;

        drawTopBar(canvas);
        drawLeftPanel(canvas, left, top, bottom);
        drawProperties(canvas, right, top, bottom);
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        drawGrid(canvas, left, top, right, bottom);
        if (mode.equals("Rysunek")) {
            drawTechnicalViews(canvas, left, top, right, bottom);
        } else {
            if (showSolid || !inSketch()) drawNativeSolid(canvas, (left + right) / 2f + panX, (top + bottom) / 2f + panY);
            if ((mode.equals("Szkic") && sketchPlaneChosen) || mode.equals("Edytuj szkic")) {
                drawSketch(canvas, left, right);
            }
            if (mode.equals("Zmierz")) drawDimensions(canvas, left, top, right, bottom);
        }
        if(!mode.equals("Rysunek")) drawAxis(canvas, right - dp(70), bottom - dp(60));
        canvas.restore();
        drawTimeline(canvas);
    }

    private void drawTopBar(Canvas canvas) {
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(0, 0, getWidth(), dp(108), paint);
        text(canvas, "TabletCAD", dp(22), dp(35), 20, Color.WHITE, true);
        String projectLabel=project.name.length()>32 ? project.name.substring(0,29)+"..." : project.name;
        text(canvas, projectLabel+" ▾  •  zapisano lokalnie", dp(145), dp(34), 14, Color.rgb(190, 201, 210), false);
        pill(canvas, getWidth() - dp(312), dp(10), dp(124), dp(38), "Otwórz plik", Color.rgb(44, 58, 70), Color.WHITE);
        pill(canvas, getWidth() - dp(172), dp(10), dp(152), dp(38), "Eksportuj", ACCENT, Color.WHITE);
        float itemWidth = topToolWidth();
        ToolbarCatalog.Group[] groups = ToolbarCatalog.groups(inSketch());
        text(canvas, inSketch() ? "SZKIC 2D" : "BRYŁA 3D", dp(22), dp(84), 13, Color.WHITE, true);
        for (int i = 0; i < groups.length; i++) {
            float x = dp(224) + i * itemWidth;
            boolean active = groupActive(groups[i]);
            drawToolButton(canvas, x + dp(2), dp(54), itemWidth - dp(4), dp(48),
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
            nativeToolbar.setBackgroundColor(Color.rgb(16,24,32));
            LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);
            for(ToolbarCatalog.Group group:ToolbarCatalog.groups(inSketch())) {
                View button=toolbarButton(group.title,group.icon,!group.direct(),groupActive(group),false);
                button.setContentDescription(group.direct()?group.title:group.title+" · otwórz grupę");
                button.setOnClickListener(v->{int[] position=new int[2];v.getLocationOnScreen(position);openToolGroup(group,position[0]);});
                row.addView(button,new LinearLayout.LayoutParams((int)topToolWidth(),(int)dp(48)));
            }
            nativeToolbar.addView(row);
            android.widget.FrameLayout.LayoutParams layout=new android.widget.FrameLayout.LayoutParams(Math.max(1,getWidth()-(int)dp(224)),(int)dp(50));
            layout.leftMargin=(int)dp(224);layout.topMargin=(int)dp(54);
            root.addView(nativeToolbar,layout);
        });
    }

    private View toolbarButton(String label,String icon,boolean dropdown,boolean active,boolean submenu) {
        View button=new View(getContext()) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setStyle(Paint.Style.FILL);paint.setColor(active?ACCENT:Color.rgb(28,40,51));
                canvas.drawRoundRect(new RectF(dp(2),dp(2),getWidth()-dp(2),getHeight()-dp(2)),dp(6),dp(6),paint);
                canvas.save();canvas.translate(getWidth()/2f,dp(submenu?20:15));canvas.scale(.8f,.8f);
                drawToolIcon(canvas,icon,0,0,active?Color.WHITE:Color.rgb(210,220,228));canvas.restore();
                text(canvas,label,getWidth()/2f,dp(submenu?49:39),submenu?10:9,Color.WHITE,false,Paint.Align.CENTER);
                if(dropdown)text(canvas,"▾",getWidth()-dp(9),dp(16),10,Color.rgb(190,201,210),false,Paint.Align.CENTER);
            }
        };
        button.setFocusable(true);button.setClickable(true);button.setTooltipText(label);
        button.setContentDescription(label);return button;
    }

    private void dismissToolPopup() {if(toolbarPopup!=null){toolbarPopup.dismiss();toolbarPopup=null;}}

    private void openToolGroup(ToolbarCatalog.Group group,float anchorX) {
        dismissToolPopup();
        boolean role=inSketch();String[] commands=group.commands();
        for(String command:commands)if(!ToolbarCatalog.allows(role,command)){invalidate();return;}
        if(group.direct()){activateTopTool(commands[0]);return;}
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding((int)dp(8),(int)dp(6),(int)dp(8),(int)dp(8));body.setBackgroundColor(Color.rgb(16,24,32));
        android.widget.TextView heading=new android.widget.TextView(getContext());
        heading.setText((role?"Szkic · ":"Bryła · ")+group.title);heading.setTextColor(Color.WHITE);heading.setTextSize(13);
        heading.setPadding((int)dp(6),0,0,(int)dp(5));body.addView(heading);
        LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);
        for(String command:commands) {
            String label=command.equals("Ukryj bryłę")&&!showSolid?"Pokaż bryłę":command;
            View button=toolbarButton(label,command,false,isToolActive(command),true);
            button.setOnClickListener(v->{dismissToolPopup();
                if(inSketch()==role&&ToolbarCatalog.allows(role,command))activateTopTool(command);
            });
            row.addView(button,new LinearLayout.LayoutParams((int)dp(88),(int)dp(64)));
        }
        body.addView(row);
        int width=(int)dp(commands.length*88+16);
        toolbarPopup=new android.widget.PopupWindow(body,width,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);
        toolbarPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(16,24,32)));
        toolbarPopup.setOutsideTouchable(true);toolbarPopup.setElevation(dp(10));
        int x=(int)Math.max(dp(224),Math.min(anchorX,getWidth()-dp(252)-width));
        int[] origin=new int[2];getLocationOnScreen(origin);
        toolbarPopup.showAtLocation(this,android.view.Gravity.TOP|android.view.Gravity.LEFT,x+origin[0],(int)dp(108)+origin[1]);
    }

    @Override protected void onDetachedFromWindow() {
        dismissToolPopup();
        // Do not mutate siblings while FrameLayout is dispatching detachment.
        toolbarState="";super.onDetachedFromWindow();
    }

    private void drawLeftPanel(Canvas canvas, float left, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(0, dp(108), left, getHeight(), paint);
        text(canvas, "MODEL", dp(18), dp(137), 12, MUTED, true);
        row(canvas, "▾  Dokument", dp(18), dp(168), true);
        treeRow(canvas, "Początek", "◇  Widok bazowy", dp(184), dp(216));
        treeRow(canvas, "Szkic", "▱  Szkic 1  (" + project.sketch.size() + ")", dp(218), dp(252));
        treeRow(canvas, "Model", !project.hasSolid()?"◇  Brak bryły":project.importedMeshSource != null ? "△  Siatka STL" : project.importedStep ?
            (project.featureOperation.equals("none") ? "◈  Import STEP"
                : project.featureOperation.equals("cut") ? "▣  Wycięcie STEP" : "▣  Dodanie STEP")
            : (!project.featureOperation.equals("none")
                ? (project.featureOperation.equals("cut") ? "▣  Wycięcie szkicu" : "▣  Dodanie szkicu")
                : (Revolve.is(project.featureBase)?"◈  Obrót profilu":project.sketchExtruded ? "◈  Wyciągnięcie szkicu" : "◈  Wyciągnięcie 1")),
            dp(252), dp(286));
        if (project.filletRadius > 0.0) {
            treeRow(canvas, "Zaokrąglenie",
                String.format(java.util.Locale.US, "◒  Zaokrąglenie R%.2f", project.filletRadius),
                dp(286), dp(322));
        }
        text(canvas, "Dotknij elementu, aby go otworzyć", dp(18), dp(350), 9.5f, MUTED, false);
    }

    private void drawProperties(Canvas canvas, float right, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(right, dp(108), getWidth(), getHeight(), paint);
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
                if(mode.equals("Szkic")&&sketchTool.equals("Polilinia")) {
                    labelValue(canvas,right,"Punkty polilinii",String.valueOf(project.polylineDraft.size()),dp(230));
                    text(canvas,"Wskazuj kolejne punkty palcem.",right+dp(18),dp(330),11,MUTED,false);
                    pill(canvas,right+dp(18),dp(374),dp(202),dp(48),"Zamknij obwód",ACCENT,Color.WHITE);
                    pill(canvas,right+dp(18),dp(432),dp(202),dp(48),"Zapisz otwarty łańcuch",GRID,INK);
                    pill(canvas,right+dp(18),dp(490),dp(202),dp(48),"Cofnij ostatni punkt",GRID,INK);
                    pill(canvas,right+dp(18),dp(548),dp(202),dp(48),"Anuluj polilinię",GRID,INK);
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
            String.format(java.util.Locale.US, "%.2f mm", shownDepth), dp(226));
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

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        paint.setStrokeWidth(1f);
        paint.setColor(GRID);
        if(inSketch() && sketchPlaneChosen) {
            double[] center=screenToSketch((left+right)/2,(top+bottom)/2);
            double step=zoom<.8?20:10;
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
        projectNativeMesh(cx, cy);
        projectedTriangles.sort((a, b) -> Float.compare(a.depth, b.depth));
        for (ProjectedTriangle triangle : projectedTriangles) {
            Path facePath = triangle.path();
            paint.setStyle(Paint.Style.FILL);
            if (triangle.faceId == selectedFace) paint.setColor(Color.rgb(255, 190, 70));
            else paint.setColor(meshFaceColor(triangle));
            canvas.drawPath(facePath, paint);
        }
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

    private void drawSketch(Canvas canvas, float left, float right) {
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
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            for(double[] point:project.polylineDraft) {PointF p=projectSketchPoint(point[0],point[1]);canvas.drawCircle(p.x,p.y,dp(4),paint);}
            double[] start=project.polylineDraft.get(0);PointF p=projectSketchPoint(start[0],start[1]);
            text(canvas,"Początek",p.x+dp(10),p.y-dp(10),10,ACCENT,true);
        }
        List<SketchPrimitive> displayedSketch=dragSolvedPreview==null?project.sketch:dragSolvedPreview;
        for (SketchPrimitive entity : displayedSketch) {
            SketchPrimitive shown=dragSolvedPreview==null&&dragPreview!=null&&dragPreview.id.equals(entity.id)?dragPreview:entity;
            drawSketchEntity(canvas,shown);
            if(shown.driving!=null)drawSketchDrivingDimensions(canvas,shown);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(Color.rgb(117,70,160));
        for(SketchConstraints.Link link:project.coincidences)for(SketchPrimitive p:displayedSketch)if(p.id.equals(link.a)) {
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
                        text(canvas,link.kind.equals("horizontal")?"H":"V",(point.x+other.x)/2+dp(8),(point.y+other.y)/2-dp(8),10,Color.rgb(117,70,160),true);
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

    private void arcTap(float x,float y) {
        double[] point=snappedSketchPoint(x,y,null,null,null);
        if(arcDraft.size()<2) {
            if(arcDraft.size()==1&&Math.hypot(point[0]-arcDraft.get(0)[0],point[1]-arcDraft.get(0)[1])<.01){operationMessage="Koniec musi być różny od początku";invalidate();return;}
            arcDraft.add(point);operationMessage=arcDraft.size()==1?"Łuk: wskaż koniec":"Łuk: wskaż punkt pośredni po właściwej stronie cięciwy";
        }else {
            double[] a=arcDraft.get(0),b=arcDraft.get(1);
            try {
                SketchPrimitive arc=new SketchPrimitive(UUID.randomUUID().toString(),SketchCurves.arc(a[0],a[1],point[0],point[1],b[0],b[1]),false);
                checkpoint();project.sketch.add(arc);selectedSketchId=arc.id;arcDraft.clear();arcPreview=null;
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
        if(!entity.closedProfile()){showCurveSegmentEditor(entity,0);return;}
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
            android.widget.TextView label=new android.widget.TextView(getContext());label.setText(labels[i]);form.addView(label);form.addView(fields[i]);}
        new PropertyBuilder().setTitle(entity.closedProfile()?"Segment "+(segment+1):"Łuk · trzy punkty").setView(form)
            .setNeutralButton("Usuń element",(d,w)->deleteSketchEntity(entity.id)).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double[] s=entity.curves.clone();for(int i=0;i<6;i++)
                        if(!fields[i].getText().toString().equals(originalText[i]))s[offset+1+i]=parseCoordinate(fields[i]);
                    if(entity.closedProfile()) {
                        int previous=(offset-7+s.length)%s.length,next=(offset+7)%s.length;
                        s[previous+5]=s[offset+1];s[previous+6]=s[offset+2];s[next+1]=s[offset+5];s[next+2]=s[offset+6];
                    }
                    replaceSketchEntity(entity,new SketchPrimitive(entity.id,s,entity.closedProfile()));
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    private void drawSketchEntity(Canvas canvas, SketchPrimitive entity) {
        boolean selected = entity.id.equals(selectedSketchId);
        PointF p1 = projectSketchPoint(entity.x1, entity.y1);
        PointF p2 = projectSketchPoint(entity.x2, entity.y2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(selected ? 3.5f : 2f));
        paint.setColor(selected ? Color.rgb(245, 145, 20) : Color.rgb(30, 105, 210));
        if(entity.type.equals("Pomocnicza")) paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(8),dp(5)},0));
        if(entity.curves!=null)drawCurves(canvas,entity.curves);
        else if(entity.vertices!=null) {
            for(int i=0;i<entity.vertices.length;i+=2) {
                int next=(i+2)%entity.vertices.length;
                drawSketchSegment(canvas,entity.vertices[i],entity.vertices[i+1],entity.vertices[next],entity.vertices[next+1]);
            }
        } else if (entity.type.equals("Linia") || entity.type.equals("Pomocnicza")) {
            drawSketchSegment(canvas,entity.x1,entity.y1,entity.x2,entity.y2);
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
        if (selected) {
            paint.setColor(Color.rgb(245, 145, 20));
            if(entity.curves!=null) {
                for(int i=0;i<entity.curves.length;i+=7) {
                    drawSketchHandle(canvas,entity.curves[i+1],entity.curves[i+2]);
                    if(!entity.closedProfile())drawSketchHandle(canvas,entity.curves[i+5],entity.curves[i+6]);
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
            String size = entity.curves!=null?(entity.closedProfile()?"Kontur · "+entity.curves.length/7+" segmentów":"Łuk · R "+formatMm(SketchCurves.arc(entity.curves,0).r)):entity.vertices!=null ? "Kontur · "+entity.vertices.length/2+" wierzchołków" : entity.type.equals("Okrąg")
                ? String.format(java.util.Locale.US, "R %.2f mm", Math.hypot(entity.x2-entity.x1,entity.y2-entity.y1))
                : entity.type.equals("Prostokąt")
                    ? String.format(java.util.Locale.US, "%.2f × %.2f mm", Math.abs(entity.x2-entity.x1),Math.abs(entity.y2-entity.y1))
                    : String.format(java.util.Locale.US, "%.2f mm", Math.hypot(entity.x2-entity.x1,entity.y2-entity.y1));
            if(entity.driving==null&&sketchPointVisible(entity.x2,entity.y2))text(canvas, size, p2.x + dp(12), p2.y - dp(10), 11, INK, true);
        }
    }

    private void drawSketchDrivingDimensions(Canvas canvas,SketchPrimitive entity) {
        if(!sketchPointVisible(entity.x1,entity.y1)||!sketchPointVisible(entity.x2,entity.y2))return;
        PointF a=projectSketchPoint(entity.x1,entity.y1),b=projectSketchPoint(entity.x2,entity.y2);
        if(entity.type.equals("Prostokąt")) {
            PointF center=projectSketchPoint((entity.x1+entity.x2)/2,(entity.y1+entity.y2)/2);
            drawSketchDimension(canvas,a,projectSketchPoint(entity.x2,entity.y1),center,"W "+formatMm(entity.driving[0]));
            drawSketchDimension(canvas,a,projectSketchPoint(entity.x1,entity.y2),center,"H "+formatMm(entity.driving[1]));
        } else drawSketchDimension(canvas,a,b,null,(entity.type.equals("Okrąg")?"R ":"L ")+formatMm(entity.driving[0]));
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
    }

    private int sketchHandleAt(SketchPrimitive entity, float x, float y) {
        if(entity.curves!=null) {
            int handle=0;double best=dp(14);
            for(int i=0;i<entity.curves.length;i+=7)for(int j:new int[]{1,3,5}) {
                if(j==3&&entity.curves[i]==0||j==5&&entity.closedProfile())continue;
                double u=entity.curves[i+j],v=entity.curves[i+j+1];if(!sketchPointVisible(u,v))continue;
                PointF p=projectSketchPoint(u,v);double d=Math.hypot(x-p.x,y-p.y);
                if(d<best){best=d;handle=entity.closedProfile()?(j==3?1000+i/7:1+i/7):(j==1?1:j==5?2:3);}
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
        if(!showSolid)return true;
        double[] p=projectSketchDepth(u,v);
        return HiddenLineVisibility.visible(p[0],p[1],p[2],depthSurfaces);
    }

    private void drawSketchHandle(Canvas canvas,double u,double v) {
        if(!sketchPointVisible(u,v))return;
        PointF p=projectSketchPoint(u,v);canvas.drawCircle(p.x,p.y,dp(5),paint);
    }

    private double sketchSegmentDistance(float x,float y,double u1,double v1,double u2,double v2) {
        double[] a=projectSketchDepth(u1,v1),b=projectSketchDepth(u2,v2);
        return HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,showSolid?depthSurfaces:java.util.Collections.emptyList());
    }

    private void drawSketchSegment(Canvas canvas,double u1,double v1,double u2,double v2) {
        double[] a=projectSketchDepth(u1,v1),b=projectSketchDepth(u2,v2);
        if(!showSolid) {
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
        double perspective = 420.0 / (420.0 - finalZ);
        float scale = dp(3.2f) * zoom;
        return new double[]{workCenterX + (float)(rx * scale * perspective),
            workCenterY - (float)(ry * scale * perspective),1.0/(420.0-finalZ)};
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
        project.sketch.add(new SketchPrimitive(UUID.randomUUID().toString(), type, x1, y1, x2, y2));
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
        return new double[] {(dx * by - dy * bx) / determinant,
            (ax * dy - ay * dx) / determinant};
    }

    private double[] polylinePoint(float x,float y) {
        if(project.polylineDraft.size()>=3) {
            double[] first=project.polylineDraft.get(0);PointF start=projectSketchPoint(first[0],first[1]);
            if(Math.hypot(x-start.x,y-start.y)<dp(14)){snapMarker=start;return first;}
        }
        double[] last=project.polylineDraft.isEmpty()?null:project.polylineDraft.get(project.polylineDraft.size()-1);
        return snappedSketchPoint(x,y,null,last==null?null:last[0],last==null?null:last[1]);
    }

    private boolean finishPolyline(boolean closed) {
        try {
            List<SketchPrimitive> entities=closed?java.util.Collections.singletonList(PolylineDraft.closed(project.polylineDraft))
                :PolylineDraft.open(project.polylineDraft);
            checkpoint();preserveExtrudedBase();commitActiveFeature();
            project.sketch.addAll(entities);project.polylineDraft.clear();polylinePreview=null;snapMarker=null;
            selectedSketchId=entities.get(0).id;project.featureProfileId=null;
            project.featureOperation="none";project.sketchExtruded=false;projectRepository.save(project);
            operationMessage=closed?"Gotowe: zamknięty profil. Zakończ szkic i wyciągnij lub wytnij."
                :"Gotowe: zapisano otwarty łańcuch jako edytowalne linie.";
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
        project.polylineDraft.clear();polylinePreview=null;snapMarker=null;projectRepository.save(project);
        operationMessage="Anulowano polilinię. Cofnij przywraca rozpoczęty łańcuch.";invalidate();
    }

    private void showPolylineActions(String deferredTool) {
        new PropertyBuilder().setTitle("Zakończ rozpoczętą polilinię")
            .setItems(new String[]{"Zamknij obwód", "Zapisz otwarty łańcuch", "Anuluj polilinię"},(d,which)-> {
                boolean done;
                if(which==2){cancelPolyline();done=true;}else done=finishPolyline(which==0);
                if(done&&deferredTool!=null)activateTopTool(deferredTool);
            }).setNegativeButton("Kontynuuj rysowanie",null).show();
    }

    private double[] snappedSketchPoint(float x,float y,String excluded,Double startX,Double startY) {
        double[] raw=screenToSketch(x,y);
        PointF a=projectSketchPoint(raw[0],raw[1]),b=projectSketchPoint(raw[0]+1,raw[1]);
        double tolerance=dp(12)/Math.max(0.1,Math.hypot(a.x-b.x,a.y-b.y));
        double[] snapped=SketchSnapper.snap(raw[0],raw[1],project.sketch,excluded,tolerance,startX,startY);
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
            if(entity.curves!=null) {
                distance=Double.POSITIVE_INFINITY;double[] points=SketchCurves.samples(entity.curves,96);
                for(int i=0;i<points.length-2;i+=2)distance=Math.min(distance,sketchSegmentDistance(sx,sy,points[i],points[i+1],points[i+2],points[i+3]));
            }else if(entity.vertices!=null) {
                distance=Double.POSITIVE_INFINITY;
                for(int i=0;i<entity.vertices.length;i+=2) {
                    int j=(i+2)%entity.vertices.length;
                    distance=Math.min(distance,sketchSegmentDistance(sx,sy,entity.vertices[i],entity.vertices[i+1],entity.vertices[j],entity.vertices[j+1]));
                }
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
        if(entity.curves!=null){showCurveEditor(entity);return;}
        if(entity.vertices!=null) {
            android.widget.TextView instructions=new android.widget.TextView(getContext());
            instructions.setText("Przeciągnij pomarańczowy wierzchołek, aby zmienić kształt. Przeciągnij krawędź, aby przesunąć cały kontur. Zmiana przebudowuje powiązane operacje.");
            new PropertyBuilder().setTitle("Kontur · "+entity.vertices.length/2+" wierzchołków")
                .setView(instructions).setNeutralButton("Usuń kontur",(d,w)->deleteSketchEntity(entity.id))
                .setNegativeButton("Zamknij",null).show();return;
        }
        showSketchDimensionEditor(entity);
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
        for(SketchPrimitive p:project.sketch)if(p.type.equals("Pomocnicza")&&!p.id.equals(source.id)) {
            axes.add(new double[]{p.x1,p.y1,p.x2,p.y2});labels.add("Linia pomocnicza "+(axes.size()-2));
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
        if(source.type.equals("Linia")||source.type.equals("Pomocnicza")) {
            labels.add(source.type.equals("Linia")?"Zmień na linię pomocniczą":"Zmień na linię konturu");
            actions.add(()->{
                if(isProfileUsed(source.id)&&!SketchTransactions.active(project)){operationMessage="Element jest używany przez bryłę";invalidate();return;}
                replaceSketchEntity(source,SketchEditing.toggleConstruction(source));
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
        labels.add("Usuń element");actions.add(()->deleteSketchEntity(source.id));
        new PropertyBuilder().setTitle("Geometria · "+source.type)
            .setItems(labels.toArray(new String[0]),(dialog,which)->actions.get(which).run())
            .setNegativeButton("Zamknij",null).show();
    }

    private void coincidenceTap(float x,float y) {
        if(constraintKind.equals("horizontal")||constraintKind.equals("vertical")) {axisConstraintTap(x,y);return;}
        SketchPrimitive picked=null;int anchor=-1;double best=dp(18);
        for(SketchPrimitive p:project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++) {
            if(p.id.equals(coincidenceFirst))continue;
            double[] a=SketchConstraints.anchor(p,i);if(!sketchPointVisible(a[0],a[1]))continue;
            PointF point=projectSketchPoint(a[0],a[1]);double distance=Math.hypot(x-point.x,y-point.y);
            if(distance<best){best=distance;picked=p;anchor=i;}
        }
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

    private void addSketchConstraint(SketchConstraints.Link rule) {
        try {
            List<SketchPrimitive> solved=SketchConstraints.connect(project.sketch,project.coincidences,rule);
            List<SketchConstraints.Link> links=new ArrayList<>(project.coincidences);links.add(rule);
            if(applySolvedSketch(solved,links))operationMessage=rule.kind.equals("fixed")?"Punkt unieruchomiony. Aby odblokować: Geometria → Usuń więzy.":"Zapisano kierunek odcinka. Edytuj pozwala zmienić długość bez zmiany kierunku.";
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

    private void showConstraintMenu() {
        new PropertyBuilder().setTitle("Więzy szkicu")
            .setItems(new String[]{"Połącz dwa punkty","Odcinek poziomy (H)","Odcinek pionowy (V)","Unieruchom punkt"},(d,which)-> {
                constraintKind=new String[]{"coincident","horizontal","vertical","fixed"}[which];
                coincidenceMode=true;coincidenceFirst=null;
                operationMessage=which==0?"Wskaż dwa punkty różnych elementów":which==3?"Dotknij punktu, który ma pozostać na miejscu":"Dotknij odcinka lub boku konturu, aby zapisać kierunek";
                invalidate();
            }).setNegativeButton("Zamknij",null).show();
    }

    private void showEntityConstraints(SketchPrimitive source) {
        List<SketchConstraints.Link> rules=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchConstraints.Link rule:project.coincidences)if(rule.uses(source.id)) {
            rules.add(rule);
            String label=rule.kind.equals("fixed")?"Punkt stały "+(rule.ai+1):rule.kind.equals("horizontal")?"Poziomo H · "+(rule.ai+1)+"–"+(rule.bi+1)
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
                try{double value=parseCoordinate(first);first.setText(String.format(java.util.Locale.US,"%.3f",checked?value*2:value/2));}
                catch(NumberFormatException ignored){}
                firstLabel.setText(checked?"Średnica Ø [mm]":"Promień R [mm]");
            });
        }
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
                    SketchPrimitive changed=SketchDimensions.apply(entity,a,circle?0:parseCoordinate(second),lock.isChecked());
                    replaceSketchEntity(entity,changed);
                }catch(IllegalArgumentException error){operationMessage=error.getMessage();invalidate();}
            }).show();
    }

    private EditText coordinateInput(String hint, double value) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
            | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(java.util.Locale.US, "%.3f", value));
        input.setSelectAllOnFocus(true);
        return input;
    }

    private double parseCoordinate(EditText input) {
        return Double.parseDouble(input.getText().toString().replace(',', '.'));
    }

    private void replaceSketchEntity(SketchPrimitive oldEntity, SketchPrimitive changed) {
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
        if(changed.vertices!=null) {
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
        if(!SketchTransactions.active(project))for(SketchPrimitive changed:solved) {
            double[] coordinates={changed.x1,changed.y1,changed.x2,changed.y2};
            if(changed.id.equals(project.featureBaseProfileId)&&project.featureBase!=null) {
                if(Revolve.is(project.featureBase))project.featureBase=Revolve.withProfile(project.featureBase,changed);
                else {
                project.featureBase=project.featureBase.clone();
                System.arraycopy(coordinates,0,project.featureBase,1,4);
                if(changed.vertices!=null||changed.curves!=null)project.featureBase=changed.appendVertices(java.util.Arrays.copyOf(project.featureBase,7));
                }
            }
            for(int i=0;i<project.committedProfileIds.size();i++)if(changed.id.equals(project.committedProfileIds.get(i))) {
                if(Revolve.is(project.committedFeatures.get(i))){project.committedFeatures.set(i,Revolve.withProfile(project.committedFeatures.get(i),changed));continue;}
                double[] updated=project.committedFeatures.get(i).clone();
                System.arraycopy(coordinates,0,updated,2,4);
                if(changed.vertices!=null||changed.curves!=null)updated=changed.appendVertices(java.util.Arrays.copyOf(updated,16));
                project.committedFeatures.set(i,updated);
            }
        }
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
        projectedTriangles.clear();
        depthSurfaces.clear();
        double[] displayedMesh=featurePreview==null?nativeMesh:featurePreview;
        double yaw = Math.toRadians(cameraYaw);
        double pitch = Math.toRadians(cameraPitch);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch), spitch = Math.sin(pitch);
        float scale = dp(3.2f) * zoom;
        for (int offset = 0; offset + 12 < displayedMesh.length; offset += 13) {
            ProjectedTriangle triangle = new ProjectedTriangle();
            triangle.faceId = (int)Math.round(displayedMesh[offset + 12]) - 1;
            triangle.nx = displayedMesh[offset + 9];
            triangle.ny = displayedMesh[offset + 10];
            triangle.nz = displayedMesh[offset + 11];
            double normalRz = -triangle.nx * syaw + triangle.nz * cyaw;
            double cameraNormalZ = triangle.ny * spitch + normalRz * cpitch;
            if (cameraNormalZ <= 1.0e-7) continue;
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
                double perspective = 420.0 / (420.0 - finalZ);
                triangle.q[vertex]=1.0/(420.0-finalZ);
                triangle.sx[vertex] = cx + (float)(rx * scale * perspective);
                triangle.sy[vertex] = cy - (float)(ry * scale * perspective);
                triangle.depth += (float)finalZ / 3f;
            }
            projectedTriangles.add(triangle);
            triangle.surface=new HiddenLineVisibility.Triangle(
                new double[]{triangle.sx[0],triangle.sx[1],triangle.sx[2]},
                new double[]{triangle.sy[0],triangle.sy[1],triangle.sy[2]},triangle.q);
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
        double perspective = 420.0 / (420.0 - finalZ);
        point.sx = cx + (float)(rx * scale * perspective);
        point.sy = cy - (float)(ry * scale * perspective);
    }

    private int meshFaceColor(ProjectedTriangle triangle) {
        int shade = 207 + (int)Math.round(22.0 * Math.abs(triangle.nz)
            + 9.0 * Math.abs(triangle.ny));
        return Color.rgb(Math.min(238, shade), Math.min(246, shade + 18), Math.min(244, shade + 16));
    }

    private int findFaceAt(float x, float y) {
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
        int next = (edge + 1) % 3;
        for (ProjectedTriangle candidate : projectedTriangles) {
            if (candidate == source || candidate.faceId != source.faceId) continue;
            for (int otherEdge = 0; otherEdge < 3; otherEdge++) {
                int otherNext = (otherEdge + 1) % 3;
                if ((samePoint(source, edge, candidate, otherEdge)
                        && samePoint(source, next, candidate, otherNext))
                        || (samePoint(source, edge, candidate, otherNext)
                        && samePoint(source, next, candidate, otherEdge))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean samePoint(ProjectedTriangle a, int ai, ProjectedTriangle b, int bi) {
        return Math.abs(a.wx[ai] - b.wx[bi]) < 1.0e-7
            && Math.abs(a.wy[ai] - b.wy[bi]) < 1.0e-7
            && Math.abs(a.wz[ai] - b.wz[bi]) < 1.0e-7;
    }

    private void chooseSketchPlane(String plane) {
        boolean newSketch = !plane.equals(project.sketchPlane)
            || (!sketchPlaneChosen && !project.sketch.isEmpty());
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
            project.sketchId = UUID.randomUUID().toString();
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
        project.sketchOriginX = selectedPointX;
        project.sketchOriginY = selectedPointY;
        project.sketchOriginZ = selectedPointZ;
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
            if (profile != null) showFaceFeatureDepth(profile, project.featureOperation);
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
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(java.util.Locale.US, "%.2f", modelDepth));
        input.setSelectAllOnFocus(true);
        new PropertyBuilder()
            .setTitle("Głębokość wyciągnięcia [mm]")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = Double.parseDouble(input.getText().toString().replace(',', '.'));
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
        return NativeCadEngine.evaluateFeatures(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
            evaluationBase(),packed,null,project.importedStep?importedSourceFile().getAbsolutePath():null);
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
        double[] base=root?f:evaluationBase(),packed=packedFeatures();
        if(!root) {
            if(index<0){int offset=packed.length;packed=java.util.Arrays.copyOf(packed,offset+f.length);System.arraycopy(f,0,packed,offset,f.length);}
            else {int offset=0;for(int i=0;i<index;i++)offset+=project.committedFeatures.get(i).length;System.arraycopy(f,0,packed,offset,f.length);}
        }
        return NativeCadEngine.evaluateFeatures(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
            base,packed,null,project.importedStep?importedSourceFile().getAbsolutePath():null);
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
                    double[] f=candidate.get();if(evaluateRevolve(f,index,root).length==0)throw new IllegalArgumentException("Nie można zbudować obrotu lub odtworzyć dalszych operacji");
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
            if(propertyEditor!=owner)return;featurePreview=null;
            try {
                double[] mesh=evaluateRevolve(candidate.get(),index,root);
                if(mesh.length==0)status.setText("Brak poprawnej bryły. Sprawdź oś, profil i przecięcie z modelem. Dokument pozostaje bez zmian.");
                else {featurePreview=mesh;status.setText("Podgląd obrotu. Zastosuj zapisuje edytowalną operację w historii.");}
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}invalidate();
        };
        angle.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}
            public void onTextChanged(CharSequence s,int a,int b,int c){removeCallbacks(preview);postDelayed(preview,180);}public void afterTextChanged(android.text.Editable s){} });
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
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(radius);form.addView(status);
        new PropertyBuilder().setTitle(chamfer?(index<0?"Fazuj · wskaż krawędź":"Fazowanie · równe odległości [mm]"):(index<0?"Zaokrąglij · wskaż krawędź":"Zaokrąglenie · promień [mm]")).setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    if(candidate[0]==null)throw new IllegalArgumentException("Najpierw dotknij widocznej krawędzi bryły");
                    double[] value=candidate[0].clone();value[11]=parseCoordinate(radius);
                    if(chamfer)EdgeChamfer.validate(value);else EdgeFillet.validate(value);
                    if(evaluateFillet(value,index).length==0)throw new IllegalArgumentException("Nie można wykonać operacji na tej krawędzi z podaną wartością lub przebudować kolejnych cech");
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
            featurePreview=null;
            if(index<0&&filletSegment>=0)candidate[0]=RootEdgeReference.anchor(
                chamfer?EdgeChamfer.create(filletEdges,filletSegment,3):EdgeFillet.create(filletEdges,filletSegment,3),rootReference,rootEdges);
            try {
                if(candidate[0]==null){status.setText("Dotknij widocznej krawędzi modelu. Przeciągnięcie obraca model.");invalidate();return;}
                candidate[0][11]=parseCoordinate(radius);
                if(chamfer)EdgeChamfer.validate(candidate[0]);else EdgeFillet.validate(candidate[0]);
                double[] mesh=evaluateFillet(candidate[0],index);
                if(mesh.length==0)status.setText("Brak poprawnego wyniku. Zmniejsz wartość lub wskaż inną krawędź. Model nie został zmieniony.");
                else {featurePreview=mesh;status.setText("Podgląd "+(chamfer?"fazy ":"R")+formatMm(candidate[0][11])+". "+(chamfer?"Równe odsunięcia na obu ścianach. ":"")+"Zastosuj zapisuje operację w historii.");}
            }catch(IllegalArgumentException e){status.setText(chamfer?"Wielkość fazy: 0,1–1000 mm":"Promień: 0,1–1000 mm");}
            invalidate();
        };
        if(index<0) {
            filletEdges=NativeCadEngine.modelEdges(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
                evaluationBase(),packedFeatures(),project.importedStep?importedSourceFile().getAbsolutePath():null);
            filletSelectionChanged=preview;
            if(filletEdges.length==0)status.setText("Nie można odczytać krawędzi bryły");
        }
        radius.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){removeCallbacks(preview);postDelayed(preview,180);}
            public void afterTextChanged(android.text.Editable s){}
        });
        preview.run();
    }

    private double[] projectWorldPoint(double x,double y,double z) {
        double yaw=Math.toRadians(cameraYaw),pitch=Math.toRadians(cameraPitch);
        x-=meshCenterX;y-=meshCenterY;z-=meshCenterZ;
        double rx=x*Math.cos(yaw)+z*Math.sin(yaw),rz=-x*Math.sin(yaw)+z*Math.cos(yaw);
        double ry=y*Math.cos(pitch)-rz*Math.sin(pitch),q=1/(420-y*Math.sin(pitch)-rz*Math.cos(pitch));
        return new double[]{workCenterX+rx*420*q*dp(3.2f)*zoom,workCenterY-ry*420*q*dp(3.2f)*zoom,q};
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
        if(selected<0){operationMessage="Dotknij bliżej widocznej krawędzi";invalidate();return;}
        filletSegment=selected;filletSelectionChanged.run();
    }

    private void drawFilletSelection(Canvas canvas) {
        if(filletSegment<0||filletSegment+15>=filletEdges.length)return;
        double id=filletEdges[filletSegment+15];paint.setColor(Color.rgb(235,130,15));paint.setStrokeWidth(dp(3));
        for(int i=0;i+15<filletEdges.length;i+=16)if(filletEdges[i+15]==id) {
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
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(java.util.Locale.US, "%.2f",
            project.filletRadius > 0.0 ? project.filletRadius : 3.0));
        input.setSelectAllOnFocus(true);
        new PropertyBuilder()
            .setTitle("Promień zaokrąglenia wszystkich krawędzi [mm]")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = Double.parseDouble(input.getText().toString().replace(',', '.'));
                    if (value < 0.1 || value > 1000.0) {
                        operationMessage = "Promień musi mieścić się w zakresie 0,1–1000 mm";
                        invalidate();
                        return;
                    }
                    if (commitModelChange(() -> project.filletRadius = value)) {
                        operationMessage = String.format(java.util.Locale.US,
                            "Gotowe: zaokrąglenie R%.2f mm", value);
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
        measureEdges=NativeCadEngine.modelEdges(modelWidth,modelHeight,modelDepth,radius,evaluationBase(),packedFeatures(),source);
        measureProperties=NativeCadEngine.edgeMeasurements(modelWidth,modelHeight,modelDepth,radius,evaluationBase(),packedFeatures(),source);
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

    private String formatMm(double value) {
        return String.format(java.util.Locale.US, "%.3f mm", value);
    }

    private void drawTechnicalViews(Canvas canvas, float left, float top, float right, float bottom) {
        if(technicalSheet!=null) technicalSheet.draw(canvas,
            new RectF(left+dp(10),top+dp(10),right-dp(10),bottom-dp(10)),project);
        else text(canvas,"Nie można utworzyć arkusza z bieżącego modelu",left+dp(20),top+dp(50),14,INK,true);
    }

    private void showBodyProperties() {
        if(!project.hasSolid()||project.importedMeshSource!=null){operationMessage="Dokładne właściwości wymagają bryły B-Rep, nie siatki STL";invalidate();return;}
        double[] properties=NativeCadEngine.bodyProperties(modelWidth,modelHeight,modelDepth,project.importedStep?0:project.filletRadius,
            evaluationBase(),packedFeatures(),project.importedStep?importedSourceFile().getAbsolutePath():null);
        if(properties.length!=7){operationMessage="Nie można obliczyć właściwości aktualnej bryły";invalidate();return;}
        android.widget.TextView values=new android.widget.TextView(getContext());values.setTextSize(16);
        values.setText(String.format(java.util.Locale.US,
            "Objętość: %.6f mm³\n\nPole powierzchni: %.6f mm²\n\nŚrodek objętości [mm]\nX: %.6f\nY: %.6f\nZ: %.6f\n\nBryły: %.0f\nŚciany: %.0f\n\nWynik z geometrii B-Rep OCCT, nie z siatki podglądu. Środek objętości odpowiada środkowi masy przy jednorodnej gęstości.",
            properties[0],properties[1],properties[2],properties[3],properties[4],properties[5],properties[6]));
        new PropertyBuilder().setTitle("Właściwości bryły · OCCT").setView(values).setNegativeButton("Zamknij",null).show();
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
        return x >= dp(224) && x <= getWidth() - dp(252)
            && y >= dp(110) && y <= getHeight() - dp(84) - bottomSystemInset;
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
            candidateMesh=new double[0];
        } else if (project.importedMeshSource != null) {
            try (FileInputStream input = new FileInputStream(new File(
                    projectRepository.importedStepFile().getParentFile(),project.importedMeshSource))) {
                candidateMesh = CadExchange.readStl(input);
            } catch (IOException error) { operationMessage = error.getMessage(); return false; }
        } else if (project.importedStep) {
            candidateMesh = NativeCadEngine.evaluateFeatures(modelWidth, modelHeight, modelDepth,
                0, null, packedFeatures(), null, importedSourceFile().getAbsolutePath());
        } else if (project.featureBase != null || !project.committedFeatures.isEmpty()
                || (!project.featureOperation.equals("none") && profile != null)) {
            candidateMesh = NativeCadEngine.evaluateFeatures(modelWidth, modelHeight, modelDepth,
                project.filletRadius, project.featureBase, packedFeatures(), null);
        } else if (project.sketchExtruded && profile != null) {
            candidateMesh = project.filletRadius > 0.0
                ? NativeCadEngine.filletSketchExtrusion(profile.type, profile.x1, profile.y1,
                    profile.x2, profile.y2, modelDepth, project.sketchPlane, project.filletRadius)
                : NativeCadEngine.tessellateSketchExtrusion(profile.type, profile.x1,
                    profile.y1, profile.x2, profile.y2, modelDepth, project.sketchPlane);
        } else {
            candidateMesh = project.filletRadius > 0.0
                ? NativeCadEngine.filletPrism(modelWidth, modelHeight, modelDepth, project.filletRadius)
                : NativeCadEngine.tessellatePrism(modelWidth, modelHeight, modelDepth);
        }
        if (!CadMesh.isValid(candidateMesh, !project.hasSolid())) {
            operationMessage = "Nie można przebudować bryły. Poprzednia geometria została zachowana.";
            return false;
        }
        double[] candidatePrism = NativeCadEngine.createPrism(modelWidth, modelHeight, modelDepth);
        nativeMesh = candidateMesh;
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
        return true;
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
            FeatureDirection.featureCode(project.featureOperation.equals("cut"),project.featureSymmetric), p.x1, p.y1, p.x2, p.y2,
            project.featureDepth, project.sketchOriginX, project.sketchOriginY,
            project.sketchOriginZ, project.sketchUx, project.sketchUy, project.sketchUz,
            project.sketchVx, project.sketchVy, project.sketchVz});
    }

    private void commitActiveFeature() {
        double[] active = activeFeatureValues();
        if (active != null) {
            project.committedFeatures.add(active);
            project.committedProfileIds.add(project.featureProfileId == null ? "" : project.featureProfileId);
        }
        project.featureOperation = "none";
        project.featureSymmetric=false;
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
        if(Revolve.is(feature)){String id=project.committedProfileIds.get(index);showRevolveEditor(Revolve.profile(id,feature),feature,index,false);return;}
        if(EdgeFillet.is(feature)||EdgeChamfer.is(feature)){showEdgeFilletEditor(index);return;}
        String linkedProfileId=index<project.committedProfileIds.size()?project.committedProfileIds.get(index):null;
        if(feature[0]>=5 || project.isProfileDimensioned(linkedProfileId)) {
            EditText depth=coordinateInput("Głębokość [mm]",Math.abs(feature[6]));
            android.widget.CheckBox reverse=directionCheck(feature[6]);
            android.widget.CheckBox symmetric=symmetricCheck(FeatureDirection.isSymmetric(feature[1]),reverse);
            LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);form.addView(depth);form.addView(reverse);
            form.addView(symmetric);
            android.widget.TextView status=new android.widget.TextView(getContext());form.addView(status);
            new PropertyBuilder().setTitle((feature[0]>=5?"Kontur":"Profil z wymiarami")+" · głębokość [mm]").setView(form)
                .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                    try {
                        double value=FeatureDirection.signed(parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                        double[] candidate=feature.clone();candidate[6]=value;
                        candidate[1]=FeatureDirection.featureCode(FeatureDirection.isCut(feature[1]),symmetric.isChecked());
                        if(commitModelChange(() -> project.committedFeatures.set(index,candidate)))
                            operationMessage="Gotowe: przeliczono operację konturu";
                    }catch(IllegalArgumentException e){operationMessage="Głębokość: 0,1–5000 mm";}
                    invalidate();
                }).show();
            bindCommittedPreview(index,new EditText[]{depth},reverse,symmetric,status,()-> {
                double[] candidate=feature.clone();candidate[6]=FeatureDirection.signed(parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                candidate[1]=FeatureDirection.featureCode(FeatureDirection.isCut(feature[1]),symmetric.isChecked());return candidate;
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
            label.setText(labels[i]);
            label.setTextColor(MUTED);
            label.setTextSize(12);
            fields.addView(label);
            inputs[i] = coordinateInput(labels[i], i==4?Math.abs(feature[6]):feature[i + 2]);
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
                    candidate[1]=FeatureDirection.featureCode(FeatureDirection.isCut(feature[1]),symmetric.isChecked());
                    if (!valid) {
                        operationMessage = "Współrzędne: ±5000 mm, głębokość: 0,1–5000 mm";
                    } else {
                        if (commitModelChange(() -> {
                            project.committedFeatures.set(index, candidate);
                            String profileId = index < project.committedProfileIds.size()
                                ? project.committedProfileIds.get(index) : "";
                            for (int i = 0; i < project.sketch.size(); i++) {
                                SketchPrimitive p = project.sketch.get(i);
                                if (p.id.equals(profileId)) project.sketch.set(i,
                                    new SketchPrimitive(p.id, p.type, candidate[2], candidate[3],
                                        candidate[4], candidate[5]));
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
            candidate[1]=FeatureDirection.featureCode(FeatureDirection.isCut(feature[1]),symmetric.isChecked());return candidate;
        });
    }

    private void bindCommittedPreview(int index,EditText[] inputs,android.widget.CheckBox reverse,android.widget.CheckBox symmetric,
            android.widget.TextView status,java.util.function.Supplier<double[]> proposed) {
        android.widget.ScrollView owner=propertyEditor;
        Runnable preview=()-> {
            if(owner==null||propertyEditor!=owner)return;
            featurePreview=null;
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
                double[] mesh=NativeCadEngine.evaluateFeatures(modelWidth,modelHeight,modelDepth,project.filletRadius,
                    project.featureBase,packed,null,project.importedStep?importedSourceFile().getAbsolutePath():null);
                if(mesh.length>0){featurePreview=mesh;status.setText("Podgląd · "+Math.abs(candidate[6])+" mm"+(FeatureDirection.isSymmetric(candidate[1])?" · symetrycznie":candidate[6]<0?" · odwrotnie":"")+". Zastosuj zapisuje zmianę.");}
                else status.setText("Brak poprawnego wyniku. Zmień głębokość lub kierunek.");
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            invalidate();
        };
        for(EditText input:inputs)input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){removeCallbacks(preview);postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
    }

    private void extrudeSketchProfile() {
        if(project.importedMeshSource != null) {
            operationMessage = "STL jest siatką — operacje bryłowe wymagają STEP";
            invalidate(); return;
        }
        if (selectedSketchId == null) {
            List<SketchPrimitive> profiles = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (SketchPrimitive p : project.sketch) {
                if (!p.closedProfile()) continue;
                profiles.add(p);
                String size = p.type.equals("Okrąg")
                    ? String.format(java.util.Locale.US, "R %.2f mm", Math.hypot(p.x2-p.x1,p.y2-p.y1))
                    : String.format(java.util.Locale.US, "%.2f × %.2f mm", Math.abs(p.x2-p.x1),Math.abs(p.y2-p.y1));
                labels.add("Profil " + profiles.size() + " — " + p.type + " · " + size);
            }
            if (profiles.size() > 1) {
                new PropertyBuilder().setTitle("Wybierz profil do wyciągnięcia")
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        selectedSketchId = profiles.get(which).id;
                        invalidate();
                        extrudeSketchProfile();
                    }).setNegativeButton("Anuluj", null).show();
                return;
            }
        }
        SketchPrimitive profile = selectedClosedProfile();
        if (profile == null) {
            operationMessage = "Najpierw narysuj lub zaznacz zamknięty profil";
        } else if (project.importedStep || project.featureBase != null || !project.committedFeatures.isEmpty()||!project.featureOperation.equals("none")
                || !(project.sketchPlane.equals("XY") || project.sketchPlane.equals("XZ")
                || project.sketchPlane.equals("YZ"))) {
            showFaceFeatureDialog(profile);
            return;
        } else {
            showBaseExtrusionEditor(profile.appendVertices(new double[]{profile.featureType(),profile.x1,profile.y1,
                profile.x2,profile.y2,project.sketchPlane.equals("XZ")?1:project.sketchPlane.equals("YZ")?2:0,modelDepth}),profile.id);
            return;
        }
        invalidate();
    }

    private void showFaceFeatureDialog(SketchPrimitive profile) {
        String[] operations = {"Dodaj materiał", "Wytnij materiał"};
        new PropertyBuilder()
            .setTitle("Operacja na wybranym szkicu")
            .setItems(operations, (dialog, which) -> showFaceFeatureDepth(profile,
                which == 0 ? "add" : "cut"))
            .setNegativeButton("Anuluj", null)
            .show();
    }

    private void showFaceFeatureDepth(SketchPrimitive profile, String operation) {
        if(project.importedMeshSource!=null) {
            operationMessage="Operacje bryłowe wymagają B-Rep, nie siatki STL";invalidate();return;
        }
        selectedSketchId=profile.id;
        double initialDepth=operation.equals(project.featureOperation)&&profile.id.equals(project.featureProfileId)
            ?project.featureDepth:Math.abs(project.featureDepth);
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(java.util.Locale.US, "%.2f", Math.abs(project.featureDepth)));
        input.setSelectAllOnFocus(true);
        LinearLayout form=new LinearLayout(getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(input);
        android.widget.CheckBox reverse=directionCheck(initialDepth);form.addView(reverse);
        android.widget.CheckBox symmetric=symmetricCheck(operation.equals(project.featureOperation)&&profile.id.equals(project.featureProfileId)&&project.featureSymmetric,reverse);form.addView(symmetric);
        android.widget.TextView previewStatus=new android.widget.TextView(getContext());
        previewStatus.setText("Podgląd nie zmienia dokumentu. Zastosuj zapisuje operację.");form.addView(previewStatus);
        new PropertyBuilder()
            .setTitle((operation.equals("cut") ? "Głębokość wycięcia" : "Wysokość dodania") + " [mm]")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = Double.parseDouble(input.getText().toString().replace(',', '.'));
                    if (!Double.isFinite(value) || value < 0.1 || value > 5000.0) {
                        operationMessage = "Głębokość musi mieścić się w zakresie 0,1–5000 mm";
                        invalidate();
                        return;
                    }
                    final double signedDepth=FeatureDirection.signed(value,reverse.isChecked(),symmetric.isChecked());
                    if (commitModelChange(() -> {
                        project.featureOperation = operation;
                        project.featureProfileId = profile.id;
                        project.featureDepth = signedDepth;
                        project.featureSymmetric=symmetric.isChecked();
                        project.sketchExtruded = true;
                    })) {
                        treeSelection = "Model";
                        mode = "Wybierz";
                        operationMessage = operation.equals("cut")
                            ? "Gotowe: materiał wycięty" : "Gotowe: materiał dodany";
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
            featurePreview=null;
            try {
                double depth=parseCoordinate(input);
                if(!Double.isFinite(depth)||depth<.1||depth>5000) throw new NumberFormatException();
                depth=FeatureDirection.signed(depth,reverse.isChecked(),symmetric.isChecked());
                double[] candidate=profile.appendVertices(new double[]{profile.featureType(),FeatureDirection.featureCode(operation.equals("cut"),symmetric.isChecked()),
                    profile.x1,profile.y1,profile.x2,profile.y2,depth,
                    project.sketchOriginX,project.sketchOriginY,project.sketchOriginZ,
                    project.sketchUx,project.sketchUy,project.sketchUz,project.sketchVx,project.sketchVy,project.sketchVz});
                int length=candidate.length;for(double[] feature:project.committedFeatures) length+=feature.length;
                double[] values=new double[length];int offset=0;
                for(double[] feature:project.committedFeatures) {System.arraycopy(feature,0,values,offset,feature.length);offset+=feature.length;}
                System.arraycopy(candidate,0,values,offset,candidate.length);
                double[] mesh=NativeCadEngine.evaluateFeatures(modelWidth,modelHeight,modelDepth,
                    project.filletRadius,project.featureBase,values,null,
                    project.importedStep?importedSourceFile().getAbsolutePath():null);
                if(mesh.length>0) {featurePreview=mesh;previewStatus.setText("Podgląd · "+Math.abs(depth)+" mm"+(symmetric.isChecked()?" · symetrycznie":depth<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");}
                else previewStatus.setText("Brak poprawnego wyniku. Zmień głębokość lub profil.");
            } catch(NumberFormatException error) {previewStatus.setText("Głębokość: 0,1–5000 mm");}
            invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                removeCallbacks(preview);postDelayed(preview,350);
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
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
            featurePreview=null;
            try {
                double signed=FeatureDirection.signed(parseCoordinate(input),reverse.isChecked(),symmetric.isChecked());
                double[] candidate=base.clone();candidate[6]=signed;
                candidate[5]=FeatureDirection.baseCode(base[5],symmetric.isChecked());
                double[] mesh=NativeCadEngine.evaluateFeatures(modelWidth,modelHeight,Math.abs(signed),
                    initial?0:project.filletRadius,candidate,packedFeatures(),null);
                if(mesh.length>0){featurePreview=mesh;status.setText("Podgląd · "+Math.abs(signed)+" mm"+(symmetric.isChecked()?" · symetrycznie":signed<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");}
                else status.setText("Brak poprawnego wyniku. Zmień głębokość lub kierunek.");
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){removeCallbacks(preview);postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{removeCallbacks(preview);postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);removeCallbacks(preview);postDelayed(preview,100);});
        postDelayed(preview,100);
    }

    private void updateMeshCenter() {
        if (nativeMesh.length < 13) {
            meshCenterX = meshCenterY = meshCenterZ = 0.0;
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
    }

    private void showExportPicker() {
        String[] formats = {"STEP — precyzyjna bryła", "STL — siatka binarna", "STL — tekst ASCII", "DXF — szkic 2D", "PDF — arkusz techniczny A4"};
        new PropertyBuilder()
            .setTitle("Eksportuj projekt")
            .setItems(formats, (dialog, which) -> {
                if((which==0||which==4) && project.importedMeshSource != null) {
                    operationMessage="Eksport STEP wymaga bryły B-Rep; siatkę zapisz jako STL";
                    invalidate(); return;
                }
                if (fileRequestListener != null) {
                    fileRequestListener.requestExport(which == 0 ? "step" : which == 1 ? "stl" : which == 2 ? "stla" : which==3 ? "dxf" : "pdf");
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
                0, null, packedFeatures(), path,
                importedSourceFile().getAbsolutePath()).length > 0;
        }
        if (!project.importedStep && (project.featureBase != null || !project.committedFeatures.isEmpty()||!project.featureOperation.equals("none"))) {
            return NativeCadEngine.evaluateFeatures(modelWidth, modelHeight, modelDepth,
                project.filletRadius, project.featureBase, packedFeatures(), path).length > 0;
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
        project.committedFeatures.clear();
        project.committedProfileIds.clear();
        project.savedSketches.clear();
        project.sketchId = UUID.randomUUID().toString();
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
        String previousInfo = modelInfo, previousMode = mode;
        TechnicalSheet previousSheet = technicalSheet;
        double w = modelWidth, h = modelHeight, d = modelDepth;
        try {
            String snapshot = previous.toJson().toString();
            project = CadProject.fromJson(new org.json.JSONObject(snapshot));
            change.run();
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
            nativeMesh = previousMesh; nativePrism = previousPrism; modelInfo = previousInfo;
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
            nativeMesh = previousMesh; nativePrism = previousPrism; modelInfo = previousInfo;
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
        mode="Wybierz";arcDraft.clear();resumeSketchWork();
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
            project.sketchId = UUID.randomUUID().toString();
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
            if(primitive.curves!=null)SketchCurves.writeDxf(writer,primitive.curves);
            else if(primitive.vertices!=null) {
                writer.write("0\nLWPOLYLINE\n8\nSKETCH\n100\nAcDbEntity\n100\nAcDbPolyline\n90\n"+primitive.vertices.length/2+"\n70\n1\n");
                for(int i=0;i<primitive.vertices.length;i+=2)
                    writer.write("10\n"+primitive.vertices[i]+"\n20\n"+primitive.vertices[i+1]+"\n");
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

        Path path() {
            Path path = new Path();
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
        float top = getHeight() - dp(78) - bottomSystemInset;
        float left = dp(224);
        float right = getWidth() - dp(252);
        canvas.save();canvas.clipRect(left,top,right,getHeight());
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(left, top, right, getHeight(), paint);
        text(canvas, "↶", left + dp(30), top + dp(37), 23, undoStack.isEmpty() ? MUTED : Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "↷", left + dp(76), top + dp(37), 23, redoStack.isEmpty() ? MUTED : Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "HISTORIA MODELU", left + dp(106), top + dp(18), 9, Color.rgb(145, 160, 172), true);
        List<CadTimeline.Item> features=CadTimeline.entries(project);
        float startX = left + dp(116);
        float gap = dp(82);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(70, 86, 98));
        if (features.size() > 1) {
            canvas.drawLine(startX + dp(19), top + dp(43),
                startX + (features.size() - 1) * gap + dp(19), top + dp(43), paint);
        }
        for (int i = 0; i < features.size(); i++) {
            float x = startX + i * gap;
            paint.setColor(i == features.size() - 1 ? ACCENT : Color.rgb(55, 72, 85));
            canvas.drawRoundRect(new RectF(x, top + dp(24), x + dp(38), top + dp(62)), dp(7), dp(7), paint);
            drawFeatureIcon(canvas, features.get(i).title, x + dp(19), top + dp(43), Color.WHITE);
            text(canvas, features.get(i).title, x + dp(19), top + dp(73), 8,
                Color.rgb(205, 214, 221), false, Paint.Align.CENTER);
        }
        canvas.restore();
    }

    private boolean isToolActive(String tool) {
        if(tool.equals("Więzy"))return inSketch()&&coincidenceMode;
        if(tool.equals("Wymiar"))return mode.equals("Edytuj szkic")&&dimensionMode;
        if(tool.equals("Przytnij"))return mode.equals("Edytuj szkic")&&trimMode;
        if (tool.equals("Łuk") || tool.equals("Linia") || tool.equals("Polilinia") || tool.equals("Prostokąt") || tool.equals("Okrąg") || tool.equals("Pomocnicza")) {
            return mode.equals("Szkic") && sketchTool.equals(tool);
        }
        if (tool.equals("Edytuj")) return mode.equals("Edytuj szkic")&&!trimMode&&!dimensionMode&&!coincidenceMode;
        if (tool.equals("Zmierz")) return mode.equals("Zmierz");
        if (tool.equals("Rysunek")) return mode.equals("Rysunek");
        return tool.equals("Wybierz") && mode.equals("Wybierz")
            || tool.equals("Szkic") && mode.equals("Szkic") && !sketchPlaneChosen;
    }

    private void activateTopTool(String tool) {
        if(!arcDraft.isEmpty()&&!tool.equals("Łuk")){showArcDraftActions(tool);return;}
        if(!project.polylineDraft.isEmpty()) {showPolylineActions(tool);return;}
        if(inSketch()&&!SketchTransactions.active(project))beginSketchWork();
        boolean hadSketchPlane=inSketch() && sketchPlaneChosen;
        closePropertyEditor();
        trimMode=false;
        dimensionMode=false;
        coincidenceMode=false;coincidenceFirst=null;
        if(tool.equals("Więzy")) {
            mode="Edytuj szkic";showConstraintMenu();
            invalidate();return;
        }
        if(tool.equals("Geometria")) {
            mode="Edytuj szkic";showSketchGeometryTools();invalidate();return;
        }
        if(tool.equals("Lustro")){mode="Edytuj szkic";showSketchMirror();invalidate();return;}
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
                                project.committedFeatures.remove(index);
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
        if(tool.equals("Dopasuj") || tool.equals("Wyczyść widok")) {
            zoom=1;panX=panY=0;selectedFace=-1;selectedSketchId=null;measureA=measureB=null;
            measuredCurve=null;
            measuredEdgeText="";operationMessage="";
            if(tool.equals("Wyczyść widok")) {if(!SketchTransactions.active(project))mode="Wybierz";showSolid=true;}
            invalidate();return;
        }
        if(tool.equals("Ukryj bryłę")) {showSolid=!showSolid;invalidate();return;}
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
            mode = "Szkic";
            sketchPlaneChosen = false;
            sketchPlane = "Wybierz płaszczyznę";
            operationMessage = "Wybierz płaszczyznę lub ścianę modelu";
        } else if (tool.equals("Łuk") || tool.equals("Linia") || tool.equals("Polilinia") || tool.equals("Prostokąt") || tool.equals("Okrąg") || tool.equals("Pomocnicza")) {
            mode = "Szkic";
            sketchTool = tool;
            sketchPlaneChosen = hadSketchPlane;
            if (sketchPlaneChosen) alignToCurrentSketchPlane();
            if(tool.equals("Łuk"))operationMessage="Łuk: dotknij początku, końca, a następnie punktu pośredniego łuku.";
        } else if (tool.equals("Edytuj")) {
            if(inSketch()){mode="Edytuj szkic";sketchPlaneChosen=true;alignToCurrentSketchPlane();invalidate();}
            else showSavedSketchPicker();
        } else if (tool.equals("Wyciągnij")) {
            extrudeSketchProfile();
            mode = "Wybierz";
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

    private void showProjectMenu() {
        closePropertyEditor();
        new PropertyBuilder().setTitle("Projekt · "+project.name)
            .setItems(new String[]{"Nowy pusty projekt","Moje projekty","Dane i nazwa dokumentu"},(d,which)-> {
                if(which==0)showNewProject();else if(which==1)showProjectLibrary();else showDrawingMetadataEditor();
            }).setNegativeButton("Zamknij",null).show();
    }

    private void showNewProject() {
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setText("Nowy projekt");name.setSelectAllOnFocus(true);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
        new PropertyBuilder().setTitle("Nowy pusty projekt").setView(name)
            .setNegativeButton("Anuluj",null).setPositiveButton("Utwórz",(d,w)-> {
                String title=name.getText().toString().trim();
                if(title.isEmpty()){operationMessage="Podaj nazwę nowego projektu";invalidate();return;}
                try {
                    projectRepository.save(project);
                    CadProject next=CadProject.blank();next.name=title;activateProject(next);
                }catch(RuntimeException error){operationMessage="Nie można zachować projektu — dokument nie został zamknięty";invalidate();}
            }).show();
    }

    private void showProjectLibrary() {
        try {
            projectRepository.save(project);
            List<CadProject> projects=projectRepository.listProjects();List<String> labels=new ArrayList<>();
            for(CadProject p:projects)labels.add((p.id.equals(project.id)?"● ":"")+p.name+" · "+(p.importedStep?"STEP":p.importedMeshSource!=null?"STL":p.hasSolid()?"bryła":"szkice"));
            new PropertyBuilder().setTitle("Moje projekty · "+projects.size())
                .setItems(labels.toArray(new String[0]),(d,which)-> {
                    try {
                        projectRepository.save(project);
                        activateProject(projectRepository.loadProject(projects.get(which).id));
                    }catch(IOException|RuntimeException error){operationMessage="Nie można otworzyć projektu; bieżący dokument zachowano";invalidate();}
                }).setNegativeButton("Zamknij",null).show();
        }catch(RuntimeException error){operationMessage="Nie można zapisać dokumentu i odczytać listy projektów";invalidate();}
    }

    private void activateProject(CadProject next) {
        CadProject previous=project;String previousMode=mode;
        double[] previousMesh=nativeMesh,previousPrism=nativePrism;
        String previousInfo=modelInfo;TechnicalSheet previousSheet=technicalSheet;
        double w=modelWidth,h=modelHeight,d=modelDepth;
        try {
            project=next;mode="Wybierz";
            modelWidth=next.width;modelHeight=next.height;modelDepth=next.depth;
            if(!updateNativeModel())throw new IllegalStateException("Nie można odtworzyć geometrii lub źródła projektu");
            projectRepository.save(next);
        }catch(RuntimeException error) {
            project=previous;mode=previousMode;modelWidth=w;modelHeight=h;modelDepth=d;
            nativeMesh=previousMesh;nativePrism=previousPrism;modelInfo=previousInfo;technicalSheet=previousSheet;updateMeshCenter();
            operationMessage="Nie można otworzyć projektu. Bieżący dokument pozostaje aktywny.";invalidate();return;
        }
        closePropertyEditor();undoStack.clear();redoStack.clear();
        sketchPlane=next.sketchPlane;sketchPlaneChosen=false;
        sketching=false;sketchRect=null;draggedSketch=dragPreview=null;dragSolvedPreview=null;
        selectedFace=-1;selectedSketchId=null;measureA=measureB=null;measuredEdgeText="";
        coincidenceMode=trimMode=dimensionMode=false;coincidenceFirst=null;snapMarker=null;
        polylineTouch=false;polylinePreview=null;
        cameraYaw=-28;cameraPitch=22;zoom=1;panX=panY=0;showSolid=true;treeSelection="Model";
        operationMessage="Otwarto projekt: "+next.name;
        arcDraft.clear();resumeSketchWork();
        invalidate();
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
        if(!SketchTransactions.active(project)&&project.polylineDraft.isEmpty())return;
        beginSketchWork();mode=project.polylineDraft.isEmpty()?"Edytuj szkic":"Szkic";
        if(!project.polylineDraft.isEmpty())sketchTool="Polilinia";
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
        arcDraft.clear();polylinePreview=null;selectedSketchId=null;selectedFace=-1;
        trimMode=dimensionMode=coincidenceMode=false;mode="Wybierz";showSolid=true;
        closePropertyEditor();
        operationMessage="Anulowano edycję szkicu; przywrócono poprzednią geometrię";invalidate();
    }

    private void enterSketchEditor() {
        trimMode=false;
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
        if(showSolid&&SketchReadability.bodyInFront(nativeMesh,project.sketchOriginX,project.sketchOriginY,
                project.sketchOriginZ,cameraYaw,cameraPitch)) {
            showSolid=false;
            operationMessage+=". Bryłę przed płaszczyzną ukryto na czas edycji. Widok → Pokaż bryłę przywraca podgląd.";
        }
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
        if (feature.equals("Szkic")) drawToolIcon(canvas, "Szkic", x, y, color);
        else if (feature.startsWith("Wyciąg") || feature.equals("Dodanie") || feature.equals("Wycięcie")) {
            drawToolIcon(canvas, "Wyciągnij", x, y, color);
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
        text(canvas, "›", dp(198), top + dp(23), 15, MUTED, true, Paint.Align.CENTER);
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
