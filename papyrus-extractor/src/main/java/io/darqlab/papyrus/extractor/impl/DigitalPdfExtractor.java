package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DigitalPdfExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("application/pdf");

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            int pageCount = document.getNumberOfPages();
            StrikeoutAwarePDFStripper stripper = new StrikeoutAwarePDFStripper();

            List<String> pageTexts = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pageTexts.add(stripper.getText(document));
            }

            String fullContent = String.join("\n", pageTexts);
            return new ExtractedText(fullContent, pageTexts, pageCount, false);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from PDF: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }

    private static class StrikeoutAwarePDFStripper extends PDFTextStripper {

        // Tracks current non-stroking color during content stream processing
        private boolean currentlyRed = false;
        // Page height needed to convert PDF y (bottom-up) → screen y (top-down) to match TextPosition.getY()
        private float pageHeight = 0f;
        // Maps screen-space glyph coords (rounded) to whether they were rendered red
        private final Map<Long, Boolean> glyphRedMap = new HashMap<>();

        StrikeoutAwarePDFStripper() throws IOException { super(); }

        @Override
        public void processPage(PDPage page) throws IOException {
            pageHeight = page.getMediaBox().getHeight();
            glyphRedMap.clear();
            currentlyRed = false;
            super.processPage(page);
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            // Update color flag BEFORE calling super so showGlyph() (invoked by text operators) sees it
            String name = operator.getName();
            if ("rg".equals(name) && operands.size() >= 3) {
                try {
                    float r = ((COSNumber) operands.get(0)).floatValue();
                    float g = ((COSNumber) operands.get(1)).floatValue();
                    float b = ((COSNumber) operands.get(2)).floatValue();
                    currentlyRed = r > 0.5f && g < 0.4f && b < 0.4f;
                } catch (Exception ignored) {}
            } else if ("g".equals(name) || "k".equals(name) || "sc".equals(name) || "scn".equals(name)) {
                currentlyRed = false;
            }
            super.processOperator(operator, operands);
        }

        @Override
        protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
                throws IOException {
            boolean red = currentlyRed;
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Matrix at  = textRenderingMatrix.multiply(ctm);
            // Convert PDF y (origin bottom) → screen y (origin top) to match TextPosition.getY()
            float screenX = at.getTranslateX();
            float screenY = pageHeight - at.getTranslateY();
            super.showGlyph(textRenderingMatrix, font, code, displacement);
            glyphRedMap.put(coordKey(screenX, screenY), red);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (!textPositions.isEmpty()) {
                TextPosition first = textPositions.get(0);
                if (Boolean.TRUE.equals(glyphRedMap.get(coordKey(first.getX(), first.getY())))) {
                    super.writeString("[STRUCK OUT: " + text + "]", textPositions);
                    return;
                }
            }
            super.writeString(text, textPositions);
        }

        // Round to 2 decimal places to tolerate floating-point imprecision
        private static long coordKey(float x, float y) {
            return (long) Math.round(x * 100.0) * 1_000_000L + (long) Math.round(y * 100.0);
        }
    }
}
