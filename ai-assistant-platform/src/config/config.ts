import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(process.cwd(), '.env') });

function required(key: string): string {
  const value = process.env[key];
  if (!value) throw new Error(`Missing required environment variable: ${key}`);
  return value;
}

function optional(key: string, fallback: string): string {
  return process.env[key] ?? fallback;
}

/**
 * Notion IDs can be pasted as a full URL slug like "PageName-abc123def456..."
 * This extracts just the 32-char hex UUID at the end.
 */
function notionId(key: string): string {
  const raw = process.env[key] ?? '';
  // Match last 32 hex chars (with optional dashes forming a UUID)
  const match = raw.replace(/-/g, '').match(/([0-9a-f]{32})$/i);
  return match ? match[1] : raw;
}

export const config = {
  server: {
    port: parseInt(optional('PORT', '3000'), 10),
    nodeEnv: optional('NODE_ENV', 'development'),
  },

  ai: {
    primaryProvider: optional('PRIMARY_AI_PROVIDER', 'claude') as 'claude' | 'openai',

    claude: {
      apiKey: optional('ANTHROPIC_API_KEY', ''),
      model: optional('CLAUDE_MODEL', 'claude-sonnet-4-6'),
    },

    openai: {
      apiKey: optional('OPENAI_API_KEY', ''),
      model: optional('OPENAI_MODEL', 'gpt-4o'),
    },

    defaults: {
      maxTokens: 4096,
      temperature: 0.7,
    },
  },

  whatsapp: {
    twilio: {
      accountSid: optional('TWILIO_ACCOUNT_SID', ''),
      authToken: optional('TWILIO_AUTH_TOKEN', ''),
      whatsappNumber: optional('TWILIO_WHATSAPP_NUMBER', 'whatsapp:+14155238886'),
    },
  },

  tools: {
    notion: {
      apiKey: optional('NOTION_API_KEY', ''),
      defaultDatabaseId: notionId('NOTION_DEFAULT_DATABASE_ID'),
    },
    google: {
      credentialsPath: optional('GOOGLE_CREDENTIALS_PATH', './credentials.json'),
      tokenPath: optional('GOOGLE_TOKEN_PATH', './token.json'),
    },
  },

  session: {
    ttlHours: parseInt(optional('SESSION_TTL_HOURS', '24'), 10),
    maxHistory: parseInt(optional('MAX_CONVERSATION_HISTORY', '50'), 10),
  },
} as const;

export type Config = typeof config;
