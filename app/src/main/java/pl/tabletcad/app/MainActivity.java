package pl.tabletcad.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class MainActivity extends Activity {
    private static final int CREATE_EXPORT_FILE = 801;
    private static final int OPEN_STEP_FILE = 802;
    private CadWorkspaceView workspace;
    private String pendingExportFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        workspace = new CadWorkspaceView(this, new CadWorkspaceView.FileRequestListener() {
            @Override public void requestExport(String format) { requestExportFile(format); }
            @Override public void requestOpenStep() { requestStepFile(); }
        });
        setContentView(workspace);
        WindowInsetsController insetsController =
            getWindow().getDecorView().getWindowInsetsController();
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.statusBars());
        }
    }

    private void requestExportFile(String format) {
        pendingExportFormat = format;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(format.equals("step") ? "application/step"
            : format.equals("stl") ? "model/stl" : "application/dxf");
        intent.putExtra(Intent.EXTRA_TITLE, "TabletCAD-model." + format);
        startActivityForResult(intent, CREATE_EXPORT_FILE);
    }

    private void requestStepFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "application/step", "application/x-step", "model/step", "application/octet-stream"
        });
        startActivityForResult(intent, OPEN_STEP_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_STEP_FILE && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            importStep(data.getData());
            return;
        }
        if (requestCode != CREATE_EXPORT_FILE || resultCode != RESULT_OK || data == null
                || data.getData() == null || pendingExportFormat == null) return;
        Uri destination = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Nie można otworzyć pliku docelowego");
            if (pendingExportFormat.equals("stl")) {
                workspace.writeBinaryStl(output);
            } else if (pendingExportFormat.equals("dxf")) {
                workspace.writeDxf(output);
            } else {
                File temporary = new File(getCacheDir(), "tabletcad-export.step");
                if (!workspace.exportStep(temporary.getAbsolutePath())) {
                    throw new IOException("Rdzeń OCCT nie zapisał pliku STEP");
                }
                try (FileInputStream input = new FileInputStream(temporary)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                } finally {
                    // Cache is private and recoverable; a failed delete is cleaned by Android.
                    temporary.delete();
                }
            }
            output.flush();
            Toast.makeText(this, "Eksport " + pendingExportFormat.toUpperCase() + " zakończony", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "Eksport nieudany: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingExportFormat = null;
        }
    }

    private void importStep(Uri source) {
        File destination = workspace.importedStepFile();
        File staged = new File(destination.getParentFile(), "imported.step.new");
        try (InputStream input = getContentResolver().openInputStream(source);
             OutputStream output = new java.io.FileOutputStream(staged)) {
            if (input == null) throw new IOException("Nie można odczytać wybranego pliku");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.flush();
            if (NativeCadEngine.tessellateStep(staged.getAbsolutePath()).length == 0) {
                throw new IOException("Plik nie zawiera poprawnej bryły STEP");
            }
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            if (!workspace.activateImportedStep()) throw new IOException("Nie można wyświetlić modelu STEP");
            Toast.makeText(this, "Model STEP otwarty", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            staged.delete();
            Toast.makeText(this, "Import nieudany: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
