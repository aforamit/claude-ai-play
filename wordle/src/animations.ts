import type { TileState } from './types';
import { FLIP_DURATION, REVEAL_STAGGER, WORD_LENGTH } from './constants';
import { getTile, getRow } from './board';

/**
 * Brief scale-bounce when a letter key is pressed.
 * Uses a CSS class + animationend for automatic cleanup.
 */
export function popTile(row: number, col: number): void {
  const tile = getTile(row, col);
  if (!tile) return;

  tile.classList.remove('board__tile--pop');
  void tile.offsetWidth; // force reflow to restart the animation
  tile.classList.add('board__tile--pop');
  tile.addEventListener('animationend', () => {
    tile.classList.remove('board__tile--pop');
  }, { once: true });
}

/**
 * Horizontal shake on the row to signal an invalid word.
 * CSS class + animationend for cleanup.
 */
export function shakeRow(row: number): void {
  const rowEl = getRow(row);
  if (!rowEl) return;

  rowEl.classList.remove('board__row--shake');
  void rowEl.offsetWidth;
  rowEl.classList.add('board__row--shake');
  rowEl.addEventListener('animationend', () => {
    rowEl.classList.remove('board__row--shake');
  }, { once: true });
}

/**
 * Staggered flip-reveal for a submitted row.
 * Returns a Promise that resolves after the last tile finishes flipping,
 * so the caller can `await` before updating keyboard colors and game status.
 *
 * Each tile flips in two phases driven by inline style transitions:
 *   Phase 1 — rotate to 90° (tile hidden)
 *   Phase 2 — apply evaluation color, rotate back to 0° (tile visible)
 */
export function revealRow(row: number, evals: TileState[]): Promise<void> {
  return new Promise(resolve => {
    const half = FLIP_DURATION / 2;

    evals.forEach((state, col) => {
      setTimeout(() => {
        const tile = getTile(row, col);

        if (!tile) {
          if (col === WORD_LENGTH - 1) setTimeout(resolve, half + 50);
          return;
        }

        // Phase 1: rotate to -90°
        tile.style.transition = `transform ${half}ms ease-in`;
        tile.style.transform = 'rotateX(-90deg)';

        setTimeout(() => {
          // At 90° — invisible to the user — swap in the evaluation color
          tile.dataset.state = state;

          // Phase 2: rotate back to 0°
          tile.style.transition = `transform ${half}ms ease-out`;
          tile.style.transform = 'rotateX(0deg)';

          if (col === WORD_LENGTH - 1) {
            setTimeout(resolve, half + 50);
          }
        }, half);

      }, col * REVEAL_STAGGER);
    });
  });
}

/**
 * Celebratory bounce on each tile in the winning row, staggered left-to-right.
 */
export function bounceRow(row: number): void {
  for (let col = 0; col < WORD_LENGTH; col++) {
    const tile = getTile(row, col);
    if (!tile) continue;

    setTimeout(() => {
      tile.classList.add('board__tile--bounce');
      tile.addEventListener('animationend', () => {
        tile.classList.remove('board__tile--bounce');
      }, { once: true });
    }, col * 100);
  }
}
