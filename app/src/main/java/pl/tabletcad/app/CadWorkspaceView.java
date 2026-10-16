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
    private static final String[] TOOL_IDS = {
        "Wybierz", "Szkic", "Linia", "Prostokąt", "Okrąg", "Edytuj",
        "Wyciągnij", "Głębokość", "Zaokrąglij", "Zmierz", "Rysunek"
    };
    private static final String[] TOOL_LABELS = {
        "Wybierz", "Szkic", "Linia", "Prostokąt", "Okrąg", "Edytuj",
        "Wyciąg.", "Głęb.", "Zaokr.", "Pomiar", "Rysunek"
    };

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
    private String selectedSketchId;

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
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                float historyY = getHeight() - dp(96);
                if (e.getX() <= dp(104) && e.getY() >= historyY) {
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
                if (e.getY() >= dp(60) && e.getY() <= dp(126)
                        && e.getX() >= dp(224) && e.getX() <= getWidth() - dp(18)) {
                    float itemWidth = (getWidth() - dp(242)) / TOOL_IDS.length;
                    int tool = (int)((e.getX() - dp(224)) / itemWidth);
                    if (tool >= 0 && tool < TOOL_IDS.length) activateTopTool(TOOL_IDS[tool]);
                    return true;
                }
                if (e.getX() < dp(224) && e.getY() >= dp(224) && e.getY() <= dp(266)) {
                    mode = "Edytuj szkic";
                    sketchPlaneChosen = true;
                    selectedSketchId = null;
                    alignToCurrentSketchPlane();
                    invalidate();
                    return true;
                }
                if (mode.equals("Szkic") && e.getX() > getWidth() - dp(234)) {
                    if (e.getY() >= dp(374) && e.getY() <= dp(426)) chooseSketchPlane("XY");
                    else if (e.getY() >= dp(432) && e.getY() <= dp(484)) chooseSketchPlane("XZ");
                    else if (e.getY() >= dp(490) && e.getY() <= dp(542)) chooseSketchPlane("YZ");
                } else if (isInsideViewport(e.getX(), e.getY())) {
                    if (mode.equals("Edytuj szkic")) {
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
                        && (mode.equals("Wybierz") || mode.equals("Zmierz"))) {
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
        float top = dp(132);
        float bottom = getHeight() - dp(104);
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
            if (!project.sketch.isEmpty() || (mode.equals("Szkic") && sketchPlaneChosen)) {
                drawSketch(canvas, left, right);
            }
            if (mode.equals("Zmierz")) drawDimensions(canvas, left, top, right, bottom);
        }
        drawAxis(canvas, right - dp(70), bottom - dp(60));
        canvas.restore();
        drawTimeline(canvas);
    }

    private void drawTopBar(Canvas canvas) {
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(0, 0, getWidth(), dp(128), paint);
        text(canvas, "TabletCAD", dp(22), dp(35), 20, Color.WHITE, true);
        text(canvas, "Bez nazwy  •  zapisano lokalnie", dp(145), dp(34), 14, Color.rgb(190, 201, 210), false);
        pill(canvas, getWidth() - dp(312), dp(10), dp(124), dp(38), "Otwórz STEP", Color.rgb(44, 58, 70), Color.WHITE);
        pill(canvas, getWidth() - dp(172), dp(10), dp(152), dp(38), "Eksportuj", ACCENT, Color.WHITE);
        float itemWidth = (getWidth() - dp(242)) / TOOL_IDS.length;
        for (int i = 0; i < TOOL_IDS.length; i++) {
            float x = dp(224) + i * itemWidth;
            boolean active = isToolActive(TOOL_IDS[i]);
            drawToolButton(canvas, x + dp(2), dp(60), itemWidth - dp(4), dp(62),
                TOOL_IDS[i], TOOL_LABELS[i], active);
        }
    }

    private void drawLeftPanel(Canvas canvas, float left, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(0, dp(128), left, getHeight(), paint);
        text(canvas, "MODEL", dp(18), dp(155), 12, MUTED, true);
        row(canvas, "▾  Dokument", dp(18), dp(184), true);
        row(canvas, "   ◇  Początek", dp(18), dp(216), false);
        row(canvas, "   ▱  Szkic 1  (" + project.sketch.size() + ")", dp(18), dp(250),
            mode.equals("Szkic") || mode.equals("Edytuj szkic"));
        row(canvas, project.importedStep ? "   ◈  Import STEP"
            : (project.sketchExtruded ? "   ◈  Wyciągnięcie szkicu" : "   ◈  Wyciągnięcie 1"),
            dp(18), dp(284), true);
        if (project.filletRadius > 0.0) {
            row(canvas, String.format(java.util.Locale.US, "   ◒  Zaokrąglenie R%.2f", project.filletRadius),
                dp(18), dp(318), true);
        }
    }

    private void drawProperties(Canvas canvas, float right, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(right, dp(128), getWidth(), getHeight(), paint);
        text(canvas, "WŁAŚCIWOŚCI", right + dp(18), dp(155), 12, MUTED, true);
        text(canvas, mode.equals("Szkic") ? "Płaszczyzna szkicu"
            : (mode.equals("Edytuj szkic") ? "Edycja szkicu"
            : (project.importedStep ? "Zaimportowany STEP"
            : (project.sketchExtruded ? "Wyciągnięcie szkicu" : "Wyciągnięcie 1"))),
            right + dp(18), dp(191), 18, INK, true);
        labelValue(canvas, right, "Głębokość", String.format(java.util.Locale.US, "%.2f mm", modelDepth), dp(226));
        labelValue(canvas, right, "Operacja", "Nowa bryła", dp(290));
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
            text(canvas, "Wybierz dwa wierzchołki", right + dp(18), dp(374), 11, MUTED, true);
            text(canvas, "lub punkty na krawędziach", right + dp(18), dp(394), 11, MUTED, false);
            if (measureA != null) labelValue(canvas, right, "Punkt A", measureA.kind, dp(414));
            if (measureA != null && measureB != null) {
                labelValue(canvas, right, "Odległość", formatMm(measureA.distanceTo(measureB)), dp(480));
            } else if (!measuredEdgeText.isEmpty()) {
                labelValue(canvas, right, "Krawędź", measuredEdgeText, dp(480));
            }
            SketchPrimitive profile = latestClosedProfile();
            if (profile != null && profile.type.equals("Okrąg")) {
                double radius = Math.hypot(profile.x2 - profile.x1, profile.y2 - profile.y1);
                text(canvas, "R = " + formatMm(radius), right + dp(18), dp(550), 12, ACCENT, true);
                text(canvas, "Ø = " + formatMm(radius * 2.0), right + dp(18), dp(574), 12, ACCENT, true);
            }
        } else {
            labelValue(canvas, right, "Tryb", mode, dp(366));
        }
        if (!operationMessage.isEmpty()) text(canvas, operationMessage,
            right + dp(18), dp(614), 10, operationMessage.startsWith("Gotowe") ? ACCENT : Color.rgb(190, 70, 55), true);
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

        if (mode.equals("Szkic") || mode.equals("Edytuj szkic")) {
            text(canvas, (mode.equals("Edytuj szkic") ? "Edytuj szkic na: " : "Szkic na: ") + sketchPlane,
                (left + right) / 2f, dp(151), 12, ACCENT, true, Paint.Align.CENTER);
        }
    }

    private void drawSketchEntity(Canvas canvas, SketchPrimitive entity) {
        float scale = dp(3.2f) * zoom;
        float x1 = workCenterX + (float)entity.x1 * scale;
        float y1 = workCenterY - (float)entity.y1 * scale;
        float x2 = workCenterX + (float)entity.x2 * scale;
        float y2 = workCenterY - (float)entity.y2 * scale;
        drawSketchPreview(canvas, entity.type, x1, y1, x2, y2,
            entity.id.equals(selectedSketchId));
    }

    private void drawSketchPreview(Canvas canvas, String type, float x1, float y1, float x2, float y2) {
        drawSketchPreview(canvas, type, x1, y1, x2, y2, false);
    }

    private void drawSketchPreview(Canvas canvas, String type, float x1, float y1, float x2, float y2,
            boolean selected) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(selected ? 3.5f : 2f));
        paint.setColor(selected ? Color.rgb(245, 145, 20) : Color.rgb(30, 105, 210));
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
        if (selected) {
            paint.setColor(Color.rgb(245, 145, 20));
            canvas.drawCircle(x1, y1, dp(5), paint);
            canvas.drawCircle(x2, y2, dp(5), paint);
        }
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

    private SketchPrimitive findSketchAt(float sx, float sy) {
        float scale = dp(3.2f) * zoom;
        float tolerance = dp(16);
        SketchPrimitive best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (SketchPrimitive entity : project.sketch) {
            float x1 = workCenterX + (float)entity.x1 * scale;
            float y1 = workCenterY - (float)entity.y1 * scale;
            float x2 = workCenterX + (float)entity.x2 * scale;
            float y2 = workCenterY - (float)entity.y2 * scale;
            double distance;
            if (entity.type.equals("Okrąg")) {
                double radius = Math.hypot(x2 - x1, y2 - y1);
                distance = Math.abs(Math.hypot(sx - x1, sy - y1) - radius);
            } else if (entity.type.equals("Prostokąt")) {
                float minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
                float minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
                distance = Math.min(Math.min(
                    distanceToSegment(sx, sy, minX, minY, maxX, minY),
                    distanceToSegment(sx, sy, maxX, minY, maxX, maxY)), Math.min(
                    distanceToSegment(sx, sy, maxX, maxY, minX, maxY),
                    distanceToSegment(sx, sy, minX, maxY, minX, minY)));
            } else {
                distance = distanceToSegment(sx, sy, x1, y1, x2, y2);
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
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)dp(18);
        form.setPadding(padding, 0, padding, 0);
        EditText x1 = coordinateInput("X1 [mm]", entity.x1);
        EditText y1 = coordinateInput("Y1 [mm]", entity.y1);
        EditText x2 = coordinateInput(entity.type.equals("Okrąg") ? "X na promieniu [mm]" : "X2 [mm]", entity.x2);
        EditText y2 = coordinateInput(entity.type.equals("Okrąg") ? "Y na promieniu [mm]" : "Y2 [mm]", entity.y2);
        form.addView(x1); form.addView(y1); form.addView(x2); form.addView(y2);
        new AlertDialog.Builder(getContext())
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
        checkpoint();
        project.sketch.set(index, changed);
        updateNativeModel();
        if (project.sketchExtruded && nativeMesh.length == 0) {
            project.sketch.set(index, oldEntity);
            updateNativeModel();
            operationMessage = "Zmiana tworzy niepoprawną bryłę";
        } else {
            selectedSketchId = changed.id;
            projectRepository.save(project);
            operationMessage = "Gotowe: zaktualizowano szkic i model";
        }
        invalidate();
    }

    private void deleteSketchEntity(String id) {
        for (int i = 0; i < project.sketch.size(); i++) {
            if (!project.sketch.get(i).id.equals(id)) continue;
            checkpoint();
            project.sketch.remove(i);
            selectedSketchId = null;
            if (latestClosedProfile() == null) project.sketchExtruded = false;
            updateNativeModel();
            projectRepository.save(project);
            operationMessage = "Gotowe: usunięto element szkicu";
            invalidate();
            return;
        }
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
            && y >= dp(132) && y <= getHeight() - dp(104);
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

    private void drawTimeline(Canvas canvas) {
        float top = getHeight() - dp(96);
        paint.setColor(Color.rgb(16, 24, 32));
        canvas.drawRect(0, top, getWidth(), getHeight(), paint);
        text(canvas, "↶", dp(30), top + dp(42), 25, undoStack.isEmpty() ? MUTED : Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "↷", dp(76), top + dp(42), 25, redoStack.isEmpty() ? MUTED : Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "HISTORIA MODELU", dp(118), top + dp(24), 10, Color.rgb(145, 160, 172), true);
        List<String> features = new ArrayList<>();
        features.add("Początek");
        if (!project.sketch.isEmpty()) features.add("Szkic");
        if (project.importedStep) features.add("Import STEP");
        else if (project.sketchExtruded) features.add("Wyciągnięcie");
        if (project.filletRadius > 0.0) features.add("R" + String.format(java.util.Locale.US, "%.1f", project.filletRadius));
        float startX = dp(130);
        float gap = dp(92);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(70, 86, 98));
        if (features.size() > 1) {
            canvas.drawLine(startX + dp(22), top + dp(53),
                startX + (features.size() - 1) * gap + dp(22), top + dp(53), paint);
        }
        for (int i = 0; i < features.size(); i++) {
            float x = startX + i * gap;
            paint.setColor(i == features.size() - 1 ? ACCENT : Color.rgb(55, 72, 85));
            canvas.drawRoundRect(new RectF(x, top + dp(32), x + dp(44), top + dp(74)), dp(8), dp(8), paint);
            drawFeatureIcon(canvas, features.get(i), x + dp(22), top + dp(53), Color.WHITE);
            text(canvas, features.get(i), x + dp(22), top + dp(88), 9,
                Color.rgb(205, 214, 221), false, Paint.Align.CENTER);
        }
    }

    private boolean isToolActive(String tool) {
        if (tool.equals("Linia") || tool.equals("Prostokąt") || tool.equals("Okrąg")) {
            return mode.equals("Szkic") && sketchTool.equals(tool);
        }
        if (tool.equals("Edytuj")) return mode.equals("Edytuj szkic");
        if (tool.equals("Zmierz")) return mode.equals("Zmierz");
        if (tool.equals("Rysunek")) return mode.equals("Rysunek");
        return tool.equals("Wybierz") && mode.equals("Wybierz")
            || tool.equals("Szkic") && mode.equals("Szkic") && !sketchPlaneChosen;
    }

    private void activateTopTool(String tool) {
        selectedSketchId = null;
        if (tool.equals("Wybierz")) mode = "Wybierz";
        else if (tool.equals("Szkic")) {
            mode = "Szkic";
            sketchPlaneChosen = !project.sketch.isEmpty();
            if (sketchPlaneChosen) alignToCurrentSketchPlane();
            else sketchPlane = "Wybierz płaszczyznę";
        } else if (tool.equals("Linia") || tool.equals("Prostokąt") || tool.equals("Okrąg")) {
            mode = "Szkic";
            sketchTool = tool;
            sketchPlaneChosen = project.sketchPlane != null && !project.sketchPlane.isEmpty();
            if (sketchPlaneChosen) alignToCurrentSketchPlane();
        } else if (tool.equals("Edytuj")) {
            mode = "Edytuj szkic";
            sketchPlaneChosen = true;
            alignToCurrentSketchPlane();
            operationMessage = project.sketch.isEmpty() ? "Brak elementów szkicu" : "Dotknij elementu szkicu";
        } else if (tool.equals("Wyciągnij")) {
            extrudeSketchProfile();
            mode = "Wybierz";
        } else if (tool.equals("Głębokość")) showDepthEditor();
        else if (tool.equals("Zaokrąglij")) showFilletEditor();
        else if (tool.equals("Zmierz")) {
            mode = "Zmierz";
            measureA = measureB = null;
        } else if (tool.equals("Rysunek")) mode = "Rysunek";
        invalidate();
    }

    private void alignToCurrentSketchPlane() {
        String plane = project.sketchPlane;
        sketchPlane = plane;
        if ("XY".equals(plane)) { cameraYaw = 0f; cameraPitch = 0f; }
        else if ("XZ".equals(plane)) { cameraYaw = 0f; cameraPitch = 89f; }
        else if ("YZ".equals(plane)) { cameraYaw = 90f; cameraPitch = 0f; }
    }

    private void drawToolButton(Canvas canvas, float x, float y, float w, float h,
            String id, String label, boolean active) {
        paint.setColor(active ? ACCENT : Color.rgb(28, 40, 51));
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), dp(7), dp(7), paint);
        drawToolIcon(canvas, id, x + w / 2f, y + dp(20), active ? Color.WHITE : Color.rgb(210, 220, 228));
        text(canvas, label, x + w / 2f, y + dp(50), 8.5f,
            active ? Color.WHITE : Color.rgb(190, 201, 210), false, Paint.Align.CENTER);
    }

    private void drawToolIcon(Canvas canvas, String id, float x, float y, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(dp(1.7f));
        paint.setStyle(Paint.Style.STROKE);
        if (id.equals("Linia")) canvas.drawLine(x - dp(10), y + dp(7), x + dp(10), y - dp(7), paint);
        else if (id.equals("Prostokąt")) canvas.drawRect(x - dp(10), y - dp(7), x + dp(10), y + dp(7), paint);
        else if (id.equals("Okrąg")) canvas.drawCircle(x, y, dp(9), paint);
        else if (id.equals("Szkic")) {
            canvas.drawRect(x - dp(10), y - dp(8), x + dp(8), y + dp(8), paint);
            canvas.drawLine(x - dp(7), y + dp(4), x + dp(5), y - dp(5), paint);
        } else if (id.equals("Wyciągnij")) {
            canvas.drawRect(x - dp(9), y - dp(2), x + dp(5), y + dp(9), paint);
            canvas.drawLine(x - dp(2), y - dp(2), x - dp(2), y - dp(11), paint);
            canvas.drawLine(x - dp(6), y - dp(7), x - dp(2), y - dp(11), paint);
            canvas.drawLine(x + dp(2), y - dp(7), x - dp(2), y - dp(11), paint);
        } else if (id.equals("Głębokość")) {
            canvas.drawLine(x - dp(9), y, x + dp(9), y, paint);
            canvas.drawLine(x - dp(9), y, x - dp(4), y - dp(5), paint);
            canvas.drawLine(x + dp(9), y, x + dp(4), y + dp(5), paint);
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
        else if (feature.startsWith("Wyciąg")) drawToolIcon(canvas, "Wyciągnij", x, y, color);
        else if (feature.startsWith("R")) drawToolIcon(canvas, "Zaokrąglij", x, y, color);
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
