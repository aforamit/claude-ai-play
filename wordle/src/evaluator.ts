import type { TileState, EvalResult } from './types';
import { WORD_LENGTH } from './constants';

/**
 * Pure function — no side effects, no class wrapper, no cache.
 *
 * Two-pass algorithm:
 *   Pass 1 — Mark exact matches (correct), consuming their slot in the target pool.
 *   Pass 2 — For remaining positions, search the pool for present letters.
 *
 * Consuming slots prevents double-counting duplicate letters.
 * e.g. guess "SPEED", target "PROSE": only the first E is present, not both.
 */
export function evaluateGuess(guess: string, target: string): EvalResult {
  const g = guess.toUpperCase().split('');
  const pool: Array<string | null> = target.toUpperCase().split('');
  const evals: TileState[] = new Array<TileState>(WORD_LENGTH).fill('absent');

  // Pass 1: exact matches
  for (let i = 0; i < WORD_LENGTH; i++) {
    if (g[i] === pool[i]) {
      evals[i] = 'correct';
      pool[i] = null;
    }
  }

  // Pass 2: present letters
  for (let i = 0; i < WORD_LENGTH; i++) {
    if (evals[i] === 'correct') continue;
    const letter = g[i];
    if (letter === undefined) continue;
    const idx = pool.indexOf(letter);
    if (idx !== -1) {
      evals[i] = 'present';
      pool[idx] = null;
    }
  }

  return {
    evaluations: evals,
    isCorrect: evals.every(e => e === 'correct'),
  };
}
