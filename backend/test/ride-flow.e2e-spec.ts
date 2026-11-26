/**
 * End-to-end tests for the full ride-sharing flow against a real PostgreSQL database.
 *
 *   DATABASE_URL=postgresql://postgres:testpass@127.0.0.1:55432/ridepool_test npm run test:e2e
 *
 * Safety: refuses to run unless DATABASE_URL points at localhost / 127.0.0.1
 * (set E2E_ALLOW_REMOTE=1 to override), so it never writes test data into Supabase.
 * Every run uses fresh users (unique suffix), so it can be re-run on the same DB.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { configureApp } from '../src/app.setup';
import { PrismaService } from '../src/prisma/prisma.service';

const dbUrl = process.env.DATABASE_URL ?? '';
if (!/@(localhost|127\.0\.0\.1)[:/]/.test(dbUrl) && process.env.E2E_ALLOW_REMOTE !== '1') {
  throw new Error(`Refusing to run e2e tests against a non-local database (${dbUrl.replace(/:[^:@/]+@/, ':***@')})`);
}

jest.setTimeout(60_000);

const RAJWADA = { lat: 22.7196, lng: 75.8577, name: 'Rajwada' };
const VIJAY_NAGAR = { lat: 22.7533, lng: 75.8937, name: 'Vijay Nagar Square' };
const MIDWAY = { lat: 22.7365, lng: 75.8757, name: 'Midway stop' };
const PALASIA = { lat: 22.7244, lng: 75.8839, name: 'Palasia' };

const run = Date.now().toString(36);
const minutesFromNow = (m: number) => new Date(Date.now() + m * 60_000).toISOString();

describe('Ride flow (e2e)', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let server: any;

  const tokens: Record<string, string> = {};
  const ids: Record<string, string> = {};

  const api = (method: 'get' | 'post' | 'put' | 'patch', path: string, who?: string) => {
    const req = request(server)[method](`/api/v1${path}`);
    return who ? req.set('Authorization', `Bearer ${tokens[who]}`) : req;
  };

  async function register(who: string, extra: Record<string, unknown> = {}) {
    const res = await api('post', '/auth/register')
      .send({
        name: `${who} Tester`,
        rollNumber: `E2E${run}${who}`.toLowerCase(), // stored upper-case
        collegeEmail: `${who}.${run}@Test.EDU`, // stored lower-case
        password: 'Password123!',
        confirmPassword: 'Password123!',
        ...extra,
      })
      .expect(201);
    tokens[who] = res.body.data.accessToken;
    ids[who] = res.body.data.user.id;
    return res.body.data.user;
  }

  function createRide(who: string, overrides: Record<string, unknown> = {}) {
    const res = api('post', '/rides', who).send({
      pickupAddress: RAJWADA.name,
      pickupLat: RAJWADA.lat,
      pickupLng: RAJWADA.lng,
      dropAddress: VIJAY_NAGAR.name,
      dropLat: VIJAY_NAGAR.lat,
      dropLng: VIJAY_NAGAR.lng,
      departureTime: minutesFromNow(30),
      totalSeats: 2,
      pricePerSeat: 60,
      vehicleType: 'Sedan',
      ...overrides,
    });
    return res;
  }

  const search = (who: string, from: typeof RAJWADA, to: typeof RAJWADA, extra: Record<string, string> = {}) =>
    api('get', '/rides/search', who).query({
      pickupLat: from.lat,
      pickupLng: from.lng,
      dropLat: to.lat,
      dropLng: to.lng,
      departureTime: new Date().toISOString(),
      seatsRequired: 1,
      radius: 2,
      ...extra,
    });

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = configureApp(moduleRef.createNestApplication());
    await app.init();
    server = app.getHttpServer();
    prisma = app.get(PrismaService);
  });

  afterAll(async () => {
    await app?.close();
  });

  // ------------------------------------------------------------------ auth

  describe('auth', () => {
    it('reports healthy with the database connected', async () => {
      const res = await api('get', '/health').expect(200);
      expect(res.body.data.database).toBe('connected');
    });

    it('registers driver and passengers, normalising email and roll number', async () => {
      const d = await register('driver');
      expect(d.collegeEmail).toBe(`driver.${run}@test.edu`);
      expect(d.rollNumber).toBe(`E2E${run}DRIVER`.toUpperCase());
      expect(d.passwordHash).toBeUndefined();
      await register('pax');
      await register('pax2');
      await register('outsider');
    });

    it('rejects weak passwords, mismatched confirmation and duplicates', async () => {
      await api('post', '/auth/register')
        .send({ name: 'Weak', rollNumber: `W${run}`, collegeEmail: `weak.${run}@test.edu`, password: 'password' })
        .expect(400);
      await api('post', '/auth/register')
        .send({ name: 'Mismatch', rollNumber: `M${run}`, collegeEmail: `mm.${run}@test.edu`, password: 'Password123!', confirmPassword: 'Password1234!' })
        .expect(400);
      await api('post', '/auth/register')
        .send({ name: 'Dup', rollNumber: `other${run}`, collegeEmail: `DRIVER.${run}@test.edu`, password: 'Password123!' })
        .expect(400);
    });

    it('logs in by email or roll number, case-insensitively', async () => {
      const byRoll = await api('post', '/auth/login')
        .send({ identifier: `e2e${run}driver`, password: 'Password123!' })
        .expect(200);
      expect(byRoll.body.data.user.id).toBe(ids.driver);
      await api('post', '/auth/login').send({ identifier: `DRIVER.${run}@TEST.EDU`, password: 'Password123!' }).expect(200);
      await api('post', '/auth/login').send({ identifier: `driver.${run}@test.edu`, password: 'wrong' }).expect(401);
    });

    it('rejects protected calls without a valid token', async () => {
      await api('get', '/users/profile').expect(401);
      await request(server).get('/api/v1/users/profile').set('Authorization', 'Bearer not-a-jwt').expect(401);
    });
  });

  // --------------------------------------------------------------- profile

  describe('profile', () => {
    it('updates profile incl. emergency contact and returns level info + stats', async () => {
      const res = await api('put', '/users/profile', 'pax')
        .send({
          department: 'CSE',
          year: 2,
          gender: 'Female',
          bloodGroup: 'O+',
          emergencyContacts: { name: 'Mom', phone: '+91 98765 43210', relation: 'Parent' },
        })
        .expect(200);
      expect(res.body.data.user.emergencyContacts).toEqual({ name: 'Mom', phone: '+91 98765 43210', relation: 'Parent' });

      const me = await api('get', '/users/profile', 'pax').expect(200);
      expect(me.body.data.levelTitle).toBe('Newcomer');
      expect(me.body.data.stats).toMatchObject({ ridesOffered: 0, ridesShared: 0 });
    });

    it('validates profile fields', async () => {
      await api('put', '/users/profile', 'pax').send({ emergencyContacts: { name: 'X', phone: 'abc' } }).expect(400);
      await api('put', '/users/profile', 'pax').send({ year: 9 }).expect(400);
      await api('put', '/users/profile', 'pax').send({ bloodGroup: 'Z+' }).expect(400);
      await api('put', '/users/profile', 'pax').send({ role: 'ADMIN' }).expect(400); // not whitelisted
    });

    it('public profile hides private fields', async () => {
      const res = await api('get', `/users/${ids.pax}`, 'driver').expect(200);
      expect(res.body.data.name).toBe('pax Tester');
      expect(res.body.data.collegeEmail).toBeUndefined();
      expect(res.body.data.emergencyContacts).toBeUndefined();
    });
  });

  // ---------------------------------------------------------------- rides

  describe('publishing rides', () => {
    it('rejects past departures, too-short trips and bad input', async () => {
      await createRide('driver', { departureTime: minutesFromNow(-5) }).expect(400);
      await createRide('driver', { dropLat: RAJWADA.lat + 0.0005, dropLng: RAJWADA.lng }).expect(400);
      await createRide('driver', { totalSeats: 0 }).expect(400);
      await createRide('driver', { pickupLat: 200 }).expect(400);
    });

    it('creates a ride with a road route (or straight-line fallback)', async () => {
      const res = await createRide('driver').expect(201);
      const ride = res.body.data.ride;
      ids.ride = ride.id;
      expect(ride.seatsLeft).toBe(2);
      expect(ride.status).toBe('SCHEDULED');
      expect(ride.routeKm).toBeGreaterThan(4);
      if (ride.routePolyline) {
        // OSRM reachable: real road distance is longer than the 5.3 km straight line
        expect(ride.routeDistanceKm).toBeGreaterThan(5.3);
        expect(ride.routeDurationMin).toBeGreaterThan(0);
      }
    });

    it('blocks a second ride by the same driver within 30 minutes', async () => {
      await createRide('driver', { departureTime: minutesFromNow(45) }).expect(400);
    });

    it('previews a route', async () => {
      const res = await api('get', '/rides/route-preview', 'driver')
        .query({ pickupLat: RAJWADA.lat, pickupLng: RAJWADA.lng, dropLat: PALASIA.lat, dropLng: PALASIA.lng })
        .expect(200);
      expect(['osrm', 'straight']).toContain(res.body.data.source);
      expect(res.body.data.distanceKm).toBeGreaterThan(2);
    });
  });

  // --------------------------------------------------------------- search

  describe('route-aware search', () => {
    it('finds the ride for a passenger getting off part-way, with a pro-rated fare', async () => {
      const res = await search('pax', RAJWADA, MIDWAY).expect(200);
      const hit = res.body.data.find((r: any) => r.id === ids.ride);
      expect(hit).toBeDefined();
      expect(hit.match.matchType).toBe('ON_ROUTE');
      expect(hit.estimatedFare).toBeLessThan(60);
      expect(hit.driverName).toBe('driver Tester');
      expect(hit.routePolyline).toBeUndefined(); // kept out of list payloads
    });

    it('finds the exact same trip as EXACT at full price', async () => {
      const res = await search('pax', RAJWADA, VIJAY_NAGAR).expect(200);
      const hit = res.body.data.find((r: any) => r.id === ids.ride);
      expect(hit.match.matchType).toBe('EXACT');
      expect(hit.estimatedFare).toBe(60);
    });

    it('does not match the opposite direction', async () => {
      const res = await search('pax', VIJAY_NAGAR, RAJWADA).expect(200);
      expect(res.body.data.find((r: any) => r.id === ids.ride)).toBeUndefined();
    });

    it('hides your own rides and respects the date window and seats', async () => {
      const own = await search('driver', RAJWADA, VIJAY_NAGAR).expect(200);
      expect(own.body.data.find((r: any) => r.id === ids.ride)).toBeUndefined();

      const tomorrow = new Date(Date.now() + 24 * 3600_000);
      const later = await search('pax', RAJWADA, VIJAY_NAGAR, {
        departureTime: tomorrow.toISOString(),
        departureTimeTo: new Date(tomorrow.getTime() + 3600_000).toISOString(),
      }).expect(200);
      expect(later.body.data.find((r: any) => r.id === ids.ride)).toBeUndefined();

      const tooMany = await search('pax', RAJWADA, VIJAY_NAGAR, { seatsRequired: '3' }).expect(200);
      expect(tooMany.body.data.find((r: any) => r.id === ids.ride)).toBeUndefined();
    });

    it('validates search input', async () => {
      await search('pax', RAJWADA, MIDWAY, { pickupLat: 'abc' }).expect(400);
      await search('pax', RAJWADA, MIDWAY, { sort: 'random' }).expect(400);
    });
  });

  // ------------------------------------------------------------- bookings

  describe('booking requests', () => {
    const book = (who: string, seats = 1, rideId = ids.ride) =>
      api('post', '/bookings/request', who).send({
        rideId,
        pickupAddress: RAJWADA.name,
        dropAddress: MIDWAY.name,
        seatsRequested: seats,
      });

    it('lets a passenger request a seat but not twice, and not on their own ride', async () => {
      const res = await book('pax').expect(201);
      ids.paxBooking = res.body.data.booking.id;
      expect(res.body.data.booking.status).toBe('PENDING');
      await book('pax').expect(400);
      await book('driver').expect(400);
      await book('pax2', 5).expect(400); // more than seats left
    });

    it('flags the request in search and ride details', async () => {
      const s = await search('pax', RAJWADA, MIDWAY).expect(200);
      expect(s.body.data.find((r: any) => r.id === ids.ride).myBookingStatus).toBe('PENDING');
      const d = await api('get', `/rides/${ids.ride}`, 'pax').expect(200);
      expect(d.body.data.myBooking.id).toBe(ids.paxBooking);
      expect(d.body.data.isDriver).toBe(false);
    });

    it('shows the request to the driver only', async () => {
      const inc = await api('get', '/bookings/incoming', 'driver').expect(200);
      const row = inc.body.data.find((b: any) => b.id === ids.paxBooking);
      expect(row.passenger.name).toBe('pax Tester');
      await api('get', `/bookings/ride/${ids.ride}`, 'pax').expect(403);
      await api('patch', `/bookings/${ids.paxBooking}/accept`, 'pax').expect(403);
    });

    it('accepts a request and decrements seats', async () => {
      const res = await api('patch', `/bookings/${ids.paxBooking}/accept`, 'driver').expect(200);
      expect(res.body.data.seatsLeft).toBe(1);
      await api('patch', `/bookings/${ids.paxBooking}/accept`, 'driver').expect(400); // not pending any more
    });

    it('declines another request', async () => {
      const res = await book('pax2').expect(201);
      ids.pax2Booking = res.body.data.booking.id;
      await api('patch', `/bookings/${ids.pax2Booking}/reject`, 'driver').expect(200);
      const mine = await api('get', '/bookings/my-bookings', 'pax2').expect(200);
      expect(mine.body.data.find((b: any) => b.id === ids.pax2Booking).status).toBe('REJECTED');
    });

    it('returns the seat when an accepted passenger cancels, then they can re-book', async () => {
      await api('patch', `/bookings/${ids.paxBooking}/cancel`, 'pax2').expect(403);
      await api('patch', `/bookings/${ids.paxBooking}/cancel`, 'pax').expect(200);
      let ride = await prisma.ride.findUniqueOrThrow({ where: { id: ids.ride } });
      expect(ride.seatsLeft).toBe(2);

      const res = await book('pax').expect(201);
      ids.paxBooking = res.body.data.booking.id;
      await api('patch', `/bookings/${ids.paxBooking}/accept`, 'driver').expect(200);
      ride = await prisma.ride.findUniqueOrThrow({ where: { id: ids.ride } });
      expect(ride.seatsLeft).toBe(1);
    });

    it('auto-declines remaining requests once the ride is full', async () => {
      const a = await book('pax2').expect(201);
      const b = await book('outsider').expect(201);
      const acc = await api('patch', `/bookings/${a.body.data.booking.id}/accept`, 'driver').expect(200);
      expect(acc.body.data.seatsLeft).toBe(0);
      const other = await prisma.bookingRequest.findUniqueOrThrow({ where: { id: b.body.data.booking.id } });
      expect(other.status).toBe('REJECTED');
      ids.pax2Booking = a.body.data.booking.id;
    });
  });

  // ------------------------------------------------------------ lifecycle

  describe('ride lifecycle, rewards and reviews', () => {
    it('does not allow reviews before the ride is completed', async () => {
      await api('post', '/reviews', 'pax').send({ rideId: ids.ride, targetId: ids.driver, rating: 5 }).expect(400);
    });

    it('only the driver can start; starting moves the ride to ONGOING', async () => {
      await api('patch', `/rides/${ids.ride}/start`, 'pax').expect(403);
      const res = await api('patch', `/rides/${ids.ride}/start`, 'driver').expect(200);
      expect(res.body.data.ride.status).toBe('ONGOING');
      await api('patch', `/rides/${ids.ride}/start`, 'driver').expect(400);
    });

    it('blocks passenger cancellation and new requests once started', async () => {
      await api('patch', `/bookings/${ids.paxBooking}/cancel`, 'pax').expect(400);
      const s = await search('outsider', RAJWADA, VIJAY_NAGAR).expect(200);
      expect(s.body.data.find((r: any) => r.id === ids.ride)).toBeUndefined();
    });

    it('completing credits points, trip counts and levels', async () => {
      const before = await prisma.user.findMany({ where: { id: { in: [ids.driver, ids.pax, ids.pax2] } } });
      const res = await api('patch', `/rides/${ids.ride}/complete`, 'driver').expect(200);
      expect(res.body.data.ride.status).toBe('COMPLETED');
      expect(res.body.data.passengersCompleted).toBe(2);
      expect(res.body.data.pointsEarned).toBe(30 + 2 * 20);

      const after = await prisma.user.findMany({ where: { id: { in: [ids.driver, ids.pax, ids.pax2] } } });
      const delta = (id: string) =>
        after.find((u) => u.id === id)!.rewardPoints - before.find((u) => u.id === id)!.rewardPoints;
      expect(delta(ids.driver)).toBe(70);
      expect(delta(ids.pax)).toBe(15);
      expect(delta(ids.pax2)).toBe(15);
      expect(after.find((u) => u.id === ids.driver)!.ridesCompleted).toBe(1);

      const bookings = await prisma.bookingRequest.findMany({ where: { rideId: ids.ride, deletedAt: null, status: 'COMPLETED' } });
      expect(bookings).toHaveLength(2);
      await api('patch', `/rides/${ids.ride}/complete`, 'driver').expect(400);
    });

    it('reviews: passenger rates driver, driver rates passenger, rating is averaged', async () => {
      await api('post', '/reviews', 'pax').send({ rideId: ids.ride, targetId: ids.driver, rating: 5, comment: 'Great' }).expect(201);
      await api('post', '/reviews', 'pax2').send({ rideId: ids.ride, targetId: ids.driver, rating: 4 }).expect(201);
      await api('post', '/reviews', 'pax').send({ rideId: ids.ride, targetId: ids.driver, rating: 1 }).expect(400); // duplicate
      await api('post', '/reviews', 'outsider').send({ rideId: ids.ride, targetId: ids.driver, rating: 1 }).expect(403);
      await api('post', '/reviews', 'pax').send({ rideId: ids.ride, targetId: ids.pax2, rating: 3 }).expect(400); // passenger→passenger
      await api('post', '/reviews', 'pax').send({ rideId: ids.ride, targetId: ids.pax, rating: 5 }).expect(400); // self
      await api('post', '/reviews', 'driver').send({ rideId: ids.ride, targetId: ids.pax, rating: 4 }).expect(201);
      await api('post', '/reviews', 'driver').send({ rideId: ids.ride, targetId: ids.driver, rating: 6 }).expect(400);

      const driver = await prisma.user.findUniqueOrThrow({ where: { id: ids.driver } });
      expect(driver.rating).toBe(4.5);

      const list = await api('get', `/reviews/user/${ids.driver}`, 'pax').expect(200);
      expect(list.body.meta).toMatchObject({ total: 2, average: 4.5 });

      const mine = await api('get', '/bookings/my-bookings', 'pax').expect(200);
      expect(mine.body.data.find((b: any) => b.rideId === ids.ride && b.status === 'COMPLETED').reviewed).toBe(true);
    });

    it('profile stats and leaderboard reflect the trip', async () => {
      const me = await api('get', '/users/profile', 'driver').expect(200);
      expect(me.body.data.stats.ridesOffered).toBe(1);
      expect(me.body.data.stats.reviewsReceived).toBe(2);
      const board = await api('get', '/users/leaderboard', 'pax').query({ limit: 50 }).expect(200);
      const row = board.body.data.find((u: any) => u.id === ids.driver);
      expect(row).toBeDefined();
      expect(row.rank).toBeGreaterThan(0);
    });
  });

  // ----------------------------------------------------------- cancellation

  describe('driver cancels a ride', () => {
    it('declines all its bookings and hides it from search', async () => {
      const ride = await createRide('driver', { departureTime: minutesFromNow(180) }).expect(201);
      const rideId = ride.body.data.ride.id;
      const b = await api('post', '/bookings/request', 'outsider')
        .send({ rideId, pickupAddress: 'A', dropAddress: 'B', seatsRequested: 1 })
        .expect(201);
      await api('patch', `/rides/${rideId}/cancel`, 'pax').expect(403);
      await api('patch', `/rides/${rideId}/cancel`, 'driver').expect(200);
      const booking = await prisma.bookingRequest.findUniqueOrThrow({ where: { id: b.body.data.booking.id } });
      expect(booking.status).toBe('REJECTED');
      const s = await search('outsider', RAJWADA, VIJAY_NAGAR).expect(200);
      expect(s.body.data.find((r: any) => r.id === rideId)).toBeUndefined();
      await api('patch', `/rides/${rideId}/start`, 'driver').expect(400);
    });

    it('cannot start a ride more than an hour early', async () => {
      const ride = await createRide('driver', { departureTime: minutesFromNow(300) }).expect(201);
      await api('patch', `/rides/${ride.body.data.ride.id}/start`, 'driver').expect(400);
      await api('patch', `/rides/${ride.body.data.ride.id}/complete`, 'driver').expect(400);
    });
  });

  // ------------------------------------------------------------ concurrency

  describe('concurrency', () => {
    it('never oversells the last seat when two accepts race', async () => {
      const ride = await createRide('pax2', { totalSeats: 1, departureTime: minutesFromNow(120) }).expect(201);
      const rideId = ride.body.data.ride.id;
      const r1 = await api('post', '/bookings/request', 'pax').send({ rideId, pickupAddress: 'A', dropAddress: 'B', seatsRequested: 1 }).expect(201);
      const r2 = await api('post', '/bookings/request', 'outsider').send({ rideId, pickupAddress: 'A', dropAddress: 'B', seatsRequested: 1 }).expect(201);

      const results = await Promise.all([
        api('patch', `/bookings/${r1.body.data.booking.id}/accept`, 'pax2'),
        api('patch', `/bookings/${r2.body.data.booking.id}/accept`, 'pax2'),
      ]);
      const ok = results.filter((r) => r.status === 200);
      expect(ok).toHaveLength(1);
      const final = await prisma.ride.findUniqueOrThrow({ where: { id: rideId } });
      expect(final.seatsLeft).toBe(0);
      const accepted = await prisma.bookingRequest.count({ where: { rideId, status: 'ACCEPTED' } });
      expect(accepted).toBe(1);
    });
  });

  // ------------------------------------------------------------------- SOS

  describe('SOS', () => {
    it('raises one active alert per user, updates its location and resolves', async () => {
      const a = await api('post', '/sos', 'pax').send({ rideId: ids.ride, lat: 22.73, lng: 75.87 }).expect(201);
      const b = await api('post', '/sos', 'pax').send({ lat: 22.74, lng: 75.88 }).expect(201);
      expect(b.body.data.alert.id).toBe(a.body.data.alert.id);
      expect(b.body.data.alert.lat).toBe(22.74);
      expect(b.body.data.alert.user.emergencyContacts.name).toBe('Mom');
      expect(b.body.data.alert.ride.vehicleType).toBe('Sedan');

      await api('get', '/sos/active', 'pax').expect(403); // security / admin only
      const mine = await api('get', '/sos/mine', 'pax').expect(200);
      expect(mine.body.data[0].status).toBe('ACTIVE');

      await api('patch', `/sos/${a.body.data.alert.id}/resolve`, 'outsider').expect(403);
      await api('patch', `/sos/${a.body.data.alert.id}/resolve`, 'pax').expect(200);
    });

    it('still raises an alert with a bogus ride id or no location', async () => {
      await api('post', '/sos', 'pax2').send({}).expect(201);
      await api('post', '/sos', 'pax2').send({ rideId: 'not-a-uuid' }).expect(400);
    });
  });
});
