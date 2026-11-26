import { IsNumber, IsDateString, IsInt, Min, Max, IsOptional, IsIn } from 'class-validator';
import { Transform, Type } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { PaginationQueryDto } from '../../common/dto/pagination-query.dto';
import { APP_CONSTANTS } from '../../common/constants/app.constants';

function toPositiveNumber(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0.1 ? parsed : fallback;
}

export class SearchRideDto extends PaginationQueryDto {
  @ApiProperty({ example: 22.7196, description: 'Passenger pickup latitude' })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  pickupLat: number;

  @ApiProperty({ example: 75.8577, description: 'Passenger pickup longitude' })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  pickupLng: number;

  @ApiProperty({ example: 22.7244, description: 'Passenger drop latitude' })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  dropLat: number;

  @ApiProperty({ example: 75.8839, description: 'Passenger drop longitude' })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  dropLng: number;

  @ApiPropertyOptional({
    example: APP_CONSTANTS.SEARCH.DEFAULT_RADIUS_KM,
    description: 'How far (km) your pickup / drop may be from the driver route',
  })
  @Transform(({ value }) =>
    Math.min(
      toPositiveNumber(value, APP_CONSTANTS.SEARCH.DEFAULT_RADIUS_KM),
      APP_CONSTANTS.SEARCH.MAX_RADIUS_KM,
    ),
  )
  @IsNumber()
  @Min(0.1)
  radius: number = APP_CONSTANTS.SEARCH.DEFAULT_RADIUS_KM;

  @ApiProperty({ example: '2026-05-15T09:00:00Z', description: 'Earliest departure time (ISO)' })
  @IsDateString()
  departureTime: string;

  @ApiPropertyOptional({ example: '2026-05-15T23:59:59Z', description: 'Latest departure time (ISO). Omit for any time after departureTime.' })
  @IsOptional()
  @IsDateString()
  departureTimeTo?: string;

  @ApiProperty({ example: 1, description: 'Number of seats required' })
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(8)
  seatsRequired: number;

  @ApiPropertyOptional({ enum: ['best', 'time', 'price'], description: 'Sort order (default: best match)' })
  @IsOptional()
  @IsIn(['best', 'time', 'price'])
  sort?: 'best' | 'time' | 'price';
}

export class RoutePreviewDto {
  @ApiProperty({ example: 22.7196 })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  pickupLat: number;

  @ApiProperty({ example: 75.8577 })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  pickupLng: number;

  @ApiProperty({ example: 22.7533 })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  dropLat: number;

  @ApiProperty({ example: 75.8937 })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  dropLng: number;
}
