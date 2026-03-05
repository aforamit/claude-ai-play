import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { IUserInterface } from '../IUserInterface.js';
import { Message, MessageHandler } from '../../types/index.js';
import { logger } from '../../core/logger.js';

interface PendingReply {
  resolve: (text: string) => void;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * REST API adapter — useful for local testing, web UIs, or any HTTP client.
 *
 * POST /api/chat
 * Body: { "userId": "user1", "message": "Book a meeting tomorrow at 10am" }
 * Response: { "reply": "...", "userId": "user1" }
 *
 * This adapter supports both fire-and-forget (webhook mode) and
 * synchronous request/response (default mode).
 */
export class RestAdapter implements IUserInterface {
  readonly id = 'rest';
  readonly name = 'REST API';

  private messageHandler?: MessageHandler;
  private router: Router;
  // userId -> pending reply callback (for synchronous mode)
  private pendingReplies = new Map<string, PendingReply>();
  private readonly REPLY_TIMEOUT_MS = 30_000;

  constructor() {
    this.router = Router();
    this.setupRoutes();
  }

  async initialize(): Promise<void> {
    logger.info(`[${this.name}] Initialized. Endpoint: POST /api/chat`);
  }

  onMessage(handler: MessageHandler): void {
    this.messageHandler = handler;
  }

  async sendMessage(to: string, text: string): Promise<void> {
    const pending = this.pendingReplies.get(to);
    if (pending) {
      clearTimeout(pending.timer);
      this.pendingReplies.delete(to);
      pending.resolve(text);
    } else {
      // No pending request — log it (or push to a queue/SSE stream)
      logger.info(`[${this.name}] Reply to ${to}: ${text}`);
    }
  }

  getRouter(): Router {
    return this.router;
  }

  // ------------------------------------------------------------------
  // Private
  // ------------------------------------------------------------------

  private setupRoutes(): void {
    // Chat endpoint — synchronous request/response
    this.router.post('/api/chat', async (req: Request, res: Response) => {
      const { userId, message } = req.body as { userId?: string; message?: string };

      if (!userId || !message) {
        res.status(400).json({ error: 'userId and message are required' });
        return;
      }

      const msg: Message = {
        id: uuidv4(),
        from: userId,
        to: 'assistant',
        content: message,
        timestamp: new Date(),
        interfaceId: this.id,
      };

      logger.info(`[${this.name}] Incoming from ${userId}: ${message}`);

      if (!this.messageHandler) {
        res.status(503).json({ error: 'No message handler registered' });
        return;
      }

      // Register a promise that resolves when sendMessage() is called for this user
      const replyPromise = new Promise<string>((resolve) => {
        const timer = setTimeout(() => {
          this.pendingReplies.delete(userId);
          resolve('Sorry, the request timed out. Please try again.');
        }, this.REPLY_TIMEOUT_MS);

        this.pendingReplies.set(userId, { resolve, timer });
      });

      // Process message (non-blocking)
      this.messageHandler(msg).catch((err) => {
        logger.error(`[${this.name}] Handler error:`, err);
      });

      const reply = await replyPromise;
      res.json({ userId, reply });
    });

    // Health check
    this.router.get('/api/health', (_req, res) => {
      res.json({ status: 'ok', timestamp: new Date().toISOString() });
    });
  }
}
