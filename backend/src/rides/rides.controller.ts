import { Controller, Post, Get, Patch, Body, Param, Query, UseGuards, ParseUUIDPipe } from '@nestjs/common';
import { RidesService } from './rides.service';
import { CreateRideDto } from './dto/create-ride.dto';
import { SearchRideDto } from './dto/search-ride.dto';
import { PaginationQueryDto } from '../common/dto/pagination-query.dto';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';

@ApiTags('rides')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('api/v1/rides')
export class RidesController {
  constructor(private readonly ridesService: RidesService) {}

  @Post()
  @ApiOperation({ summary: 'Create a new ride offer' })
  @ApiResponse({ status: 201, description: 'Ride created successfully' })
  @ApiResponse({ status: 400, description: 'Bad Request' })
  async createRide(@CurrentUser() user: any, @Body() createRideDto: CreateRideDto) {
    return this.ridesService.createRide(user.id, createRideDto);
  }

  @Get('search')
  @ApiOperation({
    summary: 'Find rides that can take you from your pickup to your drop',
    description:
      'Matches passengers whose pickup and drop both lie near the driver route, in the same direction, with an acceptable detour. Each result includes match details and a pro-rated fare.',
  })
  @ApiResponse({ status: 200, description: 'List of matching rides, best match first' })
  async searchRides(@CurrentUser() user: any, @Query() searchRideDto: SearchRideDto) {
    return this.ridesService.searchRides(searchRideDto, user?.id);
  }

  @Get('my-rides')
  @ApiOperation({ summary: 'Get rides created by the authenticated user' })
  @ApiResponse({ status: 200, description: 'List of rides' })
  async getMyRides(@CurrentUser() user: any, @Query() query: PaginationQueryDto) {
    return this.ridesService.getMyRides(user.id, query);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get ride details by ID (includes your booking on it, if any)' })
  @ApiResponse({ status: 200, description: 'Ride details' })
  @ApiResponse({ status: 404, description: 'Ride not found' })
  async getRideDetails(@CurrentUser() user: any, @Param('id', ParseUUIDPipe) id: string) {
    return this.ridesService.getRideDetails(id, user?.id);
  }

  @Patch(':id/start')
  @ApiOperation({ summary: 'Start the ride (driver only). Pending requests are declined.' })
  async startRide(@CurrentUser() user: any, @Param('id', ParseUUIDPipe) id: string) {
    return this.ridesService.startRide(user.id, id);
  }

  @Patch(':id/complete')
  @ApiOperation({ summary: 'Complete the ride (driver only). Awards reward points.' })
  async completeRide(@CurrentUser() user: any, @Param('id', ParseUUIDPipe) id: string) {
    return this.ridesService.completeRide(user.id, id);
  }

  @Patch(':id/cancel')
  @ApiOperation({ summary: 'Cancel a scheduled ride (driver only)' })
  async cancelRide(@CurrentUser() user: any, @Param('id', ParseUUIDPipe) id: string) {
    return this.ridesService.cancelRide(user.id, id);
  }
}
