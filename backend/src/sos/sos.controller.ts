import { Body, Controller, Get, Param, ParseUUIDPipe, Patch, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { SosService } from './sos.service';
import { CreateSosDto } from './dto/create-sos.dto';

@ApiTags('sos')
@ApiBearerAuth()
@UseGuards(JwtAuthGuard)
@Controller('api/v1/sos')
export class SosController {
  constructor(private readonly sos: SosService) {}

  @Post()
  @ApiOperation({ summary: 'Raise an emergency SOS with your live location' })
  raise(@CurrentUser() user: any, @Body() dto: CreateSosDto) {
    return this.sos.raise(user.id, dto);
  }

  @Get('mine')
  @ApiOperation({ summary: 'Your SOS history' })
  mine(@CurrentUser() user: any) {
    return this.sos.mine(user.id);
  }

  @Get('active')
  @ApiOperation({ summary: 'All active alerts (ADMIN / SECURITY only)' })
  active(@CurrentUser() user: any) {
    return this.sos.active(user.role);
  }

  @Patch(':id/resolve')
  @ApiOperation({ summary: 'Mark an alert resolved (owner, ADMIN or SECURITY)' })
  resolve(@CurrentUser() user: any, @Param('id', ParseUUIDPipe) id: string) {
    return this.sos.resolve(user.id, user.role, id);
  }
}
