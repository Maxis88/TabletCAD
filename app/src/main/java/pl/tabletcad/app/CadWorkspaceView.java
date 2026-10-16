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

/**
 * Touch-first foundation viewport. It deliberately renders a demonstrator model;
 * production B-Rep operations will be supplied by the planned Open CASCADE core.
 */
public final class CadWorkspaceView extends View {
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
    private String modelInfo;
    private final String stepStatus;
    private double modelWidth = 80.0;
    private double modelHeight = 48.0;
    private double modelDepth = 32.0;
    private RectF sketchRect;
    private float sketchStartX;
    private float sketchStartY;
    private boolean sketching;
    private String sketchTool = "Prostokąt";
    private final List<SketchEntity> sketchEntities = new ArrayList<>();
    private boolean sketchPlaneChosen;
    private String sketchPlane = "Nie wybrano";
    private int selectedFace = -1;
    private final float[] projectedX = new float[8];
    private final float[] projectedY = new float[8];
    private final float[] projectedZ = new float[8];
    private float touchDownX;
    private float touchDownY;
    private static final int[][] BOX_FACES = {
        {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 4, 7, 3},
        {1, 5, 6, 2}, {3, 2, 6, 7}, {0, 1, 5, 4}
    };

    private static final class SketchEntity {
        final String type;
        final float x1, y1, x2, y2;
        SketchEntity(String type, float x1, float y1, float x2, float y2) {
            this.type = type;
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    public CadWorkspaceView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
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
                    if (e.getY() >= dp(332) && e.getY() <= dp(400)) showDepthEditor();
                } else if (mode.equals("Szkic") && e.getX() > getWidth() - dp(234)) {
                    if (e.getY() >= dp(322) && e.getY() <= dp(382)) chooseSketchPlane("XY");
                    else if (e.getY() >= dp(390) && e.getY() <= dp(450)) chooseSketchPlane("XZ");
                    else if (e.getY() >= dp(458) && e.getY() <= dp(518)) chooseSketchPlane("YZ");
                } else if (isInsideViewport(e.getX(), e.getY())) {
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
                        && (mode.equals("Wybierz") || mode.equals("Modeluj"))) {
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
                    sketchEntities.add(new SketchEntity(sketchTool, sketchStartX, sketchStartY,
                        event.getX(), event.getY()));
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

        drawTopBar(canvas);
        drawLeftPanel(canvas, left, top, bottom);
        drawProperties(canvas, right, top, bottom);
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        drawGrid(canvas, left, top, right, bottom);
        if (mode.equals("Rysunek")) {
            drawTechnicalViews(canvas, left, top, right, bottom);
        } else {
            drawDemoSolid(canvas, (left + right) / 2f + panX, (top + bottom) / 2f + panY);
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
        pill(canvas, getWidth() - dp(172), dp(10), dp(152), dp(38), "Eksportuj", ACCENT, Color.WHITE);
    }

    private void drawLeftPanel(Canvas canvas, float left, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(0, dp(56), left, getHeight(), paint);
        text(canvas, "MODEL", dp(18), dp(91), 12, MUTED, true);
        row(canvas, "▾  Dokument", dp(18), dp(126), true);
        row(canvas, "   ◇  Początek", dp(18), dp(164), false);
        row(canvas, "   ▱  Szkic 1", dp(18), dp(202), false);
        row(canvas, "   ◈  Wyciągnięcie 1", dp(18), dp(240), true);
        paint.setColor(Color.rgb(232, 236, 239));
        canvas.drawRect(dp(18), bottom - dp(74), left - dp(18), bottom - dp(73), paint);
        text(canvas, "HISTORIA", dp(18), bottom - dp(48), 12, MUTED, true);
        text(canvas, "◀   ▶     100%", dp(18), bottom - dp(17), 14, INK, false);
    }

    private void drawProperties(Canvas canvas, float right, float top, float bottom) {
        paint.setColor(PANEL);
        canvas.drawRect(right, dp(56), getWidth(), getHeight(), paint);
        text(canvas, "WŁAŚCIWOŚCI", right + dp(18), dp(91), 12, MUTED, true);
        text(canvas, mode.equals("Szkic") ? "Płaszczyzna szkicu" : "Wyciągnięcie 1", right + dp(18), dp(127), 18, INK, true);
        labelValue(canvas, right, "Głębokość", String.format(java.util.Locale.US, "%.2f mm", modelDepth), dp(174));
        labelValue(canvas, right, "Operacja", "Nowa bryła", dp(238));
        if (mode.equals("Modeluj")) {
            pill(canvas, right + dp(18), dp(332), dp(202), dp(58), "Wpisz wartość", ACCENT, Color.WHITE);
            text(canvas, selectedFace >= 0 ? "Wybrano: ściana " + (selectedFace + 1) : "Dotknij ściany lub krawędzi",
                right + dp(18), dp(430), 11, selectedFace >= 0 ? ACCENT : MUTED, selectedFace >= 0);
        } else if (mode.equals("Szkic")) {
            text(canvas, sketchPlane, right + dp(18), dp(292), 12,
                sketchPlaneChosen ? ACCENT : MUTED, true);
            pill(canvas, right + dp(18), dp(322), dp(202), dp(52), "Płaszczyzna XY", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(390), dp(202), dp(52), "Płaszczyzna XZ", Color.rgb(226, 232, 236), INK);
            pill(canvas, right + dp(18), dp(458), dp(202), dp(52), "Płaszczyzna YZ", Color.rgb(226, 232, 236), INK);
            text(canvas, "lub dotknij płaskiej ściany bryły", right + dp(18), dp(542), 10, MUTED, false);
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

    private void drawDemoSolid(Canvas canvas, float cx, float cy) {
        projectBox(cx, cy);
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (a, b) -> Float.compare(faceDepth(a), faceDepth(b)));
        for (int faceIndex : order) {
            Path facePath = facePath(faceIndex);
            paint.setStyle(Paint.Style.FILL);
            if (faceIndex == selectedFace) paint.setColor(Color.rgb(255, 190, 70));
            else paint.setColor(faceColor(faceIndex));
            canvas.drawPath(facePath, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(faceIndex == selectedFace ? 3f : 1.6f));
            paint.setColor(faceIndex == selectedFace ? Color.rgb(225, 120, 10) : ACCENT);
            canvas.drawPath(facePath, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (mode.equals("Szkic") && !sketchPlaneChosen) {
            text(canvas, "Wybierz płaszczyznę bazową lub dotknij ściany", cx, cy - dp(190),
                14, INK, true, Paint.Align.CENTER);
        }
    }

    private void drawSketch(Canvas canvas, float left, float right) {
        for (SketchEntity entity : sketchEntities) drawSketchEntity(canvas, entity);
        if (sketchRect != null && sketching) {
            drawSketchEntity(canvas, new SketchEntity(sketchTool, sketchStartX, sketchStartY,
                sketchRect.right, sketchRect.bottom));
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

    private void drawSketchEntity(Canvas canvas, SketchEntity entity) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(30, 105, 210));
        if (entity.type.equals("Linia")) {
            canvas.drawLine(entity.x1, entity.y1, entity.x2, entity.y2, paint);
        } else if (entity.type.equals("Okrąg")) {
            float radius = (float)Math.hypot(entity.x2 - entity.x1, entity.y2 - entity.y1);
            canvas.drawCircle(entity.x1, entity.y1, radius, paint);
        } else {
            canvas.drawRect(Math.min(entity.x1, entity.x2), Math.min(entity.y1, entity.y2),
                Math.max(entity.x1, entity.x2), Math.max(entity.y1, entity.y2), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void projectBox(float cx, float cy) {
        double hx = modelWidth / 2.0;
        double hy = modelHeight / 2.0;
        double hz = modelDepth / 2.0;
        double[][] vertices = {
            {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, hy, -hz}, {-hx, hy, -hz},
            {-hx, -hy, hz}, {hx, -hy, hz}, {hx, hy, hz}, {-hx, hy, hz}
        };
        double yaw = Math.toRadians(cameraYaw);
        double pitch = Math.toRadians(cameraPitch);
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch), spitch = Math.sin(pitch);
        float scale = dp(3.2f) * zoom;
        for (int i = 0; i < vertices.length; i++) {
            double x = vertices[i][0];
            double y = vertices[i][1];
            double z = vertices[i][2];
            double rx = x * cyaw + z * syaw;
            double rz = -x * syaw + z * cyaw;
            double ry = y * cpitch - rz * spitch;
            double finalZ = y * spitch + rz * cpitch;
            double perspective = 420.0 / (420.0 - finalZ);
            projectedX[i] = cx + (float)(rx * scale * perspective);
            projectedY[i] = cy - (float)(ry * scale * perspective);
            projectedZ[i] = (float)finalZ;
        }
    }

    private Path facePath(int faceIndex) {
        int[] face = BOX_FACES[faceIndex];
        Path path = new Path();
        path.moveTo(projectedX[face[0]], projectedY[face[0]]);
        for (int i = 1; i < face.length; i++) path.lineTo(projectedX[face[i]], projectedY[face[i]]);
        path.close();
        return path;
    }

    private float faceDepth(int faceIndex) {
        float depth = 0f;
        for (int vertex : BOX_FACES[faceIndex]) depth += projectedZ[vertex];
        return depth / BOX_FACES[faceIndex].length;
    }

    private int faceColor(int faceIndex) {
        int shade = 205 + (faceIndex * 7 % 32);
        return Color.rgb(Math.min(238, shade), Math.min(246, shade + 18), Math.min(244, shade + 16));
    }

    private int findFaceAt(float x, float y) {
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (a, b) -> Float.compare(faceDepth(b), faceDepth(a)));
        for (int faceIndex : order) {
            if (pointInFace(x, y, BOX_FACES[faceIndex])) return faceIndex;
        }
        return -1;
    }

    private boolean pointInFace(float x, float y, int[] face) {
        boolean inside = false;
        for (int i = 0, j = face.length - 1; i < face.length; j = i++) {
            float xi = projectedX[face[i]], yi = projectedY[face[i]];
            float xj = projectedX[face[j]], yj = projectedY[face[j]];
            if (((yi > y) != (yj > y))
                    && x < (xj - xi) * (y - yi) / ((yj - yi) == 0f ? 0.0001f : (yj - yi)) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private void chooseSketchPlane(String plane) {
        sketchPlane = plane;
        sketchPlaneChosen = true;
        sketchRect = null;
        sketchEntities.clear();
        if (plane.equals("XY")) {
            cameraYaw = 0f; cameraPitch = 0f; selectedFace = -1;
        } else if (plane.equals("XZ")) {
            cameraYaw = 0f; cameraPitch = 89f; selectedFace = -1;
        } else if (plane.equals("YZ")) {
            cameraYaw = 90f; cameraPitch = 0f; selectedFace = -1;
        } else if (selectedFace == 0) {
            cameraYaw = 180f; cameraPitch = 0f;
        } else if (selectedFace == 1) {
            cameraYaw = 0f; cameraPitch = 0f;
        } else if (selectedFace == 2) {
            cameraYaw = -90f; cameraPitch = 0f;
        } else if (selectedFace == 3) {
            cameraYaw = 90f; cameraPitch = 0f;
        } else if (selectedFace == 4) {
            cameraYaw = 0f; cameraPitch = -89f;
        } else if (selectedFace == 5) {
            cameraYaw = 0f; cameraPitch = 89f;
        }
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
                        modelDepth = value;
                        updateNativeModel();
                        invalidate();
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid input leaves the last valid parameter unchanged.
                }
            })
            .show();
    }

    private void drawDimensions(Canvas canvas, float left, float top, float right, float bottom) {
        float cx = (left + right) / 2f + panX;
        float cy = (top + bottom) / 2f + panY;
        float w = dp((float)Math.min(320.0, modelWidth * 3.0)) * zoom;
        float h = dp((float)Math.min(230.0, modelHeight * 3.0)) * zoom;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(Color.rgb(45, 90, 190));
        canvas.drawLine(cx - w / 2, cy + h / 2 + dp(32), cx + w / 2, cy + h / 2 + dp(32), paint);
        canvas.drawLine(cx - w / 2, cy + h / 2 + dp(22), cx - w / 2, cy + h / 2 + dp(42), paint);
        canvas.drawLine(cx + w / 2, cy + h / 2 + dp(22), cx + w / 2, cy + h / 2 + dp(42), paint);
        paint.setStyle(Paint.Style.FILL);
        text(canvas, String.format(java.util.Locale.US, "%.2f mm", modelWidth), cx, cy + h / 2 + dp(27),
            12, Color.rgb(45, 90, 190), true, Paint.Align.CENTER);
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
        modelInfo = NativeCadEngine.modelInfo(modelWidth, modelHeight, modelDepth);
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
