import { Router } from 'express';
import { MessageHandler } from '../types/index.js';

/**
 * Contract that every UI channel adapter must implement.
 * To add a new channel (Telegram, Slack, SMS, etc.), create a class
 * that implements this interface and register it in app.ts.
 */
export interface IUserInterface {
  /** Unique identifier for this interface (e.g. 'whatsapp', 'rest', 'telegram') */
  readonly id: string;

  /** Human-readable name */
  readonly name: string;

  /**
   * Called once at startup. Should set up connections, webhook routes, etc.
   * Receives an Express app so adapters can register their own webhook routes.
   */
  initialize(): Promise<void>;

  /**
   * Send a message back to the user.
   * @param to   - user identifier (phone number, user ID, etc.)
   * @param text - message content
   */
  sendMessage(to: string, text: string): Promise<void>;

  /**
   * Register the handler that the Orchestrator will call when a message arrives.
   * The adapter must call this handler for every inbound message.
   */
  onMessage(handler: MessageHandler): void;

  /**
   * Optional: return an Express Router for this interface's HTTP endpoints
   * (e.g., Twilio webhook, Meta webhook verification).
   */
  getRouter?(): Router;
}
