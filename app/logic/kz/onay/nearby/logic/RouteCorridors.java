package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits route geometries into individual paths and shared corridors for the map overview.
 *
 * A shared corridor requires two DIFFERENT routes to run alongside each other for at least 60 m.
 * Nearby but perpendicular lines (street crossings) and multiple directions of the same route
 * never count. Sample points need not coincide: segments are resampled in metres and matched
 * using spatial buckets, alignment and lateral distance. The same shared corridor is drawn by
 * its lowest route ID only, rather than stacking many purple strokes on top of one another.
 *
 * Pure Java: can be tested without Google Maps or Android.
 */
public final class RouteCorridors {
    private static final double EARTH_METERS = 6371008.8;
    private static final double STEP_METERS = 20.0;
    private static final double GRID_METERS = 40.0;
    private static final double MAX_SEPARATION_METERS = 10.0;
    private static final double MIN_SHARED_METERS = 60.0;
    private static final double MIN_COS_ANGLE = 0.9063; // 25 degrees; direction may be reversed

    private RouteCorridors() {
    }

    public static final class Part {
        private final long routeId;
        private final boolean shared;
        private final double[] lats;
        private final double[] lngs;

        private Part(long routeId, boolean shared, List<Point> points) {
            this.routeId = routeId;
            this.shared = shared;
            lats = new double[points.size()];
            lngs = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                lats[i] = points.get(i).lat;
                lngs[i] = points.get(i).lng;
            }
        }

        public long getRouteId() { return routeId; }
        public boolean isShared() { return shared; }
        public int size() { return lats.length; }
        public double getLat(int index) { return lats[index]; }
        public double getLng(int index) { return lngs[index]; }
    }

    private static final class Point {
        final double lat;
        final double lng;
        final double x;
        final double y;

        Point(double lat, double lng, double cosLat) {
            this.lat = lat;
            this.lng = lng;
            x = Math.toRadians(lng) * EARTH_METERS * cosLat;
            y = Math.toRadians(lat) * EARTH_METERS;
        }
    }

    private static final class Segment {
        final long routeId;
        final Point a;
        final Point b;
        final double len;
        final double ux;
        final double uy;
        final double mx;
        final double my;
        boolean candidate;
        boolean shared;
        long owner;

        Segment(long routeId, Point a, Point b) {
            this.routeId = routeId;
            this.a = a;
            this.b = b;
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            len = Math.hypot(dx, dy);
            ux = dx / len;
            uy = dy / len;
            mx = (a.x + b.x) / 2;
            my = (a.y + b.y) / 2;
            owner = routeId;
        }
    }

    /**
     * Overview paths: individual sections use their route colour; shared sections use the
     * common corridor colour. The caller renders each returned part once, at a fixed width.
     */
    public static List<Part> overview(List<RouteLine> lines) {
        List<Part> result = new ArrayList<>();
        if (lines.isEmpty()) {
            return result;
        }
        double referenceLat = 0.0;
        for (RouteLine line : lines) {
            if (!line.isEmpty()) {
                referenceLat = line.getLat(0);
                break;
            }
        }
        double cosLat = Math.cos(Math.toRadians(referenceLat));
        List<List<Segment>> paths = new ArrayList<>();
        Map<Long, List<Segment>> grid = new HashMap<>();

        for (RouteLine line : lines) {
            List<Segment> path = new ArrayList<>();
            paths.add(path);
            if (!line.isDrawable()) {
                continue;
            }
            for (int i = 1; i < line.size(); i++) {
                double lat0 = line.getLat(i - 1);
                double lng0 = line.getLng(i - 1);
                double lat1 = line.getLat(i);
                double lng1 = line.getLng(i);
                Point first = new Point(lat0, lng0, cosLat);
                Point last = new Point(lat1, lng1, cosLat);
                double distance = Math.hypot(last.x - first.x, last.y - first.y);
                if (!Double.isFinite(distance) || distance < 0.01) {
                    continue;
                }
                int steps = (int) Math.ceil(distance / STEP_METERS);
                Point previous = first;
                for (int step = 1; step <= steps; step++) {
                    double t = (double) step / steps;
                    Point next = step == steps ? last : new Point(
                            lat0 + (lat1 - lat0) * t, lng0 + (lng1 - lng0) * t, cosLat);
                    Segment segment = new Segment(line.getRouteId(), previous, next);
                    matchAndInsert(segment, grid);
                    path.add(segment);
                    previous = next;
                }
            }
        }

        // A crossing or a few close points must not be mistaken for a shared street.
        for (List<Segment> path : paths) {
            for (int i = 0; i < path.size();) {
                if (!path.get(i).candidate) {
                    i++;
                    continue;
                }
                int from = i;
                double length = 0.0;
                while (i < path.size() && path.get(i).candidate) {
                    length += path.get(i).len;
                    i++;
                }
                if (length >= MIN_SHARED_METERS) {
                    for (int j = from; j < i; j++) {
                        path.get(j).shared = true;
                    }
                }
            }
        }

        // Do not let a rejected, too-short candidate become the owner of a shared section.
        // Ownership is resolved among accepted segments near the same point.
        Map<Long, List<Segment>> accepted = new HashMap<>();
        for (List<Segment> path : paths) {
            for (Segment s : path) {
                if (s.shared) {
                    insert(s, accepted);
                }
            }
        }
        for (List<Segment> path : paths) {
            for (Segment s : path) {
                if (!s.shared) {
                    continue;
                }
                long owner = s.routeId;
                int cx = cell(s.mx);
                int cy = cell(s.my);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        List<Segment> near = accepted.get(key(cx + dx, cy + dy));
                        if (near == null) {
                            continue;
                        }
                        for (Segment other : near) {
                            if (other.routeId != s.routeId && matches(s, other)) {
                                owner = Math.min(owner, other.routeId);
                            }
                        }
                    }
                }
                s.owner = owner;
            }
        }

        for (List<Segment> path : paths) {
            List<Point> points = new ArrayList<>();
            Boolean shared = null;
            long routeId = path.isEmpty() ? 0 : path.get(0).routeId;
            for (Segment s : path) {
                boolean draw = !s.shared || s.owner == s.routeId;
                if (!draw || (shared != null && shared.booleanValue() != s.shared)) {
                    flush(result, routeId, shared, points);
                    points.clear();
                    shared = null;
                }
                if (!draw) {
                    continue;
                }
                if (shared == null) {
                    shared = s.shared;
                    points.add(s.a);
                }
                points.add(s.b);
            }
            flush(result, routeId, shared, points);
        }
        return result;
    }

    private static void flush(List<Part> output, long routeId, Boolean shared, List<Point> points) {
        if (shared != null && points.size() >= 2) {
            output.add(new Part(routeId, shared.booleanValue(), points));
        }
    }

    private static void matchAndInsert(Segment s, Map<Long, List<Segment>> grid) {
        int cx = cell(s.mx);
        int cy = cell(s.my);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                List<Segment> near = grid.get(key(cx + dx, cy + dy));
                if (near == null) {
                    continue;
                }
                for (Segment other : near) {
                    if (other.routeId != s.routeId && matches(s, other)) {
                        s.candidate = true;
                        other.candidate = true;
                    }
                }
            }
        }
        insert(s, grid);
    }

    private static boolean matches(Segment a, Segment b) {
        if (Math.abs(a.ux * b.ux + a.uy * b.uy) < MIN_COS_ANGLE) {
            return false;
        }
        double p0 = (b.a.x - a.a.x) * a.ux + (b.a.y - a.a.y) * a.uy;
        double p1 = (b.b.x - a.a.x) * a.ux + (b.b.y - a.a.y) * a.uy;
        double overlap = Math.min(a.len, Math.max(p0, p1)) - Math.max(0.0, Math.min(p0, p1));
        if (overlap < Math.min(4.0, a.len * 0.3)) {
            return false;
        }
        return distanceToSegment(a.mx, a.my, b) <= MAX_SEPARATION_METERS
                && distanceToSegment(b.mx, b.my, a) <= MAX_SEPARATION_METERS;
    }

    private static double distanceToSegment(double x, double y, Segment s) {
        double projection = (x - s.a.x) * s.ux + (y - s.a.y) * s.uy;
        double t = Math.max(0.0, Math.min(s.len, projection));
        return Math.hypot(x - s.a.x - t * s.ux, y - s.a.y - t * s.uy);
    }

    private static void insert(Segment s, Map<Long, List<Segment>> grid) {
        long key = key(cell(s.mx), cell(s.my));
        List<Segment> bucket = grid.get(key);
        if (bucket == null) {
            bucket = new ArrayList<>();
            grid.put(key, bucket);
        }
        bucket.add(s);
    }

    private static int cell(double value) { return (int) Math.floor(value / GRID_METERS); }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
