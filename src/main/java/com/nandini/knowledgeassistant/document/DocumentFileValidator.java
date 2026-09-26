package com.nandini.knowledgeassistant.document;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.DocumentProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates uploads before anything is stored. The declared filename and content type are
 * client-controlled, so the file's actual bytes ("magic numbers") must agree with the extension.
 */
@Component
public class DocumentFileValidator {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final int MAX_ZIP_ENTRIES_SCANNED = 1000;
    private static final int MAX_FILENAME_LENGTH = 200;

    private final DocumentProperties properties;

    public DocumentFileValidator(DocumentProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.DOCUMENT_EMPTY, "The uploaded file is empty.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new ApiException(ErrorCode.DOCUMENT_TOO_LARGE,
                    "Documents must be at most " + properties.maxFileSize().toMegabytes() + " MB.");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        DocumentType type = DocumentType.fromFilename(filename).orElseThrow(this::unsupported);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read.", ex);
        }
        boolean contentMatches = switch (type) {
            case PDF -> startsWith(bytes, PDF_MAGIC);
            case DOCX -> startsWith(bytes, ZIP_MAGIC) && containsWordDocument(bytes);
        };
        if (!contentMatches) {
            throw new ApiException(ErrorCode.DOCUMENT_TYPE_NOT_SUPPORTED,
                    "File content does not match a valid " + type.name() + " document.");
        }
        return new ValidatedFile(filename, type, bytes, sha256(bytes));
    }

    /** Strips any path components and control characters a client might send. */
    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|*?:]", "_").trim();
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }
        return name.isEmpty() ? "document" : name;
    }

    private ApiException unsupported() {
        return new ApiException(ErrorCode.DOCUMENT_TYPE_NOT_SUPPORTED, "Only PDF and DOCX documents are supported.");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** A DOCX is a ZIP container that must contain the main document part. */
    private static boolean containsWordDocument(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int scanned = 0;
            while ((entry = zip.getNextEntry()) != null && scanned++ < MAX_ZIP_ENTRIES_SCANNED) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record ValidatedFile(String filename, DocumentType type, byte[] content, String checksumSha256) {
    }
}
