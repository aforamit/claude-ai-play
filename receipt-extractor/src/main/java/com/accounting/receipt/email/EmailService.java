package com.accounting.receipt.email;

import java.util.List;

/**
 * Abstraction for reading emails with image attachments.
 *
 * Implement this interface to support other email providers
 * (Outlook, IMAP, etc.) without touching the pipeline logic.
 */
public interface EmailService {

    /**
     * Fetches the latest unread emails that have at least one image attachment.
     *
     * @param maxResults upper bound on how many emails to return
     * @return list of email messages; never null
     */
    List<EmailMessage> fetchLatestUnreadWithImages(int maxResults) throws Exception;

    /**
     * Marks an email as read so it is not processed again on the next run.
     *
     * @param messageId provider-specific message identifier
     */
    void markAsRead(String messageId) throws Exception;
}
