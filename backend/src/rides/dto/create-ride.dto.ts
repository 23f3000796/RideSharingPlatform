import { IsString, IsNumber, IsDateString, IsInt, Min, Max, IsNotEmpty, IsOptional, MaxLength } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class CreateRideDto {
  @ApiProperty({ example: 'Main Gate, Campus', description: 'Name of the pickup location' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(255)
  pickupAddress: string;

  @ApiProperty({ example: 22.7196, description: 'Latitude of the pickup location' })
  @IsNumber()
  @Min(-90)
  @Max(90)
  pickupLat: number;

  @ApiProperty({ example: 75.8577, description: 'Longitude of the pickup location' })
  @IsNumber()
  @Min(-180)
  @Max(180)
  pickupLng: number;

  @ApiProperty({ example: 'Vijay Nagar Square', description: 'Name of the drop location' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(255)
  dropAddress: string;

  @ApiProperty({ example: 22.7533, description: 'Latitude of the drop location' })
  @IsNumber()
  @Min(-90)
  @Max(90)
  dropLat: number;

  @ApiProperty({ example: 75.8937, description: 'Longitude of the drop location' })
  @IsNumber()
  @Min(-180)
  @Max(180)
  dropLng: number;

  @ApiProperty({ example: '2026-05-15T10:30:00Z', description: 'Departure time in ISO format' })
  @IsDateString()
  @IsNotEmpty()
  departureTime: string;

  @ApiProperty({ example: 3, description: 'Total seats offered in the ride' })
  @IsInt()
  @Min(1)
  @Max(8)
  totalSeats: number;

  @ApiProperty({ example: 50, description: 'Price per seat (full route)' })
  @IsInt()
  @Min(0)
  @Max(5000)
  pricePerSeat: number;

  @ApiProperty({ example: 'Sedan', description: 'Type of vehicle' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(30)
  vehicleType: string;

  @ApiPropertyOptional({ example: 'MP09 AB 1234', description: 'Vehicle number (defaults to your profile vehicle)' })
  @IsOptional()
  @IsString()
  @MaxLength(20)
  vehicleNumber?: string;
}
