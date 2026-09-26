package com.nandini.knowledgeassistant.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Generates real PDF and DOCX files in memory so tests need no binary fixtures. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** One PDF page per entry; each page's text is written as wrapped lines. */
    public static byte[] pdf(List<String> pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String pageText : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 11);
                    stream.setLeading(14);
                    stream.newLineAtOffset(50, 740);
                    for (String line : wrap(pageText, 90)) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] docx(List<String> paragraphs, List<List<String>> tableRows) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            if (!tableRows.isEmpty()) {
                XWPFTable table = document.createTable(tableRows.size(), tableRows.get(0).size());
                for (int r = 0; r < tableRows.size(); r++) {
                    for (int c = 0; c < tableRows.get(r).size(); c++) {
                        table.getRow(r).getCell(c).setText(tableRows.get(r).get(c));
                    }
                }
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] docx(List<String> paragraphs) {
        return docx(paragraphs, List.of());
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
