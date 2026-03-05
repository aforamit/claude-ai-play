import { TOAST_DURATION, TOAST_FADE } from './constants';

/**
 * Displays a transient toast notification.
 * Appends to #toast-container, fades out, then removes itself.
 */
export function showToast(message: string, duration = TOAST_DURATION): void {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = message;
  container.appendChild(el);

  setTimeout(() => {
    el.classList.add('toast--fading');
    setTimeout(() => el.remove(), TOAST_FADE);
  }, duration);
}
