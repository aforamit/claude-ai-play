import { google } from 'googleapis';
import { OAuth2Client } from 'google-auth-library';
import fs from 'fs/promises';
import path from 'path';
import { ITool } from '../ITool.js';
import { ToolDefinition, ToolResult } from '../../types/index.js';
import { config } from '../../config/config.js';
import { logger } from '../../core/logger.js';

/** Ensure a datetime string is RFC3339 (has timezone). Appends 'Z' if missing. */
function toRFC3339(dt: string): string {
  if (!dt) return dt;
  // Already has timezone offset or Z
  if (/[Zz]$/.test(dt) || /[+-]\d{2}:\d{2}$/.test(dt)) return dt;
  return dt + 'Z';
}

/**
 * Google Calendar Tool — list, create, and delete calendar events.
 *
 * Setup:
 * 1. Create a project in Google Cloud Console.
 * 2. Enable the Google Calendar API.
 * 3. Create OAuth2 credentials (Desktop App), download as credentials.json.
 * 4. On first run, visit the URL printed to the console to authorize.
 *    A token.json file is saved automatically for future use.
 */
export class GoogleCalendarTool implements ITool {
  readonly name = 'google_calendar';
  readonly displayName = 'Google Calendar';

  private auth?: OAuth2Client;

  async isAvailable(): Promise<boolean> {
    try {
      await this.getAuth();
      return true;
    } catch {
      logger.warn('[GoogleCalendar] Credentials not configured or authorized.');
      return false;
    }
  }

  getDefinition(): ToolDefinition {
    return {
      name: this.name,
      description: `Manage Google Calendar. Actions: list_events, create_event, delete_event, list_calendars.`,
      inputSchema: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['list_events', 'create_event', 'delete_event', 'list_calendars'],
            description: 'Operation to perform',
          },
          calendar_id: {
            type: 'string',
            description: 'Calendar ID (default: "primary")',
          },
          event_id: {
            type: 'string',
            description: 'Event ID (for delete_event)',
          },
          title: {
            type: 'string',
            description: 'Event title/summary (for create_event)',
          },
          description: {
            type: 'string',
            description: 'Event description (for create_event)',
          },
          start_time: {
            type: 'string',
            description: 'Start time in ISO 8601 format, e.g. "2025-01-15T10:00:00"',
          },
          end_time: {
            type: 'string',
            description: 'End time in ISO 8601 format, e.g. "2025-01-15T11:00:00"',
          },
          attendees: {
            type: 'array',
            items: { type: 'string' },
            description: 'List of attendee email addresses',
          },
          max_results: {
            type: 'number',
            description: 'Max events to return for list_events (default: 10)',
          },
          time_min: {
            type: 'string',
            description: 'Filter events after this ISO 8601 time (for list_events)',
          },
          time_max: {
            type: 'string',
            description: 'Filter events before this ISO 8601 time (for list_events)',
          },
        },
        required: ['action'],
      },
    };
  }

  async execute(input: Record<string, unknown>): Promise<ToolResult> {
    const action = input['action'] as string;

    switch (action) {
      case 'list_events':     return this.listEvents(input);
      case 'create_event':    return this.createEvent(input);
      case 'delete_event':    return this.deleteEvent(input);
      case 'list_calendars':  return this.listCalendars();
      default:
        return { success: false, error: `Unknown Google Calendar action: ${action}` };
    }
  }

  // ------------------------------------------------------------------
  // Actions
  // ------------------------------------------------------------------

  private async listEvents(input: Record<string, unknown>): Promise<ToolResult> {
    const auth       = await this.getAuth();
    const calendar   = google.calendar({ version: 'v3', auth });
    const calendarId = (input['calendar_id'] as string) ?? 'primary';

    const response = await calendar.events.list({
      calendarId,
      timeMin: toRFC3339(input['time_min'] as string | undefined ?? new Date().toISOString()),
      timeMax: input['time_max'] ? toRFC3339(input['time_max'] as string) : undefined,
      maxResults: (input['max_results'] as number) ?? 10,
      singleEvents: true,
      orderBy: 'startTime',
    });

    const events = (response.data.items ?? []).map((e) => ({
      id: e.id,
      title: e.summary,
      description: e.description,
      start: e.start?.dateTime ?? e.start?.date,
      end: e.end?.dateTime ?? e.end?.date,
      attendees: e.attendees?.map((a) => a.email),
      location: e.location,
    }));

    return { success: true, data: events };
  }

  private async createEvent(input: Record<string, unknown>): Promise<ToolResult> {
    const auth       = await this.getAuth();
    const calendar   = google.calendar({ version: 'v3', auth });
    const calendarId = (input['calendar_id'] as string) ?? 'primary';

    const attendees = (input['attendees'] as string[] | undefined)?.map((e) => ({ email: e }));

    const event = await calendar.events.insert({
      calendarId,
      requestBody: {
        summary: input['title'] as string,
        description: input['description'] as string | undefined,
        start: { dateTime: toRFC3339(input['start_time'] as string), timeZone: 'UTC' },
        end:   { dateTime: toRFC3339(input['end_time'] as string),   timeZone: 'UTC' },
        attendees,
      },
    });

    return {
      success: true,
      data: {
        id: event.data.id,
        htmlLink: event.data.htmlLink,
        title: event.data.summary,
      },
    };
  }

  private async deleteEvent(input: Record<string, unknown>): Promise<ToolResult> {
    const auth       = await this.getAuth();
    const calendar   = google.calendar({ version: 'v3', auth });
    const calendarId = (input['calendar_id'] as string) ?? 'primary';
    const eventId    = input['event_id'] as string;

    if (!eventId) return { success: false, error: 'event_id is required' };

    await calendar.events.delete({ calendarId, eventId });
    return { success: true, data: { deleted: eventId } };
  }

  private async listCalendars(): Promise<ToolResult> {
    const auth     = await this.getAuth();
    const calendar = google.calendar({ version: 'v3', auth });

    const response = await calendar.calendarList.list();
    const items = (response.data.items ?? []).map((c) => ({
      id: c.id,
      summary: c.summary,
      primary: c.primary,
    }));

    return { success: true, data: items };
  }

  // ------------------------------------------------------------------
  // Auth
  // ------------------------------------------------------------------

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

    // Try loading saved token
    const tokenPath = path.resolve(config.tools.google.tokenPath);
    try {
      const token = JSON.parse(await fs.readFile(tokenPath, 'utf-8'));
      oAuth2Client.setCredentials(token);
    } catch {
      // Token not saved yet — in production, trigger OAuth flow
      logger.warn('[GoogleCalendar] No token.json found. Run `npm run authorize-google` to authorize.');
      throw new Error('Google OAuth not authorized. Please run the authorization flow.');
    }

    this.auth = oAuth2Client;
    return this.auth;
  }
}
