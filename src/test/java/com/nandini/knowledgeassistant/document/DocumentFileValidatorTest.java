package com.nandini.knowledgeassistant.document;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.DocumentProperties;
import com.nandini.knowledgeassistant.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileValidatorTest {

    private final DocumentFileValidator validator =
            new DocumentFileValidator(new DocumentProperties(DataSize.ofKilobytes(100), 100));

    @Test
    void acceptsRealPdfAndDocx() {
        var pdf = validator.validate(file("report.pdf", TestDocuments.pdf(List.of("hello"))));
        var docx = validator.validate(file("notes.DOCX", TestDocuments.docx(List.of("hello"))));

        assertThat(pdf.type()).isEqualTo(DocumentType.PDF);
        assertThat(pdf.checksumSha256()).hasSize(64);
        assertThat(docx.type()).isEqualTo(DocumentType.DOCX);
    }

    @Test
    void rejectsZipThatIsNotAWordDocument() {
        byte[] zipWithoutWordPart = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        assertThatThrownBy(() -> validator.validate(file("fake.docx", zipWithoutWordPart)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.DOCUMENT_TYPE_NOT_SUPPORTED);
    }

    @Test
    void rejectsFilesOverTheSizeLimit() {
        byte[] big = new byte[101 * 1024];
        assertThatThrownBy(() -> validator.validate(file("big.pdf", big)))
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.DOCUMENT_TOO_LARGE);
    }

    @Test
    void sanitizesPathTraversalAndControlCharactersInFilenames() {
        assertThat(DocumentFileValidator.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(DocumentFileValidator.sanitizeFilename("C:\\Users\\me\\plan.docx")).isEqualTo("plan.docx");
        assertThat(DocumentFileValidator.sanitizeFilename("bad\u0000name?.pdf")).isEqualTo("bad_name_.pdf");
        assertThat(DocumentFileValidator.sanitizeFilename(null)).isEqualTo("document");
        assertThat(DocumentFileValidator.sanitizeFilename("a".repeat(300) + ".pdf")).hasSize(200).endsWith(".pdf");
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }
}
