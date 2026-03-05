import { ITool } from '../ITool.js';
import { ToolDefinition, ToolResult } from '../../types/index.js';

/**
 * Calculator Tool — a simple built-in tool that works without any API keys.
 * Useful for testing the platform and demonstrating tool use.
 */
export class CalculatorTool implements ITool {
  readonly name = 'calculator';
  readonly displayName = 'Calculator';

  getDefinition(): ToolDefinition {
    return {
      name: this.name,
      description: 'Evaluate a mathematical expression and return the result.',
      inputSchema: {
        type: 'object',
        properties: {
          expression: {
            type: 'string',
            description: 'A mathematical expression to evaluate, e.g. "(2 + 3) * 4"',
          },
        },
        required: ['expression'],
      },
    };
  }

  async execute(input: Record<string, unknown>): Promise<ToolResult> {
    const expr = input['expression'] as string;
    if (!expr) return { success: false, error: 'expression is required' };

    try {
      // Safe evaluation: only allow numbers and math operators
      if (!/^[\d\s+\-*/().%^]+$/.test(expr)) {
        return { success: false, error: 'Invalid expression: only numbers and +,-,*,/,(),% allowed' };
      }
      // eslint-disable-next-line no-new-func
      const result = Function(`'use strict'; return (${expr})`)() as number;
      return { success: true, data: { expression: expr, result } };
    } catch (err) {
      return { success: false, error: `Evaluation error: ${String(err)}` };
    }
  }
}
