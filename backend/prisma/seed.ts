import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { BookingStatus, PrismaClient, RideStatus, Role } from '@prisma/client';
import * as bcrypt from 'bcrypt';
import { Pool } from 'pg';
import { RoutingService } from '../src/routing/routing.service';

const connectionString = process.env.DATABASE_URL;
if (!connectionString?.trim()) {
  throw new Error('DATABASE_URL is not set. Configure it in .env or your environment before seeding.');
}

const pool = new Pool({ connectionString });
const prisma = new PrismaClient({ adapter: new PrismaPg(pool) });

async function main() {
  console.log('Starting Prisma Seed...');

  // 1. Clean existing data (optional, useful for clean slate)
  // Be careful with this in production! This is why seed is usually run on dev/staging.
  // Children first: reviews and SOS alerts reference rides and users.
  await prisma.review.deleteMany();
  await prisma.sosAlert.deleteMany();
  await prisma.bookingRequest.deleteMany();
  await prisma.ride.deleteMany();
  await prisma.user.deleteMany();

  console.log('Cleared existing data.');

  // 2. Setup standard users
  const passwordHash = await bcrypt.hash('Password123!', 10);

  const usersData = [
    {
      name: 'Yash Upadhyay',
      rollNumber: '22BCS101',
      collegeEmail: 'yash.u@university.edu',
      department: 'Computer Science',
      course: 'B.Tech',
      year: 3,
      role: Role.ADMIN,
      profilePhotoUrl: 'https://i.pravatar.cc/150?u=yash',
      rewardPoints: 500,
      level: 4,
      rating: 4.8,
      isVerified: true,
      vehicleNumber: 'MP09 AB 1234',
    },
    {
      name: 'Ishita Das',
      rollNumber: '22BDS102',
      collegeEmail: 'ishita.d@university.edu',
      department: 'Design',
      course: 'B.Des',
      year: 3,
      role: Role.USER,
      profilePhotoUrl: 'https://i.pravatar.cc/150?u=ishita',
      rewardPoints: 350,
      level: 3,
      rating: 4.9,
      isVerified: true,
      vehicleNumber: 'MP09 XY 9876',
    },
    {
      name: 'Rahul Sharma',
      rollNumber: '23BEC205',
      collegeEmail: 'rahul.s@university.edu',
      department: 'Electronics',
      course: 'B.Tech',
      year: 2,
      role: Role.USER,
      profilePhotoUrl: 'https://i.pravatar.cc/150?u=rahul',
      rewardPoints: 120,
      level: 1,
      rating: 4.5,
      isVerified: false,
    },
    {
      name: 'Sneha Patel',
      rollNumber: '21BME045',
      collegeEmail: 'sneha.p@university.edu',
      department: 'Mechanical',
      course: 'B.Tech',
      year: 4,
      role: Role.USER,
      profilePhotoUrl: 'https://i.pravatar.cc/150?u=sneha',
      rewardPoints: 800,
      level: 6,
      rating: 5.0,
      isVerified: true,
      vehicleNumber: 'MP09 PQ 4567',
    },
    {
      name: 'Aman Gupta',
      rollNumber: '24BCA012',
      collegeEmail: 'aman.g@university.edu',
      department: 'Computer Applications',
      course: 'BCA',
      year: 1,
      role: Role.USER,
      profilePhotoUrl: 'https://i.pravatar.cc/150?u=aman',
      rewardPoints: 10,
      level: 1,
      rating: 0,
      isVerified: false,
    }
  ];

  const createdUsers = [];
  for (const u of usersData) {
    const user = await prisma.user.create({
      data: {
        ...u,
        passwordHash,
      },
    });
    createdUsers.push(user);
    console.log(`Created user: ${user.name}`);
  }

  // Assign drivers and passengers for realistic seed
  const driver1 = createdUsers[0]; // Yash
  const driver2 = createdUsers[1]; // Ishita
  const driver3 = createdUsers[3]; // Sneha
  const passenger1 = createdUsers[2]; // Rahul
  const passenger2 = createdUsers[4]; // Aman

  // 3. Create Rides (Indore realistic coordinates)
  // Campus: Symbiosis University of Applied Sciences, Super Corridor ~ 22.7533, 75.7921
  // (the old seed used 22.7196, 75.8577, which is Rajwada in the old city, not the campus)
  // Bhawarkuan Area ~ 22.6934, 75.8682
  // Palasia ~ 22.7244, 75.8839
  // Vijay Nagar ~ 22.7533, 75.8937

  const now = new Date();
  const future1 = new Date(now.getTime() + 2 * 60 * 60 * 1000); // 2 hours from now
  const future2 = new Date(now.getTime() + 24 * 60 * 60 * 1000); // Tomorrow
  const past = new Date(now.getTime() - 24 * 60 * 60 * 1000); // Yesterday

  const ridesData = [
    {
      driverId: driver1.id,
      pickupLat: 22.7533,
      pickupLng: 75.7921,
      pickupAddress: 'SUAS Main Gate, Super Corridor',
      dropLat: 22.7533,
      dropLng: 75.8937,
      dropAddress: 'Vijay Nagar Square',
      departureTime: future1,
      totalSeats: 3,
      seatsLeft: 2, // 1 seat taken
      pricePerSeat: 50,
      vehicleType: 'CAR',
      vehicleNumber: driver1.vehicleNumber,
      status: RideStatus.SCHEDULED,
    },
    {
      driverId: driver2.id,
      pickupLat: 22.6934,
      pickupLng: 75.8682,
      pickupAddress: 'Bhawarkuan',
      dropLat: 22.7533,
      dropLng: 75.7921,
      dropAddress: 'SUAS Campus Library',
      departureTime: future2,
      totalSeats: 2,
      seatsLeft: 2, // 0 taken
      pricePerSeat: 30,
      vehicleType: 'BIKE',
      vehicleNumber: driver2.vehicleNumber,
      status: RideStatus.SCHEDULED,
    },
    {
      driverId: driver3.id,
      pickupLat: 22.7244,
      pickupLng: 75.8839,
      pickupAddress: 'Palasia Square',
      dropLat: 22.7533,
      dropLng: 75.7921,
      dropAddress: 'SUAS Girls Hostel',
      departureTime: past,
      totalSeats: 4,
      seatsLeft: 1, // 3 taken, ride complete
      pricePerSeat: 40,
      vehicleType: 'CAR',
      vehicleNumber: driver3.vehicleNumber,
      status: RideStatus.COMPLETED,
    },
    {
      driverId: driver1.id, // Edge case: ride with no bookings yet
      pickupLat: 22.7533,
      pickupLng: 75.7921,
      pickupAddress: 'SUAS Campus',
      dropLat: 22.7244,
      dropLng: 75.8839,
      dropAddress: 'Palasia',
      departureTime: new Date(now.getTime() + 48 * 60 * 60 * 1000), // Day after tomorrow
      totalSeats: 2,
      seatsLeft: 2,
      pricePerSeat: 45,
      vehicleType: 'BIKE',
      vehicleNumber: driver1.vehicleNumber,
      status: RideStatus.SCHEDULED,
    }
  ];

  // Road routes from OSRM so search matches against real roads (null if OSRM is unreachable).
  const routing = new RoutingService();
  const createdRides = [];
  for (const r of ridesData) {
    const road = await routing.route({ lat: r.pickupLat, lng: r.pickupLng }, { lat: r.dropLat, lng: r.dropLng });
    const ride = await prisma.ride.create({
      data: {
        ...r,
        routePolyline: road?.polyline ?? null,
        routeDistanceKm: road?.distanceKm ?? null,
        routeDurationMin: road?.durationMin ?? null,
      },
    });
    createdRides.push(ride);
    console.log(
      `Created ride from ${ride.pickupAddress} to ${ride.dropAddress}` +
        (road ? ` (${road.distanceKm} km by road)` : ' (no road route)'),
    );
  }

  // 4. Create Bookings
  // Booking 1: Passenger 1 booking Yash's ride (Accepted, hence seatsLeft is 2)
  await prisma.bookingRequest.create({
    data: {
      rideId: createdRides[0].id,
      passengerId: passenger1.id,
      pickupAddress: 'SUAS Main Gate, Super Corridor',
      dropAddress: 'C21 Mall',
      seatsRequested: 1,
      status: BookingStatus.ACCEPTED,
    },
  });

  // Booking 2: Passenger 2 requesting Yash's ride (Pending)
  await prisma.bookingRequest.create({
    data: {
      rideId: createdRides[0].id,
      passengerId: passenger2.id,
      pickupAddress: 'SUAS Main Gate, Super Corridor',
      dropAddress: 'Vijay Nagar',
      seatsRequested: 1,
      status: BookingStatus.PENDING,
    },
  });

  // Booking 3: Passenger 1 booked past ride (Completed)
  await prisma.bookingRequest.create({
    data: {
      rideId: createdRides[2].id,
      passengerId: passenger1.id,
      pickupAddress: 'Palasia',
      dropAddress: 'Hostel',
      seatsRequested: 1,
      status: BookingStatus.COMPLETED, // the ride itself is COMPLETED
    },
  });

  console.log('Created booking requests.');
  console.log('Seed completed successfully!');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
    await pool.end();
  });
