import { Exclude } from 'class-transformer';
import { Prisma, Role, User } from '@prisma/client';

export class UserEntity implements User {
  id: string;
  name: string;
  rollNumber: string;
  collegeEmail: string;

  @Exclude()
  passwordHash: string;

  department: string | null;
  course: string | null;
  year: number | null;
  dob: Date | null;
  profilePhotoUrl: string | null;
  emergencyContacts: Prisma.JsonValue | null;
  gender: string | null;
  bloodGroup: string | null;
  vehicleNumber: string | null;
  isVerified: boolean;
  role: Role;
  rewardPoints: number;
  level: number;
  rating: number | null;
  ridesCompleted: number;
  fcmToken: string | null;

  @Exclude()
  deletedAt: Date | null;

  createdAt: Date;
  updatedAt: Date;

  constructor(partial: Partial<UserEntity>) {
    Object.assign(this, partial);
  }
}
