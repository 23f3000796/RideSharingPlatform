import { IsNotEmpty, IsString } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class LoginDto {
  @ApiProperty({ example: 'yash.u@university.edu', description: 'College Email ID or Roll Number' })
  @IsString()
  @IsNotEmpty()
  identifier: string; // Can be email or rollNumber

  @ApiProperty({ example: 'Password123!', description: 'User password' })
  @IsString()
  @IsNotEmpty()
  password: string;
}
