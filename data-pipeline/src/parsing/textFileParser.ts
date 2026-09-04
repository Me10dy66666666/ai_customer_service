import type { FileParser } from "./fileParser.js";

/** 文本类解析：Markdown/TXT 直接按 UTF-8 读取。 */
export class TextFileParser implements FileParser {
  public async parse(_filename: string, buffer: Buffer): Promise<string> {
    return buffer.toString("utf-8");
  }
}
