import * as XLSX from "xlsx";
import type { FileParser } from "./fileParser.js";

/** Excel(.xlsx) 解析：每个 sheet 转 CSV 后拼接。 */
export class XlsxFileParser implements FileParser {
  public async parse(_filename: string, buffer: Buffer): Promise<string> {
    const workbook = XLSX.read(buffer, { type: "buffer" });
    return workbook.SheetNames
      .map((name) => {
        const sheet = workbook.Sheets[name];
        return sheet ? XLSX.utils.sheet_to_csv(sheet) : "";
      })
      .filter((text) => text.length > 0)
      .join("\n\n");
  }
}
