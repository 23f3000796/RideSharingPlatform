import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CreateReviewDto } from './dto/create-review.dto';
import { APP_CONSTANTS, levelForPoints } from '../common/constants/app.constants';

@Injectable()
export class ReviewsService {
  constructor(private prisma: PrismaService) {}

  /**
   * Rules: the ride must be completed; author and target must both have been on it
   * (driver, or a passenger with a COMPLETED booking); no self-reviews; passengers
   * review the driver, the driver reviews passengers; one review per author/target/ride.
   */
  async create(authorId: string, dto: CreateReviewDto) {
    if (authorId === dto.targetId) throw new BadRequestException('You cannot review yourself');

    const ride = await this.prisma.ride.findFirst({ where: { id: dto.rideId, deletedAt: null } });
    if (!ride) throw new NotFoundException('Ride not found');
    if (ride.status !== 'COMPLETED') throw new BadRequestException('You can only review completed rides');

    const completedPassengers = await this.prisma.bookingRequest.findMany({
      where: { rideId: ride.id, status: 'COMPLETED', deletedAt: null },
      select: { passengerId: true },
    });
    const participants = new Set([ride.driverId, ...completedPassengers.map((p) => p.passengerId)]);
    if (!participants.has(authorId)) throw new ForbiddenException('You were not part of this ride');
    if (!participants.has(dto.targetId)) throw new BadRequestException('That user was not part of this ride');
    if (authorId !== ride.driverId && dto.targetId !== ride.driverId) {
      throw new BadRequestException('Passengers can only review the driver');
    }

    const existing = await this.prisma.review.findFirst({
      where: { authorId, targetId: dto.targetId, rideId: dto.rideId, deletedAt: null },
    });
    if (existing) throw new BadRequestException('You have already reviewed this user for this ride');

    return this.prisma.$transaction(async (tx) => {
      const review = await tx.review.create({
        data: {
          authorId,
          targetId: dto.targetId,
          rideId: dto.rideId,
          rating: dto.rating,
          comment: dto.comment?.trim() || null,
        },
      });

      const agg = await tx.review.aggregate({
        where: { targetId: dto.targetId, deletedAt: null },
        _avg: { rating: true },
      });
      await tx.user.update({
        where: { id: dto.targetId },
        data: { rating: Math.round((agg._avg.rating ?? dto.rating) * 10) / 10 },
      });

      const author = await tx.user.update({
        where: { id: authorId },
        data: { rewardPoints: { increment: APP_CONSTANTS.REWARDS.POINTS_PER_REVIEW } },
        select: { rewardPoints: true, level: true },
      });
      const level = levelForPoints(author.rewardPoints);
      if (level > author.level) await tx.user.update({ where: { id: authorId }, data: { level } });

      return { message: 'Thanks for your feedback!', review };
    });
  }

  async forUser(userId: string, limit = 20) {
    const [reviews, agg] = await Promise.all([
      this.prisma.review.findMany({
        where: { targetId: userId, deletedAt: null },
        orderBy: { createdAt: 'desc' },
        take: Math.min(50, Math.max(1, limit)),
        include: { author: { select: { id: true, name: true, profilePhotoUrl: true } } },
      }),
      this.prisma.review.aggregate({
        where: { targetId: userId, deletedAt: null },
        _avg: { rating: true },
        _count: true,
      }),
    ]);
    return {
      data: reviews,
      meta: {
        total: agg._count,
        average: agg._avg.rating ? Math.round(agg._avg.rating * 10) / 10 : null,
      },
    };
  }
}
