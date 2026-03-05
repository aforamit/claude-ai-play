import Anthropic from '@anthropic-ai/sdk';
import { IAIProvider } from './IAIProvider.js';
import { AIProviderConfig, AIResponse, ConversationTurn, ToolDefinition } from '../types/index.js';
import { config } from '../config/config.js';
import { logger } from '../core/logger.js';

const DEFAULT_SYSTEM_PROMPT = `You are a helpful AI assistant for a small business.
You have access to tools like Notion, Google Calendar, Gmail, and Google Drive to help
automate tasks and answer questions. Be concise, professional, and action-oriented.
When you use a tool, briefly explain what you're doing before calling it.`;

/**
 * Anthropic Claude AI provider.
 * Handles multi-turn tool use loops internally.
 */
export class ClaudeProvider implements IAIProvider {
  readonly id = 'claude';
  readonly name = 'Anthropic Claude';

  private client: Anthropic;

  constructor() {
    this.client = new Anthropic({ apiKey: config.ai.claude.apiKey });
  }

  async chat(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    override?: Partial<AIProviderConfig>,
  ): Promise<AIResponse> {
    const messages = this.toAnthropicMessages(history);
    const model    = override?.model ?? config.ai.claude.model;
    const maxTok   = override?.maxTokens ?? config.ai.defaults.maxTokens;
    const sysPrompt = override?.systemPrompt ?? DEFAULT_SYSTEM_PROMPT;

    const requestParams: Anthropic.MessageCreateParamsNonStreaming = {
      model,
      max_tokens: maxTok,
      system: sysPrompt,
      messages,
      tools: tools.map(this.toAnthropicTool),
    };

    const response = await this.client.messages.create(requestParams);

    const textContent = response.content
      .filter((b): b is Anthropic.TextBlock => b.type === 'text')
      .map((b) => b.text)
      .join('\n');

    const toolUseBlocks = response.content.filter(
      (b): b is Anthropic.ToolUseBlock => b.type === 'tool_use',
    );

    return {
      content: textContent,
      toolCalls: toolUseBlocks.map((b) => ({
        id: b.id,
        toolName: b.name,
        input: b.input as Record<string, unknown>,
      })),
      finishReason: response.stop_reason === 'tool_use' ? 'tool_use' : 'end_turn',
    };
  }

  /**
   * Full agentic loop: calls Claude repeatedly until it produces a final
   * text response, executing tools in between.
   */
  async chatWithTools(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    toolExecutor: (name: string, input: Record<string, unknown>) => Promise<unknown>,
    override?: Partial<AIProviderConfig>,
  ): Promise<string> {
    const model      = override?.model ?? config.ai.claude.model;
    const maxTok     = override?.maxTokens ?? config.ai.defaults.maxTokens;
    const sysPrompt  = override?.systemPrompt ?? DEFAULT_SYSTEM_PROMPT;
    const anthropicTools = tools.map(this.toAnthropicTool);

    // Build mutable message array for multi-turn tool use
    const messages: Anthropic.MessageParam[] = this.toAnthropicMessages(history);

    let iteration = 0;
    const MAX_ITERATIONS = 10;

    while (iteration < MAX_ITERATIONS) {
      iteration++;

      const response = await this.client.messages.create({
        model,
        max_tokens: maxTok,
        system: sysPrompt,
        messages,
        tools: anthropicTools,
      });

      logger.debug(`[Claude] iteration=${iteration} stop_reason=${response.stop_reason}`);

      if (response.stop_reason === 'end_turn' || response.stop_reason === 'stop_sequence') {
        const text = response.content
          .filter((b): b is Anthropic.TextBlock => b.type === 'text')
          .map((b) => b.text)
          .join('\n');
        return text;
      }

      if (response.stop_reason === 'tool_use') {
        // Add assistant's message (may contain text + tool_use blocks)
        messages.push({ role: 'assistant', content: response.content });

        // Execute each tool and collect results
        const toolResults: Anthropic.ToolResultBlockParam[] = [];

        for (const block of response.content) {
          if (block.type !== 'tool_use') continue;

          logger.info(`[Claude] Calling tool: ${block.name}`, block.input);

          let result: unknown;
          try {
            result = await toolExecutor(block.name, block.input as Record<string, unknown>);
          } catch (err) {
            result = { error: String(err) };
          }

          toolResults.push({
            type: 'tool_result',
            tool_use_id: block.id,
            content: JSON.stringify(result),
          });
        }

        messages.push({ role: 'user', content: toolResults });
        continue;
      }

      // max_tokens or unknown stop reason — return whatever text we have
      const text = response.content
        .filter((b): b is Anthropic.TextBlock => b.type === 'text')
        .map((b) => b.text)
        .join('\n');
      return text || 'I reached my response limit. Please try a simpler request.';
    }

    return 'I could not complete the task within the allowed number of steps.';
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private toAnthropicMessages(history: ConversationTurn[]): Anthropic.MessageParam[] {
    return history.map((turn) => ({
      role: turn.role,
      content: turn.content,
    }));
  }

  private toAnthropicTool(tool: ToolDefinition): Anthropic.Tool {
    return {
      name: tool.name,
      description: tool.description,
      input_schema: tool.inputSchema as Anthropic.Tool.InputSchema,
    };
  }
}
