import {
  IsOptional,
  IsString,
  IsInt,
  IsDateString,
  IsIn,
  Max,
  Min,
  MaxLength,
  MinLength,
  ValidateNested,
  Matches,
} from 'class-validator';
import { Type } from 'class-transformer';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class EmergencyContactDto {
  @ApiPropertyOptional({ example: 'Mom' })
  @IsString()
  @MaxLength(60)
  name: string;

  @ApiPropertyOptional({ example: '+919876543210' })
  @IsString()
  @Matches(/^\+?[0-9 ()-]{7,20}$/, { message: 'Enter a valid phone number' })
  phone: string;

  @ApiPropertyOptional({ example: 'Parent' })
  @IsOptional()
  @IsString()
  @MaxLength(30)
  relation?: string;
}

export class UpdateProfileDto {
  @ApiPropertyOptional({ example: 'Yash Upadhyay' })
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  name?: string;

  @ApiPropertyOptional({ example: 'Computer Science' })
  @IsOptional()
  @IsString()
  @MaxLength(80)
  department?: string;

  @ApiPropertyOptional({ example: 'B.Tech' })
  @IsOptional()
  @IsString()
  @MaxLength(40)
  course?: string;

  @ApiPropertyOptional({ example: 3 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(6)
  year?: number;

  @ApiPropertyOptional({ example: '2002-05-15T00:00:00.000Z' })
  @IsOptional()
  @IsDateString()
  dob?: string;

  @ApiPropertyOptional({ example: 'https://storage.../photo.jpg' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  profilePhotoUrl?: string;

  @ApiPropertyOptional({ example: 'Male', enum: ['Male', 'Female', 'Other'] })
  @IsOptional()
  @IsIn(['Male', 'Female', 'Other'])
  gender?: string;

  @ApiPropertyOptional({ example: 'O+' })
  @IsOptional()
  @IsIn(['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'])
  bloodGroup?: string;

  @ApiPropertyOptional({ type: EmergencyContactDto, example: { name: 'Mom', phone: '+919876543210', relation: 'Parent' } })
  @IsOptional()
  @ValidateNested()
  @Type(() => EmergencyContactDto)
  emergencyContacts?: EmergencyContactDto;

  @ApiPropertyOptional({ example: 'MP09 AB 1234' })
  @IsOptional()
  @IsString()
  @MaxLength(20)
  vehicleNumber?: string;
}
