package com.accounting.receipt.pipeline;

import com.accounting.receipt.email.EmailMessage;
import com.accounting.receipt.email.EmailService;
import com.accounting.receipt.email.ImageAttachment;
import com.accounting.receipt.export.DataExporter;
import com.accounting.receipt.model.DepositRecord;
import com.accounting.receipt.ocr.OcrEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingPipelineTest {

    @Mock private EmailService emailService;
    @Mock private OcrEngine    ocrEngine;
    @Mock private DataExporter exporter;

    private ReceiptProcessingPipeline pipeline(boolean markAsRead) {
        return new ReceiptProcessingPipeline(emailService, ocrEngine, exporter, 10, markAsRead);
    }

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    void returnsZeroAndSkipsExportWhenNoEmailsFound() throws Exception {
        when(emailService.fetchLatestUnreadWithImages(10)).thenReturn(List.of());

        int result = pipeline(true).process();

        assertEquals(0, result);
        verifyNoInteractions(ocrEngine, exporter);
    }

    @Test
    void extractsAndExportsSingleRecord() throws Exception {
        ImageAttachment img  = attachment("receipt.jpg", "image/jpeg");
        EmailMessage    mail = email("msg001", List.of(img));
        DepositRecord   rec  = new DepositRecord("1101", "01-15-2024", "01-16-2024", "*****4321", 1250.75, 1250.75);

        when(emailService.fetchLatestUnreadWithImages(10)).thenReturn(List.of(mail));
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString())).thenReturn(List.of(rec));
        when(exporter.export(List.of(rec))).thenReturn(1);

        int result = pipeline(true).process();

        assertEquals(1, result);
        verify(exporter).export(List.of(rec));
    }

    @Test
    void marksEmailAsReadAfterSuccessfulExtraction() throws Exception {
        ImageAttachment img  = attachment("r.jpg", "image/jpeg");
        EmailMessage    mail = email("msg002", List.of(img));
        DepositRecord   rec  = new DepositRecord("1001", "01-01-2024", "", "*****1111", 100.0, 100.0);

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString())).thenReturn(List.of(rec));
        when(exporter.export(any())).thenReturn(1);

        pipeline(true).process();

        verify(emailService).markAsRead("msg002");
    }

    @Test
    void doesNotMarkAsReadWhenMarkAsReadIsFalse() throws Exception {
        ImageAttachment img  = attachment("r.jpg", "image/jpeg");
        EmailMessage    mail = email("msg003", List.of(img));
        DepositRecord   rec  = new DepositRecord("1001", "01-01-2024", "", "*****1111", 100.0, 100.0);

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString())).thenReturn(List.of(rec));
        when(exporter.export(any())).thenReturn(1);

        pipeline(false).process();

        verify(emailService, never()).markAsRead(anyString());
    }

    @Test
    void aggregatesRecordsFromMultipleAttachmentsInSingleEmail() throws Exception {
        ImageAttachment img1 = attachment("r1.jpg", "image/jpeg");
        ImageAttachment img2 = attachment("r2.png", "image/png");
        EmailMessage    mail = email("msg004", List.of(img1, img2));

        DepositRecord rec1 = new DepositRecord("1001", "01-10-2024", "01-11-2024", "*****2222", 1250.0, 500.0);
        DepositRecord rec2 = new DepositRecord("1002", "01-10-2024", "",            "*****3333",  750.0, 750.0);

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        // Sequential: first call returns rec1, second returns rec2
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString()))
                .thenReturn(List.of(rec1))
                .thenReturn(List.of(rec2));
        when(exporter.export(any())).thenReturn(2);

        int result = pipeline(true).process();

        assertEquals(2, result);
    }

    @Test
    void aggregatesRecordsAcrossMultipleEmails() throws Exception {
        ImageAttachment img1  = attachment("a.jpg", "image/jpeg");
        ImageAttachment img2  = attachment("b.jpg", "image/jpeg");
        EmailMessage    mail1 = email("msg005", List.of(img1));
        EmailMessage    mail2 = email("msg006", List.of(img2));

        DepositRecord rec1 = new DepositRecord("1010", "02-01-2024", "", "*****4444", 300.0, 300.0);
        DepositRecord rec2 = new DepositRecord("1020", "02-02-2024", "", "*****5555", 400.0, 400.0);

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail1, mail2));
        // Sequential: first call (mail1's attachment) returns rec1, second (mail2's) returns rec2
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString()))
                .thenReturn(List.of(rec1))
                .thenReturn(List.of(rec2));
        when(exporter.export(any())).thenReturn(2);

        int result = pipeline(true).process();

        assertEquals(2, result);
    }

    // ── Error resilience ─────────────────────────────────────────────────────

    @Test
    void continuesProcessingWhenOcrFailsForOneAttachment() throws Exception {
        ImageAttachment bad  = attachment("corrupt.jpg", "image/jpeg");
        ImageAttachment good = attachment("ok.jpg",      "image/jpeg");
        EmailMessage    mail = email("msg007", List.of(bad, good));
        DepositRecord   rec  = new DepositRecord("1030", "03-01-2024", "", "*****6666", 200.0, 200.0);

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        // Sequential: first call (bad) throws, second call (good) returns rec
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("API timeout"))
                .thenReturn(List.of(rec));
        when(exporter.export(any())).thenReturn(1);

        int result = pipeline(true).process();

        assertEquals(1, result);
    }

    @Test
    void returnsZeroWhenAllAttachmentsFailOcr() throws Exception {
        ImageAttachment img  = attachment("bad.jpg", "image/jpeg");
        EmailMessage    mail = email("msg008", List.of(img));

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Upstream error"));

        int result = pipeline(true).process();

        assertEquals(0, result);
        verify(exporter, never()).export(any());
    }

    @Test
    void doesNotMarkEmailAsReadWhenNoRecordsExtracted() throws Exception {
        ImageAttachment img  = attachment("empty.jpg", "image/jpeg");
        EmailMessage    mail = email("msg009", List.of(img));

        when(emailService.fetchLatestUnreadWithImages(anyInt())).thenReturn(List.of(mail));
        when(ocrEngine.extractDepositRecords(any(), anyString(), anyString())).thenReturn(List.of());

        pipeline(true).process();

        verify(emailService, never()).markAsRead(anyString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ImageAttachment attachment(String filename, String mime) {
        return new ImageAttachment(filename, mime, new byte[]{1, 2, 3});
    }

    private EmailMessage email(String id, List<ImageAttachment> attachments) {
        return new EmailMessage(id, "Daily Deposits", "manager@store.com",
                "Mon, 15 Jan 2024 09:00:00 +0000", attachments);
    }
}
