package kz.onay.nearby.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kz.onay.nearby.logic.AgeText;
import kz.onay.nearby.logic.CameraFocus;
import kz.onay.nearby.logic.Haversine;
import kz.onay.nearby.logic.LocationQuality;
import kz.onay.nearby.logic.MarkerStacking;
import kz.onay.nearby.logic.NearbyCandidates;
import kz.onay.nearby.logic.NearestStopFinder;
import kz.onay.nearby.logic.RouteAtStop;
import kz.onay.nearby.logic.RouteHighlightState;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.RoutesAtStopSelector;
import kz.onay.nearby.logic.StopMatch;
import kz.onay.nearby.logic.StopPoint;
import kz.onay.nearby.logic.VehicleFilter;
import kz.onay.nearby.logic.VehicleInfoText;
import kz.onay.nearby.logic.VehiclePosition;
import kz.onay.ui.routes2.shared.MyLocationRequestHelper;

/**
 * "Рядом": the nearest stop, big route buttons and the routes of that stop on the map.
 * The view is built in code so that no resource ids have to be added to the app.
 * Route lines are added once per stop; selecting a route only changes colour, width and z-order.
 * Database reads and stop searches run on a background thread; everything touching views runs on the main one.
 */
public class NearbyStopFragment extends Fragment {
    static final String ARG_STATE = "state";
    static final String ARG_DATA = "data";
    static final String ARG_LAT = "lat";
    static final String ARG_LNG = "lng";

    private static final String TAG = "NearbyStop";
    private static final String SAVED_MAP = "map";
    private static final int REQ_LOCATION = 4711;
    private static final double SEARCH_RADIUS_METERS = 1000.0;
    private static final int NEARBY_LIMIT = 5;
    private static final double ALTERNATIVE_EXTRA_METERS = 120.0;
    private static final int ALTERNATIVE_LIMIT = 3;
    private static final int PICK_LIMIT = 80;
    private static final long VEHICLE_POLL_MS = 5_000L;
    private static final long VEHICLE_MAX_AGE_SEC = 180L;
    private static final int VEHICLE_FAILURES_BEFORE_NOTE = 3;
    private static final double OVERVIEW_LAT_SPAN = 0.0030; // about 330 m
    private static final double OVERVIEW_LNG_SPAN = 0.0040; // about 330 m at this latitude
    /** GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE: the nested type is not on the compile-only classpath. */
    private static final int REASON_GESTURE = 1;
    private static final float[][] STACK_ANCHORS = {{0.5f, 0.5f}, {0.5f, 1.1f}, {0.5f, -0.1f}, {1.1f, 0.5f}};
    private static final float PICK_MIN_ZOOM = 12f; // below this the map is not positioned on a place yet
    private static final double CAMERA_VEHICLE_RADIUS_METERS = 600.0;
    private static final double STACK_RADIUS_METERS = 25.0;
    private static final long LOCATION_TIMEOUT_MS = 15_000L;
    private static final long BUTTON_LOCATION_TIMEOUT_MS = 8_000L;
    private static final long LOCATION_POLL_MS = 400L;
    private static final long MAX_FIX_AGE_MS = 120_000L;
    private static final int[] PALETTE = {
        0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A, 0xFF00838F, 0xFFAD1457, 0xFF4E342E,
        0xFF283593, 0xFF827717, 0xFFD84315, 0xFF00695C
    };

    private final RouteHighlightState highlight = new RouteHighlightState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, List<Polyline>> polylines = new HashMap<>();
    private final Map<Long, List<Marker>> arrows = new HashMap<>();
    private final Map<Long, TextView> chips = new HashMap<>();
    private final Map<Long, Integer> colorIndex = new HashMap<>();
    private final Map<Marker, StopPoint> pickMarkers = new HashMap<>();
    private final Map<Long, List<RouteLine>> shownLines = new HashMap<>();
    private final Map<Long, VehicleMarker> vehicleMarkers = new HashMap<>();
    private final Map<String, BitmapDescriptor> vehicleIcons = new HashMap<>();

    private ExecutorService executor;
    private int generation;
    private boolean testMode;
    private String forcedState;
    private boolean permissionAsked;
    private boolean recheckOnResume;
    private static boolean routesTabOpenedForStops;

    private NearbyDataSource data;
    private List<StopPoint> allStops = Collections.emptyList();
    private double[] userLoc;
    private MyLocationRequestHelper locationHelper;
    private long locationDeadline;

    private View statusBar;
    private TextView titleView;
    private TextView subtitleView;
    private TextView statusView;
    private Button actionButton;
    private Button actionButton2;
    private Runnable action;
    private Runnable action2;
    private String locationNote;
    private HorizontalScrollView altScroll;
    private LinearLayout altRow;
    private HorizontalScrollView chipScroll;
    private ScrollView chipFlowScroll;
    private FlowLayout chipFlow;
    private boolean chipsExpanded;
    private Button allButton;
    private TextView expandButton;
    private TextView vehicleInfo;
    private FrameLayout rootView;
    private View topCardView;
    private View dockView;
    private FrameLayout.LayoutParams fabLp;
    private boolean originalNavigationShown;
    private LinearLayout chipRow;
    private TextView directionView;
    private TextView vehicleNote;
    private boolean vehiclePolling;
    private boolean vehicleRequestInFlight;
    private int vehicleFailures;
    private List<VehiclePosition> shownVehicles = Collections.emptyList();
    private TextView pickHint;
    private ImageView myLocationButton;
    private Marker userMarker;
    private Button changeStopButton;
    private MapView mapView;
    private GoogleMap map;
    private boolean mapLoaded;
    private CameraUpdate pendingCamera;
    private boolean pickMode;
    private boolean routesLoadSkipped;
    private boolean cameraAuto = true;
    private int mapPadTop = -1;
    private int mapPadBottom = -1;
    private Marker raisedVehicle;
    private long snapshotReferenceSec;

    private StopPoint selectedStop;
    private double selectedDistance = -1;
    private List<StopMatch> candidates = Collections.emptyList();
    private List<RouteAtStop> routes = Collections.emptyList();

    /** A vehicle on the map together with what is needed to restyle it when the selection changes. */
    private static final class VehicleMarker {
        final Marker marker;
        final long routeId;
        final int bucket;
        int stack;

        VehicleMarker(Marker marker, long routeId, int bucket, int stack) {
            this.marker = marker;
            this.routeId = routeId;
            this.bucket = bucket;
            this.stack = stack;
        }
    }

    public static NearbyStopFragment newInstance(@Nullable Bundle args) {
        NearbyStopFragment f = new NearbyStopFragment();
        f.setArguments(args == null ? new Bundle() : args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        forcedState = args.getString(ARG_STATE);
        testMode = "test".equals(args.getString(ARG_DATA));
        generation++;
        executor = Executors.newSingleThreadExecutor();

        FrameLayout root = new FrameLayout(c);
        rootView = root;
        root.setBackgroundColor(Color.WHITE);

        // The map fills the whole screen; the stop card and the route dock float above it.
        mapView = new MapView(c);
        root.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        myLocationButton = new ImageView(c);
        myLocationButton.setImageBitmap(myLocationBitmap());
        myLocationButton.setScaleType(ImageView.ScaleType.CENTER);
        myLocationButton.setContentDescription("Моё местоположение");
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(Color.WHITE);
        fabBg.setStroke(dp(1), 0x33000000);
        myLocationButton.setBackground(fabBg);
        myLocationButton.setElevation(dp(4));
        myLocationButton.setOnClickListener(v -> onMyLocationClicked());
        fabLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        fabLp.setMargins(0, 0, dp(12), dp(124));
        root.addView(myLocationButton, fabLp);

        // ---- top card: stop name, distance, change stop, neighbouring stops, status ----
        LinearLayout topCard = new LinearLayout(c);
        topCardView = topCard;
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(dp(14), dp(10), dp(14), dp(8));
        topCard.setBackground(cardBackground(dp(16)));
        topCard.setElevation(dp(8));

        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleView = text(c, 20, true, 0xFF212121);
        titleView.setMaxLines(2);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView moreButton = text(c, 22, true, 0xFF616161);
        moreButton.setText("⋯");
        moreButton.setGravity(Gravity.CENTER);
        moreButton.setContentDescription("Остальное: вкладки ONAY!, карты, оплата");
        moreButton.setOnClickListener(v -> toggleOriginalNavigation());
        titleRow.addView(moreButton, new LinearLayout.LayoutParams(dp(44), dp(40)));
        topCard.addView(titleRow);
        subtitleView = text(c, 14, false, 0xFF616161);
        topCard.addView(subtitleView);

        pickHint = text(c, 14, true, Color.WHITE);
        pickHint.setBackground(cardBackground(dp(10), 0xFF1565C0));
        pickHint.setPadding(dp(12), dp(6), dp(12), dp(6));
        pickHint.setText("Нажмите на остановку на карте");
        pickHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(6);
        topCard.addView(pickHint, hintLp);

        LinearLayout stopRow = new LinearLayout(c);
        stopRow.setOrientation(LinearLayout.HORIZONTAL);
        stopRow.setGravity(Gravity.CENTER_VERTICAL);
        changeStopButton = new Button(c);
        changeStopButton.setOnClickListener(v -> togglePickMode());
        styleChangeStopButton(false);
        stopRow.addView(changeStopButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        altScroll = new HorizontalScrollView(c);
        altScroll.setHorizontalScrollBarEnabled(false);
        altRow = new LinearLayout(c);
        altRow.setOrientation(LinearLayout.HORIZONTAL);
        altRow.setPadding(dp(8), 0, 0, 0);
        altScroll.addView(altRow);
        altScroll.setVisibility(View.GONE);
        stopRow.addView(altScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams stopRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopRowLp.topMargin = dp(4);
        topCard.addView(stopRow, stopRowLp);

        LinearLayout status = new LinearLayout(c);
        statusBar = status;
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(0, dp(4), 0, 0);
        statusView = text(c, 15, false, 0xFF424242);
        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        actionButton = new Button(c);
        actionButton.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
        actionButton2 = new Button(c);
        actionButton2.setOnClickListener(v -> {
            if (action2 != null) {
                action2.run();
            }
        });
        styleSecondaryButton(actionButton);
        styleSecondaryButton(actionButton2);
        buttons.addView(actionButton);
        buttons.addView(actionButton2);
        // Long stop names must scroll instead of running off the screen.
        HorizontalScrollView buttonScroll = new HorizontalScrollView(c);
        buttonScroll.setHorizontalScrollBarEnabled(false);
        buttonScroll.addView(buttons);
        status.addView(statusView);
        status.addView(buttonScroll);
        topCard.addView(status);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topLp.setMargins(dp(8), dp(8), dp(8), 0);
        root.addView(topCard, topLp);

        // ---- bottom dock: routes of the selected stop, selected route text, vehicle info, freshness ----
        LinearLayout dock = new LinearLayout(c);
        dockView = dock;
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(8), dp(8), dp(8), dp(8));
        dock.setBackground(cardBackground(dp(16)));
        dock.setElevation(dp(8));

        vehicleInfo = text(c, 14, true, Color.WHITE);
        vehicleInfo.setBackground(cardBackground(dp(10), 0xFF37474F));
        vehicleInfo.setPadding(dp(12), dp(6), dp(12), dp(6));
        vehicleInfo.setVisibility(View.GONE);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(dp(4), 0, dp(4), dp(6));
        dock.addView(vehicleInfo, infoLp);

        chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(c);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(4), dp(2), dp(4), dp(2));
        chipScroll.addView(chipRow);
        chipFlow = new FlowLayout(c);
        chipFlow.setPadding(dp(4), dp(2), dp(4), dp(2));
        chipFlowScroll = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(220), MeasureSpec.AT_MOST));
            }
        };
        chipFlowScroll.addView(chipFlow);
        chipFlowScroll.setVisibility(View.GONE);

        LinearLayout chipHeader = new LinearLayout(c);
        chipHeader.setOrientation(LinearLayout.HORIZONTAL);
        chipHeader.setGravity(Gravity.CENTER_VERTICAL);
        allButton = new Button(c);
        allButton.setText("Все");
        styleSecondaryButton(allButton);
        allButton.setVisibility(View.GONE);
        allButton.setOnClickListener(v -> {
            highlight.clear();
            refreshHighlight(false);
            fitCamera(null);
        });
        expandButton = text(c, 18, true, PALETTE[0]);
        expandButton.setText("⌃");
        expandButton.setGravity(Gravity.CENTER);
        expandButton.setContentDescription("Раскрыть список маршрутов");
        expandButton.setOnClickListener(v -> toggleExpandedChips());
        chipHeader.addView(allButton);
        FrameLayout chipHost = new FrameLayout(c);
        chipHost.addView(chipScroll);
        chipHost.addView(chipFlowScroll);
        chipHeader.addView(chipHost, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chipHeader.addView(expandButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        dock.addView(chipHeader);

        directionView = text(c, 14, false, 0xFF424242);
        directionView.setPadding(dp(6), dp(2), dp(6), dp(2));
        dock.addView(directionView);
        vehicleNote = text(c, 12, false, 0xFF757575);
        vehicleNote.setPadding(dp(6), 0, dp(6), 0);
        dock.addView(vehicleNote);

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        dockLp.setMargins(dp(8), 0, dp(8), dp(8));
        root.addView(dock, dockLp);

        View.OnLayoutChangeListener insets = (v, l, t, r, b, ol, ot, or, ob) -> updateMapInsets();
        topCard.addOnLayoutChangeListener(insets);
        dock.addOnLayoutChangeListener(insets);

        Bundle mapState = savedInstanceState == null ? null : savedInstanceState.getBundle(SAVED_MAP);
        mapView.onCreate(mapState);
        mapView.getMapAsync(this::onMapReady);
        showStatus("Ищем ближайшую остановку…", null, null);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        start();
    }

    // ---------- loading: stops, then location, then the nearest stop ----------

    private void start() {
        handler.removeCallbacksAndMessages(null);
        // The line above just deleted the vehicle tick and the only callback that resets the
        // in-flight flag: a request of the previous view must not block this one.
        stopVehiclePolling();
        vehicleRequestInFlight = false;
        stopLocationWatch();
        final int gen = generation;
        locationNote = null;
        leavePickState();
        clearCandidates();
        showStatus("Ищем ближайшую остановку…", null, null);
        if (testMode) {
            data = new TestData(forcedState);
            handler.postDelayed(() -> {
                if (gen != generation) {
                    return;
                }
                if ("error".equals(forcedState)) {
                    showStatus("Не удалось загрузить данные остановок", "Повторить", this::start);
                    return;
                }
                allStops = data.getStops();
                locate();
            }, 400);
            return;
        }
        executor.execute(() -> {
            try {
                AppDataSource src = AppDataSource.create();
                List<StopPoint> stops = src.getStops();
                Log.i(TAG, "stops loaded: " + stops.size());
                post(gen, () -> onStopsLoaded(src, stops));
            } catch (AppDataSource.Unavailable e) {
                Log.w(TAG, "route data unavailable: " + e.getMessage());
                // loginRequired normally comes from loadRoutes (onRoutesFailed); create() no longer needs a token.
                post(gen, () -> showStatus(e.loginRequired
                        ? "Войдите в приложение, чтобы видеть маршруты"
                        : "Город не выбран или данные транспорта ещё не готовы", "Повторить", this::start));
            } catch (Exception e) {
                Log.e(TAG, "stops load failed", e);
                post(gen, () -> showStatus("Не удалось загрузить остановки", "Повторить", this::start));
            }
        });
    }

    private void onStopsLoaded(AppDataSource src, List<StopPoint> stops) {
        data = src;
        allStops = stops;
        if (stops.isEmpty()) {
            showStatus("Остановки ещё не загружены. Открываем карту транспорта (Routes): после загрузки вернитесь на «Рядом».",
                    "Повторить", this::start);
            openRoutesTabOnce();
            return;
        }
        locate();
    }

    private void locate() {
        Bundle args = getArguments();
        double dbgLat = args == null ? Double.NaN : args.getDouble(ARG_LAT, Double.NaN);
        double dbgLng = args == null ? Double.NaN : args.getDouble(ARG_LNG, Double.NaN);
        if (!Double.isNaN(dbgLat) && !Double.isNaN(dbgLng)) {
            Log.i(TAG, "using debug location override");
            onLocation(dbgLat, dbgLng);
            return;
        }
        if (testMode) {
            double[] u = ((TestData) data).getUserLocation();
            if (u == null) {
                noLocation("Геолокация недоступна. Выберите остановку вручную.", null, null);
            } else {
                onLocation(u[0], u[1]);
            }
            return;
        }
        if (!hasLocationPermission()) {
            if (!permissionAsked) {
                permissionAsked = true;
                showStatus("Нужен доступ к геолокации", null, null);
                requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            } else {
                showPermissionDenied();
            }
            return;
        }
        if (!isLocationEnabled()) {
            recheckOnResume = true;
            noLocation("Геолокация выключена. Включите её или выберите остановку вручную.", "Включить", () ->
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return;
        }
        startLocationWatch(false);
    }

    private boolean hasLocationPermission() {
        Context c = requireContext();
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION || mapView == null) {
            return;
        }
        if (hasLocationPermission()) {
            start();
        } else {
            showPermissionDenied();
        }
    }

    private void showPermissionDenied() {
        noLocation("Нет доступа к геолокации. Разрешите его или выберите остановку вручную.", "Разрешить", () -> {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            } else {
                recheckOnResume = true;
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null)));
            }
        });
    }

    /** Shows why there is no position and lets the user pick a stop by hand. */
    private void noLocation(String message, @Nullable String actionLabel, @Nullable Runnable onAction) {
        showStatus(message, actionLabel, onAction);
        enterPickMode();
    }

    /** Uses the app's own location helper; its state is read by polling, with a timeout. */
    private void startLocationWatch(final boolean forButton) {
        locationHelper = new MyLocationRequestHelper(requireActivity());
        getViewLifecycleOwner().getLifecycle().addObserver(locationHelper);
        locationDeadline = SystemClock.elapsedRealtime() + (forButton ? BUTTON_LOCATION_TIMEOUT_MS : LOCATION_TIMEOUT_MS);
        if (!forButton) {
            showStatus("Определяем местоположение…", null, null);
        }
        final int gen = generation;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gen != generation || locationHelper == null) {
                    return;
                }
                Location loc = locationHelper.getLocation();
                LocationQuality.Verdict v = loc == null ? LocationQuality.Verdict.MISSING
                        : LocationQuality.classify(loc.getLatitude(), loc.getLongitude(), loc.getTime(),
                                System.currentTimeMillis(), MAX_FIX_AGE_MS);
                if (v == LocationQuality.Verdict.OK) {
                    Log.i(TAG, "fix ok, accuracy " + loc.getAccuracy() + " m, age "
                            + (System.currentTimeMillis() - loc.getTime()) + " ms");
                    stopLocationWatch();
                    if (forButton) {
                        locationNote = null;
                        onMyLocation(loc.getLatitude(), loc.getLongitude());
                    } else {
                        onLocation(loc.getLatitude(), loc.getLongitude());
                    }
                } else if (SystemClock.elapsedRealtime() > locationDeadline) {
                    Log.w(TAG, "no usable fix: " + v);
                    stopLocationWatch();
                    if (forButton) {
                        finishButtonSearchWithoutFreshFix(v, loc);
                    } else if (v == LocationQuality.Verdict.STALE && loc != null) {
                        offerStaleLocation(loc);
                    } else {
                        noLocation("Не удалось определить местоположение. Повторите или выберите остановку вручную.",
                                "Повторить", NearbyStopFragment.this::start);
                    }
                } else {
                    handler.postDelayed(this, LOCATION_POLL_MS);
                }
            }
        }, LOCATION_POLL_MS);
    }

    // ---------- "my location" button ----------

    /** Finds the user again and centres the map on them, without reloading stops or routes. */
    private void onMyLocationClicked() {
        if (mapView == null || data == null || locationHelper != null) {
            return; // not ready, or a search is already running
        }
        if (testMode) {
            double[] u = ((TestData) data).getUserLocation();
            if (u == null) {
                Toast.makeText(requireContext(), "Геолокация недоступна", Toast.LENGTH_SHORT).show();
            } else {
                onMyLocation(u[0], u[1]);
            }
            return;
        }
        if (!hasLocationPermission()) {
            permissionAsked = true;
            requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (!isLocationEnabled()) {
            recheckOnResume = true;
            showStatus("Геолокация выключена. Включите её, чтобы найти вас на карте.", "Включить", () ->
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return;
        }
        setMyLocationBusy(true);
        Toast.makeText(requireContext(), "Определяем местоположение…", Toast.LENGTH_SHORT).show();
        startLocationWatch(true);
    }

    private void setMyLocationBusy(boolean busy) {
        myLocationButton.setEnabled(!busy);
        myLocationButton.setAlpha(busy ? 0.5f : 1f);
    }

    /** Moves the "you are here" marker, refreshes the distance to the selected stop and centres the camera. */
    private void onMyLocation(double lat, double lng) {
        cameraAuto = false; // the user asked to see themselves: layout changes must not undo it
        setMyLocationBusy(false);
        userLoc = new double[] {lat, lng};
        LatLng at = new LatLng(lat, lng);
        if (map != null) {
            if (userMarker != null) {
                userMarker.setPosition(at);
            } else {
                addUserMarker();
            }
            moveTo(CameraUpdateFactory.newLatLngZoom(at, 16f), true);
        }
        if (selectedStop != null) {
            selectedDistance = Haversine.distanceMeters(lat, lng, selectedStop.getLat(), selectedStop.getLng());
            updateSubtitleNote();
        }
    }

    /** No fresh fix in time: shows the last known position with its age, or says that none was found. */
    private void finishButtonSearchWithoutFreshFix(LocationQuality.Verdict verdict, @Nullable Location loc) {
        setMyLocationBusy(false);
        if (verdict == LocationQuality.Verdict.STALE && loc != null) {
            String age = AgeText.ago(System.currentTimeMillis() - loc.getTime());
            locationNote = age;
            onMyLocation(loc.getLatitude(), loc.getLongitude());
            Toast.makeText(requireContext(), "Свежей координаты нет, показано положение: " + age,
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_LONG).show();
        }
    }

    /** A crosshair icon for the button. */
    private Bitmap myLocationBitmap() {
        int size = dp(28);
        float c = size / 2f;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFF1565C0);
        line.setStrokeWidth(dp(2));
        cv.drawCircle(c, c, dp(8), line);
        cv.drawLine(c, dp(1), c, dp(6), line);
        cv.drawLine(c, size - dp(6), c, size - dp(1), line);
        cv.drawLine(dp(1), c, dp(6), c, line);
        cv.drawLine(size - dp(6), c, size - dp(1), c, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF1565C0);
        cv.drawCircle(c, c, dp(3.5f), dot);
        return bmp;
    }

    /** No fresh fix: lets the user decide whether the last known position is good enough, showing its age. */
    private void offerStaleLocation(Location loc) {
        final double lat = loc.getLatitude();
        final double lng = loc.getLongitude();
        final String age = AgeText.ago(System.currentTimeMillis() - loc.getTime());
        showStatus("Свежей координаты нет. Последнее известное положение: " + age + ".",
                "Использовать его", () -> {
                    leavePickState();
                    statusBar.setVisibility(View.GONE);
                    onLocation(lat, lng);
                    locationNote = age;
                    updateSubtitleNote();
                }, "Повторить", this::start);
        enterPickMode();
    }

    private void stopLocationWatch() {
        if (locationHelper != null) {
            if (mapView != null) {
                getViewLifecycleOwner().getLifecycle().removeObserver(locationHelper);
            }
            locationHelper.stopUpdates();
            locationHelper.release();
            locationHelper = null;
        }
    }

    private void onLocation(double lat, double lng) {
        userLoc = new double[] {lat, lng};
        final int gen = generation;
        final List<StopPoint> stops = allStops;
        final NearbyDataSource src = data;
        executor.execute(() -> {
            try {
                List<StopMatch> near = NearestStopFinder.findNearby(stops, lat, lng, SEARCH_RADIUS_METERS, NEARBY_LIMIT);
                List<StopPoint> forNames = new ArrayList<>();
                for (StopMatch m : near) {
                    forNames.add(m.getStop());
                }
                src.prefetchNames(forNames);
                Log.i(TAG, "stops within " + (int) SEARCH_RADIUS_METERS + " m: " + near.size()
                        + (near.isEmpty() ? "" : ", nearest " + Math.round(near.get(0).getDistanceMeters()) + " m"));
                post(gen, () -> showNearby(near));
            } catch (Exception e) {
                Log.e(TAG, "nearest stop search failed", e);
                post(gen, () -> showStatus("Не удалось найти остановки рядом", "Повторить", this::start));
            }
        });
    }

    private void showNearby(List<StopMatch> near) {
        if (near.isEmpty()) {
            noLocation("Рядом нет остановок (радиус " + (int) SEARCH_RADIUS_METERS + " м). Выберите остановку вручную.", null, null);
            return;
        }
        statusBar.setVisibility(View.GONE);
        List<StopMatch> shown = new ArrayList<>();
        shown.add(near.get(0));
        shown.addAll(NearbyCandidates.alternatives(near, ALTERNATIVE_EXTRA_METERS, ALTERNATIVE_LIMIT));
        candidates = shown;
        showStop(near.get(0).getStop(), near.get(0).getDistanceMeters());
    }

    private void post(int gen, Runnable r) {
        handler.post(() -> {
            if (gen == generation && mapView != null) {
                r.run();
            }
        });
    }

    private void showStatus(String message, @Nullable String actionLabel, @Nullable Runnable onAction) {
        showStatus(message, actionLabel, onAction, null, null);
    }

    private void showStatus(String message, @Nullable String label, @Nullable Runnable onAction,
            @Nullable String label2, @Nullable Runnable onAction2) {
        statusBar.setVisibility(View.VISIBLE);
        statusView.setText(message);
        action = onAction;
        action2 = onAction2;
        actionButton.setText(label);
        actionButton.setVisibility(label == null ? View.GONE : View.VISIBLE);
        actionButton2.setText(label2);
        actionButton2.setVisibility(label2 == null ? View.GONE : View.VISIBLE);
    }

    // ---------- stop and routes ----------

    /** Shows the stop at once and loads its routes in the background. */
    private void showStop(StopPoint stop, double distanceMeters) {
        stopVehiclePolling();
        clearVehicles();
        cameraAuto = true;
        routesLoadSkipped = false;
        selectedStop = stop;
        selectedDistance = distanceMeters;
        routes = Collections.emptyList();
        colorIndex.clear();
        highlight.clear();
        titleView.setText(data.getStopName(stop.getId()));
        updateSubtitle(data.hasRoutes() ? "загружаем маршруты…" : "маршруты пока не подключены");
        buildCandidateButtons();
        buildChips();
        drawStop();
        directionView.setText("");
        if (data.hasRoutes()) {
            loadRoutes(stop);
        }
    }

    private String subtitleTail = "";

    private void updateSubtitle(String tail) {
        subtitleTail = tail;
        updateSubtitleNote();
    }

    private void updateSubtitleNote() {
        StringBuilder sub = new StringBuilder();
        if (selectedDistance >= 0) {
            sub.append(String.format(Locale.US, "%d м от вас", Math.round(selectedDistance)));
            if (locationNote != null) {
                sub.append(" (положение: ").append(locationNote).append(")");
            }
            sub.append(" · ");
        }
        sub.append(subtitleTail);
        subtitleView.setText(sub);
    }

    private void loadRoutes(StopPoint stop) {
        final int gen = generation;
        final NearbyDataSource src = data;
        executor.execute(() -> {
            try {
                src.loadRoutesForStop(stop.getId());
                post(gen, () -> onRoutesLoaded(stop));
            } catch (Exception e) {
                Log.e(TAG, "routes of stop " + stop.getId() + " not loaded", e);
                final boolean login = e instanceof AppDataSource.Unavailable && ((AppDataSource.Unavailable) e).loginRequired;
                post(gen, () -> onRoutesFailed(stop, login));
            }
        });
    }

    private boolean isStillSelected(StopPoint stop) {
        boolean same = selectedStop != null && selectedStop.getId() == stop.getId();
        if (same && pickMode) {
            routesLoadSkipped = true; // the answer arrived while the user was choosing a stop: load again on cancel
        }
        return same && !pickMode;
    }

    private void onRoutesLoaded(StopPoint stop) {
        if (!isStillSelected(stop)) {
            return;
        }
        statusBar.setVisibility(View.GONE);
        routes = RoutesAtStopSelector.select(stop.getId(), data.getRouteIdsAtStop(stop.getId()), data.getRoutes());
        List<Long> ids = new ArrayList<>();
        colorIndex.clear();
        for (RouteAtStop r : routes) {
            colorIndex.put(r.getRouteId(), ids.size());
            ids.add(r.getRouteId());
        }
        highlight.retainOnly(ids);
        int failed = data.getFailedRouteCount();
        String tail = routes.isEmpty() ? "маршрутов нет" : "маршрутов: " + routes.size();
        updateSubtitle(failed > 0 ? tail + " (не загрузилось: " + failed + ")" : tail);
        buildChips();
        drawStop();
        refreshHighlight(false);
        startVehiclePolling();
    }

    private void onRoutesFailed(StopPoint stop, boolean loginRequired) {
        if (!isStillSelected(stop)) {
            return;
        }
        updateSubtitle("маршруты не загрузились");
        showStatus(loginRequired ? "Войдите в приложение, чтобы видеть маршруты"
                : "Не удалось загрузить маршруты остановки", "Повторить", () -> {
            statusBar.setVisibility(View.GONE);
            updateSubtitle("загружаем маршруты…");
            loadRoutes(stop);
        });
    }

    /** Buttons for the other stops standing almost as close, typically the opposite side of the road. */
    private void buildCandidateButtons() {
        altRow.removeAllViews();
        Context c = requireContext();
        int shown = 0;
        for (StopMatch m : candidates) {
            if (selectedStop != null && m.getStop().getId() == selectedStop.getId()) {
                continue;
            }
            Button b = new Button(c);
            styleSecondaryButton(b);
            b.setText(String.format(Locale.US, "%s · %d м", data.getStopName(m.getStop().getId()),
                    Math.round(m.getDistanceMeters())));
            final StopMatch match = m;
            b.setOnClickListener(v -> {
                highlight.clear();
                showStop(match.getStop(), match.getDistanceMeters());
            });
            altRow.addView(b);
            shown++;
        }
        altScroll.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private void clearCandidates() {
        candidates = Collections.emptyList();
        altRow.removeAllViews();
        altScroll.setVisibility(View.GONE);
    }

    private void buildChips() {
        chipRow.removeAllViews();
        chipFlow.removeAllViews();
        chips.clear();
        Context c = requireContext();
        for (RouteAtStop r : routes) {
            TextView chip = new TextView(c);
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(dp(76));
            chip.setMinHeight(dp(64));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            SpannableStringBuilder sb = new SpannableStringBuilder(r.getName());
            sb.append('\n').append(typeLabel(r.getTypeId()));
            sb.setSpan(new RelativeSizeSpan(0.45f), r.getName().length(), sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            chip.setText(sb);
            chip.setTextSize(24);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            final long id = r.getRouteId();
            chip.setOnClickListener(v -> {
                highlight.toggle(id);
                refreshHighlight(true);
            });
            if (chipsExpanded) {
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dp(4), dp(2), dp(4), dp(2));
                chipFlow.addView(chip, lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dp(4), 0, dp(4), 0);
                chipRow.addView(chip, lp);
            }
            chips.put(id, chip);
        }
        chipScroll.setVisibility(routes.isEmpty() || chipsExpanded ? View.GONE : View.VISIBLE);
        chipFlowScroll.setVisibility(routes.isEmpty() || !chipsExpanded ? View.GONE : View.VISIBLE);
        expandButton.setVisibility(routes.isEmpty() ? View.GONE : View.VISIBLE);
        expandButton.setText(chipsExpanded ? "⌄" : "⌃");
        expandButton.setContentDescription(chipsExpanded ? "Свернуть список маршрутов" : "Раскрыть список маршрутов");
    }

    private static String typeLabel(int typeId) {
        return typeId == RouteInfo.TYPE_TROLLEYBUS ? "троллейбус" : "автобус";
    }

    /**
     * Routes often share a street. Lines get different widths (the first route is the widest and lies at the
     * bottom), so the colours of all routes stay visible side by side instead of the top one hiding the rest.
     */
    private int lineWidthOf(long routeId) {
        Integer idx = colorIndex.get(routeId);
        int n = Math.max(1, routes.size());
        if (highlight.hasSelection()) {
            return dp(4); // a selected route keeps the standard width; the others are only dimmed
        }
        return dp(4) + ((n - 1) - (idx == null ? 0 : idx)) * dp(3);
    }

    private float lineZOf(long routeId) {
        Integer idx = colorIndex.get(routeId);
        return 1f + (idx == null ? 0 : idx) / 100f;
    }

    /** Colours follow the order of routes at the stop, so routes shown together never share a colour. */
    private int colorOf(long routeId) {
        Integer idx = colorIndex.get(routeId);
        return PALETTE[(idx == null ? 0 : idx) % PALETTE.length];
    }

    /** Applies the selection to chips, direction text, map lines and the camera. Does not rebuild geometry. */
    private void refreshHighlight(boolean moveCamera) {
        for (RouteAtStop r : routes) {
            long id = r.getRouteId();
            boolean sel = highlight.isSelected(id);
            TextView chip = chips.get(id);
            if (chip != null) {
                styleChip(chip, colorOf(id), sel, highlight.hasSelection() && !sel);
            }
            float alpha = highlight.alphaFor(id);
            List<Polyline> pl = polylines.get(id);
            if (pl != null) {
                for (Polyline p : pl) {
                    p.setColor(withAlpha(colorOf(id), alpha));
                    p.setWidth(lineWidthOf(id));
                    p.setZIndex(sel ? 3f : lineZOf(id));
                }
            }
            List<Marker> ms = arrows.get(id);
            if (ms != null) {
                for (Marker m : ms) {
                    m.setAlpha(alpha);
                    m.setZIndex(sel ? 4f : 2f);
                }
            }
        }
        for (VehicleMarker vm : vehicleMarkers.values()) {
            boolean sel = highlight.isSelected(vm.routeId);
            vm.marker.setAlpha(highlight.alphaFor(vm.routeId));
            vm.marker.setZIndex(sel ? 8f : 7f);
        }
        updateDirectionText();
        allButton.setVisibility(highlight.hasSelection() ? View.VISIBLE : View.GONE);
        if (moveCamera) {
            fitCamera(highlight.getSelectedRouteId());
        }
    }

    private void styleChip(TextView chip, int color, boolean selected, boolean dimmed) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), color);
        bg.setColor(selected ? color : withAlpha(color, 0.12f));
        chip.setBackground(bg);
        chip.setTextColor(selected ? Color.WHITE : color);
        chip.setAlpha(dimmed ? 0.55f : 1f);
    }

    private void updateDirectionText() {
        Long id = highlight.getSelectedRouteId();
        if (id == null) {
            directionView.setText(routes.isEmpty() ? "" : "Нажмите на номер, чтобы выделить маршрут");
            return;
        }
        RouteAtStop sel = null;
        for (RouteAtStop r : routes) {
            if (r.getRouteId() == id) {
                sel = r;
            }
        }
        if (sel == null) {
            directionView.setText("");
            return;
        }
        if (!sel.isDirectionKnown()) {
            directionView.setText("Маршрут " + sel.getName() + ": направление неизвестно");
            return;
        }
        StringBuilder sb = new StringBuilder("Маршрут ").append(sel.getName()).append(": ");
        boolean first = true;
        for (int idx : sel.getDirectionIndexes()) {
            String label = data.getDirectionLabel(sel.getRouteId(), idx);
            if (!first) {
                sb.append("; ");
            }
            sb.append(label == null ? "направление " + idx : label);
            first = false;
        }
        directionView.setText(sb);
    }

    // ---------- map ----------

    private void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setRotateGesturesEnabled(false); // badge arrows are drawn for a north-up map
        updateMapInsets();
        map.setOnMapLoadedCallback(() -> {
            mapLoaded = true;
            if (pendingCamera != null) {
                map.moveCamera(pendingCamera);
                pendingCamera = null;
            }
        });
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == REASON_GESTURE) {
                cameraAuto = false; // the user took the map: later layout changes must not move it
            }
        });
        map.setOnMapClickListener(latLng -> hideVehicleInfo());
        map.setOnMarkerClickListener(marker -> {
            StopPoint s = pickMarkers.get(marker);
            if (pickMode && s != null) {
                chooseStop(s);
                return true;
            }
            if (showVehicleInfo(marker)) {
                return true;
            }
            return false;
        });
        if (pickMode) {
            showPickMarkers();
        } else if (selectedStop != null) {
            drawStop();
            refreshHighlight(false);
        }
    }

    /** Adds the geometry of the current stop once; selection later only restyles these objects. */
    private void drawStop() {
        if (map == null || selectedStop == null) {
            return;
        }
        map.setOnCameraIdleListener(null);
        map.clear();
        polylines.clear();
        arrows.clear();
        shownLines.clear();
        pickMarkers.clear();
        vehicleMarkers.clear();
        addUserMarker();
        map.addMarker(new MarkerOptions().position(new LatLng(selectedStop.getLat(), selectedStop.getLng()))
                .title(data.getStopName(selectedStop.getId())).zIndex(6f));
        Map<Integer, BitmapDescriptor> arrowIcons = new HashMap<>();
        for (RouteAtStop r : routes) {
            long id = r.getRouteId();
            int color = colorOf(id);
            List<Polyline> pl = new ArrayList<>();
            List<Marker> ms = new ArrayList<>();
            List<RouteLine> used = new ArrayList<>();
            for (RouteLine line : data.getLines(id)) {
                boolean servesStop = !r.isDirectionKnown() || r.getDirectionIndexes().contains(line.getDirectionIndex());
                if (!servesStop || !line.isDrawable()) {
                    continue;
                }
                PolylineOptions po = new PolylineOptions().color(color).width(lineWidthOf(id)).geodesic(false)
                        .zIndex(lineZOf(id));
                for (int i = 0; i < line.size(); i++) {
                    po.add(new LatLng(line.getLat(i), line.getLng(i)));
                }
                pl.add(map.addPolyline(po));
                BitmapDescriptor icon = arrowIcons.get(color);
                if (icon == null) {
                    icon = BitmapDescriptorFactory.fromBitmap(arrowBitmap(color));
                    arrowIcons.put(color, icon);
                }
                int last = line.size() - 1;
                ms.add(map.addMarker(new MarkerOptions()
                        .position(new LatLng(line.getLat(last), line.getLng(last)))
                        .icon(icon).anchor(0.5f, 0.5f).flat(true)
                        .rotation((float) line.finalBearing())));
                used.add(line);
            }
            polylines.put(id, pl);
            arrows.put(id, ms);
            shownLines.put(id, used);
        }
        renderVehicles(shownVehicles);
        fitCamera(null);
    }

    private void addUserMarker() {
        if (map != null && userLoc != null) {
            userMarker = map.addMarker(new MarkerOptions().position(new LatLng(userLoc[0], userLoc[1])).title("Вы здесь")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).zIndex(5f));
        }
    }

    private void fitCamera(@Nullable Long routeId) {
        if (map == null || selectedStop == null) {
            return;
        }
        // 'var': the compile-only classpath has no InnerClasses attribute, so LatLngBounds.Builder cannot be named
        var b = LatLngBounds.builder();
        // Always the neighbourhood of the stop (about 330 m), so a route never zooms the map out to the whole city.
        b.include(new LatLng(selectedStop.getLat() - OVERVIEW_LAT_SPAN, selectedStop.getLng() - OVERVIEW_LNG_SPAN));
        b.include(new LatLng(selectedStop.getLat() + OVERVIEW_LAT_SPAN, selectedStop.getLng() + OVERVIEW_LNG_SPAN));
        List<VehiclePosition> routeVehicles = new ArrayList<>();
        if (routeId != null) {
            for (VehiclePosition v : shownVehicles) {
                if (v.getRouteId() == routeId) {
                    routeVehicles.add(v);
                }
            }
        }
        // The selected route's vehicles near the stop are shown too; its line is not fitted (it crosses the city).
        for (double[] pt : CameraFocus.points(selectedStop.getLat(), selectedStop.getLng(), userLoc,
                routeVehicles, CAMERA_VEHICLE_RADIUS_METERS)) {
            b.include(new LatLng(pt[0], pt[1]));
        }
        moveTo(CameraUpdateFactory.newLatLngBounds(b.build(), dp(48)), true);
    }

    private void moveTo(CameraUpdate u, boolean animate) {
        if (mapLoaded) {
            if (animate) {
                map.animateCamera(u);
            } else {
                map.moveCamera(u);
            }
        } else {
            pendingCamera = u;
        }
    }

    private Bitmap arrowBitmap(int color) {
        int s = dp(26);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Path path = new Path();
        path.moveTo(s / 2f, dp(2));
        path.lineTo(s - dp(3), s - dp(3));
        path.lineTo(s / 2f, s - dp(8));
        path.lineTo(dp(3), s - dp(3));
        path.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        cv.drawPath(path, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(dp(1.5f));
        cv.drawPath(path, stroke);
        return bmp;
    }

    // ---------- vehicles in real time ----------

    private void startVehiclePolling() {
        if (vehiclePolling || routes.isEmpty() || data == null || mapView == null) {
            return;
        }
        vehiclePolling = true;
        vehicleFailures = 0;
        handler.post(vehicleTick);
    }

    private void stopVehiclePolling() {
        vehiclePolling = false;
        handler.removeCallbacks(vehicleTick);
    }

    private final Runnable vehicleTick = new Runnable() {
        @Override
        public void run() {
            if (!vehiclePolling) {
                return;
            }
            fetchVehicles();
            handler.postDelayed(this, VEHICLE_POLL_MS);
        }
    };

    private void fetchVehicles() {
        if (vehicleRequestInFlight || selectedStop == null || executor == null) {
            return;
        }
        vehicleRequestInFlight = true;
        final int gen = generation;
        final long stopId = selectedStop.getId();
        final NearbyDataSource src = data;
        final List<Long> ids = new ArrayList<>();
        for (RouteAtStop r : routes) {
            ids.add(r.getRouteId());
        }
        executor.execute(() -> {
            List<VehiclePosition> all = null;
            try {
                all = src.loadVehicles(ids);
            } catch (Exception e) {
                Log.w(TAG, "vehicles not loaded: " + e);
            }
            final List<VehiclePosition> result = all;
            handler.post(() -> {
                vehicleRequestInFlight = false;
                if (gen != generation || mapView == null || !vehiclePolling
                        || selectedStop == null || selectedStop.getId() != stopId) {
                    return;
                }
                if (result == null) {
                    vehicleFailures++;
                    if (vehicleFailures >= VEHICLE_FAILURES_BEFORE_NOTE) {
                        vehicleNote.setText("Нет связи: показаны последние известные позиции, они могут устареть");
                    }
                    return;
                }
                vehicleFailures = 0;
                applyVehicles(result);
            });
        });
    }

    private void applyVehicles(List<VehiclePosition> reported) {
        long nowSec = System.currentTimeMillis() / 1000;
        // The snapshot's own time is the reference (see VehicleFilter.referenceSec): the phone clock may be off.
        long reference = VehicleFilter.referenceSec(reported, nowSec);
        snapshotReferenceSec = reference;
        if (!reported.isEmpty() && VehicleFilter.latestTimestamp(reported) == 0L) {
            Log.w(TAG, "reports without timestamps: " + reported.size() + " will be hidden");
        }
        List<VehiclePosition> shown = VehicleFilter.select(reported, routes, reference, VEHICLE_MAX_AGE_SEC);
        shownVehicles = shown;
        renderVehicles(shown);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        long snapshotAgeSec = Math.max(0L, nowSec - reference);
        String stale = snapshotAgeSec > 60L ? " · снимок " + AgeText.ago(snapshotAgeSec * 1000L) : "";
        vehicleNote.setText(shown.isEmpty()
                ? "Транспорта на этих маршрутах сейчас нет данных · " + time + stale
                : "Транспорт на карте: " + shown.size() + " · обновлено " + time + stale);
    }

    /** Moves existing markers, adds new ones and removes those that left; never rebuilds all of them. */
    private void renderVehicles(List<VehiclePosition> shown) {
        if (map == null) {
            return;
        }
        Map<Long, VehiclePosition> byDevice = new HashMap<>();
        for (VehiclePosition p : shown) {
            byDevice.put(p.getDeviceId(), p);
        }
        java.util.Iterator<Map.Entry<Long, VehicleMarker>> it = vehicleMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, VehicleMarker> e = it.next();
            if (!byDevice.containsKey(e.getKey())) {
                e.getValue().marker.remove();
                it.remove();
            }
        }
        double[] lats = new double[shown.size()];
        double[] lngs = new double[shown.size()];
        for (int i = 0; i < lats.length; i++) {
            lats[i] = shown.get(i).getLat();
            lngs[i] = shown.get(i).getLng();
        }
        // Vehicles at (almost) the same spot: the icons are shifted apart, the real positions are not touched.
        int[] stacks = MarkerStacking.stackIndexes(lats, lngs, STACK_RADIUS_METERS);
        for (int i = 0; i < shown.size(); i++) {
            VehiclePosition p = shown.get(i);
            String name = routeName(p.getRouteId());
            // proto3 sends 0.0 when the vehicle has no bearing, so exactly 0.0 is treated as unknown (no arrow)
            int bucket = Double.isNaN(p.getBearing()) || p.getBearing() == 0.0 ? -1
                    : (int) Math.round(((p.getBearing() % 360 + 360) % 360) / 22.5) % 16;
            LatLng at = new LatLng(p.getLat(), p.getLng());
            float[] anchor = stackAnchor(stacks[i]);
            VehicleMarker vm = vehicleMarkers.get(p.getDeviceId());
            if (vm != null && vm.bucket == bucket && vm.routeId == p.getRouteId()) {
                vm.marker.setPosition(at);
                if (vm.stack != stacks[i]) {
                    vm.marker.setAnchor(anchor[0], anchor[1]);
                    vm.stack = stacks[i];
                }
                continue;
            }
            if (vm != null) {
                vm.marker.remove();
            }
            Marker m = map.addMarker(new MarkerOptions().position(at)
                    .icon(vehicleIcon(name, colorOf(p.getRouteId()), bucket)).anchor(anchor[0], anchor[1]).flat(false)
                    .alpha(highlight.alphaFor(p.getRouteId()))
                    .zIndex(highlight.isSelected(p.getRouteId()) ? 8f : 7f));
            vehicleMarkers.put(p.getDeviceId(), new VehicleMarker(m, p.getRouteId(), bucket, stacks[i]));
        }
    }

    /** Icon anchor for the n-th marker of a pile: centred, then above, below and to the side of the real spot. */
    private static float[] stackAnchor(int n) {
        return STACK_ANCHORS[n % STACK_ANCHORS.length];
    }

    private void hideVehicleInfo() {
        vehicleInfo.setVisibility(View.GONE);
        if (raisedVehicle != null) {
            for (VehicleMarker vm : vehicleMarkers.values()) {
                if (vm.marker.equals(raisedVehicle)) {
                    vm.marker.setZIndex(highlight.isSelected(vm.routeId) ? 8f : 7f);
                }
            }
            raisedVehicle = null;
        }
    }

    /** Compact facts about the tapped vehicle: only what the app really knows (no arrival forecast). */
    private boolean showVehicleInfo(Marker marker) {
        for (Map.Entry<Long, VehicleMarker> e : vehicleMarkers.entrySet()) {
            if (!e.getValue().marker.equals(marker)) {
                continue;
            }
            VehiclePosition p = null;
            for (VehiclePosition v : shownVehicles) {
                if (v.getDeviceId() == e.getKey()) {
                    p = v;
                }
            }
            if (p == null) {
                return false;
            }
            RouteAtStop r = null;
            for (RouteAtStop candidate : routes) {
                if (candidate.getRouteId() == p.getRouteId()) {
                    r = candidate;
                }
            }
            String type = r == null ? "транспорт" : typeLabel(r.getTypeId());
            String dir = data.getDirectionLabel(p.getRouteId(), p.getDirectionIndex());
            hideVehicleInfo();
            raisedVehicle = marker;
            vehicleInfo.setText(VehicleInfoText.format(routeName(p.getRouteId()), type, dir,
                    Math.max(0L, snapshotReferenceSec - p.getTimestampSec())));
            vehicleInfo.setVisibility(View.VISIBLE);
            marker.setZIndex(9f);
            return true;
        }
        return false;
    }

    private void clearVehicles() {
        hideVehicleInfo();
        for (VehicleMarker vm : vehicleMarkers.values()) {
            vm.marker.remove();
        }
        vehicleMarkers.clear();
        shownVehicles = Collections.emptyList();
        vehicleNote.setText("");
    }

    private String routeName(long routeId) {
        for (RouteAtStop r : routes) {
            if (r.getRouteId() == routeId) {
                return r.getName();
            }
        }
        return String.valueOf(routeId);
    }

    private BitmapDescriptor vehicleIcon(String label, int color, int bucket) {
        String key = label + "/" + color + "/" + bucket;
        BitmapDescriptor icon = vehicleIcons.get(key);
        if (icon == null) {
            icon = BitmapDescriptorFactory.fromBitmap(vehicleBitmap(label, color, bucket));
            vehicleIcons.put(key, icon);
        }
        return icon;
    }

    /**
     * A rounded badge with a large, high-contrast route number and, separately, a small pointer outside the badge
     * toward the direction of travel (bucket -1: direction unknown, no pointer). The badge centre is the icon centre.
     */
    private Bitmap vehicleBitmap(String label, int color, int bucket) {
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(dp(17));
        float badgeH = dp(30);
        float badgeW = Math.max(badgeH, txt.measureText(label) + dp(16));
        int pad = dp(15);
        int w = Math.round(badgeW) + 2 * pad;
        int h = Math.round(badgeH) + 2 * pad;
        float cx = w / 2f;
        float cy = h / 2f;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.WHITE);
        ring.setStrokeWidth(dp(2.5f));
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x66000000);
        edge.setStrokeWidth(dp(1));
        RectF badge = new RectF(cx - badgeW / 2, cy - badgeH / 2, cx + badgeW / 2, cy + badgeH / 2);
        if (bucket >= 0) {
            double rad = Math.toRadians(bucket * 22.5);
            double dx = Math.sin(rad);
            double dy = -Math.cos(rad);
            double toEdgeX = Math.abs(dx) < 1e-6 ? Double.MAX_VALUE : (badgeW / 2) / Math.abs(dx);
            double toEdgeY = Math.abs(dy) < 1e-6 ? Double.MAX_VALUE : (badgeH / 2) / Math.abs(dy);
            float base = (float) Math.min(toEdgeX, toEdgeY) + dp(1);
            float tip = base + dp(11);
            float half = dp(7);
            Path arrow = new Path();
            arrow.moveTo(cx + (float) (dx * tip), cy + (float) (dy * tip));
            arrow.lineTo(cx + (float) (dx * base - dy * half), cy + (float) (dy * base + dx * half));
            arrow.lineTo(cx + (float) (dx * base + dy * half), cy + (float) (dy * base - dx * half));
            arrow.close();
            cv.drawPath(arrow, fill);
            cv.drawPath(arrow, ring);
        }
        cv.drawRoundRect(badge, dp(9), dp(9), fill);
        cv.drawRoundRect(badge, dp(9), dp(9), ring);
        RectF outer = new RectF(badge);
        outer.inset(-dp(1.75f), -dp(1.75f));
        cv.drawRoundRect(outer, dp(10), dp(10), edge);
        cv.drawText(label, cx, cy - (txt.descent() + txt.ascent()) / 2f, txt);
        return bmp;
    }

    // ---------- manual stop selection ----------

    private void togglePickMode() {
        if (pickMode) {
            exitPickMode();
        } else {
            enterPickMode();
        }
    }

    private void enterPickMode() {
        hideVehicleInfo();
        if (allStops.isEmpty()) {
            return;
        }
        pickMode = true;
        stopVehiclePolling();
        pickHint.setVisibility(View.VISIBLE);
        styleChangeStopButton(true);
        showPickMarkers();
    }

    /** Leaves manual selection without touching the map content. */
    private void leavePickState() {
        for (Marker m : pickMarkers.keySet()) {
            m.remove(); // never leave the orange "pick a stop" markers behind when selection ends
        }
        pickMarkers.clear();
        pickMode = false;
        pickHint.setVisibility(View.GONE);
        styleChangeStopButton(false);
        if (map != null) {
            map.setOnCameraIdleListener(null);
        }
    }

    private void exitPickMode() {
        leavePickState();
        if (selectedStop != null) {
            drawStop();
            refreshHighlight(false);
            if (routesLoadSkipped && data != null && data.hasRoutes()) {
                routesLoadSkipped = false;
                updateSubtitle("загружаем маршруты…");
                loadRoutes(selectedStop);
            }
            startVehiclePolling();
        } else if (map != null) {
            map.clear();
            forgetMapObjects();
        }
    }

    /** map.clear() removed these from the map: drop our references so nothing updates dead objects. */
    private void forgetMapObjects() {
        userMarker = null;
        polylines.clear();
        arrows.clear();
        shownLines.clear();
        vehicleMarkers.clear();
    }

    /**
     * Shows markers for the stops in view. The camera stays where the user has it: someone who looked at another
     * part of the city and pressed "change" wants stops there, not a jump back to their own position. Only a map
     * that was never positioned (world view) is moved: to the selected stop, else the user, else the stops.
     */
    private void showPickMarkers() {
        if (map == null) {
            return;
        }
        cameraAuto = false;
        if (map.getCameraPosition().zoom < PICK_MIN_ZOOM) {
            LatLng target = selectedStop != null ? new LatLng(selectedStop.getLat(), selectedStop.getLng())
                    : userLoc != null ? new LatLng(userLoc[0], userLoc[1]) : centroid(allStops);
            if (target != null) {
                moveTo(CameraUpdateFactory.newLatLngZoom(target, 16f), false);
            }
        }
        map.setOnCameraIdleListener(this::refreshPickMarkers);
        refreshPickMarkers();
    }

    /** Shows at most PICK_LIMIT stops nearest to the middle of the visible map, so a big city stays fast. */
    private void refreshPickMarkers() {
        if (!pickMode || map == null || mapView == null) {
            return;
        }
        LatLng c = map.getCameraPosition().target;
        LatLngBounds vis = map.getProjection().getVisibleRegion().latLngBounds;
        double radius = Haversine.distanceMeters(vis.northeast.latitude, vis.northeast.longitude,
                vis.southwest.latitude, vis.southwest.longitude) / 2.0;
        final int gen = generation;
        final List<StopPoint> stops = allStops;
        final NearbyDataSource src = data;
        executor.execute(() -> {
            try {
                List<StopMatch> near = NearestStopFinder.findNearby(stops, c.latitude, c.longitude, radius, PICK_LIMIT);
                List<StopPoint> forNames = new ArrayList<>();
                for (StopMatch m : near) {
                    forNames.add(m.getStop());
                }
                src.prefetchNames(forNames);
                post(gen, () -> drawPickMarkers(forNames));
            } catch (Exception e) {
                Log.e(TAG, "pick markers failed", e);
            }
        });
    }

    private void drawPickMarkers(List<StopPoint> stops) {
        if (!pickMode || map == null) {
            return;
        }
        map.clear();
        forgetMapObjects();
        pickMarkers.clear();
        for (StopPoint s : stops) {
            boolean current = selectedStop != null && selectedStop.getId() == s.getId();
            Marker m = map.addMarker(new MarkerOptions().position(new LatLng(s.getLat(), s.getLng()))
                    .title(data.getStopName(s.getId()))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            current ? BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_ORANGE)));
            pickMarkers.put(m, s);
        }
        addUserMarker();
    }

    private void chooseStop(StopPoint stop) {
        leavePickState();
        statusBar.setVisibility(View.GONE);
        double d = userLoc == null ? -1
                : Haversine.distanceMeters(userLoc[0], userLoc[1], stop.getLat(), stop.getLng());
        highlight.clear();
        clearCandidates();
        showStop(stop, d);
    }

    private static LatLng centroid(List<StopPoint> stops) {
        double la = 0;
        double ln = 0;
        int n = 0;
        for (StopPoint s : stops) {
            if (s.getLat() != null && s.getLng() != null) {
                la += s.getLat();
                ln += s.getLng();
                n++;
            }
        }
        return n == 0 ? null : new LatLng(la / n, ln / n);
    }

    // ---------- helpers ----------

    private TextView text(Context c, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    /** Rounded pill: filled blue to change the stop, white with a blue outline to cancel the manual pick. */
    private void styleChangeStopButton(boolean cancel) {
        final int blue = PALETTE[0]; // the same blue as the route chips and the my-location button
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(cancel ? Color.WHITE : blue);
        bg.setStroke(dp(2), blue);
        changeStopButton.setBackground(bg);
        changeStopButton.setText(cancel ? "Отмена" : "Сменить");
        changeStopButton.setTextColor(cancel ? blue : Color.WHITE);
        changeStopButton.setAllCaps(false);
        changeStopButton.setTextSize(14f);
        changeStopButton.setMinHeight(0);
        changeStopButton.setMinimumHeight(0);
        changeStopButton.setPadding(dp(16), 0, dp(16), 0);
        changeStopButton.setTypeface(Typeface.DEFAULT_BOLD);
        changeStopButton.setStateListAnimator(null);
        // A Material theme may tint whatever background we set: keep the two colours exactly as drawn.
        changeStopButton.setBackgroundTintList(null);
    }

    /** Secondary action (retry, neighbouring stop): white pill with a blue outline, same blue as the main button. */
    private void styleSecondaryButton(Button b) {
        final int blue = PALETTE[0];
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), blue);
        b.setBackground(bg);
        b.setTextColor(blue);
        b.setAllCaps(false);
        b.setTextSize(14f);
        b.setSingleLine(true);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setStateListAnimator(null);
        b.setBackgroundTintList(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), dp(8), dp(4));
        b.setLayoutParams(lp);
    }

    private GradientDrawable cardBackground(int radius) {
        return cardBackground(radius, Color.WHITE);
    }

    private GradientDrawable cardBackground(int radius, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radius);
        bg.setColor(color);
        return bg;
    }

    /** The cards hide part of the map: tell the map, so its camera, logo and buttons keep to the free part. */
    private void updateMapInsets() {
        if (topCardView == null || dockView == null) {
            return;
        }
        if (rootView.getHeight() == 0) {
            return; // not laid out yet
        }
        int top = topCardView.getBottom();
        int bottom = Math.min(rootView.getHeight() - dockView.getTop(), rootView.getHeight() / 2);
        if (map != null && top >= 0 && bottom >= 0 && (top != mapPadTop || bottom != mapPadBottom)) {
            mapPadTop = top;
            mapPadBottom = bottom;
            map.setPadding(0, top, 0, bottom);
            if (cameraAuto && selectedStop != null && !pickMode) {
                fitCamera(highlight.getSelectedRouteId()); // the free part of the map changed: centre again
            }
        }
        if (fabLp != null && bottom >= 0 && fabLp.bottomMargin != bottom + dp(12)) {
            fabLp.bottomMargin = bottom + dp(12);
            myLocationButton.setLayoutParams(fabLp);
        }
    }

    private static final String[] ORIGINAL_NAVIGATION_IDS = {
        "nav_routes", "nav_profile", "nav_life", "nav_life_hint", "nav_settings", "nav_image_qr", "nav_qr",
        "viewShadow", "viewBottomBorder"
    };

    /**
     * The original ONAY! tabs, QR button and payment stay in the app but are hidden by the layout patch.
     * This button shows or hides them again (found by id name, so this code does not depend on the app's R class).
     */
    private void toggleOriginalNavigation() {
        originalNavigationShown = !originalNavigationShown;
        applyOriginalNavigation();
    }

    /** Called also on every resume: the host activity may restore its bar (for example after being recreated). */
    private void applyOriginalNavigation() {
        if (rootView == null) {
            return;
        }
        Context c = requireContext();
        for (String name : ORIGINAL_NAVIGATION_IDS) {
            int id = c.getResources().getIdentifier(name, "id", c.getPackageName());
            View v = id == 0 ? null : requireActivity().findViewById(id);
            if (v != null) {
                v.setVisibility(originalNavigationShown ? View.VISIBLE : View.GONE);
            }
        }
        // The round QR button reaches above the container when the bar is shown.
        rootView.setPadding(0, 0, 0, originalNavigationShown ? dp(44) : 0);
    }

    /**
     * The app fills its stop database when the original transport map is opened. On the very first start the
     * database is empty, so this opens that map once per process (clicking the app's own Routes tab). The tab bar is
     * shown for the trip and hidden again when this screen comes back.
     */
    private void openRoutesTabOnce() {
        if (routesTabOpenedForStops || testMode || rootView == null) {
            return;
        }
        Context c = requireContext();
        int id = c.getResources().getIdentifier("nav_routes", "id", c.getPackageName());
        View tab = id == 0 ? null : requireActivity().findViewById(id);
        if (tab == null) {
            return;
        }
        routesTabOpenedForStops = true;
        recheckOnResume = true; // when the user returns, look for stops again
        for (String name : ORIGINAL_NAVIGATION_IDS) {
            int viewId = c.getResources().getIdentifier(name, "id", c.getPackageName());
            View v = viewId == 0 ? null : requireActivity().findViewById(viewId);
            if (v != null) {
                v.setVisibility(View.VISIBLE);
            }
        }
        tab.performClick();
    }

    private void toggleExpandedChips() {
        chipsExpanded = !chipsExpanded;
        buildChips();
        refreshHighlight(false);
    }

    private int dp(float v) {
        return (int) (v * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ---------- lifecycle ----------

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        applyOriginalNavigation();
        if (!routes.isEmpty() && !pickMode) {
            startVehiclePolling();
        }
        if (recheckOnResume) {
            recheckOnResume = false;
            start();
        }
    }

    @Override
    public void onPause() {
        stopVehiclePolling();
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        mapView.onStop();
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle b = new Bundle();
            mapView.onSaveInstanceState(b);
            outState.putBundle(SAVED_MAP, b);
        }
    }

    @Override
    public void onDestroyView() {
        generation++;
        vehiclePolling = false;
        handler.removeCallbacksAndMessages(null);
        stopLocationWatch();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (mapView != null) {
            mapView.onDestroy();
        }
        mapView = null;
        map = null;
        userMarker = null;
        mapLoaded = false;
        pendingCamera = null;
        pickMode = false;
        cameraAuto = true;
        mapPadTop = -1;
        mapPadBottom = -1;
        rootView = null;
        topCardView = null;
        dockView = null;
        polylines.clear();
        arrows.clear();
        chips.clear();
        pickMarkers.clear();
        shownLines.clear();
        vehicleMarkers.clear();
        vehicleIcons.clear();
        shownVehicles = Collections.emptyList();
        super.onDestroyView();
    }
}
