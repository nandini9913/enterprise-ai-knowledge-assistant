package com.nandini.knowledgeassistant.ingestion.extraction;

import com.nandini.knowledgeassistant.document.DocumentType;
import com.nandini.knowledgeassistant.ingestion.NonRetryableProcessingException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts paragraphs and tables in document order. Paragraphs are separated by blank lines
 * so the chunker can prefer paragraph boundaries; table rows become pipe-separated lines.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public DocumentType supportedType() {
        return DocumentType.DOCX;
    }

    @Override
    public List<ExtractedPage> extract(byte[] content) {
        // POI's ZipSecureFile guards against zip bombs (compression-ratio and size limits).
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendBlock(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendBlock(text, tableText(table));
                }
            }
            return List.of(new ExtractedPage(null, text.toString()));
        } catch (IOException | RuntimeException ex) {
            throw new NonRetryableProcessingException("DOCX could not be parsed.", ex);
        }
    }

    private static String tableText(XWPFTable table) {
        StringBuilder rows = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            rows.append(row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(String::trim)
                    .collect(Collectors.joining(" | ")));
            rows.append('\n');
        }
        return rows.toString();
    }

    private static void appendBlock(StringBuilder text, String block) {
        if (block != null && !block.isBlank()) {
            text.append(block.strip()).append("\n\n");
        }
    }
}
