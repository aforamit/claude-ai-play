import { WORD_LENGTH, MAX_ATTEMPTS } from './constants';

/**
 * Builds the 6×5 grid of tile divs and appends them to #board.
 * Called once on game init. Letters and states are set later via
 * setTileLetter / setTileState.
 */
export function buildBoard(): void {
  const board = document.getElementById('board');
  if (!board) return;

  board.innerHTML = '';

  for (let row = 0; row < MAX_ATTEMPTS; row++) {
    const rowEl = document.createElement('div');
    rowEl.className = 'board__row';
    rowEl.dataset.row = String(row);

    for (let col = 0; col < WORD_LENGTH; col++) {
      const tile = document.createElement('div');
      tile.className = 'board__tile';
      tile.dataset.row = String(row);
      tile.dataset.col = String(col);
      tile.dataset.state = 'empty';
      tile.setAttribute('aria-label', 'empty');
      rowEl.appendChild(tile);
    }

    board.appendChild(rowEl);
  }
}

/** Updates a tile's displayed letter and state (tbd / empty). */
export function setTileLetter(row: number, col: number, letter: string): void {
  const tile = getTile(row, col);
  if (!tile) return;

  tile.textContent = letter;
  tile.dataset.state = letter ? 'tbd' : 'empty';
  tile.setAttribute('aria-label', letter || 'empty');
}

/** Sets the revealed evaluation state on a tile (correct / present / absent). */
export function setTileState(row: number, col: number, state: string): void {
  const tile = getTile(row, col);
  if (!tile) return;
  tile.dataset.state = state;
}

/** Queries a tile by row and column. Returns null if not found. */
export function getTile(row: number, col: number): HTMLElement | null {
  return document.querySelector<HTMLElement>(
    `.board__tile[data-row="${row}"][data-col="${col}"]`
  );
}

/** Queries a row element by index. */
export function getRow(row: number): HTMLElement | null {
  return document.querySelector<HTMLElement>(`.board__row[data-row="${row}"]`);
}
