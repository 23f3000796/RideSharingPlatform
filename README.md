# Campus RidePool

A campus ride-sharing app: students who drive to or from college publish their trip and
empty seats; other students going the same way find them, request a seat and travel together.

```
frontend/   Android app (Kotlin, XML views, Google Maps / Places, Retrofit)
backend/    REST API (NestJS 11, Prisma 7, PostgreSQL on Supabase, JWT auth, Swagger)
```

---

## How route matching works ("can this driver drop me?")

`backend/src/rides/route-matching.ts` (unit tests in `route-matching.spec.ts`).

A driver's ride is a route from their pickup to their drop. A passenger searching
`A → B` matches a ride when **all** of these hold:

| Check | Rule |
|---|---|
| Pickup near the route | distance from A to the closest point on the driver's route ≤ radius (default 2 km, widen to 5 km from the app) |
| Drop near the route | same for B |
| Same direction | A is reached *before* B along the route, so passengers going the opposite way are rejected |
| Reasonable detour | `(start→A→B→end) − (start→end)` ≤ max(3 km, 2×radius, 35 % of the route) |

The old logic only matched when A was within 5 km of the driver's *start* and B within 5 km
of the driver's *end*. So anyone getting on or off part-way was never matched, there was no direction
check, and the SQL itself was broken (a JS `Date` string was pasted into raw SQL).

### Road routes (OSRM)

When a driver publishes, the backend asks **OSRM** (OpenStreetMap routing, free, no API key) for
the real road route and stores it on the ride (`routePolyline`, `routeDistanceKm`, `routeDurationMin`).
Matching then measures distances to the actual road, the riding distance along it, and the
time the driver reaches each passenger's pickup. Rides created before this get their route the
first time someone opens them.

* If OSRM is slow or down, the ride is saved without a road route and the straight line is used.
  Publishing and searching never fail because of routing.
* Default server: `https://router.project-osrm.org`, the public demo. It's fine for development
  and demos, but it's rate-limited, has no uptime guarantee and only offers the car profile.
  Set `OSRM_URL` in `backend/.env` to a self-hosted OSRM for anything heavier.
* `GET /api/v1/rides/route-preview` gives the Offer Ride screen the road distance and duration for its price.

Each result also carries:

* `match.matchType`: `EXACT` (same start and end, within 1 km) or `ON_ROUTE` (joins or leaves part-way)
* `pickupDistanceKm`, `dropDistanceKm`, `detourKm`, `pickupFraction` / `dropFraction` (where along the route)
* `estimatedFare`: pro-rated fare for partial trips (`price × yourKm / routeKm`, min ₹10, never above the full price)
* `myBookingStatus`: whether you already requested this ride

Results are ranked by fit (`pickupDist + dropDist + 0.5 × detour`), or by fare or time.
The SQL step is parameterised (`Prisma.sql`) and pre-filters by status, seats, time window,
"not my own ride" and a bounding box. The geometry runs in TypeScript. The matcher works on
any polyline, so road-following routes (e.g. from the Directions API) can be plugged in later
without changing the maths.

---

## Features

**Accounts & profile**
- Register / sign in with college email or roll number (case-insensitive), JWT session
- Profile with level, points, rating, trips, department, course/year, gender, blood group, vehicle
- Edit profile, including an **emergency contact** used by SOS
- Stays signed in when offline (only a real 401 signs you out)

**Offer a ride**
- Pick start and destination on the map (Places search, GPS, centre-pin)
- Date/time (must be ≥ 10 min ahead), seats capped by vehicle (bike 1, auto 3, car 4, SUV 6)
- Fair cost-sharing price suggestion (trip cost split between driver and passengers), editable, can be free
- Vehicle number, prefilled from profile
- Server blocks overlapping rides by the same driver (±30 min) and trips shorter than 300 m

**Find a ride**
- Route-aware search (above), "Any day" or a specific date, 1–4 seats
- Cards show *Same route* / *On your way*, pickup and drop walking distance, and your fare
- Filters (vehicle, driver gender), sort (best match / lowest fare / earliest)
- Empty state offers to widen the search radius

**Ride details**
- Map with the driver route, your pickup and drop, dashed walk-to-route lines and the stretch you ride
- Seat picker, request confirmation, cancel / withdraw, live booking status
- Share trip details (driver, vehicle, route link) with friends or family

**My Rides** (3 tabs)
- *Booked*: withdraw or cancel, SOS while ongoing, rate the driver after completion
- *Requests*: accept or decline, seat availability, rate passengers after completion
- *Offered*: **Start → Complete** or **Cancel** a ride, pending-request badge

**Ride lifecycle (backend)**
- `SCHEDULED → ONGOING → COMPLETED`, or `CANCELLED`
- Start: pending requests are declined automatically
- Accepting the last seat auto-declines the remaining requests
- Seat decrement is atomic, so two accepts can't oversell the last seat
- Complete: bookings become COMPLETED and points and trip counts are credited

**Ratings & rewards**
- 1–5 stars + comment after a completed ride (passenger → driver, driver → passenger)
- User rating = average of reviews. One review per person per ride.
- Points: driver +30 +20/passenger, passenger +15, review +5. Level every 150 points (Newcomer → Legend).
- Leaderboard with podium, your rank highlighted

**Safety**
- SOS from home, ride details and ongoing bookings: sends GPS + ride + vehicle to `/api/v1/sos`
- Then offers: call 112, call or SMS your emergency contact with a maps link, share location
- Security/admin roles can list active alerts and resolve them

---

## Running it

### Backend

```bash
cd backend
npm install
# .env needs DATABASE_URL (pooler), DIRECT_URL, JWT_SECRET, PORT; optional OSRM_URL, OSRM_TIMEOUT_MS
npx prisma generate
npx prisma db push      # REQUIRED once: adds the road-route columns (routePolyline, routeDistanceKm, routeDurationMin) to Ride
npx prisma db seed      # optional: WIPES users/rides/bookings and loads demo data (password: Password123!)
npm run start:dev
```

Swagger docs: `http://localhost:3000/api/docs`. Unit tests: `npm test`.

> If the API logs `connection to database not available`, the Supabase project is probably
> paused (free tier pauses after inactivity). Resume it from the Supabase dashboard.

### Android

1. Open `frontend/` in Android Studio.
2. Set the backend address in `frontend/gradle.properties` (or pass `-PAPI_BASE_URL=...`):
   - Emulator: `API_BASE_URL=http://10.0.2.2:3000/`
   - Phone on the same Wi-Fi: `API_BASE_URL=http://<your-laptop-LAN-IP>:3000/`
3. Make sure `google_maps_key` in `res/values/strings.xml` has Maps SDK + Places enabled.
4. Run the `app` configuration.

Cleartext HTTP is allowed for development (`res/xml/network_security_config.xml`). Use HTTPS for a release build.

---

## API overview (`/api/v1`)

| Area | Endpoints |
|---|---|
| Auth | `POST auth/register`, `POST auth/login` |
| Users | `GET/PUT users/profile`, `GET users/leaderboard`, `GET users/:id` |
| Rides | `POST rides`, `GET rides/search`, `GET rides/route-preview`, `GET rides/my-rides`, `GET rides/:id`, `PATCH rides/:id/start \| complete \| cancel` |
| Bookings | `POST bookings/request`, `GET bookings/my-bookings`, `GET bookings/incoming`, `GET bookings/ride/:rideId`, `PATCH bookings/:id/accept \| reject \| cancel` |
| Reviews | `POST reviews`, `GET reviews/user/:id` |
| SOS | `POST sos`, `GET sos/mine`, `GET sos/active` (security/admin), `PATCH sos/:id/resolve` |
| Health | `GET health` |

All responses use `{ success, message, data, meta?, timestamp }`.
