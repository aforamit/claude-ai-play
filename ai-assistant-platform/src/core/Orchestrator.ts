import { IUserInterface } from '../interfaces/IUserInterface.js';
import { IAIProvider } from '../ai/IAIProvider.js';
import { ToolRegistry } from '../tools/ToolRegistry.js';
import { SessionManager } from './SessionManager.js';
import { Message, ConversationTurn } from '../types/index.js';
import { logger } from './logger.js';

const CLEAR_COMMANDS = ['clear', 'reset', 'start over', '/clear', '/reset'];
const HELP_TEXT = `
*AI Assistant Help*

I can help you with:
• 📅 Google Calendar — schedule meetings, list events, create/delete events
• 📧 Gmail — search emails, read messages, send emails
• 📂 Google Drive — list, search, read, and create files
• 📝 Notion — search pages, create notes, update databases
• 🔢 Calculator — math expressions

*Commands:*
• Type *clear* or *reset* to start a fresh conversation
• Type *help* to see this message
• Type *tools* to see available integrations

Just describe what you need in plain language!
`.trim();

/**
 * Central coordinator — receives messages from any interface,
 * manages session context, calls the AI with tools, and
 * routes responses back to the correct interface.
 */
export class Orchestrator {
  constructor(
    private interfaces: Map<string, IUserInterface>,
    private aiProvider: IAIProvider,
    private toolRegistry: ToolRegistry,
    private sessionManager: SessionManager,
  ) {}

  /** Wire up message handlers for all registered interfaces. */
  initialize(): void {
    for (const iface of this.interfaces.values()) {
      iface.onMessage((msg) => this.handleMessage(msg));
      logger.info(`[Orchestrator] Listening on interface: ${iface.name}`);
    }
  }

  /** Core message processing pipeline. */
  async handleMessage(message: Message): Promise<void> {
    const { from, interfaceId, content } = message;
    const iface = this.interfaces.get(interfaceId);

    if (!iface) {
      logger.error(`[Orchestrator] Unknown interface: ${interfaceId}`);
      return;
    }

    logger.info(`[Orchestrator] [${interfaceId}] from=${from} content="${content}"`);

    // Handle built-in commands
    const lower = content.trim().toLowerCase();

    if (CLEAR_COMMANDS.includes(lower)) {
      this.sessionManager.clear(from, interfaceId);
      await iface.sendMessage(from, '✅ Conversation cleared. How can I help you?');
      return;
    }

    if (lower === 'help' || lower === '/help') {
      await iface.sendMessage(from, HELP_TEXT);
      return;
    }

    if (lower === 'tools' || lower === '/tools') {
      const toolDefs = await this.toolRegistry.getDefinitions();
      const toolList = toolDefs.length
        ? toolDefs.map((t) => `• *${t.name}* — ${t.description.split('.')[0]}`).join('\n')
        : 'No tools currently available.';
      await iface.sendMessage(from, `*Available Tools:*\n${toolList}`);
      return;
    }

    // Add user turn to session history
    const userTurn: ConversationTurn = {
      role: 'user',
      content,
      timestamp: new Date(),
    };
    this.sessionManager.addTurn(from, interfaceId, userTurn);

    const session = this.sessionManager.getOrCreate(from, interfaceId);

    try {
      // Get available tool definitions
      const toolDefinitions = await this.toolRegistry.getDefinitions();

      // Run AI with agentic tool loop
      const reply = await this.aiProvider.chatWithTools(
        session.history,
        toolDefinitions,
        async (toolName, toolInput) => {
          const result = await this.toolRegistry.execute(toolName, toolInput);
          return result;
        },
      );

      // Add assistant turn to session history
      const assistantTurn: ConversationTurn = {
        role: 'assistant',
        content: reply,
        timestamp: new Date(),
      };
      this.sessionManager.addTurn(from, interfaceId, assistantTurn);

      // Send reply back via the originating interface
      await iface.sendMessage(from, reply || 'I could not generate a response. Please try again.');
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : String(err);
      logger.error(`[Orchestrator] Error processing message:`, err);

      const userFriendlyError =
        errorMsg.includes('API key') || errorMsg.includes('api_key')
          ? 'AI service not configured. Please check your API key.'
          : errorMsg.includes('credit balance') || errorMsg.includes('quota') || errorMsg.includes('billing')
          ? 'AI service billing issue. Please add credits to your Anthropic/OpenAI account.'
          : errorMsg.includes('rate limit') || errorMsg.includes('429')
          ? 'AI service rate limit reached. Please wait a moment and try again.'
          : 'Sorry, I encountered an error. Please try again.';

      await iface.sendMessage(from, `❌ ${userFriendlyError}`);
    }
  }
}
