package kz.onay.nearby.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
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
import kz.onay.nearby.logic.NearestStopFinder;
import kz.onay.nearby.logic.RouteAtStop;
import kz.onay.nearby.logic.RouteHighlightState;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.RoutesAtStopSelector;
import kz.onay.nearby.logic.StopMatch;
import kz.onay.nearby.logic.StopPoint;

/**
 * "Рядом": the nearest stop, big route buttons and the routes of that stop on the map.
 * The view is built in code so that no resource ids have to be added to the app.
 * Route lines are added once per stop; selecting a route only changes colour, width and z-order.
 */
public class NearbyStopFragment extends Fragment {
    private static final String ARG_STATE = "state";
    private static final String SAVED_MAP = "map";
    private static final double SEARCH_RADIUS_METERS = 1000.0;
    private static final int NEARBY_LIMIT = 5;
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

    private NearbyDataSource data;
    private String forcedState;

    private TextView titleView;
    private TextView subtitleView;
    private TextView statusView;
    private Button retryButton;
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
    private double selectedDistance = -1;
    private List<RouteAtStop> routes = Collections.emptyList();

    public static NearbyStopFragment newInstance(@Nullable String forcedState) {
        NearbyStopFragment f = new NearbyStopFragment();
        Bundle b = new Bundle();
        b.putString(ARG_STATE, forcedState);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        forcedState = getArguments() == null ? null : getArguments().getString(ARG_STATE);
        data = new TestData(forcedState);

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

        LinearLayout status = new LinearLayout(c);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(4), dp(16), dp(4));
        statusView = text(c, 15, false, 0xFF424242);
        statusView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        retryButton = new Button(c);
        retryButton.setText("Повторить");
        retryButton.setOnClickListener(v -> start());
        status.addView(statusView);
        status.addView(retryButton);
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
        showStatus("Ищем ближайшую остановку…", false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        start();
    }

    // ---------- loading ----------

    private void start() {
        handler.removeCallbacksAndMessages(null);
        showStatus("Ищем ближайшую остановку…", false);
        handler.postDelayed(this::load, 400);
    }

    private void load() {
        if (mapView == null) {
            return;
        }
        if ("error".equals(forcedState)) {
            showStatus("Не удалось загрузить данные остановок", true);
            return;
        }
        double[] loc = data.getUserLocation();
        if (loc == null) {
            showStatus("Геолокация недоступна. Выберите остановку вручную.", false);
            enterPickMode();
            return;
        }
        List<StopPoint> stops = "empty".equals(forcedState)
                ? Collections.<StopPoint>emptyList() : data.getStops();
        List<StopMatch> near = NearestStopFinder.findNearby(stops, loc[0], loc[1], SEARCH_RADIUS_METERS, NEARBY_LIMIT);
        if (near.isEmpty()) {
            showStatus("Рядом нет остановок. Выберите остановку вручную.", false);
            enterPickMode();
            return;
        }
        statusView.setText("");
        ((View) statusView.getParent()).setVisibility(View.GONE);
        showStop(near.get(0).getStop(), near.get(0).getDistanceMeters());
    }

    private void showStatus(String message, boolean withRetry) {
        ((View) statusView.getParent()).setVisibility(View.VISIBLE);
        statusView.setText(message);
        retryButton.setVisibility(withRetry ? View.VISIBLE : View.GONE);
    }

    // ---------- stop and routes ----------

    private void showStop(StopPoint stop, double distanceMeters) {
        selectedStop = stop;
        selectedDistance = distanceMeters;
        routes = RoutesAtStopSelector.select(stop.getId(), data.getRouteIdsAtStop(stop.getId()), data.getRoutes());
        List<Long> ids = new ArrayList<>();
        colorIndex.clear();
        for (RouteAtStop r : routes) {
            colorIndex.put(r.getRouteId(), ids.size());
            ids.add(r.getRouteId());
        }
        highlight.retainOnly(ids);
        titleView.setText(stop.getName());
        StringBuilder sub = new StringBuilder();
        if (distanceMeters >= 0) {
            sub.append(String.format(Locale.US, "%d м от вас · ", Math.round(distanceMeters)));
        }
        sub.append("маршрутов: ").append(routes.size());
        subtitleView.setText(sub);
        buildChips();
        drawStop();
        refreshHighlight(false);
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
        if (selectedStop != null) {
            drawStop();
            refreshHighlight(false);
        } else if (pickMode) {
            showPickMarkers();
        }
    }

    /** Adds the geometry of the current stop once; selection later only restyles these objects. */
    private void drawStop() {
        if (map == null || selectedStop == null) {
            return;
        }
        map.clear();
        polylines.clear();
        arrows.clear();
        shownLines.clear();
        pickMarkers.clear();
        double[] loc = data.getUserLocation();
        if (loc != null) {
            map.addMarker(new MarkerOptions().position(new LatLng(loc[0], loc[1])).title("Вы здесь")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).zIndex(5f));
        }
        map.addMarker(new MarkerOptions().position(new LatLng(selectedStop.getLat(), selectedStop.getLng()))
                .title(selectedStop.getName()).zIndex(6f));
        Map<Integer, com.google.android.gms.maps.model.BitmapDescriptor> arrowIcons = new HashMap<>();
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
                com.google.android.gms.maps.model.BitmapDescriptor icon = arrowIcons.get(color);
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

    private void fitCamera(@Nullable Long routeId) {
        if (map == null || selectedStop == null) {
            return;
        }
        // 'var': the compile-only classpath has no InnerClasses attribute, so LatLngBounds.Builder cannot be named
        var b = LatLngBounds.builder();
        b.include(new LatLng(selectedStop.getLat(), selectedStop.getLng()));
        double[] loc = data.getUserLocation();
        if (loc != null) {
            b.include(new LatLng(loc[0], loc[1]));
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
        CameraUpdate u = CameraUpdateFactory.newLatLngBounds(b.build(), dp(48));
        if (mapLoaded) {
            map.animateCamera(u);
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
        pickMode = true;
        pickHint.setVisibility(View.VISIBLE);
        changeStopButton.setText("Отмена");
        showPickMarkers();
    }

    private void exitPickMode() {
        pickMode = false;
        pickHint.setVisibility(View.GONE);
        changeStopButton.setText("Сменить остановку");
        if (selectedStop != null) {
            drawStop();
            refreshHighlight(false);
        } else if (map != null) {
            map.clear();
        }
    }

    private void showPickMarkers() {
        if (map == null) {
            return;
        }
        map.clear();
        pickMarkers.clear();
        // 'var': the compile-only classpath has no InnerClasses attribute, so LatLngBounds.Builder cannot be named
        var b = LatLngBounds.builder();
        for (StopPoint s : data.getStops()) {
            LatLng p = new LatLng(s.getLat(), s.getLng());
            Marker m = map.addMarker(new MarkerOptions().position(p).title(s.getName())
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            selectedStop != null && selectedStop.getId() == s.getId()
                                    ? BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_ORANGE)));
            pickMarkers.put(m, s);
            b.include(p);
        }
        double[] loc = data.getUserLocation();
        if (loc != null) {
            LatLng u = new LatLng(loc[0], loc[1]);
            map.addMarker(new MarkerOptions().position(u).title("Вы здесь")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            b.include(u);
        }
        if (!pickMarkers.isEmpty()) {
            CameraUpdate u = CameraUpdateFactory.newLatLngBounds(b.build(), dp(64));
            if (mapLoaded) {
                map.animateCamera(u);
            } else {
                pendingCamera = u;
            }
        }
    }

    private void chooseStop(StopPoint stop) {
        pickMode = false;
        pickHint.setVisibility(View.GONE);
        changeStopButton.setText("Сменить остановку");
        ((View) statusView.getParent()).setVisibility(View.GONE);
        double[] loc = data.getUserLocation();
        double d = loc == null ? -1
                : kz.onay.nearby.logic.Haversine.distanceMeters(loc[0], loc[1], stop.getLat(), stop.getLng());
        highlight.clear();
        showStop(stop, d);
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

    // ---------- lifecycle forwarding for MapView ----------

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
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
        handler.removeCallbacksAndMessages(null);
        if (mapView != null) {
            mapView.onDestroy();
        }
        mapView = null;
        map = null;
        mapLoaded = false;
        pendingCamera = null;
        polylines.clear();
        arrows.clear();
        chips.clear();
        pickMarkers.clear();
        shownLines.clear();
        super.onDestroyView();
    }
}
