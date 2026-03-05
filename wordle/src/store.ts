type Listener<T> = (state: T) => void;
type Updater<T> = (current: Readonly<T>) => T;

/**
 * Minimal reactive state container.
 *
 * - `getState()` returns a readonly reference — no defensive copying.
 * - `update(fn)` expects a pure function that returns a new state object.
 * - `subscribe(fn)` returns an unsubscribe function.
 *
 * ES module caching gives singleton semantics for free — just export one
 * instance from state.ts and every importer gets the same object.
 */
export class Store<T> {
  private state: T;
  private readonly listeners = new Set<Listener<T>>();

  constructor(initial: T) {
    this.state = initial;
  }

  getState(): Readonly<T> {
    return this.state;
  }

  update(updater: Updater<T>): void {
    this.state = updater(this.state);
    this.notify();
  }

  /** Returns an unsubscribe function. */
  subscribe(listener: Listener<T>): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    for (const fn of this.listeners) {
      fn(this.state);
    }
  }
}
