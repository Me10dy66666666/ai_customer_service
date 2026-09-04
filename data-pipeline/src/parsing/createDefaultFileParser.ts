import { CompositeFileParser, type FileParser } from "./fileParser.js";
import { TextFileParser } from "./textFileParser.js";
import { PdfFileParser } from "./pdfFileParser.js";
import { DocxFileParser } from "./docxFileParser.js";
import { XlsxFileParser } from "./xlsxFileParser.js";

/** 构建默认解析器：支持 md/markdown/txt、pdf、docx、xlsx。 */
export function createDefaultFileParser(): FileParser {
  const parsers = new Map<string, FileParser>([
    ["md", new TextFileParser()],
    ["markdown", new TextFileParser()],
    ["txt", new TextFileParser()],
    ["pdf", new PdfFileParser()],
    ["docx", new DocxFileParser()],
    ["xlsx", new XlsxFileParser()]
  ]);
  return new CompositeFileParser(parsers);
}
