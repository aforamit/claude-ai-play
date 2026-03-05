// ============================================================
// Core Domain Types
// ============================================================

/** Normalized inbound/outbound message across all interfaces */
export interface Message {
  id: string;
  from: string;          // user identifier (phone, user ID, etc.)
  to: string;            // destination (bot number, channel, etc.)
  content: string;
  timestamp: Date;
  interfaceId: string;   // which interface this came from
  metadata?: Record<string, unknown>;
}

/** A single turn in the conversation history */
export interface ConversationTurn {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  toolCalls?: ToolCallRecord[];
}

/** Record of a tool invocation within a turn */
export interface ToolCallRecord {
  toolName: string;
  input: Record<string, unknown>;
  output: ToolResult;
  durationMs: number;
}

/** Per-user conversation session */
export interface Session {
  id: string;
  userId: string;
  interfaceId: string;
  history: ConversationTurn[];
  createdAt: Date;
  updatedAt: Date;
  metadata?: Record<string, unknown>;
}

/** Standardized result from any tool */
export interface ToolResult {
  success: boolean;
  data?: unknown;
  error?: string;
}

/** Claude/OpenAI-style tool definition (JSON Schema) */
export interface ToolDefinition {
  name: string;
  description: string;
  inputSchema: {
    type: 'object';
    properties: Record<string, unknown>;
    required?: string[];
  };
}

/** AI provider response after processing a message */
export interface AIResponse {
  content: string;
  toolCalls?: Array<{
    id: string;
    toolName: string;
    input: Record<string, unknown>;
  }>;
  finishReason: 'end_turn' | 'tool_use' | 'max_tokens' | 'stop';
}

/** Config for an AI provider */
export interface AIProviderConfig {
  model: string;
  maxTokens?: number;
  temperature?: number;
  systemPrompt?: string;
}

/** Handler called by interface adapters when a message arrives */
export type MessageHandler = (message: Message) => Promise<void>;
