import { Router, Request, Response } from 'express';
import twilio from 'twilio';
import { v4 as uuidv4 } from 'uuid';
import { IUserInterface } from '../IUserInterface.js';
import { Message, MessageHandler } from '../../types/index.js';
import { config } from '../../config/config.js';
import { logger } from '../../core/logger.js';

/**
 * WhatsApp adapter using Twilio's API.
 *
 * Setup:
 * 1. Create a Twilio account and enable the WhatsApp Sandbox.
 * 2. Set webhook URL to: https://<your-ngrok-url>/webhooks/whatsapp
 * 3. Fill in TWILIO_* env vars.
 *
 * On Windows: run `ngrok http 3000` to expose your local server.
 */
export class TwilioWhatsAppAdapter implements IUserInterface {
  readonly id = 'whatsapp-twilio';
  readonly name = 'WhatsApp (Twilio)';

  private _client?: ReturnType<typeof twilio>;
  private messageHandler?: MessageHandler;
  private router: Router;

  constructor() {
    this.router = Router();
    this.setupRoutes();
  }

  /** Lazy-initialised so the constructor never throws on missing/placeholder creds. */
  private get client(): ReturnType<typeof twilio> {
    if (!this._client) {
      this._client = twilio(
        config.whatsapp.twilio.accountSid,
        config.whatsapp.twilio.authToken,
      );
    }
    return this._client;
  }

  async initialize(): Promise<void> {
    logger.info(`[${this.name}] Initialized. Webhook path: POST /webhooks/whatsapp`);
    logger.info(`[${this.name}] From number: ${config.whatsapp.twilio.whatsappNumber}`);
  }

  onMessage(handler: MessageHandler): void {
    this.messageHandler = handler;
  }

  async sendMessage(to: string, text: string): Promise<void> {
    // Ensure 'whatsapp:' prefix
    const toFormatted = to.startsWith('whatsapp:') ? to : `whatsapp:${to}`;

    // WhatsApp has a 1600-char limit; split if needed
    const chunks = this.splitMessage(text, 1500);

    for (const chunk of chunks) {
      await this.client.messages.create({
        from: config.whatsapp.twilio.whatsappNumber,
        to: toFormatted,
        body: chunk,
      });
    }

    logger.info(`[${this.name}] Sent message to ${toFormatted}`);
  }

  getRouter(): Router {
    return this.router;
  }

  // ------------------------------------------------------------------
  // Private
  // ------------------------------------------------------------------

  private setupRoutes(): void {
    // Twilio sends form-encoded POST when a WhatsApp message arrives
    this.router.post('/webhooks/whatsapp', async (req: Request, res: Response) => {
      try {
        const body = req.body as Record<string, string>;
        const from = body['From'] ?? '';   // e.g. "whatsapp:+1234567890"
        const text = body['Body'] ?? '';
        const to   = body['To'] ?? '';

        logger.info(`[${this.name}] Incoming from ${from}: ${text}`);

        const message: Message = {
          id: uuidv4(),
          from,
          to,
          content: text,
          timestamp: new Date(),
          interfaceId: this.id,
          metadata: { rawBody: body },
        };

        if (this.messageHandler) {
          // Process asynchronously; send empty TwiML response immediately
          this.messageHandler(message).catch((err) =>
            logger.error(`[${this.name}] Handler error:`, err),
          );
        }

        // Twilio expects a TwiML response (can be empty)
        res.set('Content-Type', 'text/xml');
        res.send('<Response></Response>');
      } catch (err) {
        logger.error(`[${this.name}] Webhook error:`, err);
        res.status(500).send('Internal Server Error');
      }
    });
  }

  private splitMessage(text: string, maxLen: number): string[] {
    if (text.length <= maxLen) return [text];
    const chunks: string[] = [];
    let i = 0;
    while (i < text.length) {
      chunks.push(text.slice(i, i + maxLen));
      i += maxLen;
    }
    return chunks;
  }
}
