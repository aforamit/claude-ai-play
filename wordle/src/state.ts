import type { GameState } from './types';
import { Store } from './store';
import { pickRandomWord } from './words';

export function createInitialState(target?: string): GameState {
  return {
    target: (target ?? pickRandomWord()).toUpperCase(),
    currentGuess: '',
    guesses: [],
    evaluations: [],
    keyMap: {},
    row: 0,
    status: 'playing',
  };
}

/**
 * The single game store — exported as a module-level instance.
 * ES module caching means every importer gets this exact same object,
 * providing singleton semantics without the Singleton pattern.
 *
 * To start a new game: gameStore.update(() => createInitialState())
 */
export const gameStore = new Store<GameState>(createInitialState());
