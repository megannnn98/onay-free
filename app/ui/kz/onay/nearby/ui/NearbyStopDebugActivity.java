package kz.onay.nearby.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Stage 2 host for {@link NearbyStopFragment}, started only with adb:
 * am start -n kz.onay/kz.onay.nearby.ui.NearbyStopDebugActivity [--es data test] [--es state error|empty|nogps|south]
 * [--ed lat 43.2 --ed lng 76.9]. Without "data" the real stops from the app database and the real GPS are used;
 * lat/lng replace the GPS position for testing away from the city.
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
            Bundle args = new Bundle();
            args.putString(NearbyStopFragment.ARG_STATE, getIntent().getStringExtra("state"));
            args.putString(NearbyStopFragment.ARG_DATA, getIntent().getStringExtra("data"));
            args.putDouble(NearbyStopFragment.ARG_LAT, getIntent().getDoubleExtra("lat", Double.NaN));
            args.putDouble(NearbyStopFragment.ARG_LNG, getIntent().getDoubleExtra("lng", Double.NaN));
            Fragment f = NearbyStopFragment.newInstance(args);
            getSupportFragmentManager().beginTransaction().replace(root.getId(), f).commit();
        }
    }
}
