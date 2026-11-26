import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleInit, OnModuleDestroy {
  private readonly pool: Pool;

  constructor() {
    const connectionString = process.env.DATABASE_URL;
    const pool = new Pool({ connectionString });
    const adapter = new PrismaPg(pool);
    super({ adapter });
    this.pool = pool;
  }

  async onModuleInit() {
    // Prisma connects lazily on the first query.
    // Explicit connection is removed to prevent startup hangs if DB is unreachable.
  }

  /** Close the connection pool on shutdown (graceful exit, and lets tests finish cleanly). */
  async onModuleDestroy() {
    await this.$disconnect();
    await this.pool.end().catch(() => undefined);
  }
}
