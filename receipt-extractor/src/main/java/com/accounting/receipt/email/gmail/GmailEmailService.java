package com.accounting.receipt.email.gmail;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.email.EmailMessage;
import com.accounting.receipt.email.EmailService;
import com.accounting.receipt.email.ImageAttachment;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.SendAs;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gmail implementation of {@link EmailService}.
 *
 * Authentication (two modes, selected automatically):
 *   - Service account with domain-wide delegation: when {@code gmail.service-account.key-path}
 *     is set and the file exists. Impersonates {@code gmail.impersonate-user} — no browser needed.
 *   - Browser OAuth (fallback): when no service account key is configured. Opens a browser
 *     on the first run; token is cached to disk for subsequent silent runs.
 *
 * Required setup: see SETUP_GUIDE.md
 */
public class GmailEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailService.class);

    private static final String APP_NAME    = "Receipt-Extractor";
    private static final GsonFactory JSON   = GsonFactory.getDefaultInstance();
    private static final String USER_ID     = "me";

    /** Gmail API scopes needed. MODIFY allows us to mark emails as read. */
    private static final List<String> SCOPES = List.of(
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_MODIFY
    );

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png",
            "image/gif", "image/tiff", "image/webp", "image/bmp"
    );

    private final Gmail        gmail;
    private final List<String> accessibleAddresses;
    private final List<String> recipientFilters;

    private GmailEmailService(Gmail gmail, List<String> accessibleAddresses, List<String> recipientFilters) {
        this.gmail               = gmail;
        this.accessibleAddresses = accessibleAddresses;
        this.recipientFilters    = recipientFilters;
    }

    /**
     * Factory method — builds an authenticated Gmail client.
     *
     * Selects auth mode based on config:
     *   - If {@code gmail.service-account.key-path} points to an existing file →
     *     service account with domain-wide delegation (impersonates {@code gmail.impersonate-user}).
     *   - Otherwise → browser OAuth flow (token cached to disk after first login).
     */
    public static GmailEmailService create(AppConfig config) throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

        String serviceAccountKeyPath = config.get("gmail.service-account.key-path", "");
        Path   serviceAccountKeyFile = Paths.get(serviceAccountKeyPath);

        Gmail gmail;
        if (!serviceAccountKeyPath.isBlank() && Files.exists(serviceAccountKeyFile)) {
            // ── Service account with domain-wide delegation ──────────────
            String impersonateUser = config.get("gmail.impersonate-user", "");
            if (impersonateUser.isBlank()) {
                throw new IllegalStateException(
                        "gmail.impersonate-user must be set when using a service account key.");
            }
            log.info("Auth mode      : Service account (impersonating {})", impersonateUser);
            try (InputStream is = Files.newInputStream(serviceAccountKeyFile)) {
                ServiceAccountCredentials credentials = (ServiceAccountCredentials)
                        ServiceAccountCredentials.fromStream(is)
                                .createDelegated(impersonateUser)
                                .createScoped(SCOPES);
                HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
                gmail = new Gmail.Builder(transport, JSON, requestInitializer)
                        .setApplicationName(APP_NAME)
                        .build();
            }
        } else {
            // ── Browser OAuth (fallback) ──────────────────────────────────
            String credentialsPath = config.get("gmail.credentials.path", "credentials.json");
            String tokensPath      = config.get("gmail.tokens.path",       "tokens");
            int    oauthPort       = config.getInt("gmail.oauth.port",      8888);

            Path credFile = Paths.get(credentialsPath);
            if (!Files.exists(credFile)) {
                throw new FileNotFoundException(
                        "Gmail credentials file not found: " + credFile.toAbsolutePath()
                        + "\nSet gmail.service-account.key-path for service account auth, or"
                        + "\ndownload credentials.json from Google Cloud Console > APIs & Services > Credentials.");
            }
            log.info("Auth mode      : Browser OAuth ({})", credentialsPath);
            GoogleClientSecrets clientSecrets;
            try (var is = Files.newInputStream(credFile)) {
                clientSecrets = GoogleClientSecrets.load(JSON, new InputStreamReader(is));
            }
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport, JSON, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(tokensPath)))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setPort(oauthPort).build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            gmail = new Gmail.Builder(transport, JSON, credential)
                    .setApplicationName(APP_NAME)
                    .build();
        }

        List<String> addresses = discoverAccessibleAddresses(gmail);
        List<String> recipientFilters = Arrays.stream(config.get("email.recipient-filter", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        log.info("Gmail authenticated successfully");
        log.info("Accessible email address(es): {}", addresses.size());
        for (String addr : addresses) {
            log.info("  - {}", addr);
        }
        if (!recipientFilters.isEmpty()) {
            log.info("Recipient filter(s): {}", recipientFilters.size());
            for (String f : recipientFilters) {
                log.info("  - to:{}", f);
            }
        }
        return new GmailEmailService(gmail, addresses, recipientFilters);
    }

    /**
     * Queries the Gmail send-as settings to enumerate every email address accessible
     * through the authenticated account (primary address + all configured aliases/delegates).
     * Falls back to the profile email address if the settings API call fails.
     */
    private static List<String> discoverAccessibleAddresses(Gmail gmail) {
        try {
            List<SendAs> sendAsList = gmail.users().settings().sendAs().list(USER_ID).execute().getSendAs();
            if (sendAsList != null && !sendAsList.isEmpty()) {
                return sendAsList.stream()
                        .map(SendAs::getSendAsEmail)
                        .filter(addr -> addr != null && !addr.isBlank())
                        .sorted()
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve send-as addresses ({}); falling back to profile email.", e.getMessage());
        }

        // Fallback: use the primary address from the profile
        try {
            String primary = gmail.users().getProfile(USER_ID).execute().getEmailAddress();
            return primary != null ? List.of(primary) : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not retrieve profile email address: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<EmailMessage> fetchLatestUnreadWithImages(int maxResults) throws Exception {
        log.info("Querying Gmail for up to {} unread emails with attachments...", maxResults);

        String toClause = recipientFilters.isEmpty() ? "" :
                " {" + recipientFilters.stream().map(a -> "to:" + a).collect(java.util.stream.Collectors.joining(" ")) + "}";
        String query = "is:unread has:attachment" + toClause;

        ListMessagesResponse response = gmail.users().messages().list(USER_ID)
                .setQ(query)
                .setMaxResults((long) maxResults)
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            log.info("No matching emails found.");
            return Collections.emptyList();
        }

        List<EmailMessage> result = new ArrayList<>();
        for (Message msg : messages) {
            try {
                EmailMessage email = buildEmailMessage(msg.getId());
                if (email != null && !email.imageAttachments().isEmpty()) {
                    result.add(email);
                    log.info("  Email '{}' has {} image attachment(s)",
                            email.subject(), email.imageAttachments().size());
                }
            } catch (Exception e) {
                log.warn("  Skipping email {}: {}", msg.getId(), e.getMessage());
            }
        }

        log.info("Found {} email(s) with image attachments.", result.size());
        return result;
    }

    private EmailMessage buildEmailMessage(String messageId) throws Exception {
        Message message = gmail.users().messages().get(USER_ID, messageId)
                .setFormat("full")
                .execute();

        String subject = header(message, "Subject");
        String from    = header(message, "From");
        String date    = header(message, "Date");

        List<ImageAttachment> images = new ArrayList<>();
        collectImages(message.getPayload(), messageId, images);

        if (images.isEmpty()) return null;

        return new EmailMessage(messageId, subject, from, date, images);
    }

    private String header(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) return "";
        return message.getPayload().getHeaders().stream()
                .filter(h -> h.getName().equalsIgnoreCase(name))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    /** Recursively walks MIME parts and collects image attachments. */
    private void collectImages(MessagePart part, String msgId, List<ImageAttachment> out) throws Exception {
        if (part == null) return;

        String mime = part.getMimeType() != null ? part.getMimeType().toLowerCase() : "";
        if (IMAGE_MIME_TYPES.contains(mime)) {
            byte[] data = fetchBytes(part, msgId);
            if (data != null && data.length > 0) {
                String filename = part.getFilename() != null && !part.getFilename().isBlank()
                        ? part.getFilename()
                        : "image_" + out.size() + guessExtension(mime);
                out.add(new ImageAttachment(filename, mime, data));
                log.debug("    Attachment: {} ({} bytes)", filename, data.length);
            }
        }

        // Recurse into multipart/* containers
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                collectImages(child, msgId, out);
            }
        }
    }

    private byte[] fetchBytes(MessagePart part, String msgId) throws Exception {
        MessagePartBody body = part.getBody();
        if (body == null) return null;

        if (body.getAttachmentId() != null) {
            // Separate attachment — must be fetched individually
            MessagePartBody attachment = gmail.users().messages().attachments()
                    .get(USER_ID, msgId, body.getAttachmentId())
                    .execute();
            return Base64.getUrlDecoder().decode(attachment.getData());
        }

        if (body.getData() != null) {
            // Inline content embedded directly in the message payload
            return Base64.getUrlDecoder().decode(body.getData());
        }

        return null;
    }

    private String guessExtension(String mime) {
        return switch (mime) {
            case "image/png"  -> ".png";
            case "image/gif"  -> ".gif";
            case "image/tiff" -> ".tiff";
            case "image/webp" -> ".webp";
            case "image/bmp"  -> ".bmp";
            default           -> ".jpg";
        };
    }

    @Override
    public void markAsRead(String messageId) throws Exception {
        ModifyMessageRequest req = new ModifyMessageRequest()
                .setRemoveLabelIds(List.of("UNREAD"));
        gmail.users().messages().modify(USER_ID, messageId, req).execute();
        log.debug("Marked email {} as read.", messageId);
    }
}
