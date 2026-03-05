import { AIProviderConfig, AIResponse, ConversationTurn, ToolDefinition } from '../types/index.js';

/**
 * Contract for AI providers (Claude, OpenAI, Gemini, etc.).
 * To swap AI backends, implement this interface.
 */
export interface IAIProvider {
  /** Unique provider identifier */
  readonly id: string;

  /** Human-readable name */
  readonly name: string;

  /**
   * Send a conversation and get a response.
   * The provider must handle multi-turn tool use internally
   * until it produces a final text response.
   *
   * @param history     - conversation history (user + assistant turns)
   * @param tools       - available tools (may be empty)
   * @param config      - overrides for model/tokens/temp/system prompt
   */
  chat(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    config?: Partial<AIProviderConfig>,
  ): Promise<AIResponse>;

  /**
   * Called by the Orchestrator to let the provider execute intermediate
   * tool use rounds (tool_call → tool_result → final response).
   * The provider receives a callback to actually run each tool.
   */
  chatWithTools(
    history: ConversationTurn[],
    tools: ToolDefinition[],
    toolExecutor: (name: string, input: Record<string, unknown>) => Promise<unknown>,
    config?: Partial<AIProviderConfig>,
  ): Promise<string>;
}
