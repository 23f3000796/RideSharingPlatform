import { ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsInt, Min, Max } from 'class-validator';
import { APP_CONSTANTS } from '../constants/app.constants';

function toPositiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 1 ? Math.trunc(parsed) : fallback;
}

// Field initialisers provide defaults when a query param is omitted entirely:
// @Transform only runs for keys that are present.
export class PaginationQueryDto {
  @ApiPropertyOptional({ description: 'Page number', default: APP_CONSTANTS.PAGINATION.DEFAULT_PAGE })
  @Transform(({ value }) => toPositiveInt(value, APP_CONSTANTS.PAGINATION.DEFAULT_PAGE))
  @IsInt()
  @Min(1)
  page: number = APP_CONSTANTS.PAGINATION.DEFAULT_PAGE;

  @ApiPropertyOptional({ description: 'Items per page', default: APP_CONSTANTS.PAGINATION.DEFAULT_LIMIT })
  @Transform(({ value }) =>
    Math.min(
      toPositiveInt(value, APP_CONSTANTS.PAGINATION.DEFAULT_LIMIT),
      APP_CONSTANTS.PAGINATION.MAX_LIMIT,
    ),
  )
  @IsInt()
  @Min(1)
  @Max(APP_CONSTANTS.PAGINATION.MAX_LIMIT)
  limit: number = APP_CONSTANTS.PAGINATION.DEFAULT_LIMIT;
}
