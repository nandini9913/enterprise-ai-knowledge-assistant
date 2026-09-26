package com.nandini.knowledgeassistant.ingestion.extraction;

import com.nandini.knowledgeassistant.config.DocumentProperties;
import com.nandini.knowledgeassistant.ingestion.NonRetryableProcessingException;
import com.nandini.knowledgeassistant.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractorTest {

    private final PdfTextExtractor pdf = new PdfTextExtractor(new DocumentProperties(DataSize.ofMegabytes(20), 3));
    private final DocxTextExtractor docx = new DocxTextExtractor();

    @Test
    void pdfTextIsExtractedPerPage() {
        List<ExtractedPage> pages = pdf.extract(TestDocuments.pdf(List.of("Alpha page", "Beta page")));

        assertThat(pages).extracting(ExtractedPage::pageNumber).containsExactly(1, 2);
        assertThat(pages.get(0).text()).contains("Alpha page");
        assertThat(pages.get(1).text()).contains("Beta page");
    }

    @Test
    void pdfOverThePageLimitIsRejectedPermanently() {
        byte[] fourPages = TestDocuments.pdf(List.of("1", "2", "3", "4"));
        assertThatThrownBy(() -> pdf.extract(fourPages))
                .isInstanceOf(NonRetryableProcessingException.class)
                .hasMessageContaining("limit is 3");
    }

    @Test
    void corruptPdfIsRejectedPermanently() {
        assertThatThrownBy(() -> pdf.extract("%PDF-garbage".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(NonRetryableProcessingException.class);
    }

    @Test
    void docxParagraphsAndTablesAreExtractedInOrder() {
        byte[] file = TestDocuments.docx(List.of("Intro paragraph.", "Second paragraph."),
                List.of(List.of("Name", "Value"), List.of("Limit", "42")));

        ExtractedPage page = docx.extract(file).get(0);

        assertThat(page.pageNumber()).isNull();
        assertThat(page.text()).contains("Intro paragraph.\n\nSecond paragraph.")
                .contains("Name | Value")
                .contains("Limit | 42");
        assertThat(page.text().indexOf("Intro")).isLessThan(page.text().indexOf("Limit"));
    }
}
