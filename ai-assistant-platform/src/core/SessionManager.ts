import NodeCache from 'node-cache';
import { v4 as uuidv4 } from 'uuid';
import { Session, ConversationTurn } from '../types/index.js';
import { config } from '../config/config.js';
import { logger } from './logger.js';

/**
 * In-memory session manager with TTL expiry.
 *
 * Keyed by `${interfaceId}:${userId}` to allow the same user to have
 * independent sessions across different interfaces.
 *
 * For production: swap out NodeCache for a Redis adapter by implementing
 * the same get/set/delete API — the Orchestrator won't need to change.
 */
export class SessionManager {
  private cache: NodeCache;

  constructor() {
    this.cache = new NodeCache({
      stdTTL: config.session.ttlHours * 60 * 60,
      checkperiod: 60 * 10, // prune expired sessions every 10 min
    });

    logger.info(`[SessionManager] TTL: ${config.session.ttlHours}h, max history: ${config.session.maxHistory}`);
  }

  /** Retrieve or create a session for a user on a given interface. */
  getOrCreate(userId: string, interfaceId: string): Session {
    const key     = this.key(userId, interfaceId);
    const existing = this.cache.get<Session>(key);

    if (existing) {
      existing.updatedAt = new Date();
      this.cache.set(key, existing);
      return existing;
    }

    const session: Session = {
      id: uuidv4(),
      userId,
      interfaceId,
      history: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.cache.set(key, session);
    logger.debug(`[SessionManager] Created session for ${key}`);
    return session;
  }

  /** Add a turn to the session history and enforce max-history limit. */
  addTurn(userId: string, interfaceId: string, turn: ConversationTurn): void {
    const session = this.getOrCreate(userId, interfaceId);
    session.history.push(turn);

    // Keep only the most recent N turns
    const max = config.session.maxHistory;
    if (session.history.length > max) {
      session.history = session.history.slice(-max);
    }

    session.updatedAt = new Date();
    this.cache.set(this.key(userId, interfaceId), session);
  }

  /** Wipe a user's session (e.g., on "clear" command). */
  clear(userId: string, interfaceId: string): void {
    const key = this.key(userId, interfaceId);
    this.cache.del(key);
    logger.info(`[SessionManager] Cleared session for ${key}`);
  }

  /** Return all active session keys (for debugging/admin). */
  listSessions(): string[] {
    return this.cache.keys();
  }

  private key(userId: string, interfaceId: string): string {
    return `${interfaceId}:${userId}`;
  }
}
