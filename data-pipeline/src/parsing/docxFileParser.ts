import mammoth from "mammoth";
import type { FileParser } from "./fileParser.js";

/** Word(.docx) 解析：抽取纯文本。 */
export class DocxFileParser implements FileParser {
  public async parse(_filename: string, buffer: Buffer): Promise<string> {
    const result = await mammoth.extractRawText({ buffer });
    return result.value;
  }
}
