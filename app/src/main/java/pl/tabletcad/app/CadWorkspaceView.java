package pl.tabletcad.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
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
    private final List<String> actions = new ArrayList<>();

    private float density;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private float cameraYaw = -28f;
    private float cameraPitch = 22f;
    private String mode = "Wybierz";
    private final String kernelStatus;
    private double[] nativePrism;
    private double[] nativeMesh = new double[0];
    private String modelInfo;
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
    private final List<ProjectedTriangle> projectedTriangles = new ArrayList<>();
    private MeasurePoint measureA;
    private MeasurePoint measureB;
    private String measuredEdgeText = "";
    private float touchDownX;
    private float touchDownY;
    private float workCenterX;
    private float workCenterY;

    public CadWorkspaceView(Context context, FileRequestListener fileRequestListener) {
        super(context);
        this.fileRequestListener = fileRequestListener;
        density = getResources().getDisplayMetrics().density;
        projectRepository = new ProjectRepository(context);
        project = projectRepository.loadOrCreate();
        modelWidth = project.width;
        modelHeight = project.height;
        modelDepth = project.depth;
        sketchPlane = project.sketchPlane;
        projectRepository.save(project);
        kernelStatus = NativeCadEngine.status();
        updateNativeModel();
        // STEP is deliberately not exercised during Activity construction. A native
        // importer failure must never prevent the workspace from opening.
        stepStatus = "STEP: moduł zainstalowany";
        setBackgroundColor(BG);
        setFocusable(true);
        actions.add("Wybierz");
        actions.add("Szkic");
        actions.add("Modeluj");
        actions.add("Rysunek");
        actions.add("Zmierz");

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                float historyY = getHeight() - dp(178);
                if (e.getX() <= dp(96) && e.getY() >= historyY && e.getY() <= historyY + dp(55)) {
                    if (e.getX() < dp(52)) undo(); else redo();
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
                if (mode.equals("Szkic") && sketchPlaneChosen
                        && e.getY() >= dp(68) && e.getY() <= dp(126)
                        && e.getX() >= dp(240) && e.getX() <= getWidth() - dp(268)) {
                    float available = getWidth() - dp(508);
                    int tool = (int)((e.getX() - dp(240)) / (available / 3f));
                    if (tool == 0) sketchTool = "Linia";
                    else if (tool == 1) sketchTool = "Prostokąt";
                    else if (tool == 2) sketchTool = "Okrąg";
                    invalidate();
                    return true;
                }
                float barTop = getHeight() - dp(120);
                if (e.getY() >= barTop && e.getY() <= barTop + dp(72)) {
                    float usable = getWidth() - dp(40);
                    int index = (int) ((e.getX() - dp(20)) / (usable / actions.size()));
                    if (index >= 0 && index < actions.size()) {
                        mode = actions.get(index);
                        if (mode.equals("Szkic")) {
                            sketchPlaneChosen = false;
                            sketchPlane = "Wybierz płaszczyznę";
                        }
                        invalidate();
                    }
                } else if (mode.equals("Modeluj") && e.getX() > getWidth() - dp(234)) {
                    if (e.getY() >= dp(318) && e.getY() <= dp(370)) extrudeSketchProfile();
                    else if (e.getY() >= dp(382) && e.getY() <= dp(440)) showDepthEditor();
                    else if (e.getY() >= dp(446) && e.getY() <= dp(504)) showFilletEditor();
                } else if (mode.equals("Szkic") && e.getX() > getWidth() - dp(234)) {
                    if (e.getY() >= dp(322) && e.getY() <= dp(382)) chooseSketchPlane("XY");
                    else if (e.getY() >= dp(390) && e.getY() <= dp(450)) chooseSketchPlane("XZ");
                    else if (e.getY() >= dp(458) && e.getY() <= dp(518)) chooseSketchPlane("YZ");
                } else if (isInsideViewport(e.getX(), e.getY())) {
                    if (mode.equals("Zmierz")) {
                        handleMeasureTap(e.getX(), e.getY());
                        return true;
                    }
                    int face = findFaceAt(e.getX(), e.getY());
                    if (face >= 0) {
                        selectedFace = face;
                        if (mode.equals("Szkic") && !sketchPlaneChosen) {
                            chooseSketchPlane("Ściana " + (face + 1));
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
                        && (mode.equals("Wybierz") || mode.equals("Modeluj") || mode.equals("Zmierz"))) {
                    cameraYaw -= dx * 0.22f;
                    cameraPitch = Math.max(-89f, Math.min(89f, cameraPitch - dy * 0.22f));
                    invalidate();
                }
                return true;
            }
        });
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
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
        }
        if (mode.equals("Szkic") && sketchPlaneChosen
                && isInsideViewport(event.getX(), event.getY()) && event.getY() >= dp(152)) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                sketchStartX = event.getX();
                sketchStartY = event.getY();
                sketchRect = new RectF(sketchStartX, sketchStartY, sketchStartX, sketchStartY);
                sketching = true;
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && sketching) {
                sketchRect.set(Math.min(sketchStartX, event.getX()), Math.min(sketchStartY, event.getY()),
                    Math.max(sketchStartX, event.getX()), Math.max(sketchStartY, event.getY()));
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && sketching) {
                sketching = false;
                if (Math.hypot(event.getX() - sketchStartX, event.getY() - sketchStartY) > dp(4)) {
                    sketchEntitiesAdd(sketchTool, sketchStartX, sketchStartY,
                        event.getX(), event.getY());
                }
                sketchRect = null;
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
        float left = dp(224);
        float right = getWidth() - dp(252);
        float top = dp(64);
        float bottom = getHeight() - dp(132);
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
            drawNativeSolid(canvas, (left + right) / 2f + panX, (top + bottom) / 2f + panY);
            if (mode.equals("Szkic") && sketchPlaneChosen) drawSketch(canvas, left, right);
            if (mode.equals("Zmierz")) drawDimensions(canvas, left, top, right, bottom);
        }
        drawAxis(canvas, right - dp(70), bottom - dp(60));
        canvas.restore();
        drawBottomBar(canvas);
    }

    private void drawTopBar(Canvas canvas) {
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(0, 0, getWidth(), dp(56), paint);
        text(canvas, "TabletCAD", dp(22), dp(35), 20, Color.WHITE, true);
        text(canvas, "Bez nazwy  •  zapisano lokalnie", dp(145), dp(34), 14, Color.rgb(190, 201, 210), false);
        pill(canvas, getWidth() - dp(312), dp(10), dp(124), dp(38), "Otwórz STEP", Color.rgb(44, 58, 70), Color.WHITE);
        pill(canvas, getWidth() - dp(172), dp(10), dp(152), dp(38), "Eksportuj", ACCENT, Color.WHITE);
    }

    private void drawLeftPanel(Canvas canvas, float left, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(0, dp(56), left, getHeight(), paint);
        text(canvas, "MODEL", dp(18), dp(91), 12, MUTED, true);
        row(canvas, "▾  Dokument", dp(18), dp(126), true);
        row(canvas, "   ◇  Początek", dp(18), dp(164), false);
        row(canvas, "   ▱  Szkic 1", dp(18), dp(202), false);
        row(canvas, project.importedStep ? "   ◈  Import STEP"
            : (project.sketchExtruded ? "   ◈  Wyciągnięcie szkicu" : "   ◈  Wyciągnięcie 1"),
            dp(18), dp(240), true);
        if (project.filletRadius > 0.0) {
            row(canvas, String.format(java.util.Locale.US, "   ◒  Zaokrąglenie R%.2f", project.filletRadius),
                dp(18), dp(278), true);
        }
        paint.setColor(Color.rgb(232, 236, 239));
        canvas.drawRect(dp(18), bottom - dp(74), left - dp(18), bottom - dp(73), paint);
        text(canvas, "HISTORIA", dp(18), bottom - dp(48), 12, MUTED, true);
        text(canvas, "◀", dp(18), bottom - dp(17), 16, undoStack.isEmpty() ? MUTED : ACCENT, true);
        text(canvas, "▶", dp(58), bottom - dp(17), 16, redoStack.isEmpty() ? MUTED : ACCENT, true);
        text(canvas, "100%", dp(96), bottom - dp(17), 14, INK, false);
    }

    private void drawProperties(Canvas canvas, float right, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(right, dp(56), getWidth(), getHeight(), paint);
        text(canvas, "WŁAŚCIWOŚCI", right + dp(18), dp(91), 12, MUTED, true);
        text(canvas, mode.equals("Szkic") ? "Płaszczyzna szkicu"
            : (project.importedStep ? "Zaimportowany STEP"
            : (project.sketchExtruded ? "Wyciągnięcie szkicu" : "Wyciągnięcie 1")),
            right + dp(18), dp(127), 18, INK, true);
        labelValue(canvas, right, "Głębokość", String.format(java.util.Locale.US, "%.2f mm", modelDepth), dp(174));
        labelValue(canvas, right, "Operacja", "Nowa bryła", dp(238));
        if (mode.equals("Modeluj")) {
            pill(canvas, right + dp(18), dp(318), dp(202), dp(48), "Wyciągnij szkic", ACCENT, Color.WHITE);
            pill(canvas, right + dp(18), dp(382), dp(202), dp(48), "Wpisz głębokość", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(446), dp(202), dp(48), "Zaokrąglij krawędzie", Color.rgb(226, 232, 236), INK);
            text(canvas, selectedFace >= 0 ? "Wybrano: ściana " + (selectedFace + 1) : "Dotknij ściany lub krawędzi",
                right + dp(18), dp(526), 11, selectedFace >= 0 ? ACCENT : MUTED, selectedFace >= 0);
            if (!operationMessage.isEmpty()) text(canvas, operationMessage,
                right + dp(18), dp(554), 10, operationMessage.startsWith("Gotowe") ? ACCENT : Color.rgb(190, 70, 55), true);
        } else if (mode.equals("Szkic")) {
            text(canvas, sketchPlane, right + dp(18), dp(292), 12,
                sketchPlaneChosen ? ACCENT : MUTED, true);
            pill(canvas, right + dp(18), dp(322), dp(202), dp(52), "Płaszczyzna XY", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(390), dp(202), dp(52), "Płaszczyzna XZ", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(458), dp(202), dp(52), "Płaszczyzna YZ", Color.rgb(226, 232, 236), INK);
            text(canvas, "lub dotknij płaskiej ściany bryły", right + dp(18), dp(542), 10, MUTED, false);
        } else if (mode.equals("Zmierz")) {
            text(canvas, "Wybierz dwa wierzchołki", right + dp(18), dp(332), 11, MUTED, true);
            text(canvas, "lub punkty na krawędziach", right + dp(18), dp(352), 11, MUTED, false);
            if (measureA != null) labelValue(canvas, right, "Punkt A", measureA.kind, dp(382));
            if (measureA != null && measureB != null) {
                labelValue(canvas, right, "Odległość", formatMm(measureA.distanceTo(measureB)), dp(452));
            } else if (!measuredEdgeText.isEmpty()) {
                labelValue(canvas, right, "Krawędź", measuredEdgeText, dp(452));
            }
            SketchPrimitive profile = latestClosedProfile();
            if (profile != null && profile.type.equals("Okrąg")) {
                double radius = Math.hypot(profile.x2 - profile.x1, profile.y2 - profile.y1);
                text(canvas, "R = " + formatMm(radius), right + dp(18), dp(532), 12, ACCENT, true);
                text(canvas, "Ø = " + formatMm(radius * 2.0), right + dp(18), dp(556), 12, ACCENT, true);
            }
        } else {
            labelValue(canvas, right, "Tryb", mode, dp(302));
        }
        text(canvas, "RDZEŃ GEOMETRII", right + dp(18), bottom - dp(106), 11, MUTED, true);
        text(canvas, kernelStatus, right + dp(18), bottom - dp(82), 12,
            nativePrism.length == 24 ? ACCENT : Color.rgb(205, 65, 65), true);
        text(canvas, modelInfo, right + dp(18), bottom - dp(60), 10, MUTED, false);
        text(canvas, stepStatus, right + dp(18), bottom - dp(38), 10, MUTED, false);
        pill(canvas, right + dp(18), bottom + dp(2), dp(100), dp(38), "Zatwierdź", ACCENT, Color.WHITE);
        pill(canvas, right + dp(128), bottom + dp(2), dp(92), dp(38), "Anuluj", Color.rgb(226, 232, 236), INK);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        paint.setStrokeWidth(1f);
        paint.setColor(GRID);
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
                    canvas.drawLine(triangle.sx[edge], triangle.sy[edge],
                        triangle.sx[next], triangle.sy[next], paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        if (mode.equals("Szkic") && !sketchPlaneChosen) {
            text(canvas, "Wybierz płaszczyznę bazową lub dotknij ściany", cx, cy - dp(190),
                14, INK, true, Paint.Align.CENTER);
        }
    }

    private void drawSketch(Canvas canvas, float left, float right) {
        for (SketchPrimitive entity : project.sketch) drawSketchEntity(canvas, entity);
        if (sketchRect != null && sketching) {
            drawSketchPreview(canvas, sketchTool, sketchStartX, sketchStartY,
                sketchRect.right, sketchRect.bottom);
        }

        float toolbarLeft = dp(240);
        float toolbarRight = getWidth() - dp(268);
        float itemWidth = (toolbarRight - toolbarLeft) / 3f;
        String[] tools = {"Linia", "Prostokąt", "Okrąg"};
        for (int i = 0; i < tools.length; i++) {
            boolean active = tools[i].equals(sketchTool);
            pill(canvas, toolbarLeft + i * itemWidth + dp(4), dp(70), itemWidth - dp(8), dp(48),
                tools[i], active ? ACCENT : Color.rgb(226, 232, 236), active ? Color.WHITE : INK);
        }
        text(canvas, "Szkic na: " + sketchPlane, (left + right) / 2f, dp(144),
            12, ACCENT, true, Paint.Align.CENTER);
    }

    private void drawSketchEntity(Canvas canvas, SketchPrimitive entity) {
        float scale = dp(3.2f) * zoom;
        float x1 = workCenterX + (float)entity.x1 * scale;
        float y1 = workCenterY - (float)entity.y1 * scale;
        float x2 = workCenterX + (float)entity.x2 * scale;
        float y2 = workCenterY - (float)entity.y2 * scale;
        drawSketchPreview(canvas, entity.type, x1, y1, x2, y2);
    }

    private void drawSketchPreview(Canvas canvas, String type, float x1, float y1, float x2, float y2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(30, 105, 210));
        if (type.equals("Linia")) {
            canvas.drawLine(x1, y1, x2, y2, paint);
        } else if (type.equals("Okrąg")) {
            float radius = (float)Math.hypot(x2 - x1, y2 - y1);
            canvas.drawCircle(x1, y1, radius, paint);
        } else {
            canvas.drawRect(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void sketchEntitiesAdd(String type, float screenX1, float screenY1,
                                   float screenX2, float screenY2) {
        checkpoint();
        float scale = dp(3.2f) * zoom;
        double x1 = (screenX1 - workCenterX) / scale;
        double y1 = (workCenterY - screenY1) / scale;
        double x2 = (screenX2 - workCenterX) / scale;
        double y2 = (workCenterY - screenY2) / scale;
        project.sketch.add(new SketchPrimitive(UUID.randomUUID().toString(), type, x1, y1, x2, y2));
        project.sketchExtruded = false;
        projectRepository.save(project);
    }

    private void projectNativeMesh(float cx, float cy) {
        projectedTriangles.clear();
        double yaw = Math.toRadians(cameraYaw);
        double pitch = Math.toRadians(cameraPitch);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch), spitch = Math.sin(pitch);
        float scale = dp(3.2f) * zoom;
        for (int offset = 0; offset + 12 < nativeMesh.length; offset += 13) {
            ProjectedTriangle triangle = new ProjectedTriangle();
            triangle.faceId = (int)Math.round(nativeMesh[offset + 12]) - 1;
            triangle.nx = nativeMesh[offset + 9];
            triangle.ny = nativeMesh[offset + 10];
            triangle.nz = nativeMesh[offset + 11];
            double normalRz = -triangle.nx * syaw + triangle.nz * cyaw;
            double cameraNormalZ = triangle.ny * spitch + normalRz * cpitch;
            if (cameraNormalZ <= 1.0e-7) continue;
            for (int vertex = 0; vertex < 3; vertex++) {
                double x = nativeMesh[offset + vertex * 3] - meshCenterX;
                double y = nativeMesh[offset + vertex * 3 + 1] - meshCenterY;
                double z = nativeMesh[offset + vertex * 3 + 2] - meshCenterZ;
                triangle.wx[vertex] = x;
                triangle.wy[vertex] = y;
                triangle.wz[vertex] = z;
                double rx = x * cyaw + z * syaw;
                double rz = -x * syaw + z * cyaw;
                double ry = y * cpitch - rz * spitch;
                double finalZ = y * spitch + rz * cpitch;
                double perspective = 420.0 / (420.0 - finalZ);
                triangle.sx[vertex] = cx + (float)(rx * scale * perspective);
                triangle.sy[vertex] = cy - (float)(ry * scale * perspective);
                triangle.depth += (float)finalZ / 3f;
            }
            projectedTriangles.add(triangle);
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
        for (ProjectedTriangle triangle : projectedTriangles) {
            if (triangle.contains(x, y) && (best == null || triangle.depth > best.depth)) best = triangle;
        }
        if (best != null) {
            selectedNormalX = best.nx;
            selectedNormalY = best.ny;
            selectedNormalZ = best.nz;
            return best.faceId;
        }
        return -1;
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
        if (!plane.equals(project.sketchPlane) || !sketchPlaneChosen) checkpoint();
        sketchPlane = plane;
        sketchPlaneChosen = true;
        sketchRect = null;
        if (!plane.equals(project.sketchPlane)) project.sketch.clear();
        project.sketchPlane = plane;
        if (plane.equals("XY")) {
            cameraYaw = 0f; cameraPitch = 0f; selectedFace = -1;
        } else if (plane.equals("XZ")) {
            cameraYaw = 0f; cameraPitch = 89f; selectedFace = -1;
        } else if (plane.equals("YZ")) {
            cameraYaw = 90f; cameraPitch = 0f; selectedFace = -1;
        } else {
            double yaw = Math.atan2(-selectedNormalX, selectedNormalZ);
            double rz = -selectedNormalX * Math.sin(yaw) + selectedNormalZ * Math.cos(yaw);
            cameraYaw = (float)Math.toDegrees(yaw);
            cameraPitch = (float)Math.toDegrees(Math.atan2(selectedNormalY, rz));
        }
        projectRepository.save(project);
        invalidate();
    }

    private void showDepthEditor() {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(java.util.Locale.US, "%.2f", modelDepth));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(getContext())
            .setTitle("Głębokość wyciągnięcia [mm]")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = Double.parseDouble(input.getText().toString().replace(',', '.'));
                    if (value >= 0.1 && value <= 5000.0) {
                        checkpoint();
                        modelDepth = value;
                        project.depth = value;
                        updateNativeModel();
                        projectRepository.save(project);
                        invalidate();
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid input leaves the last valid parameter unchanged.
                }
            })
            .show();
    }

    private void showFilletEditor() {
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
        new AlertDialog.Builder(getContext())
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
                    checkpoint();
                    double oldRadius = project.filletRadius;
                    project.filletRadius = value;
                    updateNativeModel();
                    if (nativeMesh.length == 0) {
                        project.filletRadius = oldRadius;
                        updateNativeModel();
                        operationMessage = "Promień jest za duży dla tej bryły";
                    } else {
                        projectRepository.save(project);
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
        if (measureA == null) {
            text(canvas, "Dotknij wierzchołka lub krawędzi", (left + right) / 2f,
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
        MeasurePoint picked = nearestMeasurePoint(x, y);
        if (picked == null) {
            measuredEdgeText = "Brak krawędzi w zasięgu";
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
        invalidate();
    }

    private MeasurePoint nearestMeasurePoint(float x, float y) {
        MeasurePoint bestVertex = null;
        double bestVertexDistance = dp(15);
        for (ProjectedTriangle triangle : projectedTriangles) {
            for (int i = 0; i < 3; i++) {
                double screenDistance = Math.hypot(x - triangle.sx[i], y - triangle.sy[i]);
                if (screenDistance < bestVertexDistance) {
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
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float w = dp((float)Math.min(240.0, modelWidth * 2.0));
        float h = dp((float)Math.min(160.0, modelHeight * 2.0));
        float d = dp((float)Math.min(130.0, modelDepth * 2.0));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(INK);
        canvas.drawRect(cx - w - dp(40), cy - h - dp(30), cx - dp(40), cy - dp(30), paint);
        canvas.drawRect(cx + dp(40), cy - h - dp(30), cx + dp(40) + d, cy - dp(30), paint);
        canvas.drawRect(cx - w - dp(40), cy + dp(50), cx - dp(40), cy + dp(50) + d, paint);
        paint.setStyle(Paint.Style.FILL);
        text(canvas, "PRZÓD", cx - w / 2 - dp(40), cy - h - dp(45), 12, MUTED, true, Paint.Align.CENTER);
        text(canvas, "BOK", cx + dp(40) + d / 2, cy - h - dp(45), 12, MUTED, true, Paint.Align.CENTER);
        text(canvas, "GÓRA", cx - w / 2 - dp(40), cy + dp(50) + d + dp(24), 12, MUTED, true, Paint.Align.CENTER);
    }

    private boolean isInsideViewport(float x, float y) {
        return x >= dp(224) && x <= getWidth() - dp(252)
            && y >= dp(64) && y <= getHeight() - dp(132);
    }

    private void updateNativeModel() {
        nativePrism = NativeCadEngine.createPrism(modelWidth, modelHeight, modelDepth);
        SketchPrimitive profile = latestClosedProfile();
        if (project.importedStep && projectRepository.importedStepFile().isFile()) {
            nativeMesh = NativeCadEngine.tessellateStep(projectRepository.importedStepFile().getAbsolutePath());
        } else if (project.sketchExtruded && profile != null) {
            nativeMesh = project.filletRadius > 0.0
                ? NativeCadEngine.filletSketchExtrusion(profile.type, profile.x1, profile.y1,
                    profile.x2, profile.y2, modelDepth, project.sketchPlane, project.filletRadius)
                : NativeCadEngine.tessellateSketchExtrusion(profile.type, profile.x1,
                    profile.y1, profile.x2, profile.y2, modelDepth, project.sketchPlane);
        } else {
            nativeMesh = project.filletRadius > 0.0
                ? NativeCadEngine.filletPrism(modelWidth, modelHeight, modelDepth, project.filletRadius)
                : NativeCadEngine.tessellatePrism(modelWidth, modelHeight, modelDepth);
        }
        updateMeshCenter();
        modelInfo = project.importedStep ? "Zaimportowany i zweryfikowany model STEP"
            : NativeCadEngine.modelInfo(modelWidth, modelHeight, modelDepth);
    }

    private SketchPrimitive latestClosedProfile() {
        for (int i = project.sketch.size() - 1; i >= 0; i--) {
            SketchPrimitive primitive = project.sketch.get(i);
            if (primitive.type.equals("Prostokąt") || primitive.type.equals("Okrąg")) return primitive;
        }
        return null;
    }

    private void extrudeSketchProfile() {
        SketchPrimitive profile = latestClosedProfile();
        if (profile == null) {
            operationMessage = "Najpierw narysuj zamknięty profil";
        } else if (!(project.sketchPlane.equals("XY") || project.sketchPlane.equals("XZ")
                || project.sketchPlane.equals("YZ"))) {
            operationMessage = "Wyciąganie na ścianie: kolejny etap";
        } else {
            checkpoint();
            project.sketchExtruded = true;
            project.importedStep = false;
            project.filletRadius = 0.0;
            updateNativeModel();
            if (nativeMesh.length == 0) {
                project.sketchExtruded = false;
                operationMessage = "Błąd tworzenia bryły B-Rep";
            } else {
                operationMessage = "Gotowe: profil wyciągnięty";
                projectRepository.save(project);
            }
        }
        invalidate();
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
        String[] formats = {"STEP — precyzyjna bryła", "STL — siatka binarna", "DXF — szkic 2D"};
        new AlertDialog.Builder(getContext())
            .setTitle("Eksportuj projekt")
            .setItems(formats, (dialog, which) -> {
                if (fileRequestListener != null) {
                    fileRequestListener.requestExport(which == 0 ? "step" : which == 1 ? "stl" : "dxf");
                }
            })
            .setNegativeButton("Anuluj", null)
            .show();
    }

    public boolean exportStep(String path) {
        if (project.importedStep && projectRepository.importedStepFile().isFile()) {
            try (FileInputStream input = new FileInputStream(projectRepository.importedStepFile());
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
        SketchPrimitive profile = latestClosedProfile();
        String type = profile == null ? "Prostokąt" : profile.type;
        double x1 = profile == null ? 0.0 : profile.x1;
        double y1 = profile == null ? 0.0 : profile.y1;
        double x2 = profile == null ? modelWidth : profile.x2;
        double y2 = profile == null ? modelHeight : profile.y2;
        return NativeCadEngine.exportStep(path, modelWidth, modelHeight, modelDepth,
            project.sketchExtruded && profile != null, type, x1, y1, x2, y2, project.sketchPlane,
            project.filletRadius);
    }

    public File importedStepFile() { return projectRepository.importedStepFile(); }

    public boolean activateImportedStep() {
        double[] imported = NativeCadEngine.tessellateStep(projectRepository.importedStepFile().getAbsolutePath());
        if (imported.length == 0) return false;
        checkpoint();
        nativeMesh = imported;
        project.importedStep = true;
        project.sketchExtruded = false;
        project.filletRadius = 0.0;
        selectedFace = -1;
        operationMessage = "Gotowe: otwarto STEP";
        updateMeshCenter();
        modelInfo = "Zaimportowany i zweryfikowany model STEP";
        projectRepository.save(project);
        invalidate();
        return true;
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

    private void undo() {
        if (undoStack.isEmpty()) return;
        try {
            redoStack.push(project.toJson().toString());
            restoreSnapshot(undoStack.pop());
        } catch (Exception ignored) {
            operationMessage = "Nie udało się cofnąć operacji";
        }
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        try {
            undoStack.push(project.toJson().toString());
            restoreSnapshot(redoStack.pop());
        } catch (Exception ignored) {
            operationMessage = "Nie udało się ponowić operacji";
        }
    }

    private void restoreSnapshot(String snapshot) throws Exception {
        project = CadProject.fromJson(new org.json.JSONObject(snapshot));
        modelWidth = project.width;
        modelHeight = project.height;
        modelDepth = project.depth;
        sketchPlane = project.sketchPlane;
        sketchPlaneChosen = true;
        selectedFace = -1;
        updateNativeModel();
        projectRepository.save(project);
        operationMessage = "Gotowe: historia przywrócona";
        invalidate();
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

    public void writeDxf(OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.US_ASCII);
        writer.write("0\nSECTION\n2\nHEADER\n9\n$INSUNITS\n70\n4\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        for (SketchPrimitive primitive : project.sketch) {
            if (primitive.type.equals("Okrąg")) {
                double radius = Math.hypot(primitive.x2 - primitive.x1, primitive.y2 - primitive.y1);
                writer.write("0\nCIRCLE\n8\nSKETCH\n10\n" + primitive.x1 + "\n20\n" + primitive.y1
                    + "\n30\n0\n40\n" + radius + "\n");
            } else if (primitive.type.equals("Prostokąt")) {
                writeDxfLine(writer, primitive.x1, primitive.y1, primitive.x2, primitive.y1);
                writeDxfLine(writer, primitive.x2, primitive.y1, primitive.x2, primitive.y2);
                writeDxfLine(writer, primitive.x2, primitive.y2, primitive.x1, primitive.y2);
                writeDxfLine(writer, primitive.x1, primitive.y2, primitive.x1, primitive.y1);
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

    private void drawBottomBar(Canvas canvas) {
        float top = getHeight() - dp(120);
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(0, top, getWidth(), getHeight(), paint);
        float itemW = (getWidth() - dp(40)) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            float x = dp(20) + i * itemW;
            boolean selected = actions.get(i).equals(mode);
            if (selected) {
                paint.setColor(ACCENT);
                canvas.drawRoundRect(new RectF(x + dp(5), top + dp(10), x + itemW - dp(5), top + dp(61)), dp(12), dp(12), paint);
            }
            text(canvas, actions.get(i), x + itemW / 2, top + dp(43), 15,
                selected ? Color.WHITE : Color.rgb(205, 214, 221), true, Paint.Align.CENTER);
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
