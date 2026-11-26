import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CreateSosDto } from './dto/create-sos.dto';

const ALERT_INCLUDE = {
  user: {
    select: {
      id: true,
      name: true,
      rollNumber: true,
      collegeEmail: true,
      gender: true,
      bloodGroup: true,
      emergencyContacts: true,
      profilePhotoUrl: true,
    },
  },
  ride: {
    select: {
      id: true,
      pickupAddress: true,
      dropAddress: true,
      vehicleType: true,
      vehicleNumber: true,
      status: true,
      departureTime: true,
      driver: { select: { id: true, name: true, rollNumber: true } },
    },
  },
} as const;

@Injectable()
export class SosService {
  constructor(private prisma: PrismaService) {}

  async raise(userId: string, dto: CreateSosDto) {
    let rideId: string | null = null;
    if (dto.rideId) {
      const ride = await this.prisma.ride.findFirst({ where: { id: dto.rideId, deletedAt: null } });
      rideId = ride?.id ?? null; // never fail an SOS because of a bad ride id
    }

    // One active alert per user: refresh its location instead of creating duplicates.
    const active = await this.prisma.sosAlert.findFirst({ where: { userId, status: 'ACTIVE' } });
    const alert = active
      ? await this.prisma.sosAlert.update({
          where: { id: active.id },
          data: {
            lat: dto.lat ?? active.lat,
            lng: dto.lng ?? active.lng,
            rideId: rideId ?? active.rideId,
          },
          include: ALERT_INCLUDE,
        })
      : await this.prisma.sosAlert.create({
          data: { userId, rideId, lat: dto.lat ?? null, lng: dto.lng ?? null },
          include: ALERT_INCLUDE,
        });

    return { message: 'SOS alert raised. Campus security has been notified.', alert };
  }

  mine(userId: string) {
    return this.prisma.sosAlert.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      take: 20,
      include: ALERT_INCLUDE,
    });
  }

  active(role: string) {
    if (role !== 'ADMIN' && role !== 'SECURITY') {
      throw new ForbiddenException('Only campus security can view all alerts');
    }
    return this.prisma.sosAlert.findMany({
      where: { status: 'ACTIVE' },
      orderBy: { createdAt: 'desc' },
      include: ALERT_INCLUDE,
    });
  }

  async resolve(userId: string, role: string, id: string) {
    const alert = await this.prisma.sosAlert.findUnique({ where: { id } });
    if (!alert) throw new NotFoundException('Alert not found');
    if (alert.userId !== userId && role !== 'ADMIN' && role !== 'SECURITY') {
      throw new ForbiddenException('You cannot resolve this alert');
    }
    const updated = await this.prisma.sosAlert.update({ where: { id }, data: { status: 'RESOLVED' } });
    return { message: 'Alert marked as resolved', alert: updated };
  }
}
