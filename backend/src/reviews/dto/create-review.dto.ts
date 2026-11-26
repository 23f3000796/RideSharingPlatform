import { IsInt, IsOptional, IsString, IsUUID, Max, MaxLength, Min } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class CreateReviewDto {
  @ApiProperty({ description: 'Completed ride the review is about' })
  @IsUUID()
  rideId: string;

  @ApiProperty({ description: 'User being reviewed (the driver, or a passenger if you drove)' })
  @IsUUID()
  targetId: string;

  @ApiProperty({ example: 5, minimum: 1, maximum: 5 })
  @IsInt()
  @Min(1)
  @Max(5)
  rating: number;

  @ApiPropertyOptional({ example: 'On time and drove safely!' })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  comment?: string;
}
