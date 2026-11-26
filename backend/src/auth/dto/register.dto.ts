import { IsEmail, IsNotEmpty, IsString, MinLength, Matches, ValidateIf } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class RegisterDto {
  @ApiProperty({ example: 'Yash Upadhyay', description: 'Full name of the student' })
  @IsString()
  @IsNotEmpty()
  name: string;

  @ApiProperty({ example: '2024CS001', description: 'University Roll Number' })
  @IsString()
  @IsNotEmpty()
  rollNumber: string;

  @ApiProperty({ example: 'yash.u@university.edu', description: 'College Email ID' })
  @IsEmail()
  @IsNotEmpty()
  collegeEmail: string;

  @ApiProperty({ example: 'Password123!', description: 'Strong password (min 8 chars, 1 upper, 1 lower, 1 number)' })
  @IsString()
  @MinLength(8)
  @Matches(/((?=.*\d)|(?=.*\W+))(?![.\n])(?=.*[A-Z])(?=.*[a-z]).*$/, {
    message: 'password too weak',
  })
  password: string;

  @ApiPropertyOptional({ example: 'Password123!', description: 'Confirm password' })
  @ValidateIf((o) => o.confirmPassword !== undefined)
  @IsString()
  confirmPassword?: string;
}
