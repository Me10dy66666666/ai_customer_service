import { extname } from "node:path";

/**
 * 文件解析接缝：把上传文件的字节解析为纯文本。
 * 后续可扩展 OCR、委托后端解析等实现。
 */
export interface FileParser {
  parse(filename: string, buffer: Buffer): Promise<string>;
}

/**
 * 文件解析失败：携带明确失败原因，便于上层返回可读错误。
 */
export class FileParseError extends Error {
  public constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "FileParseError";
  }
}

/**
 * 组合解析器：按扩展名分派到具体解析器。
 */
export class CompositeFileParser implements FileParser {
  public constructor(private readonly parsers: ReadonlyMap<string, FileParser>) {}

  public async parse(filename: string, buffer: Buffer): Promise<string> {
    const extension = extname(filename).toLowerCase().replace(/^\./, "");
    const parser = this.parsers.get(extension);
    if (!parser) {
      throw new FileParseError(`不支持的文件类型: .${extension || "(无扩展名)"}`);
    }
    try {
      return await parser.parse(filename, buffer);
    } catch (error) {
      if (error instanceof FileParseError) throw error;
      throw new FileParseError(`文件解析失败: ${filename}`, { cause: error });
    }
  }
}
