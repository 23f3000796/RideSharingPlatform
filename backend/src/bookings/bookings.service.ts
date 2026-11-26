import { Injectable, NotFoundException, BadRequestException, ForbiddenException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { RequestBookingDto } from './dto/request-booking.dto';
import { PaginationQueryDto } from '../common/dto/pagination-query.dto';

const PASSENGER_SELECT = {
  id: true,
  name: true,
  rollNumber: true,
  profilePhotoUrl: true,
  rating: true,
  gender: true,
  department: true,
  year: true,
  isVerified: true,
} as const;

const DRIVER_SELECT = {
  id: true,
  name: true,
  profilePhotoUrl: true,
  vehicleNumber: true,
  rating: true,
  gender: true,
  isVerified: true,
} as const;

@Injectable()
export class BookingsService {
  constructor(private prisma: PrismaService) {}

  async requestBooking(passengerId: string, dto: RequestBookingDto) {
    const ride = await this.prisma.ride.findFirst({
      where: { id: dto.rideId, deletedAt: null },
    });

    if (!ride) {
      throw new NotFoundException('Ride not found or is no longer available');
    }
    if (ride.driverId === passengerId) {
      throw new BadRequestException('You cannot book your own ride');
    }
    if (ride.status !== 'SCHEDULED') {
      throw new BadRequestException('This ride is no longer accepting requests');
    }
    if (ride.departureTime.getTime() <= Date.now()) {
      throw new BadRequestException('This ride has already departed');
    }
    if (ride.seatsLeft < dto.seatsRequested) {
      throw new BadRequestException(
        ride.seatsLeft === 0 ? 'This ride is full' : `Only ${ride.seatsLeft} seat(s) left`,
      );
    }

    const existingBooking = await this.prisma.bookingRequest.findFirst({
      where: {
        rideId: dto.rideId,
        passengerId,
        status: { in: ['PENDING', 'ACCEPTED'] },
        deletedAt: null,
      },
    });
    if (existingBooking) {
      throw new BadRequestException('You already have an active request for this ride');
    }

    const booking = await this.prisma.bookingRequest.create({
      data: {
        rideId: dto.rideId,
        passengerId,
        pickupAddress: dto.pickupAddress.trim(),
        dropAddress: dto.dropAddress.trim(),
        seatsRequested: dto.seatsRequested,
      },
    });
    return { message: 'Request sent to the driver', booking };
  }

  async getMyBookings(passengerId: string, query: PaginationQueryDto) {
    const { page, limit } = query;
    const skip = (page - 1) * limit;
    const where = { passengerId, deletedAt: null };

    const [bookings, total] = await Promise.all([
      this.prisma.bookingRequest.findMany({
        where,
        include: { ride: { include: { driver: { select: DRIVER_SELECT } } } },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      this.prisma.bookingRequest.count({ where }),
    ]);

    // Has the passenger already reviewed the driver for each ride?
    const reviews = await this.prisma.review.findMany({
      where: { authorId: passengerId, rideId: { in: bookings.map((b) => b.rideId) }, deletedAt: null },
      select: { rideId: true, targetId: true },
    });
    const reviewed = new Set(reviews.map((r) => `${r.rideId}:${r.targetId}`));

    const data = bookings.map((b) => ({
      ...b,
      reviewed: reviewed.has(`${b.rideId}:${b.ride.driverId}`),
    }));
    return { data, meta: { total, page, limit } };
  }

  /** All requests on every ride the user drives, newest first (single query, no N+1). */
  async getIncomingRequests(driverId: string, query: PaginationQueryDto) {
    const { page, limit } = query;
    const skip = (page - 1) * limit;
    const where = { deletedAt: null, ride: { driverId, deletedAt: null } };

    const [bookings, total] = await Promise.all([
      this.prisma.bookingRequest.findMany({
        where,
        include: {
          passenger: { select: PASSENGER_SELECT },
          ride: true,
        },
        // Pending first so drivers see what needs action
        orderBy: [{ status: 'asc' }, { createdAt: 'desc' }],
        skip,
        take: limit,
      }),
      this.prisma.bookingRequest.count({ where }),
    ]);

    const reviews = await this.prisma.review.findMany({
      where: { authorId: driverId, rideId: { in: bookings.map((b) => b.rideId) }, deletedAt: null },
      select: { rideId: true, targetId: true },
    });
    const reviewed = new Set(reviews.map((r) => `${r.rideId}:${r.targetId}`));

    const data = bookings.map((b) => ({ ...b, reviewed: reviewed.has(`${b.rideId}:${b.passengerId}`) }));
    return { data, meta: { total, page, limit } };
  }

  async getRideBookings(driverId: string, rideId: string) {
    const ride = await this.prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride || ride.deletedAt) {
      throw new NotFoundException('Ride not found');
    }
    if (ride.driverId !== driverId) {
      throw new ForbiddenException('Only the driver can view booking requests for this ride');
    }

    return this.prisma.bookingRequest.findMany({
      where: { rideId, deletedAt: null },
      include: { passenger: { select: PASSENGER_SELECT } },
      orderBy: { createdAt: 'desc' },
    });
  }

  async acceptBooking(driverId: string, bookingId: string) {
    return this.prisma.$transaction(async (tx) => {
      const booking = await tx.bookingRequest.findUnique({
        where: { id: bookingId },
        include: { ride: true },
      });

      if (!booking || booking.deletedAt) {
        throw new NotFoundException('Booking request not found');
      }
      if (booking.ride.driverId !== driverId) {
        throw new ForbiddenException('Only the driver can accept this request');
      }
      if (booking.status !== 'PENDING') {
        throw new BadRequestException('Only pending requests can be accepted');
      }
      if (booking.ride.status !== 'SCHEDULED') {
        throw new BadRequestException('Ride is no longer scheduled');
      }

      // Atomic conditional decrement: protects against two accepts racing for the last seat.
      const dec = await tx.ride.updateMany({
        where: { id: booking.rideId, seatsLeft: { gte: booking.seatsRequested } },
        data: { seatsLeft: { decrement: booking.seatsRequested } },
      });
      if (dec.count === 0) {
        throw new BadRequestException('Not enough seats left to accept this request');
      }

      const updatedBooking = await tx.bookingRequest.update({
        where: { id: bookingId },
        data: { status: 'ACCEPTED' },
      });
      const ride = await tx.ride.findUniqueOrThrow({ where: { id: booking.rideId } });

      // Ride is full: remaining pending requests can no longer be served.
      let autoRejected = 0;
      if (ride.seatsLeft === 0) {
        const res = await tx.bookingRequest.updateMany({
          where: { rideId: ride.id, status: 'PENDING', deletedAt: null },
          data: { status: 'REJECTED' },
        });
        autoRejected = res.count;
      }

      return {
        message: autoRejected
          ? `Booking accepted. Ride is now full; ${autoRejected} other request(s) declined.`
          : 'Booking accepted successfully',
        seatsLeft: ride.seatsLeft,
        booking: updatedBooking,
      };
    });
  }

  async rejectBooking(driverId: string, bookingId: string) {
    const booking = await this.prisma.bookingRequest.findUnique({
      where: { id: bookingId },
      include: { ride: true },
    });

    if (!booking || booking.deletedAt) {
      throw new NotFoundException('Booking request not found');
    }
    if (booking.ride.driverId !== driverId) {
      throw new ForbiddenException('Only the driver can reject this request');
    }
    if (booking.status !== 'PENDING') {
      throw new BadRequestException('Only pending requests can be rejected');
    }

    const updated = await this.prisma.bookingRequest.update({
      where: { id: bookingId },
      data: { status: 'REJECTED' },
    });
    return { message: 'Request declined', booking: updated };
  }

  async cancelBooking(passengerId: string, bookingId: string) {
    return this.prisma.$transaction(async (tx) => {
      const booking = await tx.bookingRequest.findUnique({
        where: { id: bookingId },
        include: { ride: true },
      });

      if (!booking || booking.deletedAt) {
        throw new NotFoundException('Booking request not found');
      }
      if (booking.passengerId !== passengerId) {
        throw new ForbiddenException('Only the passenger can cancel their own request');
      }
      if (booking.status !== 'PENDING' && booking.status !== 'ACCEPTED') {
        throw new BadRequestException('This booking cannot be cancelled');
      }
      if (booking.ride.status !== 'SCHEDULED') {
        throw new BadRequestException('The ride has already started; it can no longer be cancelled');
      }

      // Return seats to the ride if they were reserved
      if (booking.status === 'ACCEPTED') {
        await tx.ride.update({
          where: { id: booking.rideId },
          data: { seatsLeft: { increment: booking.seatsRequested } },
        });
      }

      // BookingStatus has no CANCELLED value, so cancellation is a soft delete.
      const cancelled = await tx.bookingRequest.update({
        where: { id: bookingId },
        data: { deletedAt: new Date() },
      });
      return { message: 'Booking cancelled', booking: cancelled };
    });
  }
}
