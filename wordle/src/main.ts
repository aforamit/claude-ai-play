import { buildBoard } from './board';
import { buildKeyboard } from './keyboard';
import { registerKeyboard } from './input';
import { handleKey } from './game';

document.addEventListener('DOMContentLoaded', () => {
  buildBoard();
  buildKeyboard(handleKey);
  registerKeyboard(handleKey);
});
