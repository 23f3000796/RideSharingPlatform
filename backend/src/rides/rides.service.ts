import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateRideDto } from './dto/create-ride.dto';
import { SearchRideDto } from './dto/search-ride.dto';
import { PaginationQueryDto } from '../common/dto/pagination-query.dto';
import { APP_CONSTANTS, levelForPoints } from '../common/constants/app.constants';
import { haversineKm, matchPassengerToRoute, proRatedFare, LatLng } from './route-matching';
import { RoutingService } from '../routing/routing.service';
import { decodePolyline } from '../routing/polyline';

const DRIVER_PUBLIC_SELECT = {
  id: true,
  name: true,
  rating: true,
  level: true,
  gender: true,
  profilePhotoUrl: true,
  isVerified: true,
  ridesCompleted: true,
  department: true,
  year: true,
} satisfies Prisma.UserSelect;

@Injectable()
export class RidesService {
  constructor(
    private prisma: PrismaService,
    private routing: RoutingService,
  ) {}

  /** Road route if OSRM answers, else the straight line between the endpoints. */
  private routeOf(ride: { pickupLat: number; pickupLng: number; dropLat: number; dropLng: number; routePolyline?: string | null }): LatLng[] {
    if (ride.routePolyline) {
      const pts = decodePolyline(ride.routePolyline);
      if (pts.length >= 2) return pts;
    }
    return [
      { lat: ride.pickupLat, lng: ride.pickupLng },
      { lat: ride.dropLat, lng: ride.dropLng },
    ];
  }

  /** Preview the road route before publishing (distance, duration, polyline). */
  async previewRoute(from: LatLng, to: LatLng) {
    const road = await this.routing.route(from, to);
    const straightKm = round(haversineKm(from, to));
    if (!road) {
      return { source: 'straight', distanceKm: straightKm, durationMin: null, polyline: null };
    }
    return { source: 'osrm', distanceKm: road.distanceKm, durationMin: road.durationMin, polyline: road.polyline };
  }

  // ---------------------------------------------------------------- create

  async createRide(userId: string, dto: CreateRideDto) {
    const departureTime = new Date(dto.departureTime);
    if (Number.isNaN(departureTime.getTime()) || departureTime.getTime() <= Date.now()) {
      throw new BadRequestException('Departure time must be in the future.');
    }

    const routeKm = haversineKm(
      { lat: dto.pickupLat, lng: dto.pickupLng },
      { lat: dto.dropLat, lng: dto.dropLng },
    );
    if (routeKm < 0.3) {
      throw new BadRequestException('Pickup and drop locations are too close to each other.');
    }

    // A driver cannot be in two places at once: block overlapping active rides (±30 min).
    const windowMs = 30 * 60 * 1000;
    const clash = await this.prisma.ride.findFirst({
      where: {
        driverId: userId,
        deletedAt: null,
        status: { in: ['SCHEDULED', 'ONGOING'] },
        departureTime: {
          gte: new Date(departureTime.getTime() - windowMs),
          lte: new Date(departureTime.getTime() + windowMs),
        },
      },
    });
    if (clash) {
      throw new BadRequestException('You already have a ride scheduled around this time.');
    }

    // Road route (best effort; null when OSRM is unreachable)
    const road = await this.routing.route(
      { lat: dto.pickupLat, lng: dto.pickupLng },
      { lat: dto.dropLat, lng: dto.dropLng },
    );

    let vehicleNumber = dto.vehicleNumber?.trim() || null;
    if (!vehicleNumber) {
      const me = await this.prisma.user.findUnique({ where: { id: userId }, select: { vehicleNumber: true } });
      vehicleNumber = me?.vehicleNumber ?? null;
    }

    const ride = await this.prisma.ride.create({
      data: {
        driverId: userId,
        pickupAddress: dto.pickupAddress.trim(),
        pickupLat: dto.pickupLat,
        pickupLng: dto.pickupLng,
        dropAddress: dto.dropAddress.trim(),
        dropLat: dto.dropLat,
        dropLng: dto.dropLng,
        departureTime,
        totalSeats: dto.totalSeats,
        seatsLeft: dto.totalSeats,
        pricePerSeat: dto.pricePerSeat,
        vehicleType: dto.vehicleType.trim(),
        vehicleNumber,
        routePolyline: road?.polyline ?? null,
        routeDistanceKm: road?.distanceKm ?? null,
        routeDurationMin: road?.durationMin ?? null,
      },
      include: { driver: { select: DRIVER_PUBLIC_SELECT } },
    });

    return {
      message: 'Ride created successfully',
      ride: { ...ride, routeKm: ride.routeDistanceKm ?? round(routeKm) },
    };
  }

  // --------------------------------------------------------------- details

  async getRideDetails(rideId: string, viewerId?: string) {
    const ride = await this.prisma.ride.findFirst({
      where: { id: rideId, deletedAt: null },
      include: { driver: { select: DRIVER_PUBLIC_SELECT } },
    });

    if (!ride) {
      throw new NotFoundException('Ride not found');
    }

    // Rides created before road routing existed get their route on first view.
    if (!ride.routePolyline && (ride.status === 'SCHEDULED' || ride.status === 'ONGOING')) {
      const road = await this.routing.route(
        { lat: ride.pickupLat, lng: ride.pickupLng },
        { lat: ride.dropLat, lng: ride.dropLng },
      );
      if (road) {
        await this.prisma.ride.update({
          where: { id: ride.id },
          data: { routePolyline: road.polyline, routeDistanceKm: road.distanceKm, routeDurationMin: road.durationMin },
        });
        Object.assign(ride, { routePolyline: road.polyline, routeDistanceKm: road.distanceKm, routeDurationMin: road.durationMin });
      }
    }

    const isDriver = viewerId === ride.driverId;

    const [myBooking, passengers] = await Promise.all([
      viewerId && !isDriver
        ? this.prisma.bookingRequest.findFirst({
            where: { rideId, passengerId: viewerId, deletedAt: null },
            orderBy: { createdAt: 'desc' },
          })
        : Promise.resolve(null),
      this.prisma.bookingRequest.findMany({
        where: { rideId, deletedAt: null, status: { in: ['ACCEPTED', 'COMPLETED'] } },
        select: {
          id: true,
          seatsRequested: true,
          status: true,
          passenger: { select: { id: true, name: true, profilePhotoUrl: true, rating: true, gender: true } },
        },
      }),
    ]);

    return {
      ...ride,
      routeKm:
        ride.routeDistanceKm ??
        round(haversineKm({ lat: ride.pickupLat, lng: ride.pickupLng }, { lat: ride.dropLat, lng: ride.dropLng })),
      isDriver,
      myBooking,
      passengers,
    };
  }

  // -------------------------------------------------------------- my rides

  async getMyRides(userId: string, query: PaginationQueryDto) {
    const { page, limit } = query;
    const skip = (page - 1) * limit;
    const where = { driverId: userId, deletedAt: null };

    const [rides, total] = await Promise.all([
      this.prisma.ride.findMany({
        where,
        orderBy: { departureTime: 'desc' },
        skip,
        take: limit,
        include: {
          _count: {
            select: { bookings: { where: { deletedAt: null, status: 'PENDING' } } },
          },
        },
      }),
      this.prisma.ride.count({ where }),
    ]);

    const data = rides.map(({ _count, ...r }) => ({ ...r, pendingRequests: _count.bookings }));
    return { data, meta: { total, page, limit } };
  }

  // ---------------------------------------------------------------- search

  /**
   * Find rides whose route can carry the passenger from their pickup to their drop.
   *
   * Step 1 (SQL): cheap pre-filter — scheduled, enough seats, in the time window,
   *   not your own ride, and the passenger's points fall inside the route's
   *   bounding box padded by the search radius.
   * Step 2 (JS): exact route matching — distance of both points to the route,
   *   direction of travel and driver detour (see route-matching.ts).
   */
  async searchRides(dto: SearchRideDto, viewerId?: string) {
    const { page, limit } = dto;
    const requested = new Date(dto.departureTime);
    const now = new Date();
    const from = requested > now ? requested : now; // never return rides that already left
    const to = dto.departureTimeTo ? new Date(dto.departureTimeTo) : null;
    if (to && to < from) {
      return { data: [], meta: { total: 0, page, limit } };
    }

    const r = dto.radius;
    // Roads can bulge outside the endpoints' bounding box, so pad generously here;
    // the exact check happens in JS against the real route.
    const pad = r + 3;
    const latPad = pad / 111.0;
    const lngPad = pad / (111.0 * Math.max(0.2, Math.cos((dto.pickupLat * Math.PI) / 180)));
    const minLat = Math.min(dto.pickupLat, dto.dropLat);
    const maxLat = Math.max(dto.pickupLat, dto.dropLat);
    const minLng = Math.min(dto.pickupLng, dto.dropLng);
    const maxLng = Math.max(dto.pickupLng, dto.dropLng);

    // Parameterised query (no string interpolation of user input).
    const candidates = await this.prisma.$queryRaw<any[]>(Prisma.sql`
      SELECT r.*,
        u.name            AS "driverName",
        u.gender          AS "driverGender",
        u.rating          AS "driverRating",
        u.level           AS "driverLevel",
        u."isVerified"    AS "driverVerified",
        u."profilePhotoUrl" AS "driverPhotoUrl"
      FROM "Ride" r
      INNER JOIN "User" u ON u.id = r."driverId"
      WHERE r."deletedAt" IS NULL
        AND u."deletedAt" IS NULL
        AND r."status" = 'SCHEDULED'
        AND r."seatsLeft" >= ${dto.seatsRequired}
        AND r."departureTime" >= (${from.toISOString()}::timestamptz AT TIME ZONE 'UTC')
        ${to ? Prisma.sql`AND r."departureTime" <= (${to.toISOString()}::timestamptz AT TIME ZONE 'UTC')` : Prisma.empty}
        ${viewerId ? Prisma.sql`AND r."driverId" <> ${viewerId}` : Prisma.empty}
        AND LEAST(r."pickupLat", r."dropLat") - ${latPad} <= ${minLat}
        AND GREATEST(r."pickupLat", r."dropLat") + ${latPad} >= ${maxLat}
        AND LEAST(r."pickupLng", r."dropLng") - ${lngPad} <= ${minLng}
        AND GREATEST(r."pickupLng", r."dropLng") + ${lngPad} >= ${maxLng}
      ORDER BY r."departureTime" ASC
      LIMIT ${APP_CONSTANTS.SEARCH.MAX_CANDIDATES}
    `);

    const pickup: LatLng = { lat: dto.pickupLat, lng: dto.pickupLng };
    const drop: LatLng = { lat: dto.dropLat, lng: dto.dropLng };

    // Rides the viewer already has an active request on are flagged, not hidden.
    const activeBookings = viewerId
      ? await this.prisma.bookingRequest.findMany({
          where: {
            passengerId: viewerId,
            deletedAt: null,
            status: { in: ['PENDING', 'ACCEPTED'] },
            rideId: { in: candidates.map((c) => c.id) },
          },
          select: { rideId: true, status: true },
        })
      : [];
    const bookingByRide = new Map(activeBookings.map((b) => [b.rideId, b.status]));

    const matched = candidates
      .map((ride) => {
        const match = matchPassengerToRoute(this.routeOf(ride), pickup, drop, { radiusKm: r });
        if (!match) return null;
        const { score, ...matchInfo } = match;
        // When the driver should reach the passenger's pickup point (road routes know the duration).
        const pickupEtaMin =
          typeof ride.routeDurationMin === 'number' ? Math.round(ride.routeDurationMin * match.pickupFraction) : null;
        const { routePolyline: _p, ...rest } = ride;
        return {
          ...rest,
          match: { ...matchInfo, pickupEtaMin },
          score,
          estimatedFare: proRatedFare(ride.pricePerSeat, match),
          myBookingStatus: bookingByRide.get(ride.id) ?? null,
        };
      })
      .filter((x): x is NonNullable<typeof x> => x !== null);

    const sort = dto.sort ?? 'best';
    matched.sort((a, b) => {
      if (sort === 'price') return a.estimatedFare - b.estimatedFare || a.score - b.score;
      if (sort === 'time') return +new Date(a.departureTime) - +new Date(b.departureTime);
      // best: route fit first, then sooner departures
      return a.score - b.score || +new Date(a.departureTime) - +new Date(b.departureTime);
    });

    const total = matched.length;
    const data = matched.slice((page - 1) * limit, page * limit).map(({ score: _s, ...rest }) => rest);
    return { data, meta: { total, page, limit } };
  }

  // -------------------------------------------------------------- lifecycle

  private async getOwnedRide(driverId: string, rideId: string) {
    const ride = await this.prisma.ride.findFirst({ where: { id: rideId, deletedAt: null } });
    if (!ride) throw new NotFoundException('Ride not found');
    if (ride.driverId !== driverId) throw new ForbiddenException('Only the driver can manage this ride');
    return ride;
  }

  /** Driver starts the trip. Pending requests are declined automatically. */
  async startRide(driverId: string, rideId: string) {
    const ride = await this.getOwnedRide(driverId, rideId);
    if (ride.status !== 'SCHEDULED') {
      throw new BadRequestException(`Ride is ${ride.status.toLowerCase()} and cannot be started`);
    }
    const earliestStart = ride.departureTime.getTime() - 60 * 60 * 1000;
    if (Date.now() < earliestStart) {
      throw new BadRequestException('You can start the ride at most 1 hour before departure');
    }

    const [updated] = await this.prisma.$transaction([
      this.prisma.ride.update({ where: { id: rideId }, data: { status: 'ONGOING' } }),
      this.prisma.bookingRequest.updateMany({
        where: { rideId, status: 'PENDING', deletedAt: null },
        data: { status: 'REJECTED' },
      }),
    ]);
    return { message: 'Ride started. Have a safe trip!', ride: updated };
  }

  /**
   * Driver completes the trip: accepted bookings become COMPLETED and reward
   * points / ride counters are credited to the driver and every passenger.
   */
  async completeRide(driverId: string, rideId: string) {
    const ride = await this.getOwnedRide(driverId, rideId);
    if (ride.status !== 'ONGOING' && ride.status !== 'SCHEDULED') {
      throw new BadRequestException(`Ride is already ${ride.status.toLowerCase()}`);
    }
    if (ride.status === 'SCHEDULED' && ride.departureTime.getTime() > Date.now()) {
      throw new BadRequestException('Start the ride first, or wait until departure time');
    }

    const R = APP_CONSTANTS.REWARDS;

    return this.prisma.$transaction(async (tx) => {
      const accepted = await tx.bookingRequest.findMany({
        where: { rideId, status: 'ACCEPTED', deletedAt: null },
        select: { id: true, passengerId: true },
      });

      await tx.bookingRequest.updateMany({
        where: { rideId, status: 'ACCEPTED', deletedAt: null },
        data: { status: 'COMPLETED' },
      });
      await tx.bookingRequest.updateMany({
        where: { rideId, status: 'PENDING', deletedAt: null },
        data: { status: 'REJECTED' },
      });
      const updatedRide = await tx.ride.update({ where: { id: rideId }, data: { status: 'COMPLETED' } });

      const driverPoints = accepted.length > 0 ? R.POINTS_PER_RIDE_OFFERED + R.POINTS_PER_PASSENGER_CARRIED * accepted.length : 0;
      if (accepted.length > 0) {
        await this.addPoints(tx, driverId, driverPoints, true);
        for (const p of new Set(accepted.map((a) => a.passengerId))) {
          await this.addPoints(tx, p, R.POINTS_PER_RIDE_COMPLETED, true);
        }
      }

      return {
        message:
          accepted.length > 0
            ? `Ride completed! You earned ${driverPoints} points.`
            : 'Ride completed. No passengers were on board.',
        ride: updatedRide,
        passengersCompleted: accepted.length,
        pointsEarned: driverPoints,
      };
    });
  }

  /** Driver cancels a ride that has not started. All requests are declined. */
  async cancelRide(driverId: string, rideId: string) {
    const ride = await this.getOwnedRide(driverId, rideId);
    if (ride.status !== 'SCHEDULED') {
      throw new BadRequestException('Only scheduled rides can be cancelled');
    }
    const [updated, affected] = await this.prisma.$transaction([
      this.prisma.ride.update({ where: { id: rideId }, data: { status: 'CANCELLED' } }),
      this.prisma.bookingRequest.updateMany({
        where: { rideId, status: { in: ['PENDING', 'ACCEPTED'] }, deletedAt: null },
        data: { status: 'REJECTED' },
      }),
    ]);
    return {
      message: `Ride cancelled${affected.count ? `; ${affected.count} passenger(s) notified` : ''}`,
      ride: updated,
    };
  }

  private async addPoints(tx: Prisma.TransactionClient, userId: string, points: number, rideCompleted: boolean) {
    const user = await tx.user.update({
      where: { id: userId },
      data: {
        rewardPoints: { increment: points },
        ...(rideCompleted ? { ridesCompleted: { increment: 1 } } : {}),
      },
      select: { rewardPoints: true, level: true },
    });
    // Levels only ever go up.
    const level = levelForPoints(user.rewardPoints);
    if (level > user.level) {
      await tx.user.update({ where: { id: userId }, data: { level } });
    }
  }
}

function round(v: number, digits = 2): number {
  const f = 10 ** digits;
  return Math.round(v * f) / f;
}
