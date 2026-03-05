export const WORD_LENGTH = 5;
export const MAX_ATTEMPTS = 6;

// Animation timings (ms)
export const FLIP_DURATION = 500;
export const REVEAL_STAGGER = 300;
export const SHAKE_DURATION = 500;
export const POP_DURATION = 100;
export const TOAST_DURATION = 1500;
export const TOAST_FADE = 300;

export const KEYBOARD_ROWS: readonly string[][] = [
  ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
  ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
  ['ENTER', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '⌫'],
] as const;

export const WIN_MESSAGES: readonly string[] = [
  'Genius!', 'Magnificent!', 'Impressive!', 'Splendid!', 'Great!', 'Phew!',
] as const;

/**
 * Priority for keyboard key coloring.
 * A key's state only upgrades, never downgrades.
 * correct (3) > present (2) > absent (1) > tbd/empty (0).
 */
export const STATE_PRIORITY: Record<string, number> = {
  correct: 3,
  present: 2,
  absent: 1,
  tbd: 0,
  empty: 0,
};
