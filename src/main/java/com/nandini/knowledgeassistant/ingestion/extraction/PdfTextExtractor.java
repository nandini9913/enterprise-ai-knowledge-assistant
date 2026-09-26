package com.nandini.knowledgeassistant.ingestion.extraction;

import com.nandini.knowledgeassistant.config.DocumentProperties;
import com.nandini.knowledgeassistant.document.DocumentType;
import com.nandini.knowledgeassistant.ingestion.NonRetryableProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Extracts text page by page so every chunk can cite its page number. */
@Component
public class PdfTextExtractor implements TextExtractor {

    private final DocumentProperties properties;

    public PdfTextExtractor(DocumentProperties properties) {
        this.properties = properties;
    }

    @Override
    public DocumentType supportedType() {
        return DocumentType.PDF;
    }

    @Override
    public List<ExtractedPage> extract(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            int pageCount = pdf.getNumberOfPages();
            if (pageCount > properties.maxPages()) {
                throw new NonRetryableProcessingException(
                        "PDF has " + pageCount + " pages; the limit is " + properties.maxPages() + ".");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<ExtractedPage> pages = new ArrayList<>(pageCount);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedPage(page, stripper.getText(pdf)));
            }
            return pages;
        } catch (InvalidPasswordException ex) {
            throw new NonRetryableProcessingException("PDF is password protected.", ex);
        } catch (IOException ex) {
            throw new NonRetryableProcessingException("PDF could not be parsed.", ex);
        }
    }
}
