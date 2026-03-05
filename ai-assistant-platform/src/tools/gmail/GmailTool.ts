import { google } from 'googleapis';
import { OAuth2Client } from 'google-auth-library';
import fs from 'fs/promises';
import path from 'path';
import { ITool } from '../ITool.js';
import { ToolDefinition, ToolResult } from '../../types/index.js';
import { config } from '../../config/config.js';
import { logger } from '../../core/logger.js';

/**
 * Gmail Tool — read, search, send, and draft emails.
 *
 * Uses the same credentials.json / token.json as Google Calendar.
 * Required OAuth scopes: https://www.googleapis.com/auth/gmail.modify
 */
export class GmailTool implements ITool {
  readonly name = 'gmail';
  readonly displayName = 'Gmail';

  private auth?: OAuth2Client;

  async isAvailable(): Promise<boolean> {
    try {
      await this.getAuth();
      return true;
    } catch {
      logger.warn('[Gmail] Credentials not configured or authorized.');
      return false;
    }
  }

  getDefinition(): ToolDefinition {
    return {
      name: this.name,
      description: `Manage Gmail. Actions: search_emails, get_email, send_email, list_labels.`,
      inputSchema: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['search_emails', 'get_email', 'send_email', 'list_labels'],
            description: 'Operation to perform',
          },
          query: {
            type: 'string',
            description: 'Gmail search query, e.g. "from:boss@company.com subject:invoice"',
          },
          message_id: {
            type: 'string',
            description: 'Email message ID (for get_email)',
          },
          to: {
            type: 'string',
            description: 'Recipient email address (for send_email)',
          },
          subject: {
            type: 'string',
            description: 'Email subject (for send_email)',
          },
          body: {
            type: 'string',
            description: 'Email body text (for send_email)',
          },
          max_results: {
            type: 'number',
            description: 'Max emails to return (default: 10)',
          },
        },
        required: ['action'],
      },
    };
  }

  async execute(input: Record<string, unknown>): Promise<ToolResult> {
    const action = input['action'] as string;

    switch (action) {
      case 'search_emails': return this.searchEmails(input);
      case 'get_email':     return this.getEmail(input);
      case 'send_email':    return this.sendEmail(input);
      case 'list_labels':   return this.listLabels();
      default:
        return { success: false, error: `Unknown Gmail action: ${action}` };
    }
  }

  // ------------------------------------------------------------------
  // Actions
  // ------------------------------------------------------------------

  private async searchEmails(input: Record<string, unknown>): Promise<ToolResult> {
    const gmail  = await this.getGmail();
    const query  = (input['query'] as string) ?? '';
    const maxResults = (input['max_results'] as number) ?? 10;

    const listRes = await gmail.users.messages.list({
      userId: 'me',
      q: query,
      maxResults,
    });

    const messages = listRes.data.messages ?? [];
    if (!messages.length) return { success: true, data: [] };

    // Fetch snippet for each message
    const details = await Promise.all(
      messages.slice(0, maxResults).map((m) =>
        gmail.users.messages.get({ userId: 'me', id: m.id!, format: 'metadata',
          metadataHeaders: ['From', 'To', 'Subject', 'Date'] }).then((r) => ({
            id: r.data.id,
            snippet: r.data.snippet,
            headers: Object.fromEntries(
              (r.data.payload?.headers ?? []).map((h) => [h.name, h.value])
            ),
          })),
      ),
    );

    return { success: true, data: details };
  }

  private async getEmail(input: Record<string, unknown>): Promise<ToolResult> {
    const gmail     = await this.getGmail();
    const messageId = input['message_id'] as string;
    if (!messageId) return { success: false, error: 'message_id is required' };

    const res = await gmail.users.messages.get({ userId: 'me', id: messageId, format: 'full' });

    const headers = Object.fromEntries(
      (res.data.payload?.headers ?? []).map((h) => [h.name, h.value])
    );

    const body = this.extractBody(res.data.payload as Record<string, unknown> | undefined);

    return {
      success: true,
      data: { id: res.data.id, headers, snippet: res.data.snippet, body },
    };
  }

  private async sendEmail(input: Record<string, unknown>): Promise<ToolResult> {
    const gmail   = await this.getGmail();
    const to      = input['to'] as string;
    const subject = input['subject'] as string;
    const body    = input['body'] as string;

    if (!to || !subject || !body) {
      return { success: false, error: 'to, subject, and body are required' };
    }

    const raw = this.encodeEmail(to, subject, body);

    const res = await gmail.users.messages.send({
      userId: 'me',
      requestBody: { raw },
    });

    return { success: true, data: { id: res.data.id, threadId: res.data.threadId } };
  }

  private async listLabels(): Promise<ToolResult> {
    const gmail = await this.getGmail();
    const res   = await gmail.users.labels.list({ userId: 'me' });
    const labels = (res.data.labels ?? []).map((l) => ({ id: l.id, name: l.name }));
    return { success: true, data: labels };
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private encodeEmail(to: string, subject: string, body: string): string {
    const email = [
      `To: ${to}`,
      `Subject: ${subject}`,
      'MIME-Version: 1.0',
      'Content-Type: text/plain; charset=UTF-8',
      '',
      body,
    ].join('\r\n');

    return Buffer.from(email).toString('base64url');
  }

  private extractBody(payload: Record<string, unknown> | undefined): string {
    if (!payload) return '';
    const body = payload['body'] as { data?: string } | undefined;
    if (body?.data) return Buffer.from(body.data, 'base64').toString('utf-8');

    const parts = payload['parts'] as Record<string, unknown>[] | undefined;
    if (parts) {
      for (const part of parts) {
        if (part['mimeType'] === 'text/plain') {
          const partBody = part['body'] as { data?: string } | undefined;
          if (partBody?.data) return Buffer.from(partBody.data, 'base64').toString('utf-8');
        }
      }
    }

    return '';
  }

  private async getGmail() {
    const auth = await this.getAuth();
    return google.gmail({ version: 'v1', auth });
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
      logger.warn('[Gmail] No token.json found. Run `npm run authorize-google` to authorize.');
      throw new Error('Google OAuth not authorized.');
    }

    this.auth = oAuth2Client;
    return this.auth;
  }
}
