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
    private String pendingImportFormat = "step";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        workspace = new CadWorkspaceView(this, new CadWorkspaceView.FileRequestListener() {
            @Override public void requestExport(String format) { requestExportFile(format); }
            @Override public void requestOpenStep() {
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Otwórz plik")
                    .setItems(new String[]{"STEP — bryła", "STL — siatka ASCII / binarna", "DXF — szkic 2D"},
                        (dialog,which)-> {
                            pendingImportFormat = which==0 ? "step" : which==1 ? "stl" : "dxf";
                            requestStepFile();
                        }).setNegativeButton("Anuluj",null).show();
            }
        });
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(workspace);
        setContentView(root);
        if(savedInstanceState==null)workspace.post(()->{if(workspace.isAttachedToWindow())workspace.showStartupProjects();});
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
        intent.setType(format.equals("pdf") ? "application/pdf" : format.equals("step") ? "application/step"
            : format.startsWith("stl") ? "model/stl" : format.equals("obj") ? "model/obj" : "application/dxf");
        intent.putExtra(Intent.EXTRA_TITLE, "TabletCAD-model." + (format.equals("stla") ? "stl" : format));
        startActivityForResult(intent, CREATE_EXPORT_FILE);
    }

    private void requestStepFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_STEP_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_STEP_FILE && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            if(pendingImportFormat.equals("step")) importStep(data.getData());
            else importExchange(data.getData());
            return;
        }
        if (requestCode != CREATE_EXPORT_FILE || resultCode != RESULT_OK || data == null
                || data.getData() == null || pendingExportFormat == null) return;
        Uri destination = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Nie można otworzyć pliku docelowego");
            if(pendingExportFormat.equals("pdf")) {
                workspace.writeTechnicalPdf(output);
            } else if (pendingExportFormat.equals("stl")) {
                workspace.writeBinaryStl(output);
            } else if(pendingExportFormat.equals("stla")) {
                workspace.writeAsciiStl(output);
            } else if (pendingExportFormat.equals("obj")) {
                workspace.writeObj(output);
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
            Toast.makeText(this, "Eksport " + (pendingExportFormat.equals("stla")
                ? "STL ASCII" : pendingExportFormat.toUpperCase()) + " zakończony", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "Eksport nieudany: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingExportFormat = null;
        }
    }

    private void importStep(Uri source) {
        File staged = new File(getCacheDir(), "imported.step.new");
        try (InputStream input = getContentResolver().openInputStream(source);
             OutputStream output = new java.io.FileOutputStream(staged)) {
            if (input == null) throw new IOException("Nie można odczytać wybranego pliku");
            byte[] buffer = new byte[64 * 1024];
            int read;long total=0;
            while ((read = input.read(buffer)) >= 0) {
                total+=read;if(total>32L*1024*1024)throw new IOException("Limit STEP: 32 MB");
                output.write(buffer, 0, read);
            }
            output.flush();
            workspace.importStep(staged);
            Toast.makeText(this, "Model STEP otwarty", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "Import nieudany: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally { staged.delete(); }
    }

    private void importExchange(Uri source) {
        File staged = new File(getCacheDir(),"exchange-import.tmp");
        try (InputStream input=getContentResolver().openInputStream(source)) {
            if(input==null) throw new IOException("Nie można otworzyć pliku");
            if(pendingImportFormat.equals("dxf")) workspace.importDxf(input);
            else {
                try(OutputStream output=new java.io.FileOutputStream(staged)) {
                    byte[] buffer=new byte[16384]; int n,total=0;
                    while((n=input.read(buffer))!=-1) {
                        total+=n;
                        if(total>32*1024*1024) throw new IOException("Limit STL: 32 MB");
                        output.write(buffer,0,n);
                    }
                }
                workspace.importStl(staged);
            }
            Toast.makeText(this,"Otwarto "+pendingImportFormat.toUpperCase(),Toast.LENGTH_LONG).show();
        } catch(IOException error) {
            Toast.makeText(this,"Import nieudany: "+error.getMessage(),Toast.LENGTH_LONG).show();
        } finally { staged.delete(); }
    }
}
