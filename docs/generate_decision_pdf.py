#!/usr/bin/env python3
"""Generate the Archive Filename Convention decision document as PDF."""

import sys

# Work around broken system cryptography package
for mod in [
    "cryptography", "cryptography.hazmat",
    "cryptography.hazmat.primitives",
    "cryptography.hazmat.primitives.serialization",
    "cryptography.hazmat.primitives.serialization.pkcs12",
]:
    sys.modules.setdefault(mod, type(sys)(mod))

from fpdf import FPDF


class DecisionPDF(FPDF):
    def header(self):
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(100, 100, 100)
        self.cell(0, 8, "Papyrus - Archive Feature Decisions", align="R")
        self.ln(12)

    def footer(self):
        self.set_y(-15)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(150, 150, 150)
        self.cell(0, 10, f"Page {self.page_no()}/{{nb}}", align="C")

    def section_title(self, title):
        self.set_font("Helvetica", "B", 14)
        self.set_text_color(30, 60, 120)
        self.cell(0, 10, title)
        self.ln(8)
        self.set_draw_color(30, 60, 120)
        self.line(self.l_margin, self.get_y(), self.w - self.r_margin, self.get_y())
        self.ln(6)

    def sub_title(self, title):
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(50, 50, 50)
        self.cell(0, 8, title)
        self.ln(7)

    def body_text(self, text):
        self.set_font("Helvetica", "", 10)
        self.set_text_color(40, 40, 40)
        self.multi_cell(0, 5.5, text)
        self.ln(3)

    def code_block(self, text):
        self.set_font("Courier", "", 9)
        self.set_fill_color(245, 245, 245)
        self.set_text_color(50, 50, 50)
        x = self.get_x()
        y = self.get_y()
        lines = text.split("\n")
        block_h = len(lines) * 5 + 6
        self.rect(x, y, self.w - self.l_margin - self.r_margin, block_h, "F")
        self.ln(3)
        for line in lines:
            self.cell(0, 5, "  " + line)
            self.ln(5)
        self.ln(4)

    def table_row(self, cells, widths, bold=False, header=False):
        style = "B" if bold or header else ""
        self.set_font("Helvetica", style, 9)
        if header:
            self.set_fill_color(30, 60, 120)
            self.set_text_color(255, 255, 255)
        else:
            self.set_fill_color(255, 255, 255)
            self.set_text_color(40, 40, 40)
        row_h = 7
        for i, cell in enumerate(cells):
            self.cell(widths[i], row_h, " " + cell, border=1, fill=True)
        self.ln(row_h)

    def decision_table(self, rows):
        widths = [55, 125]
        self.table_row(["Decision", "Choice"], widths, header=True)
        for row in rows:
            self.table_row(row, widths)
        self.ln(5)

    def pattern_table(self, header, rows):
        widths = [50, 55, 75]
        self.table_row(header, widths, header=True)
        for row in rows:
            self.table_row(row, widths)
        self.ln(5)

    def fallback_table(self, rows):
        widths = [60, 120]
        self.table_row(["Scenario", "Result"], widths, header=True)
        for row in rows:
            self.table_row(row, widths)
        self.ln(5)


def main():
    pdf = DecisionPDF()
    pdf.alias_nb_pages()
    pdf.set_auto_page_break(auto=True, margin=20)
    pdf.add_page()

    # Title
    pdf.set_font("Helvetica", "B", 20)
    pdf.set_text_color(30, 60, 120)
    pdf.cell(0, 12, "Archive Feature Decisions")
    pdf.ln(10)
    pdf.set_font("Helvetica", "", 11)
    pdf.set_text_color(100, 100, 100)
    pdf.cell(0, 7, "Papyrus Document Intelligence System")
    pdf.ln(6)
    pdf.cell(0, 7, "Date: April 12, 2026")
    pdf.ln(15)

    # Context
    pdf.section_title("1. Context")
    pdf.body_text(
        "Papyrus ingests scanned page images from physical hardbound committee "
        "meeting minute books. The archive feature saves the original image and "
        "extracted OCR text to the filesystem. Filenames are derived from the "
        "document content so each archived file traces back to its physical source."
    )

    # Scope
    pdf.section_title("2. Scope")
    pdf.body_text(
        "Archive only OCR-processed files:\n"
        "  - Image files: PNG, JPG, TIFF, BMP, GIF (via ImageOcrExtractor)\n"
        "  - Scanned PDFs: PDFs that fall back to OCR (via SmartPdfExtractor)\n\n"
        "Non-OCR documents (digital PDFs, DOCX, XLSX, etc.) are NOT archived."
    )

    # Decisions
    pdf.section_title("3. Decisions")
    pdf.decision_table([
        ["Archive scope", "OCR-processed files only"],
        ["Storage path", "/data/papyrus/archive"],
        ["Directory layout", "Side-by-side: {sourceId}/file.png + file.txt"],
        ["Date in filename", "Meeting date from OCR text (not upload date)"],
        ["Date format", "yyyy-MM-dd (ISO, sorts naturally)"],
        ["Date fallback", "Upload date when no date found in content"],
        ["Page number source", "Extracted from OCR text"],
        ["Page fallback", "Omitted from filename if not found"],
        ["Content slug", "First meaningful line, slugified, max 80 chars"],
        ["Abbreviations", "Configurable in application.yml"],
        ["Failure behavior", "Best-effort: log warning, never block ingestion"],
        ["Default state", "Disabled (opt-in via ARCHIVE_ENABLED=true)"],
    ])

    # Filename format
    pdf.section_title("4. Filename Format")
    pdf.code_block("{date}_{page}_{content-slug}.{ext}")
    pdf.ln(2)

    pdf.sub_title("Example: Page 1 of meeting minutes")
    pdf.body_text("Original upload: IMG_001.png")
    pdf.code_block(
        "{sourceId}/\n"
        "  1985-01-19_p1_wmm-execomm-minutes.png\n"
        "  1985-01-19_p1_wmm-execomm-minutes.txt"
    )

    pdf.sub_title("Example: Page 2 (continuation)")
    pdf.body_text("Original upload: IMG_002.png")
    pdf.code_block(
        "{sourceId}/\n"
        "  1985-01-19_p2_wmm-minutes.png\n"
        "  1985-01-19_p2_wmm-minutes.txt"
    )

    pdf.sub_title("Fallback Chain")
    pdf.fallback_table([
        ["All found", "1985-01-19_p1_wmm-execomm-minutes.png"],
        ["No page found", "1985-01-19_wmm-execomm-minutes.png"],
        ["No date in content", "2026-04-12_p1_wmm-execomm-minutes.png"],
        ["No text at all", "2026-04-12_IMG_001.png (original name)"],
    ])

    # Extraction patterns
    pdf.add_page()
    pdf.section_title("5. Extraction Patterns")

    pdf.sub_title("Date Extraction (first match wins)")
    pdf.pattern_table(
        ["Document text", "Pattern", "Result"],
        [
            ["January 19, 1985", "Month DD, YYYY", "1985-01-19"],
            ["19 January 1985", "DD Month YYYY", "1985-01-19"],
            ["1985-01-19", "YYYY-MM-DD (ISO)", "1985-01-19"],
            ["01/19/1985", "MM/DD/YYYY", "1985-01-19"],
        ],
    )

    pdf.sub_title("Page Number Extraction")
    pdf.pattern_table(
        ["Document text", "Pattern", "Result"],
        [
            ["1.", "Standalone number + period", "p1"],
            ["Page 42", "Page N", "p42"],
            ["p. 3", "p. N", "p3"],
            ["- 5 -", "Dash-surrounded number", "p5"],
        ],
    )

    # Abbreviations
    pdf.section_title("6. Abbreviation Configuration")
    pdf.body_text(
        "Abbreviations are configurable in application.yml and applied to the "
        "content slug before filename generation. Case-insensitive replacement."
    )
    pdf.code_block(
        "papyrus:\n"
        "  archive:\n"
        "    enabled: true\n"
        "    path: /data/papyrus/archive\n"
        "    abbreviations:\n"
        '      "executive committee": execomm\n'
        '      "western mindanao mission": wmm'
    )

    # Architecture
    pdf.section_title("7. Architecture Summary")
    pdf.body_text(
        "Module: papyrus-pipeline (shared between API and MCP)\n\n"
        "Key class: ArchiveService\n"
        "  - archive(sourceId, filename, content, extractedText)\n"
        "  - Extracts date, page, slug from extractedText\n"
        "  - Applies abbreviation map\n"
        "  - Writes original file + .txt to {basePath}/{sourceId}/\n\n"
        "Configuration: PapyrusProperties.ArchiveProperties\n"
        "  - enabled (boolean)\n"
        "  - path (String)\n"
        "  - abbreviations (Map<String, String>)\n\n"
        "Integration points:\n"
        "  - DocumentService (papyrus-api) - after OCR extraction\n"
        "  - IngestionOrchestrator (papyrus-mcp) - after OCR extraction\n\n"
        "Docker: Shared volume 'papyrus_archive' mounted at /data/papyrus/archive\n"
        "Env vars: ARCHIVE_ENABLED, ARCHIVE_PATH"
    )

    # Output
    out = "/home/user/papyrus/docs/archive-feature-decisions.pdf"
    pdf.output(out)
    print(f"PDF written to {out}")


if __name__ == "__main__":
    main()
