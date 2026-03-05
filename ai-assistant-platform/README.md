# AI Assistant Platform

A modular, extensible AI assistant automation platform for small businesses.
- **Interface**: WhatsApp (via Twilio) + REST API (for testing / web UIs)
- **AI Engine**: Anthropic Claude or OpenAI (swappable via `.env`)
- **Tools**: Notion, Google Calendar, Gmail, Google Drive, Calculator
- **Runs on**: Windows 10, Node.js 18+

---

## Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                     Interfaces (UI Layer)                     │
│   WhatsApp (Twilio) │  REST API  │  [Add Telegram, Slack…]    │
└───────────────────────────┬───────────────────────────────────┘
                            │ Message
                            ▼
┌───────────────────────────────────────────────────────────────┐
│                        Orchestrator                           │
│    Routes messages ▸ Session Manager ▸ AI Provider ▸ Tools    │
└───────────────────────────────────────────────────────────────┘
          ▲                 │                        │
          │           ┌─────▼──────┐        ┌───────▼───────┐
          │           │ AI Provider│        │ Tool Registry │
          │           │  (Claude / │        │  Notion       │
          │           │   OpenAI)  │        │  G. Calendar  │
          └───────────│            │        │  Gmail        │
           Response   └────────────┘        │  G. Drive     │
                                            │  Calculator   │
                                            └───────────────┘
```

**Key design principles:**
- Add a new **channel** → implement `IUserInterface`, register in `app.ts`
- Swap **AI provider** → set `PRIMARY_AI_PROVIDER=openai` in `.env`
- Add a new **tool** → implement `ITool`, call `toolRegistry.register()`

---

## Quick Start (Windows 10)

### Prerequisites
- [Node.js 18+](https://nodejs.org/) (LTS recommended)
- [ngrok](https://ngrok.com/download) (for WhatsApp webhooks)

### 1. Install dependencies

```powershell
cd ai-assistant-platform
npm install
```

### 2. Configure environment

```powershell
copy .env.example .env
notepad .env
```

At minimum, set `ANTHROPIC_API_KEY` (from [console.anthropic.com](https://console.anthropic.com)).

### 3. Start the server (dev mode)

```powershell
npm run dev
```

The REST API is immediately usable — no other setup needed for testing.

### 4. Test via REST API

```powershell
curl -X POST http://localhost:3000/api/chat `
  -H "Content-Type: application/json" `
  -d '{"userId":"me","message":"What is 123 * 456?"}'
```

Or open the admin panel to see platform info:
- `GET http://localhost:3000/admin/info`
- `GET http://localhost:3000/admin/tools`

---

## WhatsApp Setup (Twilio)

1. **Create a Twilio account** at [twilio.com](https://www.twilio.com) (free trial available)
2. **Enable WhatsApp Sandbox**: Console → Messaging → Try it out → Send a WhatsApp message
3. **Get credentials** from [Twilio Console](https://console.twilio.com/):
   - `TWILIO_ACCOUNT_SID`
   - `TWILIO_AUTH_TOKEN`
4. **Expose your local server** with ngrok:
   ```powershell
   ngrok http 3000
   ```
5. **Set the webhook URL** in Twilio sandbox settings:
   ```
   https://<your-ngrok-id>.ngrok.io/webhooks/whatsapp
   ```
6. **Join your sandbox** by sending the join code from your WhatsApp to the Twilio number.

Set the env vars and restart the server — WhatsApp is now live.

---

## Google Tools Setup (Calendar, Gmail, Drive)

### Step 1: Google Cloud Project

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a new project (e.g. "AI Assistant")
3. Enable these APIs:
   - Google Calendar API
   - Gmail API
   - Google Drive API
4. Go to **APIs & Services → Credentials**
5. Create **OAuth 2.0 Client ID** → Application type: **Desktop App**
6. Download the JSON → save as `credentials.json` in the project root

### Step 2: Authorize

```powershell
npm run authorize-google
```

- Opens a browser link — sign in with your Google account
- Paste the code back into the terminal
- `token.json` is saved automatically

The Google tools are now available to the AI.

---

## Notion Setup

1. Go to [notion.so/my-integrations](https://www.notion.so/my-integrations) → Create integration
2. Copy the **Internal Integration Token** → set `NOTION_API_KEY` in `.env`
3. In Notion, open each database/page you want the AI to access → **Share → Invite** your integration
4. (Optional) Set `NOTION_DEFAULT_DATABASE_ID` for the default database for new pages

---

## Swapping the AI Provider

Edit `.env`:
```
PRIMARY_AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
```

Restart the server. No code changes needed.

---

## Adding a New Interface (e.g. Telegram)

1. Create `src/interfaces/telegram/TelegramAdapter.ts` implementing `IUserInterface`
2. In `src/app.ts`:
```typescript
const telegramAdapter = new TelegramAdapter();
interfaces.set(telegramAdapter.id, telegramAdapter);
```
That's it. The Orchestrator will automatically route messages.

---

## Adding a New Tool (e.g. Slack)

1. Create `src/tools/slack/SlackTool.ts` implementing `ITool`
2. In `src/app.ts`:
```typescript
toolRegistry.register(new SlackTool());
```
The AI will automatically discover and use the tool via its JSON schema definition.

---

## Production Deployment (Windows)

For a lightweight Windows deployment without Docker:

1. Build:
   ```powershell
   npm run build
   ```
2. Run as a Windows service using [NSSM](https://nssm.cc/):
   ```powershell
   nssm install AIAssistant "C:\path\to\node.exe" "C:\Workshop\...\dist\app.js"
   nssm start AIAssistant
   ```
3. Use a permanent tunnel (ngrok paid, Cloudflare Tunnel, or expose port via router).

### Environment Variables for Production
Set them via Windows System Properties → Environment Variables, or use a `.env` file in the project root.

---

## Project Structure

```
ai-assistant-platform/
├── src/
│   ├── app.ts                          # Entry point — wire everything together
│   ├── config/config.ts                # All configuration from .env
│   ├── types/index.ts                  # Core domain types
│   ├── core/
│   │   ├── logger.ts                   # Winston logger
│   │   ├── Orchestrator.ts             # Message routing hub
│   │   └── SessionManager.ts          # Per-user conversation history
│   ├── interfaces/                     # UI channel adapters
│   │   ├── IUserInterface.ts           # Contract
│   │   ├── whatsapp/TwilioWhatsAppAdapter.ts
│   │   └── rest/RestAdapter.ts
│   ├── ai/                             # AI provider adapters
│   │   ├── IAIProvider.ts              # Contract
│   │   ├── ClaudeProvider.ts           # Anthropic Claude
│   │   └── OpenAIProvider.ts           # OpenAI
│   └── tools/                          # Tool integrations
│       ├── ITool.ts                    # Contract
│       ├── ToolRegistry.ts             # Central registry
│       ├── calculator/CalculatorTool.ts
│       ├── notion/NotionTool.ts
│       ├── googleCalendar/GoogleCalendarTool.ts
│       ├── gmail/GmailTool.ts
│       └── googleDrive/GoogleDriveTool.ts
├── scripts/
│   └── authorize-google.ts             # One-time Google OAuth flow
├── .env.example
├── package.json
└── tsconfig.json
```
