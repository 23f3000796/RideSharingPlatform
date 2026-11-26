import { CallHandler, ExecutionContext, Injectable, NestInterceptor } from '@nestjs/common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface Response<T> {
  success: boolean;
  message: string;
  data: T;
  meta?: any;
  timestamp: string;
}

@Injectable()
export class TransformInterceptor<T> implements NestInterceptor<T, Response<T>> {
  intercept(context: ExecutionContext, next: CallHandler): Observable<Response<T>> {
    return next.handle().pipe(
      map(result => {
        let message = 'Success';
        let data = result;
        let meta = undefined;

        // Extract message if it was provided explicitly in the response object
        if (result && typeof result === 'object' && !Array.isArray(result) && result.message) {
          message = result.message;
          // Clean up the payload
          const { message: _, ...rest } = result;
          
          // If the rest has only one key, flatten it if it makes sense, or just return rest
          // e.g. { message: '...', ride: {...} } -> data = { ride: {...} } or just the ride
          data = rest;
        }

        // Handle Paginated Responses
        if (result && typeof result === 'object' && result.data && result.meta) {
          data = result.data;
          meta = result.meta;
          if (result.message) message = result.message;
        }

        // Recursive function to strip sensitive fields globally
        const sanitize = (obj: any): any => {
          if (obj === null || obj === undefined) return obj;
          if (Array.isArray(obj)) {
            return obj.map(item => sanitize(item));
          }
          if (typeof obj === 'object') {
            if (obj instanceof Date) return obj;
            const newObj = { ...obj };
            delete newObj.passwordHash;
            delete newObj.deletedAt;
            for (const key in newObj) {
              if (Object.prototype.hasOwnProperty.call(newObj, key)) {
                newObj[key] = sanitize(newObj[key]);
              }
            }
            return newObj;
          }
          return obj;
        };

        const sanitizedData = sanitize(data);

        return {
          success: true,
          message,
          data: sanitizedData,
          ...(meta && { meta }), // Include meta only if it exists
          timestamp: new Date().toISOString(),
        };
      }),
    );
  }
}
