#!/usr/bin/env bash
# Mutation check for the pure-logic layer: each mutant must make at least one JVM test fail.
# Runs on a copy under work/mut, never touches app/. Mutants run one at a time.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JUNIT="$ROOT/work/libs/junit-console.jar"
L=kz/onay/nearby/logic
MUT="$ROOT/work/mut"
mkdir -p "$MUT"
survived=0
run_mutant() { # name file old new
  local name="$1" file="$2" old="$3" new="$4" dir="$MUT/$1"
  mkdir -p "$dir/src" "$dir/out"
  cp -r "$ROOT/app/logic" "$dir/src/"
  python3 -I - "$dir/src/logic/$L/$file" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit("mutation pattern must match exactly once: %r (found %d)" % (old, s.count(old)))
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  javac --release 11 -d "$dir/out" -cp "$JUNIT" "$dir"/src/logic/$L/*.java "$ROOT"/app/test/$L/*.java
  if java -jar "$JUNIT" execute --class-path "$dir/out" --scan-class-path --disable-banner >"$dir/log.txt" 2>&1; then
    echo "SURVIVED  $name"; survived=$((survived+1))
  else
    echo "killed    $name"
  fi
}
run_mutant radius-exclusive   NearestStopFinder.java     "d <= maxRadiusMeters"            "d < maxRadiusMeters"
run_mutant no-id-tiebreak     NearestStopFinder.java     "return c != 0 ? c : Long.compare(a.getStop().getId(), b.getStop().getId());" "return c;"
run_mutant no-lat-range       NearestStopFinder.java     "&& lat >= -90.0 && lat <= 90.0"  ""
run_mutant sort-descending    NearestStopFinder.java     "int c = Double.compare(a.getDistanceMeters(), b.getDistanceMeters());" "int c = Double.compare(b.getDistanceMeters(), a.getDistanceMeters());"
run_mutant no-limit           NearestStopFinder.java     "return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;" "return matches;"
run_mutant haversine-radius   Haversine.java             "6371008.8"                       "6371008.8 * 1.01"
run_mutant no-type-filter     RoutesAtStopSelector.java  "|| !isSupportedType(r.getTypeId())" ""
run_mutant no-dedupe          RoutesAtStopSelector.java  "Set<Long> wanted = new LinkedHashSet<>(routeIdsAtStop);" "Collection<Long> wanted = routeIdsAtStop;"
run_mutant dir-inverted       RoutesAtStopSelector.java  "if (d.servesStop(stopId))"       "if (!d.servesStop(stopId))"
run_mutant guess-direction    RoutesAtStopSelector.java  "Collections.sort(dirs);"         "if (dirs.isEmpty()) { dirs.add(0); } Collections.sort(dirs);"
run_mutant string-sort        RoutesAtStopSelector.java  "if (ak != bk) {"                 "if (false) {"
run_mutant toggle-no-clear    RouteHighlightState.java   "selectedRouteId = isSelected(routeId) ? null : Long.valueOf(routeId);" "selectedRouteId = Long.valueOf(routeId);"
run_mutant alpha-swapped      RouteHighlightState.java   "? FULL_ALPHA : DIM_ALPHA"        "? DIM_ALPHA : FULL_ALPHA"
run_mutant retain-never-drops RouteHighlightState.java   "(routeIds == null || !routeIds.contains(selectedRouteId))" "false"
run_mutant bearing-no-wrap    Bearing.java               "return (deg + 360.0) % 360.0;"   "return deg;"
run_mutant bearing-swapped    Bearing.java               "double y = Math.sin(dLambda) * Math.cos(phi2);" "double y = Math.cos(dLambda) * Math.cos(phi2);"
run_mutant line-no-length-chk RouteLine.java             "if (lats.length != lngs.length) {" "if (false) {"
run_mutant line-drawable-one  RouteLine.java             "return lats.length >= 2;"        "return lats.length >= 1;"
run_mutant line-bearing-last2 RouteLine.java             "if (lats[i] != lats[i - 1] || lngs[i] != lngs[i - 1]) {" "if (true) {"
run_mutant quality-no-nullisland LocationQuality.java    "|| (lat == 0.0 && lng == 0.0)"   ""
run_mutant quality-age-strict LocationQuality.java       "nowMs - fixTimeMs > maxAgeMs"    "nowMs - fixTimeMs >= maxAgeMs"
run_mutant quality-null-time  LocationQuality.java       "fixTimeMs == null || "           ""
run_mutant quality-no-range   LocationQuality.java       "|| lat < -90.0 || lat > 90.0"    ""
run_mutant cand-extra-strict  NearbyCandidates.java      "if (m.getDistanceMeters() > nearest.getDistanceMeters() + extraMeters) {" "if (m.getDistanceMeters() >= nearest.getDistanceMeters() + extraMeters) {"
run_mutant cand-no-dedupe     NearbyCandidates.java      "if (seen.add(m.getStop().getId())) {" "if (true) {"
run_mutant cand-no-sort       NearbyCandidates.java      "int c = Double.compare(a.getDistanceMeters(), b.getDistanceMeters());" "int c = 0;"
run_mutant cand-no-max        NearbyCandidates.java      "&& out.size() < max"             ""
run_mutant names-no-exact     Translations.java             "if (k.equalsIgnoreCase(preferredLanguage) && usable(byLanguage.get(k))) {" "if (false) {"
run_mutant names-no-russian   Translations.java             'if (primary(k).equals("ru") && usable(byLanguage.get(k))) {' "if (false) {"
run_mutant names-no-placeholder StopNames.java           'return name != null ? name : "Остановка " + stopId;' 'return name != null ? name : "";'
run_mutant names-blank-ok     Translations.java             "return s != null && !s.trim().isEmpty();" "return s != null;"
run_mutant route-placeholder  RouteNames.java           'return name != null ? name.trim() : "Маршрут " + routeId;' 'return name != null ? name.trim() : "Остановка " + routeId;'
run_mutant route-no-trim      RouteNames.java            "name.trim() :"                   "name :"
run_mutant label-no-blank     DirectionLabel.java        "lastStopName.trim().isEmpty()"   "false"
run_mutant label-no-trim      DirectionLabel.java        'return "в сторону " + lastStopName.trim();' 'return "в сторону " + lastStopName;'
run_mutant age-minute-edge    AgeText.java               "if (ageMs < MINUTE_MS) {"        "if (ageMs <= MINUTE_MS) {"
run_mutant age-hour-edge      AgeText.java               "if (ageMs < HOUR_MS) {"          "if (ageMs <= HOUR_MS) {"
run_mutant age-day-edge       AgeText.java               "if (ageMs < DAY_MS) {"           "if (ageMs <= DAY_MS) {"
run_mutant age-wrong-unit     AgeText.java               'return (ageMs / HOUR_MS) + " ч назад";' 'return (ageMs / MINUTE_MS) + " ч назад";'
run_mutant veh-no-route-filter VehicleFilter.java        "if (r == null) {"                "if (false) {"
run_mutant veh-no-direction   VehicleFilter.java         "if (r.isDirectionKnown() && !r.getDirectionIndexes().contains(p.getDirectionIndex())) {" "if (false) {"
run_mutant veh-direction-inv  VehicleFilter.java         "if (r.isDirectionKnown() && !r.getDirectionIndexes()" "if (!r.isDirectionKnown() && !r.getDirectionIndexes()"
run_mutant veh-no-coords      VehicleFilter.java         "if (!validCoordinates(p.getLat(), p.getLng())) {" "if (false) {"
run_mutant veh-age-strict     VehicleFilter.java         "if (nowSec - p.getTimestampSec() > maxAgeSec) {" "if (nowSec - p.getTimestampSec() >= maxAgeSec) {"
run_mutant veh-no-age         VehicleFilter.java         "if (nowSec - p.getTimestampSec() > maxAgeSec) {" "if (false) {"
run_mutant veh-oldest-wins    VehicleFilter.java         "p.getTimestampSec() > seen.getTimestampSec()" "p.getTimestampSec() < seen.getTimestampSec()"
run_mutant veh-no-sort        VehicleFilter.java         "int c = Long.compare(a.getRouteId(), b.getRouteId());" "int c = 0;"
run_mutant veh-null-island    VehicleFilter.java         "&& !(lat == 0.0 && lng == 0.0)"  ""
echo "survivors: $survived"
[ "$survived" -eq 0 ]
