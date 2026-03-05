package com.accounting.receipt.email;

/**
 * An image file extracted from an email attachment or inline part.
 *
 * @param filename original filename (e.g., "deposit_jan15.jpg")
 * @param mimeType MIME type (e.g., "image/jpeg", "image/png")
 * @param data     raw image bytes
 */
public record ImageAttachment(String filename, String mimeType, byte[] data) {
}
