import { Client as NotionClient } from '@notionhq/client';
import { ITool } from '../ITool.js';
import { ToolDefinition, ToolResult } from '../../types/index.js';
import { config } from '../../config/config.js';

/**
 * Notion Tool — create, search, and read pages/databases.
 *
 * Setup:
 * 1. Go to https://www.notion.so/my-integrations and create an integration.
 * 2. Copy the Internal Integration Token to NOTION_API_KEY in .env.
 * 3. Share your Notion pages/databases with the integration.
 */
export class NotionTool implements ITool {
  readonly name = 'notion';
  readonly displayName = 'Notion';

  private client: NotionClient;

  constructor() {
    this.client = new NotionClient({ auth: config.tools.notion.apiKey });
  }

  async isAvailable(): Promise<boolean> {
    if (!config.tools.notion.apiKey) return false;
    try {
      await this.client.users.me({});
      return true;
    } catch {
      return false;
    }
  }

  getDefinition(): ToolDefinition {
    return {
      name: this.name,
      description: `Interact with Notion. Actions: search, create_page, get_page, append_to_page, list_databases.`,
      inputSchema: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['search', 'create_page', 'get_page', 'append_to_page', 'list_databases'],
            description: 'The operation to perform',
          },
          query: {
            type: 'string',
            description: 'Search query (for search action)',
          },
          database_id: {
            type: 'string',
            description: 'Notion database ID (for create_page)',
          },
          page_id: {
            type: 'string',
            description: 'Notion page ID (for get_page or append_to_page)',
          },
          title: {
            type: 'string',
            description: 'Page title (for create_page)',
          },
          content: {
            type: 'string',
            description: 'Page content / text to append (for create_page, append_to_page)',
          },
          properties: {
            type: 'object',
            description: 'Additional Notion page properties (key-value)',
          },
        },
        required: ['action'],
      },
    };
  }

  async execute(input: Record<string, unknown>): Promise<ToolResult> {
    const action = input['action'] as string;

    switch (action) {
      case 'search':       return this.search(input);
      case 'create_page':  return this.createPage(input);
      case 'get_page':     return this.getPage(input);
      case 'append_to_page': return this.appendToPage(input);
      case 'list_databases': return this.listDatabases();
      default:
        return { success: false, error: `Unknown Notion action: ${action}` };
    }
  }

  // ------------------------------------------------------------------
  // Actions
  // ------------------------------------------------------------------

  private async search(input: Record<string, unknown>): Promise<ToolResult> {
    const query = (input['query'] as string) ?? '';
    const response = await this.client.search({ query, page_size: 10 });
    const results = response.results.map((r: Record<string, unknown>) => ({
      id: r['id'],
      type: r['object'],
      url: r['url'],
      title: this.extractTitle(r),
    }));
    return { success: true, data: results };
  }

  private async createPage(input: Record<string, unknown>): Promise<ToolResult> {
    const databaseId = (input['database_id'] as string) ?? config.tools.notion.defaultDatabaseId;
    const title      = (input['title'] as string) ?? 'Untitled';
    const content    = (input['content'] as string) ?? '';

    const page = await this.client.pages.create({
      parent: { database_id: databaseId },
      properties: {
        title: { title: [{ text: { content: title } }] },
        ...(input['properties'] as Record<string, unknown> ?? {}),
      },
      children: content ? ([
        {
          object: 'block',
          type: 'paragraph',
          paragraph: { rich_text: [{ text: { content } }] },
        },
      ] as Parameters<typeof this.client.pages.create>[0]['children']) : [],
    });

    return { success: true, data: { id: page.id, url: (page as Record<string, unknown>)['url'] } };
  }

  private async getPage(input: Record<string, unknown>): Promise<ToolResult> {
    const pageId = input['page_id'] as string;
    if (!pageId) return { success: false, error: 'page_id is required' };

    const [page, blocks] = await Promise.all([
      this.client.pages.retrieve({ page_id: pageId }),
      this.client.blocks.children.list({ block_id: pageId }),
    ]);

    const text = blocks.results
      .map((b) => this.extractBlockText(b as Record<string, unknown>))
      .filter(Boolean)
      .join('\n');

    return {
      success: true,
      data: {
        id: page.id,
        url: (page as Record<string, unknown>)['url'],
        title: this.extractTitle(page as Record<string, unknown>),
        content: text,
      },
    };
  }

  private async appendToPage(input: Record<string, unknown>): Promise<ToolResult> {
    const pageId  = input['page_id'] as string;
    const content = input['content'] as string;
    if (!pageId || !content) return { success: false, error: 'page_id and content are required' };

    await this.client.blocks.children.append({
      block_id: pageId,
      children: [{
        object: 'block',
        type: 'paragraph',
        paragraph: { rich_text: [{ text: { content } }] },
      } as Parameters<typeof this.client.blocks.children.append>[0]['children'][0]],
    });

    return { success: true, data: { appended: true } };
  }

  private async listDatabases(): Promise<ToolResult> {
    const response = await this.client.search({
      filter: { property: 'object', value: 'database' },
      page_size: 20,
    });
    const databases = response.results.map((d: Record<string, unknown>) => ({
      id: d['id'],
      title: this.extractTitle(d),
      url: d['url'],
    }));
    return { success: true, data: databases };
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private extractTitle(obj: Record<string, unknown>): string {
    try {
      const props = obj['properties'] as Record<string, unknown> | undefined;
      if (props) {
        for (const key of ['title', 'Name', 'Title']) {
          const prop = props[key] as Record<string, unknown> | undefined;
          if (prop?.['title']) {
            const titleArr = prop['title'] as Array<{ plain_text?: string }>;
            return titleArr.map((t) => t.plain_text ?? '').join('');
          }
        }
      }
      const title = (obj['title'] as Array<{ plain_text?: string }> | undefined);
      if (title) return title.map((t) => t.plain_text ?? '').join('');
    } catch {
      // ignore
    }
    return 'Untitled';
  }

  private extractBlockText(block: Record<string, unknown>): string {
    const type = block['type'] as string;
    const content = block[type] as Record<string, unknown> | undefined;
    const richText = content?.['rich_text'] as Array<{ plain_text?: string }> | undefined;
    if (!richText) return '';
    return richText.map((t) => t.plain_text ?? '').join('');
  }
}
