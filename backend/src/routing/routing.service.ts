import { Injectable, Logger } from '@nestjs/common';
import { LatLng } from '../rides/route-matching';
import { decodePolyline } from './polyline';

export interface RoadRoute {
  polyline: string;
  points: LatLng[];
  distanceKm: number;
  durationMin: number;
}

/**
 * Road routing via OSRM (OpenStreetMap). Free, no API key.
 *
 * Defaults to the public demo server, which is meant for light use only and has no
 * uptime guarantee. Point OSRM_URL at a self-hosted OSRM for anything serious.
 * Every failure returns null so callers fall back to a straight line: publishing
 * or searching rides never fails because routing is down.
 */
@Injectable()
export class RoutingService {
  private readonly logger = new Logger(RoutingService.name);
  private readonly baseUrl = (process.env.OSRM_URL || 'https://router.project-osrm.org').replace(/\/+$/, '');
  private readonly timeoutMs = Number(process.env.OSRM_TIMEOUT_MS) || 6000;

  async route(from: LatLng, to: LatLng): Promise<RoadRoute | null> {
    // OSRM takes lng,lat order
    const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`;
    const url = `${this.baseUrl}/route/v1/driving/${coords}?overview=simplified&geometries=polyline&steps=false`;
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': 'campus-ridepool' },
        signal: AbortSignal.timeout(this.timeoutMs),
      });
      if (!res.ok) {
        this.logger.warn(`OSRM HTTP ${res.status}`);
        return null;
      }
      const body: any = await res.json();
      const r = body?.code === 'Ok' ? body.routes?.[0] : null;
      if (!r?.geometry || typeof r.distance !== 'number') {
        this.logger.warn(`OSRM returned no route (${body?.code ?? 'unknown'})`);
        return null;
      }
      const points = decodePolyline(r.geometry);
      if (points.length < 2) return null;
      return {
        polyline: r.geometry,
        points,
        distanceKm: Math.round((r.distance / 1000) * 100) / 100,
        durationMin: Math.round((r.duration / 60) * 10) / 10,
      };
    } catch (e) {
      this.logger.warn(`OSRM unavailable: ${(e as Error).message}`);
      return null;
    }
  }
}
