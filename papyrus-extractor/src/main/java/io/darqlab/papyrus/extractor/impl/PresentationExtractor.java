package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts text from PPTX presentations using Apache POI.
 * Each slide becomes one page; text shapes are concatenated in order.
 */
public class PresentationExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            List<String> slideTexts = new ArrayList<>();
            List<XSLFSlide> slides  = ppt.getSlides();

            for (XSLFSlide slide : slides) {
                List<String> parts = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = extractShapeText(textShape);
                        if (!text.isBlank()) {
                            parts.add(text);
                        }
                    }
                }
                if (!parts.isEmpty()) {
                    slideTexts.add(String.join("\n", parts));
                }
            }

            String content = String.join("\n\n", slideTexts);
            List<String> pages = slideTexts.isEmpty() ? List.of() : slideTexts;
            return new ExtractedText(content, pages, slides.size(), false);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from presentation: " + filename, e);
        }
    }

    private String extractShapeText(XSLFTextShape shape) {
        StringBuilder sb = new StringBuilder();
        for (XSLFTextParagraph para : shape.getTextParagraphs()) {
            StringBuilder line = new StringBuilder();
            for (XSLFTextRun run : para.getTextRuns()) {
                String text = run.getRawText();
                if (text == null || text.isEmpty()) continue;
                if (run.isStrikethrough()) {
                    line.append("[STRUCK OUT: ").append(text).append("]");
                } else {
                    line.append(text);
                }
            }
            String lineStr = line.toString().strip();
            if (!lineStr.isBlank()) sb.append(lineStr).append("\n");
        }
        return sb.toString().strip();
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
