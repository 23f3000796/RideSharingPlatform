export const APP_CONSTANTS = {
  PAGINATION: {
    DEFAULT_PAGE: 1,
    DEFAULT_LIMIT: 10,
    MAX_LIMIT: 50,
  },
  SEARCH: {
    DEFAULT_RADIUS_KM: 2,
    MAX_RADIUS_KM: 25,
    /** Max candidate rides pulled from the DB before route matching. */
    MAX_CANDIDATES: 500,
  },
  REWARDS: {
    /** Driver: base points for completing an offered ride. */
    POINTS_PER_RIDE_OFFERED: 30,
    /** Driver: bonus per passenger carried. */
    POINTS_PER_PASSENGER_CARRIED: 20,
    /** Passenger: points for completing a shared ride. */
    POINTS_PER_RIDE_COMPLETED: 15,
    /** Points for leaving a review. */
    POINTS_PER_REVIEW: 5,
    /** Points needed per level. */
    POINTS_PER_LEVEL: 150,
    MAX_LEVEL: 10,
  },
  AUTH: {
    JWT_EXPIRES_IN: '7d',
  },
};

export function levelForPoints(points: number): number {
  const { POINTS_PER_LEVEL, MAX_LEVEL } = APP_CONSTANTS.REWARDS;
  return Math.min(MAX_LEVEL, 1 + Math.floor(Math.max(0, points) / POINTS_PER_LEVEL));
}

export const LEVEL_TITLES: Record<number, string> = {
  1: 'Newcomer',
  2: 'Explorer',
  3: 'Commuter',
  4: 'Navigator',
  5: 'Road Buddy',
  6: 'Pool Pro',
  7: 'Campus Cruiser',
  8: 'Route Master',
  9: 'Eco Champion',
  10: 'Legend',
};
