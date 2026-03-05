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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
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
 * Authentication:
 *   - First run: opens browser for OAuth consent; token saved to disk.
 *   - Subsequent runs: token reloaded silently from disk.
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

    private final Gmail gmail;

    private GmailEmailService(Gmail gmail) {
        this.gmail = gmail;
    }

    /**
     * Factory method — builds an authenticated Gmail client.
     * On first use the user will be prompted to authorise via browser.
     */
    public static GmailEmailService create(AppConfig config) throws Exception {
        String credentialsPath = config.get("gmail.credentials.path", "credentials.json");
        String tokensPath      = config.get("gmail.tokens.path",       "tokens");
        int    oauthPort       = config.getInt("gmail.oauth.port",      8888);

        Path credFile = Paths.get(credentialsPath);
        if (!Files.exists(credFile)) {
            throw new FileNotFoundException(
                    "Gmail credentials file not found: " + credFile.toAbsolutePath()
                    + "\nDownload credentials.json from Google Cloud Console > APIs & Services > Credentials."
                    + "\nSee SETUP_GUIDE.md for step-by-step instructions.");
        }

        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

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
                .setPort(oauthPort)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        Gmail gmail = new Gmail.Builder(transport, JSON, credential)
                .setApplicationName(APP_NAME)
                .build();

        log.info("Gmail authenticated successfully");
        return new GmailEmailService(gmail);
    }

    @Override
    public List<EmailMessage> fetchLatestUnreadWithImages(int maxResults) throws Exception {
        log.info("Querying Gmail for up to {} unread emails with attachments...", maxResults);

        ListMessagesResponse response = gmail.users().messages().list(USER_ID)
                .setQ("is:unread has:attachment")
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
