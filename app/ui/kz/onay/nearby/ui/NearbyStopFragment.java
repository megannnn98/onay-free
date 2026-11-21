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
import android.widget.LinearLayout;
import android.widget.TextView;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kz.onay.nearby.logic.Haversine;
import kz.onay.nearby.logic.LocationQuality;
import kz.onay.nearby.logic.NearbyCandidates;
import kz.onay.nearby.logic.NearestStopFinder;
import kz.onay.nearby.logic.RouteAtStop;
import kz.onay.nearby.logic.RouteHighlightState;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.RoutesAtStopSelector;
import kz.onay.nearby.logic.StopMatch;
import kz.onay.nearby.logic.StopPoint;
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
    private static final long LOCATION_TIMEOUT_MS = 15_000L;
    private static final long LOCATION_POLL_MS = 400L;
    private static final long MAX_FIX_AGE_MS = 120_000L;
    private static final int[] PALETTE = {
        0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A, 0xFF00838F, 0xFFAD1457, 0xFF4E342E
    };

    private final RouteHighlightState highlight = new RouteHighlightState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, List<Polyline>> polylines = new HashMap<>();
    private final Map<Long, List<Marker>> arrows = new HashMap<>();
    private final Map<Long, TextView> chips = new HashMap<>();
    private final Map<Long, Integer> colorIndex = new HashMap<>();
    private final Map<Marker, StopPoint> pickMarkers = new HashMap<>();
    private final Map<Long, List<RouteLine>> shownLines = new HashMap<>();

    private ExecutorService executor;
    private int generation;
    private boolean testMode;
    private String forcedState;
    private boolean permissionAsked;
    private boolean recheckOnResume;

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
    private Runnable action;
    private HorizontalScrollView altScroll;
    private LinearLayout altRow;
    private HorizontalScrollView chipScroll;
    private LinearLayout chipRow;
    private TextView directionView;
    private TextView pickHint;
    private Button changeStopButton;
    private MapView mapView;
    private GoogleMap map;
    private boolean mapLoaded;
    private CameraUpdate pendingCamera;
    private boolean pickMode;

    private StopPoint selectedStop;
    private List<StopMatch> candidates = Collections.emptyList();
    private List<RouteAtStop> routes = Collections.emptyList();

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

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(4));
        titleView = text(c, 22, true, 0xFF212121);
        subtitleView = text(c, 14, false, 0xFF616161);
        header.addView(titleView);
        header.addView(subtitleView);
        root.addView(header);

        altScroll = new HorizontalScrollView(c);
        altScroll.setHorizontalScrollBarEnabled(false);
        altRow = new LinearLayout(c);
        altRow.setOrientation(LinearLayout.HORIZONTAL);
        altRow.setPadding(dp(12), 0, dp(12), 0);
        altScroll.addView(altRow);
        altScroll.setVisibility(View.GONE);
        root.addView(altScroll);

        LinearLayout status = new LinearLayout(c);
        statusBar = status;
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(4), dp(16), dp(4));
        statusView = text(c, 15, false, 0xFF424242);
        statusView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionButton = new Button(c);
        actionButton.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
        status.addView(statusView);
        status.addView(actionButton);
        root.addView(status);

        chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(c);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        chipScroll.addView(chipRow);
        root.addView(chipScroll);

        directionView = text(c, 14, false, 0xFF424242);
        directionView.setPadding(dp(16), dp(2), dp(16), dp(6));
        root.addView(directionView);

        FrameLayout mapFrame = new FrameLayout(c);
        mapView = new MapView(c);
        mapFrame.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pickHint = text(c, 15, true, Color.WHITE);
        pickHint.setBackgroundColor(0xCC1565C0);
        pickHint.setPadding(dp(16), dp(8), dp(16), dp(8));
        pickHint.setText("Нажмите на остановку на карте");
        pickHint.setVisibility(View.GONE);
        mapFrame.addView(pickHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        root.addView(mapFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        changeStopButton = new Button(c);
        changeStopButton.setText("Сменить остановку");
        changeStopButton.setOnClickListener(v -> togglePickMode());
        root.addView(changeStopButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        stopLocationWatch();
        final int gen = generation;
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
                Log.w(TAG, "route component unavailable: " + e.getMessage());
                post(gen, () -> showStatus("Город не выбран или данные транспорта ещё не готовы", "Повторить", this::start));
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
            showStatus("Остановки ещё не загружены. Откройте обычную карту транспорта и повторите.", "Повторить", this::start);
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
        startLocationWatch();
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
    private void startLocationWatch() {
        locationHelper = new MyLocationRequestHelper(requireActivity());
        getViewLifecycleOwner().getLifecycle().addObserver(locationHelper);
        locationDeadline = SystemClock.elapsedRealtime() + LOCATION_TIMEOUT_MS;
        showStatus("Определяем местоположение…", null, null);
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
                    onLocation(loc.getLatitude(), loc.getLongitude());
                } else if (SystemClock.elapsedRealtime() > locationDeadline) {
                    Log.w(TAG, "no usable fix: " + v);
                    stopLocationWatch();
                    noLocation(v == LocationQuality.Verdict.STALE
                            ? "Координаты устарели, свежих нет. Повторите или выберите остановку вручную."
                            : "Не удалось определить местоположение. Повторите или выберите остановку вручную.",
                            "Повторить", NearbyStopFragment.this::start);
                } else {
                    handler.postDelayed(this, LOCATION_POLL_MS);
                }
            }
        }, LOCATION_POLL_MS);
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
        statusBar.setVisibility(View.VISIBLE);
        statusView.setText(message);
        action = onAction;
        actionButton.setText(actionLabel);
        actionButton.setVisibility(actionLabel == null ? View.GONE : View.VISIBLE);
    }

    // ---------- stop and routes ----------

    private void showStop(StopPoint stop, double distanceMeters) {
        selectedStop = stop;
        routes = RoutesAtStopSelector.select(stop.getId(), data.getRouteIdsAtStop(stop.getId()), data.getRoutes());
        List<Long> ids = new ArrayList<>();
        colorIndex.clear();
        for (RouteAtStop r : routes) {
            colorIndex.put(r.getRouteId(), ids.size());
            ids.add(r.getRouteId());
        }
        highlight.retainOnly(ids);
        titleView.setText(data.getStopName(stop.getId()));
        StringBuilder sub = new StringBuilder();
        if (distanceMeters >= 0) {
            sub.append(String.format(Locale.US, "%d м от вас · ", Math.round(distanceMeters)));
        }
        sub.append(data.hasRoutes() ? "маршрутов: " + routes.size() : "маршруты пока не подключены");
        subtitleView.setText(sub);
        buildCandidateButtons();
        buildChips();
        drawStop();
        refreshHighlight(false);
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
            b.setAllCaps(false);
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            chipRow.addView(chip, lp);
            chips.put(id, chip);
        }
        chipScroll.setVisibility(routes.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String typeLabel(int typeId) {
        return typeId == RouteInfo.TYPE_TROLLEYBUS ? "троллейбус" : "автобус";
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
                    p.setWidth(sel ? dp(8) : dp(4));
                    p.setZIndex(sel ? 3f : 1f);
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
        updateDirectionText();
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
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnMapLoadedCallback(() -> {
            mapLoaded = true;
            if (pendingCamera != null) {
                map.moveCamera(pendingCamera);
                pendingCamera = null;
            }
        });
        map.setOnMarkerClickListener(marker -> {
            StopPoint s = pickMarkers.get(marker);
            if (pickMode && s != null) {
                chooseStop(s);
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
                PolylineOptions po = new PolylineOptions().color(color).width(dp(4)).geodesic(false);
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
        fitCamera(null);
    }

    private void addUserMarker() {
        if (map != null && userLoc != null) {
            map.addMarker(new MarkerOptions().position(new LatLng(userLoc[0], userLoc[1])).title("Вы здесь")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).zIndex(5f));
        }
    }

    private void fitCamera(@Nullable Long routeId) {
        if (map == null || selectedStop == null) {
            return;
        }
        // 'var': the compile-only classpath has no InnerClasses attribute, so LatLngBounds.Builder cannot be named
        var b = LatLngBounds.builder();
        b.include(new LatLng(selectedStop.getLat(), selectedStop.getLng()));
        if (userLoc != null) {
            b.include(new LatLng(userLoc[0], userLoc[1]));
        }
        for (Map.Entry<Long, List<RouteLine>> e : shownLines.entrySet()) {
            if (routeId != null && !routeId.equals(e.getKey())) {
                continue;
            }
            for (RouteLine line : e.getValue()) {
                for (int i = 0; i < line.size(); i++) {
                    b.include(new LatLng(line.getLat(i), line.getLng(i)));
                }
            }
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

    // ---------- manual stop selection ----------

    private void togglePickMode() {
        if (pickMode) {
            exitPickMode();
        } else {
            enterPickMode();
        }
    }

    private void enterPickMode() {
        if (allStops.isEmpty()) {
            return;
        }
        pickMode = true;
        pickHint.setVisibility(View.VISIBLE);
        changeStopButton.setText("Отмена");
        showPickMarkers();
    }

    /** Leaves manual selection without touching the map content. */
    private void leavePickState() {
        pickMode = false;
        pickHint.setVisibility(View.GONE);
        changeStopButton.setText("Сменить остановку");
        if (map != null) {
            map.setOnCameraIdleListener(null);
        }
    }

    private void exitPickMode() {
        leavePickState();
        if (selectedStop != null) {
            drawStop();
            refreshHighlight(false);
        } else if (map != null) {
            map.clear();
        }
    }

    /** Puts the camera near the user (or the middle of all stops) and keeps markers for the stops in view. */
    private void showPickMarkers() {
        if (map == null) {
            return;
        }
        LatLng target = userLoc != null ? new LatLng(userLoc[0], userLoc[1]) : centroid(allStops);
        if (target != null) {
            moveTo(CameraUpdateFactory.newLatLngZoom(target, 16f), false);
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
        if (recheckOnResume) {
            recheckOnResume = false;
            start();
        }
    }

    @Override
    public void onPause() {
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
        mapLoaded = false;
        pendingCamera = null;
        pickMode = false;
        polylines.clear();
        arrows.clear();
        chips.clear();
        pickMarkers.clear();
        shownLines.clear();
        super.onDestroyView();
    }
}
