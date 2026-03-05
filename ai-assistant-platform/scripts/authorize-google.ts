/**
 * Run this script ONCE to authorize Google APIs (Calendar, Drive, Gmail).
 *
 * Usage:
 *   cd ai-assistant-platform
 *   PATH="C:/Workshop/Development/node-v18.20.8-win-x64:$PATH" npx ts-node scripts/authorize-google.ts
 *
 * What it does:
 * 1. Reads credentials.json from the project root.
 * 2. Starts a temporary local HTTP server on port 3001 to catch the OAuth redirect.
 * 3. Prints a URL — open it in your browser and approve access.
 * 4. Google redirects back to localhost:3001, the code is captured automatically.
 * 5. Exchanges the code for tokens and saves them to token.json.
 *
 * IMPORTANT — before running, add this redirect URI in Google Cloud Console:
 *   APIs & Services → Credentials → your OAuth client → Authorized redirect URIs
 *   Add:  http://localhost:3001/oauth2callback
 *   (Not needed if you chose "Desktop app" type — those allow localhost by default)
 */

import { google } from 'googleapis';
import fs from 'fs/promises';
import http from 'http';
import path from 'path';
import { URL } from 'url';

const SCOPES = [
  'https://www.googleapis.com/auth/calendar',
  'https://www.googleapis.com/auth/drive',
  'https://www.googleapis.com/auth/gmail.modify',
];

const CREDENTIALS_PATH = path.resolve('./credentials.json');
const TOKEN_PATH        = path.resolve('./token.json');
const CALLBACK_PORT     = 3001;
// Desktop App credentials allow any localhost port — no path needed
const REDIRECT_URI      = `http://localhost:${CALLBACK_PORT}`;

async function authorize(): Promise<void> {
  // ---- Read credentials ----
  let raw: string;
  try {
    raw = await fs.readFile(CREDENTIALS_PATH, 'utf-8');
  } catch {
    console.error(`\n❌ credentials.json not found at: ${CREDENTIALS_PATH}`);
    console.error('Download it from Google Cloud Console → APIs & Services → Credentials.');
    process.exit(1);
  }

  const creds = JSON.parse(raw) as {
    installed?: { client_id: string; client_secret: string; redirect_uris: string[] };
    web?:       { client_id: string; client_secret: string; redirect_uris: string[] };
  };

  const { client_id, client_secret } = creds.installed ?? creds.web!;
  const credType = creds.installed ? 'Desktop App' : 'Web App';
  console.log(`\n✅ Credentials loaded (type: ${credType})`);

  const oAuth2Client = new google.auth.OAuth2(client_id, client_secret, REDIRECT_URI);

  const authUrl = oAuth2Client.generateAuthUrl({
    access_type: 'offline',
    scope: SCOPES,
    prompt: 'consent',          // force refresh_token to be returned
    redirect_uri: REDIRECT_URI,
  });

  console.log('\n=============================================================');
  console.log('  Step 1: Open this URL in your browser');
  console.log('=============================================================\n');
  console.log(authUrl);
  console.log('\n=============================================================');
  console.log('  Step 2: Approve access in Google, then wait...');
  console.log('  (This window will complete automatically)');
  console.log('=============================================================\n');

  // ---- Temporary HTTP server to catch the redirect ----
  const code = await new Promise<string>((resolve, reject) => {
    const server = http.createServer((req, res) => {
      try {
        const url = new URL(req.url ?? '/', `http://localhost:${CALLBACK_PORT}`);
        const code = url.searchParams.get('code');
        const error = url.searchParams.get('error');

        // Ignore favicon requests
        if (req.url === '/favicon.ico') { res.writeHead(204); res.end(); return; }

        if (error) {
          res.writeHead(400, { 'Content-Type': 'text/html' });
          res.end(`<h2>❌ Authorization denied: ${error}</h2><p>You can close this tab.</p>`);
          server.close();
          reject(new Error(`OAuth error: ${error}`));
          return;
        }

        if (code) {
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(`
            <html><body style="font-family:sans-serif;text-align:center;padding:40px">
              <h2>✅ Authorization successful!</h2>
              <p>You can close this tab and return to the terminal.</p>
            </body></html>
          `);
          server.close();
          resolve(code);
        }
      } catch (err) {
        reject(err);
      }
    });

    server.on('error', (err: NodeJS.ErrnoException) => {
      if (err.code === 'EADDRINUSE') {
        console.error(`\n❌ Port ${CALLBACK_PORT} is already in use.`);
        console.error('Stop whatever is running on that port and try again.');
      }
      reject(err);
    });

    server.listen(CALLBACK_PORT, () => {
      console.log(`⏳ Waiting for Google to redirect to http://localhost:${CALLBACK_PORT} ...`);
    });
  });

  // ---- Exchange code for tokens ----
  console.log('\n🔄 Exchanging authorization code for tokens...');
  const { tokens } = await oAuth2Client.getToken({ code, redirect_uri: REDIRECT_URI });
  oAuth2Client.setCredentials(tokens);

  if (!tokens.refresh_token) {
    console.warn('\n⚠️  No refresh_token received.');
    console.warn('   If this keeps happening, go to https://myaccount.google.com/permissions');
    console.warn('   remove access for your app, then re-run this script.');
  }

  await fs.writeFile(TOKEN_PATH, JSON.stringify(tokens, null, 2));

  console.log(`\n✅ token.json saved to: ${TOKEN_PATH}`);
  console.log('   Google Calendar, Gmail, and Drive tools are now authorized.');
  console.log('   Restart the AI Assistant server to activate them.\n');
}

authorize().catch((err) => {
  console.error('\n❌ Authorization failed:', err.message ?? err);
  process.exit(1);
});
