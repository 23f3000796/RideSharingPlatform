import { Controller, Post, Get, Patch, Param, Body, UseGuards, Query, ParseUUIDPipe } from '@nestjs/common';
import { BookingsService } from './bookings.service';
import { RequestBookingDto } from './dto/request-booking.dto';
import { PaginationQueryDto } from '../common/dto/pagination-query.dto';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';

@ApiTags('bookings')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('api/v1/bookings')
export class BookingsController {
  constructor(private readonly bookingsService: BookingsService) {}

  @Post('request')
  @ApiOperation({ summary: 'Request seats in a ride' })
  @ApiResponse({ status: 201, description: 'Booking request created successfully' })
  @ApiResponse({ status: 400, description: 'Bad Request (Not enough seats, own ride, etc.)' })
  async requestBooking(
    @CurrentUser() user: any,
    @Body() dto: RequestBookingDto,
  ) {
    return this.bookingsService.requestBooking(user.id, dto);
  }

  @Get('my-bookings')
  @ApiOperation({ summary: 'View booking requests made by the passenger' })
  @ApiResponse({ status: 200, description: 'List of passenger bookings' })
  async getMyBookings(
    @CurrentUser() user: any,
    @Query() query: PaginationQueryDto,
  ) {
    return this.bookingsService.getMyBookings(user.id, query);
  }

  @Get('incoming')
  @ApiOperation({ summary: 'All booking requests on rides you drive (pending first)' })
  @ApiResponse({ status: 200, description: 'List of incoming requests' })
  async getIncoming(
    @CurrentUser() user: any,
    @Query() query: PaginationQueryDto,
  ) {
    return this.bookingsService.getIncomingRequests(user.id, query);
  }

  @Get('ride/:rideId')
  @ApiOperation({ summary: 'View booking requests for a specific ride (Driver only)' })
  @ApiResponse({ status: 200, description: 'List of ride bookings' })
  @ApiResponse({ status: 403, description: 'Forbidden' })
  async getRideBookings(
    @CurrentUser() user: any,
    @Param('rideId', ParseUUIDPipe) rideId: string,
  ) {
    return this.bookingsService.getRideBookings(user.id, rideId);
  }

  @Patch(':id/accept')
  @ApiOperation({ summary: 'Accept a booking request (Driver only)' })
  @ApiResponse({ status: 200, description: 'Booking accepted' })
  async acceptBooking(
    @CurrentUser() user: any,
    @Param('id', ParseUUIDPipe) bookingId: string,
  ) {
    return this.bookingsService.acceptBooking(user.id, bookingId);
  }

  @Patch(':id/reject')
  @ApiOperation({ summary: 'Reject a booking request (Driver only)' })
  @ApiResponse({ status: 200, description: 'Booking rejected' })
  async rejectBooking(
    @CurrentUser() user: any,
    @Param('id', ParseUUIDPipe) bookingId: string,
  ) {
    return this.bookingsService.rejectBooking(user.id, bookingId);
  }

  @Patch(':id/cancel')
  @ApiOperation({ summary: 'Cancel a booking request (Passenger only)' })
  @ApiResponse({ status: 200, description: 'Booking cancelled' })
  async cancelBooking(
    @CurrentUser() user: any,
    @Param('id', ParseUUIDPipe) bookingId: string,
  ) {
    return this.bookingsService.cancelBooking(user.id, bookingId);
  }
}
