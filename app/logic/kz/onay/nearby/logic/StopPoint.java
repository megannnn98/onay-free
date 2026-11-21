package kz.onay.nearby.logic;

/** A physical stop. Coordinates may be missing, so they are boxed. Identity is the id, never the name. */
public final class StopPoint {
    private final long id;
    private final String name;
    private final Double lat;
    private final Double lng;

    public StopPoint(long id, String name, Double lat, Double lng) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }
}
