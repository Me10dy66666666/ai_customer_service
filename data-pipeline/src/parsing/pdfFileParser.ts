import pdfParse from "pdf-parse";
import type { FileParser } from "./fileParser.js";

/** PDF 解析：抽取纯文本。 */
export class PdfFileParser implements FileParser {
  public async parse(_filename: string, buffer: Buffer): Promise<string> {
    const result = await pdfParse(buffer);
    return result.text ?? "";
  }
}
