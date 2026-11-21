package kz.onay.nearby.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Stage 2 host for {@link NearbyStopFragment}, started only with adb:
 * am start -n kz.onay/kz.onay.nearby.ui.NearbyStopDebugActivity --es state error|empty|nogps
 * The real entry point (the transport tab) is added in stage 6.
 */
public class NearbyStopDebugActivity extends FragmentActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setId(View.generateViewId());
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        if (savedInstanceState == null) {
            Fragment f = NearbyStopFragment.newInstance(getIntent().getStringExtra("state"));
            getSupportFragmentManager().beginTransaction().replace(root.getId(), f).commit();
        }
    }
}
