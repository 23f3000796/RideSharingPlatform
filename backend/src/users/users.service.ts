import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { UpdateProfileDto } from './dto/update-profile.dto';
import { PaginationQueryDto } from '../common/dto/pagination-query.dto';
import { APP_CONSTANTS, LEVEL_TITLES } from '../common/constants/app.constants';

@Injectable()
export class UsersService {
  constructor(private prisma: PrismaService) {}

  private levelInfo(level: number, points: number) {
    const per = APP_CONSTANTS.REWARDS.POINTS_PER_LEVEL;
    const maxed = level >= APP_CONSTANTS.REWARDS.MAX_LEVEL;
    return {
      levelTitle: LEVEL_TITLES[level] ?? 'Rider',
      nextLevelAt: maxed ? null : level * per,
      pointsToNextLevel: maxed ? 0 : Math.max(0, level * per - points),
    };
  }

  private async stats(userId: string) {
    const [ridesOffered, ridesShared, upcomingAsDriver, upcomingAsPassenger, reviewsReceived] = await Promise.all([
      this.prisma.ride.count({ where: { driverId: userId, deletedAt: null, status: 'COMPLETED' } }),
      this.prisma.bookingRequest.count({ where: { passengerId: userId, deletedAt: null, status: 'COMPLETED' } }),
      this.prisma.ride.count({
        where: { driverId: userId, deletedAt: null, status: { in: ['SCHEDULED', 'ONGOING'] }, departureTime: { gte: new Date(Date.now() - 6 * 3600_000) } },
      }),
      this.prisma.bookingRequest.count({
        where: {
          passengerId: userId,
          deletedAt: null,
          status: { in: ['PENDING', 'ACCEPTED'] },
          ride: { status: { in: ['SCHEDULED', 'ONGOING'] } },
        },
      }),
      this.prisma.review.count({ where: { targetId: userId, deletedAt: null } }),
    ]);
    return { ridesOffered, ridesShared, upcomingAsDriver, upcomingAsPassenger, reviewsReceived };
  }

  async getProfile(userId: string) {
    const user = await this.prisma.user.findFirst({ where: { id: userId, deletedAt: null } });
    if (!user) {
      throw new NotFoundException('User profile not found');
    }
    const { passwordHash: _, ...result } = user;
    return { ...result, ...this.levelInfo(user.level, user.rewardPoints), stats: await this.stats(userId) };
  }

  /** What other students can see about a user (no email / emergency info). */
  async getPublicProfile(userId: string) {
    const user = await this.prisma.user.findFirst({
      where: { id: userId, deletedAt: null },
      select: {
        id: true,
        name: true,
        department: true,
        course: true,
        year: true,
        gender: true,
        profilePhotoUrl: true,
        isVerified: true,
        rating: true,
        rewardPoints: true,
        ridesCompleted: true,
        level: true,
        createdAt: true,
      },
    });
    if (!user) throw new NotFoundException('User not found');

    const reviews = await this.prisma.review.findMany({
      where: { targetId: userId, deletedAt: null },
      orderBy: { createdAt: 'desc' },
      take: 10,
      include: { author: { select: { id: true, name: true, profilePhotoUrl: true } } },
    });
    return { ...user, ...this.levelInfo(user.level, user.rewardPoints), stats: await this.stats(userId), reviews };
  }

  async updateProfile(userId: string, dto: UpdateProfileDto) {
    const data: Record<string, unknown> = { ...dto };
    if (dto.dob) data.dob = new Date(dto.dob);
    if (dto.emergencyContacts) data.emergencyContacts = JSON.parse(JSON.stringify(dto.emergencyContacts));
    if (typeof dto.name === 'string') data.name = dto.name.trim();
    if (typeof dto.vehicleNumber === 'string') data.vehicleNumber = dto.vehicleNumber.trim().toUpperCase() || null;

    const user = await this.prisma.user.update({ where: { id: userId }, data });
    const { passwordHash: _, ...result } = user;
    return {
      message: 'Profile updated successfully',
      user: { ...result, ...this.levelInfo(user.level, user.rewardPoints) },
    };
  }

  async getLeaderboard(query: PaginationQueryDto) {
    const { page, limit } = query;
    const skip = (page - 1) * limit;

    const [data, total] = await Promise.all([
      this.prisma.user.findMany({
        where: { deletedAt: null },
        orderBy: [{ rewardPoints: 'desc' }, { ridesCompleted: 'desc' }, { createdAt: 'asc' }],
        skip,
        take: limit,
        select: {
          id: true,
          name: true,
          rewardPoints: true,
          level: true,
          rating: true,
          ridesCompleted: true,
          department: true,
          profilePhotoUrl: true,
        },
      }),
      this.prisma.user.count({ where: { deletedAt: null } }),
    ]);

    return {
      data: data.map((u, i) => ({ ...u, rank: skip + i + 1, levelTitle: LEVEL_TITLES[u.level] ?? 'Rider' })),
      meta: { total, page, limit },
    };
  }
}
