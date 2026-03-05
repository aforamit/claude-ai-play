package com.accounting.receipt.email;

import java.util.List;

/**
 * Immutable representation of an email with its extracted image attachments.
 *
 * @param id                provider-specific message ID (used for markAsRead)
 * @param subject           email subject line
 * @param from              sender address
 * @param receivedDate      date header value from the email
 * @param imageAttachments  list of image files attached to the email
 */
public record EmailMessage(
        String id,
        String subject,
        String from,
        String receivedDate,
        List<ImageAttachment> imageAttachments) {
}
