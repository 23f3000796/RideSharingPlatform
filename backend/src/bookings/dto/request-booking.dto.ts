import { IsString, IsInt, Min, Max, IsNotEmpty, IsUUID, MaxLength } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

export class RequestBookingDto {
  @ApiProperty({ example: 'uuid-of-ride', description: 'ID of the ride being booked' })
  @IsUUID()
  rideId: string;

  @ApiProperty({ example: 'Main Gate', description: 'Passenger pickup address' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(255)
  pickupAddress: string;

  @ApiProperty({ example: 'Library', description: 'Passenger drop address' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(255)
  dropAddress: string;

  @ApiProperty({ example: 1, description: 'Number of seats requested' })
  @IsInt()
  @Min(1)
  @Max(8)
  seatsRequested: number;
}
