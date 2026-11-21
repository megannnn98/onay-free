package kz.onay.nearby.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kz.onay.OnayApp;
import kz.onay.core.domain.routes.Localization;
import kz.onay.di.RouteComponent;
import kz.onay.features.routes.data.database.RouteDatabase;
import kz.onay.features.routes.data.database.entities.Stop;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.StopNames;
import kz.onay.nearby.logic.StopPoint;

/**
 * Stops from the app's own Room database (kept up to date by the app). Routes are not connected yet:
 * stage 4 adds them. Every method that reads the database must run off the main thread.
 */
final class AppDataSource implements NearbyDataSource {
    /** The route component is absent until a city is chosen. */
    static final class Unavailable extends Exception {
        private static final long serialVersionUID = 1L;

        Unavailable(String message) {
            super(message);
        }
    }

    private final RouteDatabase db;
    private final String language = Locale.getDefault().getLanguage();
    private final Map<Long, String> names = new ConcurrentHashMap<>();
    private volatile List<StopPoint> stops = Collections.emptyList();

    private AppDataSource(RouteDatabase db) {
        this.db = db;
    }

    static AppDataSource create() throws Unavailable {
        RouteComponent component = OnayApp.get().getRouteComponent();
        if (component == null) {
            throw new Unavailable("route component is not built (city not selected)");
        }
        return new AppDataSource(component.getRouteDatabase());
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
        return false;
    }

    @Override
    public Collection<Long> getRouteIdsAtStop(long stopId) {
        return Collections.emptyList();
    }

    @Override
    public List<RouteInfo> getRoutes() {
        return Collections.emptyList();
    }

    @Override
    public List<RouteLine> getLines(long routeId) {
        return Collections.emptyList();
    }

    @Override
    public String getDirectionLabel(long routeId, int directionIndex) {
        return null;
    }
}
