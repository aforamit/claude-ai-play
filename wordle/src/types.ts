export type TileState = 'empty' | 'tbd' | 'correct' | 'present' | 'absent';

export type GameStatus = 'playing' | 'won' | 'lost';

export interface EvalResult {
  evaluations: TileState[];
  isCorrect: boolean;
}

export interface GameState {
  /** The uppercase target word for this session. Never changes. */
  target: string;
  /** Letters typed so far in the current row (0–5 chars, uppercase). */
  currentGuess: string;
  /** All submitted guesses, in order (uppercase). */
  guesses: string[];
  /** Parallel to guesses — evaluation result for each submitted guess. */
  evaluations: TileState[][];
  /** Best known state per letter key ('A'–'Z'). */
  keyMap: Record<string, TileState>;
  /** Index of the active row (0–5). Advances after each valid submit. */
  row: number;
  status: GameStatus;
}
