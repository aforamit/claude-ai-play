import { ITool } from './ITool.js';
import { ToolDefinition, ToolResult } from '../types/index.js';
import { logger } from '../core/logger.js';

/**
 * Central registry for all tool integrations.
 * The Orchestrator queries this for definitions and execution.
 */
export class ToolRegistry {
  private tools = new Map<string, ITool>();

  /** Register a tool. Throws if name is already taken. */
  register(tool: ITool): void {
    if (this.tools.has(tool.name)) {
      throw new Error(`Tool already registered: ${tool.name}`);
    }
    this.tools.set(tool.name, tool);
    logger.info(`[ToolRegistry] Registered: ${tool.name}`);
  }

  /** Register multiple tools at once. */
  registerAll(tools: ITool[]): void {
    tools.forEach((t) => this.register(t));
  }

  /**
   * Returns definitions for all registered (and available) tools
   * to be passed to the AI provider.
   */
  async getDefinitions(): Promise<ToolDefinition[]> {
    const defs: ToolDefinition[] = [];

    for (const tool of this.tools.values()) {
      if (tool.isAvailable) {
        const available = await tool.isAvailable().catch(() => false);
        if (!available) {
          logger.warn(`[ToolRegistry] Tool unavailable, skipping: ${tool.name}`);
          continue;
        }
      }
      defs.push(tool.getDefinition());
    }

    return defs;
  }

  /**
   * Execute a tool by name with the given input.
   * Returns a ToolResult so errors are surfaced to the AI gracefully.
   */
  async execute(name: string, input: Record<string, unknown>): Promise<ToolResult> {
    const tool = this.tools.get(name);
    if (!tool) {
      return { success: false, error: `Unknown tool: ${name}` };
    }

    const start = Date.now();
    try {
      const result = await tool.execute(input);
      logger.info(`[ToolRegistry] ${name} completed in ${Date.now() - start}ms`);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      logger.error(`[ToolRegistry] ${name} failed: ${msg}`);
      return { success: false, error: msg };
    }
  }

  /** List all registered tool names. */
  list(): string[] {
    return [...this.tools.keys()];
  }
}
