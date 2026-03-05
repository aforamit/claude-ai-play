import type { TileState } from './types';
import { KEYBOARD_ROWS, STATE_PRIORITY } from './constants';

/**
 * Builds the on-screen keyboard into #keyboard.
 * Each button fires `onKey` with its label string on click.
 * Wide keys (ENTER, ⌫) get the `keyboard__key--wide` modifier.
 */
export function buildKeyboard(onKey: (key: string) => void): void {
  const container = document.getElementById('keyboard');
  if (!container) return;

  container.innerHTML = '';

  for (const row of KEYBOARD_ROWS) {
    const rowEl = document.createElement('div');
    rowEl.className = 'keyboard__row';

    for (const key of row) {
      const btn = document.createElement('button');
      const isWide = key.length > 1;
      btn.className = isWide ? 'keyboard__key keyboard__key--wide' : 'keyboard__key';
      btn.dataset.key = key;
      btn.dataset.state = 'empty';
      btn.textContent = key;
      btn.type = 'button';
      btn.addEventListener('click', () => onKey(key));
      rowEl.appendChild(btn);
    }

    container.appendChild(rowEl);
  }
}

/**
 * Updates a key's visual state only if the new state outranks the current one.
 * Prevents downgrading a key from correct → present after a later guess.
 */
export function setKeyState(letter: string, state: TileState): void {
  const btn = document.querySelector<HTMLElement>(
    `.keyboard__key[data-key="${letter}"]`
  );
  if (!btn) return;

  const current = btn.dataset.state ?? 'empty';
  if ((STATE_PRIORITY[state] ?? 0) > (STATE_PRIORITY[current] ?? 0)) {
    btn.dataset.state = state;
  }
}

/** Syncs the full keyboard from a keyMap snapshot (e.g. after loading saved state). */
export function syncKeyboard(keyMap: Record<string, TileState>): void {
  for (const [letter, state] of Object.entries(keyMap)) {
    setKeyState(letter, state);
  }
}
