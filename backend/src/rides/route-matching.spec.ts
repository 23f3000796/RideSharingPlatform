import { haversineKm, matchPassengerToRoute, proRatedFare, projectOntoRoute, LatLng } from './route-matching';

// Real Indore points (Rajwada = old city centre)
const RAJWADA: LatLng = { lat: 22.7196, lng: 75.8577 };
const VIJAY_NAGAR: LatLng = { lat: 22.7533, lng: 75.8937 };
const PALASIA: LatLng = { lat: 22.7244, lng: 75.8839 };
const BHAWARKUAN: LatLng = { lat: 22.6934, lng: 75.8682 };
// Roughly half-way along Campus -> Vijay Nagar
const MIDWAY: LatLng = { lat: 22.7365, lng: 75.8757 };

const route = [RAJWADA, VIJAY_NAGAR]; // ~5.3 km

describe('route matching', () => {
  it('haversine gives sensible city distances', () => {
    const d = haversineKm(RAJWADA, VIJAY_NAGAR);
    expect(d).toBeGreaterThan(5);
    expect(d).toBeLessThan(5.6);
  });

  it('projects a midway point onto the middle of the route', () => {
    const p = projectOntoRoute(route, MIDWAY);
    expect(p.distanceKm).toBeLessThan(0.1);
    expect(p.alongKm / p.routeKm).toBeGreaterThan(0.4);
    expect(p.alongKm / p.routeKm).toBeLessThan(0.6);
  });

  it('matches the exact same trip as EXACT', () => {
    const m = matchPassengerToRoute(route, RAJWADA, VIJAY_NAGAR, { radiusKm: 2 });
    expect(m).not.toBeNull();
    expect(m!.matchType).toBe('EXACT');
    expect(m!.detourKm).toBeCloseTo(0, 1);
  });

  it('matches a passenger getting off part-way (old logic missed this)', () => {
    const m = matchPassengerToRoute(route, RAJWADA, MIDWAY, { radiusKm: 2 });
    expect(m).not.toBeNull();
    expect(m!.matchType).toBe('ON_ROUTE');
    expect(m!.dropFraction).toBeLessThan(0.7);
  });

  it('matches a passenger joining part-way', () => {
    const m = matchPassengerToRoute(route, MIDWAY, VIJAY_NAGAR, { radiusKm: 2 });
    expect(m).not.toBeNull();
    expect(m!.pickupFraction).toBeGreaterThan(0.3);
  });

  it('rejects a passenger going the opposite direction', () => {
    expect(matchPassengerToRoute(route, VIJAY_NAGAR, RAJWADA, { radiusKm: 2 })).toBeNull();
    expect(matchPassengerToRoute(route, MIDWAY, RAJWADA, { radiusKm: 2 })).toBeNull();
  });

  it('rejects a drop that is far from the route', () => {
    // Bhawarkuan is south of campus, the route goes north-east
    expect(matchPassengerToRoute(route, RAJWADA, BHAWARKUAN, { radiusKm: 1.5 })).toBeNull();
  });

  it('accepts a nearby drop only when within radius', () => {
    // Palasia is ~1.5 km off the Campus -> Vijay Nagar line
    const p = projectOntoRoute(route, PALASIA);
    expect(matchPassengerToRoute(route, RAJWADA, PALASIA, { radiusKm: p.distanceKm + 0.2 })).not.toBeNull();
    expect(matchPassengerToRoute(route, RAJWADA, PALASIA, { radiusKm: p.distanceKm - 0.2 })).toBeNull();
  });

  it('rejects trips that would force a big detour', () => {
    const m = matchPassengerToRoute(route, RAJWADA, MIDWAY, { radiusKm: 2, maxDetourKm: 0.0001 });
    // RAJWADA -> MIDWAY is on the line, detour ~0, still ok
    expect(m).not.toBeNull();
    const offRoute = { lat: MIDWAY.lat - 0.012, lng: MIDWAY.lng + 0.012 }; // ~1.8 km off
    expect(matchPassengerToRoute(route, RAJWADA, offRoute, { radiusKm: 3, maxDetourKm: 0.5 })).toBeNull();
  });

  it('works for multi-point (road) polylines', () => {
    const roadRoute = [RAJWADA, PALASIA, VIJAY_NAGAR];
    const m = matchPassengerToRoute(roadRoute, RAJWADA, PALASIA, { radiusKm: 0.5 });
    expect(m).not.toBeNull();
    expect(m!.pickupDistanceKm).toBeLessThan(0.05);
    expect(m!.dropDistanceKm).toBeLessThan(0.05);
  });

  it('pro-rates fares for partial trips', () => {
    const exact = matchPassengerToRoute(route, RAJWADA, VIJAY_NAGAR, { radiusKm: 2 })!;
    expect(proRatedFare(60, exact)).toBe(60);
    const partial = matchPassengerToRoute(route, RAJWADA, MIDWAY, { radiusKm: 2 })!;
    const fare = proRatedFare(60, partial);
    expect(fare).toBeLessThan(60);
    expect(fare).toBeGreaterThanOrEqual(10);
    expect(proRatedFare(0, partial)).toBe(0); // free rides stay free
  });
});
