import { describe, it, expect, vi } from "vitest";

vi.mock("pdf-parse", () => ({
  default: async (_buffer: unknown) => ({ text: "PDF 文本" })
}));
vi.mock("mammoth", () => ({
  default: { extractRawText: async () => ({ value: "DOCX 文本" }) }
}));
vi.mock("xlsx", () => ({
  read: () => ({ SheetNames: ["Sheet1"], Sheets: { Sheet1: {} } }),
  utils: { sheet_to_csv: () => "XLSX 文本" }
}));

import { PdfFileParser } from "./pdfFileParser.js";
import { DocxFileParser } from "./docxFileParser.js";
import { XlsxFileParser } from "./xlsxFileParser.js";

describe("二进制文件解析器", () => {
  it("PDF 解析器抽取文本", async () => {
    const parser = new PdfFileParser();
    await expect(parser.parse("a.pdf", Buffer.from("binary"))).resolves.toBe("PDF 文本");
  });

  it("DOCX 解析器抽取文本", async () => {
    const parser = new DocxFileParser();
    await expect(parser.parse("a.docx", Buffer.from("binary"))).resolves.toBe("DOCX 文本");
  });

  it("XLSX 解析器抽取文本", async () => {
    const parser = new XlsxFileParser();
    await expect(parser.parse("a.xlsx", Buffer.from("binary"))).resolves.toBe("XLSX 文本");
  });
});
