// 第三方库缺少类型声明时的环境声明。

declare module "pdf-parse" {
  export interface PdfParseResult {
    text: string;
    numpages: number;
    info: Record<string, unknown>;
    metadata: Record<string, unknown>;
    version: string;
  }

  function pdfParse(dataBuffer: Buffer, options?: unknown): Promise<PdfParseResult>;

  export default pdfParse;
}
