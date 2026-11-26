import { IsNumber, IsOptional, IsUUID, Max, Min } from 'class-validator';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class CreateSosDto {
  @ApiPropertyOptional({ description: 'Ride in progress, if any' })
  @IsOptional()
  @IsUUID()
  rideId?: string;

  @ApiPropertyOptional({ example: 22.7196 })
  @IsOptional()
  @IsNumber()
  @Min(-90)
  @Max(90)
  lat?: number;

  @ApiPropertyOptional({ example: 75.8577 })
  @IsOptional()
  @IsNumber()
  @Min(-180)
  @Max(180)
  lng?: number;
}
