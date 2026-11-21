package kz.onay.nearby.ui;

import android.util.Log;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import kz.onay.OnayApp;
import kz.onay.core.domain.routes.Localization;
import kz.onay.di.RouteComponent;
import kz.onay.domain.entity.User;
import kz.onay.features.routes.data.interceptors.GrpcHeaderClientInterceptor;
import kz.onay.managers.AccountManager;
import kz.onay.features.routes.data.database.RouteDatabase;
import kz.onay.features.routes.data.database.entities.Stop;
import kz.onay.nearby.logic.DirectionLabel;
import kz.onay.nearby.logic.RouteDirectionInfo;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.RouteNames;
import kz.onay.nearby.logic.StopNames;
import kz.onay.nearby.logic.StopPoint;

/**
 * Stops from the app's own Room database (kept up to date by the app) and routes from the app's own gRPC channel.
 * The channel already carries the app's interceptor, so no token is read or copied here.
 * Every method that reads the database or the network must run off the main thread.
 *
 * The compile-only classpath has no generic signatures and no InnerClasses attribute, so generated
 * protobuf and gRPC nested classes are named with '$' and builder results are cast.
 */
final class AppDataSource implements NearbyDataSource {
    /** The route component is absent until a city is chosen; the route services need a signed-in user. */
    static final class Unavailable extends Exception {
        private static final long serialVersionUID = 1L;
        final boolean loginRequired;

        Unavailable(String message, boolean loginRequired) {
            super(message);
            this.loginRequired = loginRequired;
        }
    }

    private static final String TAG = "NearbyStop";
    private static final long RPC_DEADLINE_SECONDS = 10;

    private final RouteDatabase db;
    private final ManagedChannel channel;
    private final GrpcHeaderClientInterceptor interceptor;
    private final String language = Locale.getDefault().getLanguage();
    private final Map<Long, String> names = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> routeIdsByStop = new ConcurrentHashMap<>();
    private final Map<Long, RouteInfo> routeInfos = new ConcurrentHashMap<>();
    private final Map<Long, List<RouteLine>> routeLines = new ConcurrentHashMap<>();
    private final Map<String, String> directionLabels = new ConcurrentHashMap<>();
    private volatile List<StopPoint> stops = Collections.emptyList();
    private volatile int failedRoutes;

    private AppDataSource(RouteDatabase db, ManagedChannel channel, GrpcHeaderClientInterceptor interceptor) {
        this.db = db;
        this.channel = channel;
        this.interceptor = interceptor;
    }

    static AppDataSource create() throws Unavailable {
        RouteComponent component = OnayApp.get().getRouteComponent();
        if (component == null) {
            throw new Unavailable("route component is not built (city not selected)", false);
        }
        AppDataSource src = new AppDataSource(component.getRouteDatabase(), component.getManagedChannel(),
                interceptorOf(component));
        src.applyAccountToken();
        return src;
    }

    /**
     * The channel's own header interceptor is the scoped instance held by the generated Dagger component.
     * The component does not expose it, so it is read by field name. This is tied to this exact app version.
     */
    private static GrpcHeaderClientInterceptor interceptorOf(RouteComponent component) throws Unavailable {
        try {
            java.lang.reflect.Field f = component.getClass().getDeclaredField("provideInterceptorProvider");
            f.setAccessible(true);
            return (GrpcHeaderClientInterceptor) ((javax.inject.Provider) f.get(component)).get();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, "cannot reach the route header interceptor", e);
            throw new Unavailable("route header interceptor not reachable: " + e, false);
        }
    }

    /**
     * Does what the app's own route screens do before calling the route services: hands the signed-in
     * account's short token to the interceptor. The token is never read, logged or shown here.
     */
    private void applyAccountToken() throws Unavailable {
        AccountManager accounts = OnayApp.get().getAccountManager();
        User account = accounts == null ? null : accounts.getAccount();
        if (account == null) {
            throw new Unavailable("no signed-in account", true);
        }
        interceptor.setShortToken(account.getShortToken());
    }

    @Override
    public List<StopPoint> getStops() {
        List<StopPoint> cached = stops;
        if (!cached.isEmpty()) {
            return cached;
        }
        // The compile-only classpath has no generic signatures, so Room results arrive as raw types.
        @SuppressWarnings("unchecked")
        List<Stop> rows = (List<Stop>) db.stopDao().getListRx().blockingFirst();
        List<StopPoint> out = new ArrayList<>(rows.size());
        for (Stop s : rows) {
            if (s.id == null || Boolean.TRUE.equals(s.isDeleted)) {
                continue;
            }
            out.add(new StopPoint(s.id, null, s.lat, s.lng));
        }
        stops = out;
        return out;
    }

    @Override
    public void prefetchNames(Collection<StopPoint> wanted) {
        for (StopPoint s : wanted) {
            if (names.containsKey(s.getId())) {
                continue;
            }
            Map<String, String> byLanguage = new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Localization> rows = (List<Localization>) db.localizationDao().getList(Stop.RESOURCE_NAME, s.getId());
            if (rows != null) {
                for (Localization l : rows) {
                    byLanguage.put(l.language, l.translation);
                }
            }
            names.put(s.getId(), StopNames.choose(byLanguage, language, s.getId()));
        }
    }

    @Override
    public String getStopName(long stopId) {
        String n = names.get(stopId);
        return n != null ? n : "Остановка " + stopId;
    }

    @Override
    public boolean hasRoutes() {
        return true;
    }

    @Override
    public void loadRoutesForStop(long stopId) throws Exception {
        applyAccountToken();
        List<Long> ids = routeIdsByStop.get(stopId);
        if (ids == null) {
            ids = fetchStopRoutes(stopId);
            routeIdsByStop.put(stopId, ids);
        }
        int failed = 0;
        for (Long id : ids) {
            if (routeInfos.containsKey(id)) {
                continue;
            }
            try {
                fetchRoute(id);
            } catch (Exception e) {
                failed++;
                Log.w(TAG, "route " + id + " not loaded: " + e);
            }
        }
        failedRoutes = failed;
        Log.i(TAG, "stop " + stopId + ": " + ids.size() + " routes listed, " + failed + " failed");
    }

    @Override
    public int getFailedRouteCount() {
        return failedRoutes;
    }

    /** StopService.Get: the ids of the routes the server links to the stop. */
    private List<Long> fetchStopRoutes(long stopId) {
        var stub = (stop.StopServiceGrpc$StopServiceBlockingStub) stop.StopServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS);
        var req = (stop.StopServiceOuterClass$GetRequest) stop.StopServiceOuterClass$GetRequest.newBuilder()
                .setStopId(stopId).build();
        var st = stub.get(req).getStop();
        List<?> raw = st.getRoutesList();
        List<Long> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            out.add(((Number) o).longValue());
        }
        return out;
    }

    /** RouteService.Get: name, type, directions with their stops, and the drawn line of each direction. */
    private void fetchRoute(long routeId) {
        long t0 = System.nanoTime();
        var stub = (route.RouteServiceGrpc$RouteServiceBlockingStub) route.RouteServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS);
        var req = (route.RouteServiceOuterClass$GetRequest) route.RouteServiceOuterClass$GetRequest.newBuilder()
                .setRouteId(routeId).build();
        var r = stub.get(req).getRoute();

        @SuppressWarnings("unchecked")
        Map<String, String> nameMap = (Map<String, String>) r.getNameMap();
        List<RouteDirectionInfo> dirs = new ArrayList<>();
        List<RouteLine> lines = new ArrayList<>();
        int points = 0;
        for (Object d : r.getDirectionsList()) {
            var dir = (route.RouteServiceOuterClass$Direction) d;
            int index = dir.getIndex();
            Set<Long> stopIds = new HashSet<>();
            long lastStopId = -1;
            for (Object s : dir.getStopsList()) {
                var sp = (route.RouteServiceOuterClass$StopPosition) s;
                stopIds.add(sp.getStopId());
                lastStopId = sp.getStopId();
            }
            double[] la = new double[dir.getLineCount()];
            double[] ln = new double[dir.getLineCount()];
            int i = 0;
            for (Object p : dir.getLineList()) {
                var pt = (route.RouteServiceOuterClass$Point) p;
                la[i] = pt.getLatitude();
                ln[i] = pt.getLongitude();
                i++;
            }
            points += i;
            dirs.add(new RouteDirectionInfo(index, stopIds));
            lines.add(new RouteLine(routeId, index, la, ln));
            if (lastStopId >= 0) {
                prefetchNames(Collections.singletonList(new StopPoint(lastStopId, null, null, null)));
                String name = getStopName(lastStopId);
                String label = name.equals("Остановка " + lastStopId) ? null : DirectionLabel.towards(name);
                if (label != null) {
                    directionLabels.put(routeId + "/" + index, label);
                }
            }
        }
        routeLines.put(routeId, lines);
        routeInfos.put(routeId, new RouteInfo(routeId, RouteNames.choose(nameMap, language, routeId),
                (int) r.getTypeId(), dirs));
        Log.i(TAG, "route " + routeId + ": " + dirs.size() + " directions, " + points + " points, "
                + (System.nanoTime() - t0) / 1_000_000 + " ms");
    }

    @Override
    public Collection<Long> getRouteIdsAtStop(long stopId) {
        List<Long> ids = routeIdsByStop.get(stopId);
        return ids == null ? Collections.<Long>emptyList() : ids;
    }

    @Override
    public List<RouteInfo> getRoutes() {
        return new ArrayList<>(routeInfos.values());
    }

    @Override
    public List<RouteLine> getLines(long routeId) {
        List<RouteLine> l = routeLines.get(routeId);
        return l == null ? Collections.<RouteLine>emptyList() : l;
    }

    @Override
    public String getDirectionLabel(long routeId, int directionIndex) {
        return directionLabels.get(routeId + "/" + directionIndex);
    }
}
