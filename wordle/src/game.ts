import type { TileState, GameState } from './types';
import { WORD_LENGTH, MAX_ATTEMPTS, WIN_MESSAGES, STATE_PRIORITY } from './constants';
import { gameStore } from './state';
import { evaluateGuess } from './evaluator';
import { isValidWord } from './words';
import { setTileLetter } from './board';
import { syncKeyboard } from './keyboard';
import { popTile, shakeRow, revealRow, bounceRow } from './animations';
import { showToast } from './toast';

/**
 * Guards against input during the reveal animation.
 * Set to `true` on Enter, `false` only when continuing to the next row.
 * Win/loss paths leave it `true` — the status check handles those cases anyway.
 */
let locked = false;

/**
 * Central dispatcher for all keyboard input — both physical and on-screen.
 * Normalised key format: uppercase letters, 'ENTER', '⌫'.
 */
export async function handleKey(key: string): Promise<void> {
  if (locked) return;

  const state = gameStore.getState();
  if (state.status !== 'playing') return;

  if (key === '⌫') {
    handleBackspace(state);
  } else if (key === 'ENTER') {
    await handleEnter(state);
  } else if (/^[A-Z]$/.test(key)) {
    handleLetter(key, state);
  }
}

// ─── Private handlers ────────────────────────────────────────────────────────

function handleLetter(letter: string, state: Readonly<GameState>): void {
  if (state.currentGuess.length >= WORD_LENGTH) return;

  const col = state.currentGuess.length;
  gameStore.update(s => ({ ...s, currentGuess: s.currentGuess + letter }));
  setTileLetter(state.row, col, letter);
  popTile(state.row, col);
}

function handleBackspace(state: Readonly<GameState>): void {
  if (!state.currentGuess.length) return;

  const col = state.currentGuess.length - 1;
  gameStore.update(s => ({ ...s, currentGuess: s.currentGuess.slice(0, -1) }));
  setTileLetter(state.row, col, '');
}

async function handleEnter(state: Readonly<GameState>): Promise<void> {
  if (state.currentGuess.length < WORD_LENGTH) {
    showToast('Not enough letters');
    shakeRow(state.row);
    return;
  }

  if (!isValidWord(state.currentGuess)) {
    showToast('Not in word list');
    shakeRow(state.row);
    return;
  }

  locked = true;

  // Capture these before mutating state
  const { currentGuess, target, row } = state;
  const result = evaluateGuess(currentGuess, target);

  // Record the guess immediately so state is consistent during the animation
  gameStore.update(s => ({
    ...s,
    guesses: [...s.guesses, currentGuess],
    evaluations: [...s.evaluations, result.evaluations],
    currentGuess: '',
  }));

  // Wait for all tiles to flip before updating keyboard and checking outcome
  await revealRow(row, result.evaluations);

  // Update keyboard key colors
  const updatedKeyMap = buildUpdatedKeyMap(
    gameStore.getState().keyMap,
    currentGuess,
    result.evaluations
  );
  gameStore.update(s => ({ ...s, keyMap: updatedKeyMap }));
  syncKeyboard(updatedKeyMap);

  // Resolve outcome
  if (result.isCorrect) {
    gameStore.update(s => ({ ...s, status: 'won' }));
    bounceRow(row);
    const msg = WIN_MESSAGES[Math.min(row, WIN_MESSAGES.length - 1)] ?? 'Genius!';
    setTimeout(() => showToast(msg, 2000), 400);

  } else if (row + 1 >= MAX_ATTEMPTS) {
    gameStore.update(s => ({ ...s, status: 'lost' }));
    setTimeout(() => showToast(target, 3500), 300);

  } else {
    gameStore.update(s => ({ ...s, row: s.row + 1 }));
    locked = false;
  }
}

/**
 * Returns a new keyMap with updated priorities for the letters in this guess.
 * A letter's state only upgrades (correct > present > absent), never downgrades.
 */
function buildUpdatedKeyMap(
  current: Record<string, TileState>,
  guess: string,
  evals: TileState[]
): Record<string, TileState> {
  const next: Record<string, TileState> = { ...current };

  for (let i = 0; i < guess.length; i++) {
    const letter = guess[i];
    const evalState = evals[i];
    if (!letter || !evalState) continue;

    const existing = next[letter] ?? 'empty';
    if ((STATE_PRIORITY[evalState] ?? 0) > (STATE_PRIORITY[existing] ?? 0)) {
      next[letter] = evalState;
    }
  }

  return next;
}
