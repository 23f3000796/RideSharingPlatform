import { decodePolyline } from './polyline';
import { haversineKm, matchPassengerToRoute, proRatedFare, projectOntoRoute } from '../rides/route-matching';

// Real OSRM response: University Main Gate -> Vijay Nagar Square, Indore (6.09 km by road)
const OSRM_CAMPUS_TO_VIJAY_NAGAR =
  'oltiCs}~mMs@_D}V_CiBeJj@qFsVkCv@wI|EsHyAsDgGeDyU{]eh@ij@kf@i`@wKcScFgA}@iFkXiCzA_OwHyA';

describe('polyline + road-route matching', () => {
  const road = decodePolyline(OSRM_CAMPUS_TO_VIJAY_NAGAR);

  it('decodes the classic Google example', () => {
    const pts = decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@');
    expect(pts).toHaveLength(3);
    expect(pts[0].lat).toBeCloseTo(38.5, 5);
    expect(pts[0].lng).toBeCloseTo(-120.2, 5);
    expect(pts[2].lat).toBeCloseTo(43.252, 5);
    expect(pts[2].lng).toBeCloseTo(-126.453, 5);
  });

  it('decodes the OSRM route with endpoints at campus and Vijay Nagar', () => {
    expect(road.length).toBeGreaterThan(5);
    expect(haversineKm(road[0], { lat: 22.7196, lng: 75.8577 })).toBeLessThan(0.05);
    expect(haversineKm(road[road.length - 1], { lat: 22.7533, lng: 75.8937 })).toBeLessThan(0.1);
    // Road length (simplified geometry) is close to OSRM's reported 6.09 km and longer than the straight line
    const len = projectOntoRoute(road, road[0]).routeKm;
    expect(len).toBeGreaterThan(5.3);
    expect(len).toBeLessThan(6.5);
  });

  it('matches a passenger standing on a road point mid-route, measuring the ride along the road', () => {
    const mid = road[Math.floor(road.length / 2)];
    const m = matchPassengerToRoute(road, { lat: 22.7196, lng: 75.8577 }, mid, { radiusKm: 1 });
    expect(m).not.toBeNull();
    expect(m!.roadRoute).toBe(true);
    expect(m!.dropDistanceKm).toBeLessThan(0.05);
    expect(m!.passengerTripKm).toBeGreaterThan(0.5);
    expect(proRatedFare(60, m!)).toBeLessThan(60);
  });

  it('still rejects the reverse direction on a road route', () => {
    const mid = road[Math.floor(road.length / 2)];
    expect(matchPassengerToRoute(road, mid, road[0], { radiusKm: 1 })).toBeNull();
  });
});
