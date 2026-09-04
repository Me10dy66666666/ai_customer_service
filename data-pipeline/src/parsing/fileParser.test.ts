import { describe, it, expect } from "vitest";
import { CompositeFileParser, FileParseError, type FileParser } from "./fileParser.js";
import { TextFileParser } from "./textFileParser.js";

describe("TextFileParser", () => {
  it("按 UTF-8 读取文本", async () => {
    const parser = new TextFileParser();
    await expect(parser.parse("a.md", Buffer.from("你好，世界"))).resolves.toBe("你好，世界");
  });
});

describe("CompositeFileParser", () => {
  it("按扩展名分派到对应解析器", async () => {
    const fake: FileParser = { parse: async () => "解析结果" };
    const composite = new CompositeFileParser(new Map([["md", fake]]));
    await expect(composite.parse("readme.md", Buffer.from("x"))).resolves.toBe("解析结果");
  });

  it("未知扩展名抛出明确错误", async () => {
    const composite = new CompositeFileParser(new Map([["md", new TextFileParser()]]));
    await expect(composite.parse("file.xyz", Buffer.from("x"))).rejects.toThrow(/不支持的文件类型/);
  });

  it("解析器抛错时包装为 FileParseError", async () => {
    const broken: FileParser = {
      parse: async () => {
        throw new Error("boom");
      }
    };
    const composite = new CompositeFileParser(new Map([["md", broken]]));
    await expect(composite.parse("a.md", Buffer.from("x"))).rejects.toThrow(/文件解析失败/);
  });

  it("FileParseError 原样透传，不再二次包装", async () => {
    const throwing: FileParser = {
      parse: async () => {
        throw new FileParseError("自定义失败");
      }
    };
    const composite = new CompositeFileParser(new Map([["md", throwing]]));
    await expect(composite.parse("a.md", Buffer.from("x"))).rejects.toThrow("自定义失败");
  });
});
