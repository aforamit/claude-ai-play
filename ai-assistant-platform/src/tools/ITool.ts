import { ToolDefinition, ToolResult } from '../types/index.js';

/**
 * Contract every tool integration must implement.
 *
 * Adding a new tool is as simple as:
 * 1. Create a class that implements ITool
 * 2. Register it in ToolRegistry
 * The Orchestrator and AI providers pick it up automatically.
 */
export interface ITool {
  /** Must match the tool name used in ToolDefinition */
  readonly name: string;

  /** Human-readable name */
  readonly displayName: string;

  /** Returns the JSON-schema tool definition sent to the AI */
  getDefinition(): ToolDefinition;

  /**
   * Executes the tool with the given input.
   * @param input - AI-supplied arguments (validated against the schema)
   */
  execute(input: Record<string, unknown>): Promise<ToolResult>;

  /**
   * Optional: called once on startup to verify credentials / connectivity.
   * Should NOT throw; return false if unavailable.
   */
  isAvailable?(): Promise<boolean>;
}
