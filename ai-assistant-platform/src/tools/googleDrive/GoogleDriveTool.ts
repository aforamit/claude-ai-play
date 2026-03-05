import { google } from 'googleapis';
import { OAuth2Client } from 'google-auth-library';
import fs from 'fs/promises';
import path from 'path';
import { Readable } from 'stream';
import { ITool } from '../ITool.js';
import { ToolDefinition, ToolResult } from '../../types/index.js';
import { config } from '../../config/config.js';
import { logger } from '../../core/logger.js';

/**
 * Google Drive Tool — list, search, read, and create files.
 *
 * Uses the same credentials.json / token.json as Google Calendar.
 * Required OAuth scopes: https://www.googleapis.com/auth/drive
 */
export class GoogleDriveTool implements ITool {
  readonly name = 'google_drive';
  readonly displayName = 'Google Drive';

  private auth?: OAuth2Client;

  async isAvailable(): Promise<boolean> {
    try {
      await this.getAuth();
      return true;
    } catch {
      logger.warn('[GoogleDrive] Credentials not configured or authorized.');
      return false;
    }
  }

  getDefinition(): ToolDefinition {
    return {
      name: this.name,
      description: `Manage Google Drive. Actions: list_files, search_files, get_file_content, create_file, share_file.`,
      inputSchema: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['list_files', 'search_files', 'get_file_content', 'create_file', 'share_file'],
            description: 'Operation to perform',
          },
          query: {
            type: 'string',
            description: 'Search query for search_files (Drive query syntax)',
          },
          file_id: {
            type: 'string',
            description: 'File ID (for get_file_content or share_file)',
          },
          folder_id: {
            type: 'string',
            description: 'Parent folder ID (for list_files or create_file)',
          },
          filename: {
            type: 'string',
            description: 'File name (for create_file)',
          },
          content: {
            type: 'string',
            description: 'Text content to write (for create_file)',
          },
          mime_type: {
            type: 'string',
            description: 'MIME type (for create_file, default: text/plain)',
          },
          email: {
            type: 'string',
            description: 'Email to share file with (for share_file)',
          },
          role: {
            type: 'string',
            enum: ['reader', 'writer', 'commenter'],
            description: 'Permission role (for share_file)',
          },
          max_results: {
            type: 'number',
            description: 'Max files to return (default: 20)',
          },
        },
        required: ['action'],
      },
    };
  }

  async execute(input: Record<string, unknown>): Promise<ToolResult> {
    const action = input['action'] as string;

    switch (action) {
      case 'list_files':       return this.listFiles(input);
      case 'search_files':     return this.searchFiles(input);
      case 'get_file_content': return this.getFileContent(input);
      case 'create_file':      return this.createFile(input);
      case 'share_file':       return this.shareFile(input);
      default:
        return { success: false, error: `Unknown Google Drive action: ${action}` };
    }
  }

  // ------------------------------------------------------------------
  // Actions
  // ------------------------------------------------------------------

  private async listFiles(input: Record<string, unknown>): Promise<ToolResult> {
    const drive    = await this.getDrive();
    const folderId = input['folder_id'] as string | undefined;
    const q        = folderId ? `'${folderId}' in parents and trashed=false` : 'trashed=false';

    const res = await drive.files.list({
      q,
      pageSize: (input['max_results'] as number) ?? 20,
      fields: 'files(id, name, mimeType, size, modifiedTime, webViewLink)',
    });

    return { success: true, data: res.data.files ?? [] };
  }

  private async searchFiles(input: Record<string, unknown>): Promise<ToolResult> {
    const drive = await this.getDrive();
    const query = input['query'] as string;

    const res = await drive.files.list({
      q: `fullText contains '${query.replace(/'/g, "\\'")}' and trashed=false`,
      pageSize: (input['max_results'] as number) ?? 20,
      fields: 'files(id, name, mimeType, size, modifiedTime, webViewLink)',
    });

    return { success: true, data: res.data.files ?? [] };
  }

  private async getFileContent(input: Record<string, unknown>): Promise<ToolResult> {
    const drive  = await this.getDrive();
    const fileId = input['file_id'] as string;
    if (!fileId) return { success: false, error: 'file_id is required' };

    // Get file metadata first
    const meta = await drive.files.get({ fileId, fields: 'mimeType,name' });
    const mimeType = meta.data.mimeType ?? '';

    let content: string;

    if (mimeType.startsWith('application/vnd.google-apps')) {
      // Export Google Doc/Sheet/Slides as plain text
      const exportMime = mimeType.includes('document') ? 'text/plain' :
                         mimeType.includes('spreadsheet') ? 'text/csv' : 'text/plain';
      const res = await drive.files.export({ fileId, mimeType: exportMime }, { responseType: 'text' });
      content = res.data as string;
    } else {
      // Download binary/text file
      const res = await drive.files.get({ fileId, alt: 'media' }, { responseType: 'text' });
      content = res.data as string;
    }

    // Truncate long files
    const MAX = 10_000;
    if (content.length > MAX) {
      content = content.slice(0, MAX) + `\n... [truncated at ${MAX} chars]`;
    }

    return { success: true, data: { id: fileId, name: meta.data.name, content } };
  }

  private async createFile(input: Record<string, unknown>): Promise<ToolResult> {
    const drive    = await this.getDrive();
    const filename = input['filename'] as string;
    const content  = (input['content'] as string) ?? '';
    const mimeType = (input['mime_type'] as string) ?? 'text/plain';
    const folderId = input['folder_id'] as string | undefined;

    const parents = folderId ? [folderId] : [];

    const res = await drive.files.create({
      requestBody: { name: filename, mimeType, parents },
      media: { mimeType, body: Readable.from([content]) },
      fields: 'id, name, webViewLink',
    });

    return { success: true, data: res.data };
  }

  private async shareFile(input: Record<string, unknown>): Promise<ToolResult> {
    const drive  = await this.getDrive();
    const fileId = input['file_id'] as string;
    const email  = input['email'] as string;
    const role   = (input['role'] as string) ?? 'reader';

    if (!fileId || !email) return { success: false, error: 'file_id and email are required' };

    await drive.permissions.create({
      fileId,
      requestBody: { type: 'user', role, emailAddress: email },
    });

    return { success: true, data: { shared: true, fileId, email, role } };
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private async getDrive() {
    const auth = await this.getAuth();
    return google.drive({ version: 'v3', auth });
  }

  private async getAuth(): Promise<OAuth2Client> {
    if (this.auth) return this.auth;

    const credPath = path.resolve(config.tools.google.credentialsPath);
    const raw      = await fs.readFile(credPath, 'utf-8');
    const creds    = JSON.parse(raw) as {
      installed?: { client_id: string; client_secret: string; redirect_uris: string[] };
      web?:       { client_id: string; client_secret: string; redirect_uris: string[] };
    };

    const { client_id, client_secret, redirect_uris } = creds.installed ?? creds.web!;
    const oAuth2Client = new google.auth.OAuth2(client_id, client_secret, redirect_uris[0]);

    const tokenPath = path.resolve(config.tools.google.tokenPath);
    try {
      const token = JSON.parse(await fs.readFile(tokenPath, 'utf-8'));
      oAuth2Client.setCredentials(token);
    } catch {
      logger.warn('[GoogleDrive] No token.json found. Run `npm run authorize-google` to authorize.');
      throw new Error('Google OAuth not authorized.');
    }

    this.auth = oAuth2Client;
    return this.auth;
  }
}
