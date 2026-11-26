/**
 * Route matching: "can this driver drop me?"
 *
 * A driver publishes a route (pickup -> drop). A passenger asks for their own
 * pickup -> drop. The passenger is a match when:
 *   1. their pickup lies within `radiusKm` of the driver's route,
 *   2. their drop lies within `radiusKm` of the driver's route,
 *   3. they travel in the SAME direction as the driver (pickup is reached
 *      before drop along the route), and
 *   4. picking them up and dropping them off does not force the driver into
 *      an unreasonable detour.
 *
 * The route is modelled as a polyline. Today rides only store their two
 * endpoints, so the polyline is the straight segment between them, but every
 * function here works on an arbitrary list of points so road-following
 * polylines can be plugged in later without touching the maths.
 *
 * Distances use an equirectangular projection around the route, which is
 * accurate to well under 1% for city-scale trips (< 100 km).
 */

export interface LatLng {
  lat: number;
  lng: number;
}

export interface MatchOptions {
  /** Max distance (km) between the passenger's points and the driver's route. */
  radiusKm: number;
  /** Max extra distance (km) the driver may have to drive. Defaults to max(3, 2 * radius, 35% of route). */
  maxDetourKm?: number;
}

export type MatchType = 'EXACT' | 'ON_ROUTE';

export interface RouteMatch {
  matchType: MatchType;
  /** Distance (km) from the passenger pickup to the nearest point on the route. */
  pickupDistanceKm: number;
  /** Distance (km) from the passenger drop to the nearest point on the route. */
  dropDistanceKm: number;
  /** Position of the pickup/drop along the route, 0 = driver start, 1 = driver end. */
  pickupFraction: number;
  dropFraction: number;
  /** Extra km the driver drives to serve this passenger. */
  detourKm: number;
  /** Length of the driver's route (km). */
  routeKm: number;
  /** Length of the passenger's own trip (km). */
  passengerTripKm: number;
  /** Share of the passenger's trip that overlaps the driver's route (0..1). */
  overlapRatio: number;
  /** Lower is better. Used to rank results. */
  score: number;
}

const EARTH_RADIUS_KM = 6371;
const EXACT_ENDPOINT_KM = 1.0;

const toRad = (deg: number) => (deg * Math.PI) / 180;

export function haversineKm(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Project lat/lng to a local planar (km) frame centred on `origin`. */
function toPlane(p: LatLng, origin: LatLng): { x: number; y: number } {
  const kmPerDegLat = (Math.PI * EARTH_RADIUS_KM) / 180;
  const kmPerDegLng = kmPerDegLat * Math.cos(toRad(origin.lat));
  return {
    x: (p.lng - origin.lng) * kmPerDegLng,
    y: (p.lat - origin.lat) * kmPerDegLat,
  };
}

interface Projection {
  /** Perpendicular distance (km) from the point to the polyline. */
  distanceKm: number;
  /** Distance (km) along the polyline to the projected point. */
  alongKm: number;
}

/** Closest point on a polyline to `p`. */
export function projectOntoRoute(route: LatLng[], p: LatLng): Projection & { routeKm: number } {
  if (route.length === 0) throw new Error('Route must contain at least one point');
  const origin = route[0];
  const pts = route.map((r) => toPlane(r, origin));
  const q = toPlane(p, origin);

  if (pts.length === 1) {
    return { distanceKm: Math.hypot(q.x - pts[0].x, q.y - pts[0].y), alongKm: 0, routeKm: 0 };
  }

  let best: Projection = { distanceKm: Number.POSITIVE_INFINITY, alongKm: 0 };
  let walked = 0;
  for (let i = 0; i < pts.length - 1; i++) {
    const a = pts[i];
    const b = pts[i + 1];
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const segLen = Math.hypot(dx, dy);
    let t = 0;
    if (segLen > 1e-9) {
      t = ((q.x - a.x) * dx + (q.y - a.y) * dy) / (segLen * segLen);
      t = Math.max(0, Math.min(1, t));
    }
    const px = a.x + t * dx;
    const py = a.y + t * dy;
    const d = Math.hypot(q.x - px, q.y - py);
    if (d < best.distanceKm) {
      best = { distanceKm: d, alongKm: walked + t * segLen };
    }
    walked += segLen;
  }
  return { ...best, routeKm: walked };
}

/**
 * Decide whether a passenger trip fits on a driver's route.
 * Returns `null` when the driver cannot reasonably take this passenger.
 */
export function matchPassengerToRoute(
  route: LatLng[],
  passengerPickup: LatLng,
  passengerDrop: LatLng,
  options: MatchOptions,
): RouteMatch | null {
  const { radiusKm } = options;
  const start = route[0];
  const end = route[route.length - 1];

  const pickup = projectOntoRoute(route, passengerPickup);
  const drop = projectOntoRoute(route, passengerDrop);
  const routeKm = pickup.routeKm;
  const passengerTripKm = haversineKm(passengerPickup, passengerDrop);

  // 1 + 2: both points close to the route
  if (pickup.distanceKm > radiusKm || drop.distanceKm > radiusKm) return null;

  // Degenerate driver route (start ~ end): fall back to plain endpoint radius.
  if (routeKm < 0.2) {
    const ok = haversineKm(start, passengerPickup) <= radiusKm && haversineKm(end, passengerDrop) <= radiusKm;
    if (!ok) return null;
  } else {
    // 3: same direction. Allow a small tolerance for points that project onto the
    // same spot (e.g. both near the start), but never a reversed trip.
    const directionToleranceKm = Math.min(0.3, routeKm * 0.05);
    if (drop.alongKm + directionToleranceKm <= pickup.alongKm) return null;
    if (passengerTripKm > 0.5 && drop.alongKm - pickup.alongKm < 0.2) return null;
  }

  // 4: detour = (start -> P -> D -> end) - (start -> end)
  const withPassenger =
    haversineKm(start, passengerPickup) + passengerTripKm + haversineKm(passengerDrop, end);
  const direct = haversineKm(start, end);
  const detourKm = Math.max(0, withPassenger - direct);
  const maxDetourKm = options.maxDetourKm ?? Math.max(3, 2 * radiusKm, direct * 0.35);
  if (detourKm > maxDetourKm) return null;

  const overlapKm = Math.max(0, drop.alongKm - pickup.alongKm);
  const overlapRatio = passengerTripKm > 0 ? Math.min(1, overlapKm / passengerTripKm) : 1;

  const isExact =
    haversineKm(start, passengerPickup) <= EXACT_ENDPOINT_KM &&
    haversineKm(end, passengerDrop) <= EXACT_ENDPOINT_KM;

  const score = pickup.distanceKm + drop.distanceKm + 0.5 * detourKm + (isExact ? 0 : 0.25);

  return {
    matchType: isExact ? 'EXACT' : 'ON_ROUTE',
    pickupDistanceKm: round(pickup.distanceKm),
    dropDistanceKm: round(drop.distanceKm),
    pickupFraction: routeKm > 0 ? round(pickup.alongKm / routeKm, 3) : 0,
    dropFraction: routeKm > 0 ? round(drop.alongKm / routeKm, 3) : 1,
    detourKm: round(detourKm),
    routeKm: round(routeKm),
    passengerTripKm: round(passengerTripKm),
    overlapRatio: round(overlapRatio, 3),
    score,
  };
}

/**
 * Fair per-seat fare for a passenger riding only part of the route.
 * Full-route (EXACT) passengers pay the published price; partial riders pay
 * pro-rata on distance, never less than a minimum fare and never more than
 * the published price.
 */
export function proRatedFare(pricePerSeat: number, match: RouteMatch, minimumFare = 10): number {
  if (match.matchType === 'EXACT' || match.routeKm <= 0) return pricePerSeat;
  const ratio = Math.min(1, match.passengerTripKm / match.routeKm);
  const fare = Math.round(pricePerSeat * ratio);
  return Math.min(pricePerSeat, Math.max(Math.min(minimumFare, pricePerSeat), fare));
}

function round(v: number, digits = 2): number {
  const f = 10 ** digits;
  return Math.round(v * f) / f;
}
