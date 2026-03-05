/**
 * AI Assistant Platform — Entry Point
 *
 * To add a new interface:  instantiate + register in interfaces Map
 * To add a new tool:       instantiate + call toolRegistry.register(tool)
 * To swap AI providers:    change primaryProvider in .env or pass a different IAIProvider
 */

import express from 'express';
import { config } from './config/config.js';
import { logger } from './core/logger.js';

// --- Interfaces ---
import { IUserInterface } from './interfaces/IUserInterface.js';
import { TwilioWhatsAppAdapter } from './interfaces/whatsapp/TwilioWhatsAppAdapter.js';
import { RestAdapter } from './interfaces/rest/RestAdapter.js';

// --- AI Providers ---
import { IAIProvider } from './ai/IAIProvider.js';
import { ClaudeProvider } from './ai/ClaudeProvider.js';
import { OpenAIProvider } from './ai/OpenAIProvider.js';

// --- Tools ---
import { ToolRegistry } from './tools/ToolRegistry.js';
import { CalculatorTool } from './tools/calculator/CalculatorTool.js';
import { NotionTool } from './tools/notion/NotionTool.js';
import { GoogleCalendarTool } from './tools/googleCalendar/GoogleCalendarTool.js';
import { GmailTool } from './tools/gmail/GmailTool.js';
import { GoogleDriveTool } from './tools/googleDrive/GoogleDriveTool.js';

// --- Core ---
import { SessionManager } from './core/SessionManager.js';
import { Orchestrator } from './core/Orchestrator.js';

// ============================================================
// Bootstrap
// ============================================================

async function main(): Promise<void> {
  logger.info('='.repeat(60));
  logger.info('   AI Assistant Platform');
  logger.info('='.repeat(60));

  // ---- Express App ----
  const app = express();
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  // ---- AI Provider ----
  const aiProvider: IAIProvider =
    config.ai.primaryProvider === 'openai'
      ? new OpenAIProvider()
      : new ClaudeProvider();

  logger.info(`[App] AI Provider: ${aiProvider.name}`);

  // ---- Tool Registry ----
  const toolRegistry = new ToolRegistry();

  // Always-available tools (no API key needed)
  toolRegistry.register(new CalculatorTool());

  // External API tools — register unconditionally; each checks isAvailable() at runtime
  toolRegistry.register(new NotionTool());
  toolRegistry.register(new GoogleCalendarTool());
  toolRegistry.register(new GmailTool());
  toolRegistry.register(new GoogleDriveTool());

  // Add your own tools here:
  // toolRegistry.register(new MyCustomTool());

  // ---- Interfaces ----
  const interfaces = new Map<string, IUserInterface>();

  // REST API (always enabled — great for testing)
  const restAdapter = new RestAdapter();
  interfaces.set(restAdapter.id, restAdapter);

  // WhatsApp via Twilio (enabled only when a real SID is provided — Twilio SIDs start with "AC")
  const twilioSid = config.whatsapp.twilio.accountSid;
  if (twilioSid.startsWith('AC')) {
    const whatsappAdapter = new TwilioWhatsAppAdapter();
    interfaces.set(whatsappAdapter.id, whatsappAdapter);
  } else {
    logger.warn('[App] Twilio credentials not configured — WhatsApp interface disabled.');
    logger.warn('[App] Set TWILIO_ACCOUNT_SID (starts with AC) in .env to enable WhatsApp.');
  }

  // Add more interfaces here:
  // const telegramAdapter = new TelegramAdapter();
  // interfaces.set(telegramAdapter.id, telegramAdapter);

  // ---- Session Manager ----
  const sessionManager = new SessionManager();

  // ---- Orchestrator ----
  const orchestrator = new Orchestrator(interfaces, aiProvider, toolRegistry, sessionManager);
  orchestrator.initialize();

  // ---- Register HTTP routes from interfaces ----
  for (const iface of interfaces.values()) {
    await iface.initialize();
    if (iface.getRouter) {
      app.use('/', iface.getRouter());
    }
  }

  // ---- Admin endpoints ----
  app.get('/admin/sessions', (_req, res) => {
    res.json({ sessions: sessionManager.listSessions() });
  });

  app.get('/admin/tools', async (_req, res) => {
    const defs = await toolRegistry.getDefinitions();
    res.json({ tools: defs.map((d) => ({ name: d.name, description: d.description })) });
  });

  app.get('/admin/info', (_req, res) => {
    res.json({
      aiProvider: aiProvider.name,
      interfaces: [...interfaces.values()].map((i) => ({ id: i.id, name: i.name })),
      tools: toolRegistry.list(),
    });
  });

  // ---- Start Server ----
  const port = config.server.port;
  app.listen(port, () => {
    logger.info(`[App] Server running on http://localhost:${port}`);
    logger.info(`[App] REST API: POST http://localhost:${port}/api/chat`);
    logger.info(`[App] Health:   GET  http://localhost:${port}/api/health`);
    logger.info(`[App] Admin:    GET  http://localhost:${port}/admin/info`);
    logger.info(`[App] WhatsApp webhook: POST http://localhost:${port}/webhooks/whatsapp`);
    logger.info('='.repeat(60));
    logger.info('[App] Ready! Use ngrok to expose for WhatsApp webhooks.');
  });
}

main().catch((err) => {
  logger.error('Fatal startup error:', err);
  process.exit(1);
});
