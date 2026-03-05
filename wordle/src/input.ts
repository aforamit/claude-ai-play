type KeyHandler = (key: string) => void;

/**
 * Attaches a physical keyboard listener.
 * Normalises browser key names to the same format used by the on-screen
 * keyboard: uppercase letters, 'ENTER', '⌫'.
 *
 * Returns an unregister function so the listener can be removed if needed.
 */
export function registerKeyboard(onKey: KeyHandler): () => void {
  function handler(e: KeyboardEvent): void {
    if (e.ctrlKey || e.altKey || e.metaKey) return;

    if (e.key === 'Enter') {
      onKey('ENTER');
    } else if (e.key === 'Backspace') {
      onKey('⌫');
    } else if (/^[a-zA-Z]$/.test(e.key)) {
      onKey(e.key.toUpperCase());
    }
  }

  document.addEventListener('keydown', handler);
  return () => document.removeEventListener('keydown', handler);
}
