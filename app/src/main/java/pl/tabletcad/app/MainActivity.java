package pl.tabletcad.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(new CadWorkspaceView(this));
        WindowInsetsController insetsController =
            getWindow().getDecorView().getWindowInsetsController();
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.statusBars());
        }
    }
}
