import OpenAI from 'openai';
import { IAIProvider } from './IAIProvider.js';
import { AIProviderConfig, AIResponse, ConversationTurn, ToolDefinition } from '../types/index.js';
import { config } from '../config/config.js';
import { logger } from '../core/logger.js';

const DEFAULT_SYSTEM_PROMPT = `You are a helpful AI assistant for a small business.
You have access to tools like Notion, Google Calendar, Gmail, and Google Drive to help
automate tasks and answer questions. Be concise, professional, and action-oriented.`;

/**
 * OpenAI provider (GPT-4o, etc.).
 * Drop-in replacement for ClaudeProvider.
 */
export class OpenAIProvider implements IAIProvider {
  readonly id = 'openai';
  readonly name = 'OpenAI';

  private client: OpenAI;

  constructor() {
    this.client = new OpenAI({ apiKey: config.ai.openai.apiKey });
  }

  async chat(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    override?: Partial<AIProviderConfig>,
  ): Promise<AIResponse> {
    const model     = override?.model ?? config.ai.openai.model;
    const maxTok    = override?.maxTokens ?? config.ai.defaults.maxTokens;
    const sysPrompt = override?.systemPrompt ?? DEFAULT_SYSTEM_PROMPT;

    const messages: OpenAI.ChatCompletionMessageParam[] = [
      { role: 'system', content: sysPrompt },
      ...this.toOpenAIMessages(history),
    ];

    const response = await this.client.chat.completions.create({
      model,
      max_tokens: maxTok,
      messages,
      tools: tools.map(this.toOpenAITool),
      tool_choice: tools.length ? 'auto' : undefined,
    });

    const choice = response.choices[0];
    const msg    = choice.message;

    return {
      content: msg.content ?? '',
      toolCalls: msg.tool_calls?.map((tc) => ({
        id: tc.id,
        toolName: tc.function.name,
        input: JSON.parse(tc.function.arguments) as Record<string, unknown>,
      })),
      finishReason: choice.finish_reason === 'tool_calls' ? 'tool_use' : 'end_turn',
    };
  }

  async chatWithTools(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    toolExecutor: (name: string, input: Record<string, unknown>) => Promise<unknown>,
    override?: Partial<AIProviderConfig>,
  ): Promise<string> {
    const model     = override?.model ?? config.ai.openai.model;
    const maxTok    = override?.maxTokens ?? config.ai.defaults.maxTokens;
    const sysPrompt = override?.systemPrompt ?? DEFAULT_SYSTEM_PROMPT;
    const oaiTools  = tools.map(this.toOpenAITool);

    const messages: OpenAI.ChatCompletionMessageParam[] = [
      { role: 'system', content: sysPrompt },
      ...this.toOpenAIMessages(history),
    ];

    let iteration = 0;
    const MAX_ITERATIONS = 10;

    while (iteration < MAX_ITERATIONS) {
      iteration++;

      const response = await this.client.chat.completions.create({
        model,
        max_tokens: maxTok,
        messages,
        tools: oaiTools,
        tool_choice: 'auto',
      });

      const choice = response.choices[0];
      const msg    = choice.message;

      logger.debug(`[OpenAI] iteration=${iteration} finish_reason=${choice.finish_reason}`);

      if (choice.finish_reason === 'stop' || !msg.tool_calls?.length) {
        return msg.content ?? '';
      }

      if (choice.finish_reason === 'tool_calls' && msg.tool_calls?.length) {
        messages.push({ role: 'assistant', content: msg.content, tool_calls: msg.tool_calls });

        for (const tc of msg.tool_calls) {
          const input = JSON.parse(tc.function.arguments) as Record<string, unknown>;
          logger.info(`[OpenAI] Calling tool: ${tc.function.name}`, input);

          let result: unknown;
          try {
            result = await toolExecutor(tc.function.name, input);
          } catch (err) {
            result = { error: String(err) };
          }

          messages.push({
            role: 'tool',
            tool_call_id: tc.id,
            content: JSON.stringify(result),
          });
        }
        continue;
      }

      return msg.content ?? '';
    }

    return 'I could not complete the task within the allowed number of steps.';
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private toOpenAIMessages(history: ConversationTurn[]): OpenAI.ChatCompletionMessageParam[] {
    return history.map((t) => ({ role: t.role, content: t.content }));
  }

  private toOpenAITool(tool: ToolDefinition): OpenAI.ChatCompletionTool {
    return {
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.inputSchema,
      },
    };
  }
}
