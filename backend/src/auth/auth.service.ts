import { BadRequestException, Injectable, UnauthorizedException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { JwtService } from '@nestjs/jwt';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import * as bcrypt from 'bcrypt';

@Injectable()
export class AuthService {
  constructor(
    private prisma: PrismaService,
    private jwtService: JwtService,
  ) {}

  async register(dto: RegisterDto) {
    if (dto.confirmPassword && dto.password !== dto.confirmPassword) {
      throw new BadRequestException('Passwords do not match');
    }

    const collegeEmail = dto.collegeEmail.trim().toLowerCase();
    const rollNumber = dto.rollNumber.trim().toUpperCase();

    // Check if user exists
    const existingUser = await this.prisma.user.findFirst({
      where: {
        OR: [
          { collegeEmail: { equals: collegeEmail, mode: 'insensitive' } },
          { rollNumber: { equals: rollNumber, mode: 'insensitive' } },
        ],
      },
    });

    if (existingUser) {
      throw new BadRequestException('User with this email or roll number already exists');
    }

    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(dto.password, saltRounds);

    const user = await this.prisma.user.create({
      data: {
        name: dto.name.trim(),
        rollNumber,
        collegeEmail,
        passwordHash,
      },
    });

    const { passwordHash: _, ...result } = user;
    
    return {
      message: 'Registration successful',
      user: result,
      accessToken: this.generateToken(user.id, user.collegeEmail),
    };
  }

  async login(dto: LoginDto) {
    const identifier = dto.identifier.trim();
    const user = await this.prisma.user.findFirst({
      where: {
        deletedAt: null,
        OR: [
          { collegeEmail: { equals: identifier, mode: 'insensitive' } },
          { rollNumber: { equals: identifier, mode: 'insensitive' } },
        ],
      },
    });

    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const isPasswordValid = await bcrypt.compare(dto.password, user.passwordHash);

    if (!isPasswordValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const { passwordHash: _, ...result } = user;

    return {
      message: 'Login successful',
      user: result,
      accessToken: this.generateToken(user.id, user.collegeEmail),
    };
  }

  private generateToken(userId: string, email: string) {
    const payload = { sub: userId, email };
    return this.jwtService.sign(payload);
  }
}
