import type {
  WorkOrderAnalysisResult,
  SummaryResult,
  SentimentResult
} from "@enterprise-webagent/shared";

/**
 * 情绪分析服务
 *
 * 基于关键词 + 情感词典的快速情绪分析
 * 对齐 Backend 的 Dify 工单分析工作流中的情绪分析能力
 */
export class SentimentAnalysisService {
  private readonly negativeWords = new Set([
    "坏", "差", "糟糕", "垃圾", "坑", "骗", "坑爹", "失望",
    "生气", "愤怒", "投诉", "维权", "烂", "后悔", "恶心",
    "不行", "不好", "不能", "无法", "错误", "失败", "有问题"
  ]);

  private readonly angryWords = new Set([
    "投诉", "维权", "举报", "曝光", "退款", "骗子", "坑爹",
    "垃圾公司", "什么玩意", "要命", "去死"
  ]);

  private readonly positiveWords = new Set([
    "好", "棒", "赞", "满意", "开心", "感谢", "谢谢", "不错",
    "优秀", "厉害", "推荐", "喜欢", "方便", "快捷", "高效"
  ]);

  /**
   * 分析用户输入的情绪
   */
  public analyze(userInput: string, chatHistory?: string[]): SentimentResult {
    const combinedText = chatHistory
      ? [userInput, ...chatHistory].join(" ")
      : userInput;

    const words = this.extractWords(combinedText);

    const angryCount = words.filter((w) => this.angryWords.has(w)).length;
    const negativeCount = words.filter((w) => this.negativeWords.has(w)).length;
    const positiveCount = words.filter((w) => this.positiveWords.has(w)).length;

    const totalSentiment = positiveCount - (negativeCount + angryCount * 2);

    let emotionLevel: SentimentResult["emotionLevel"];
    let confidence: number;
    let suggestion: string | undefined;

    if (angryCount > 0) {
      emotionLevel = "angry";
      confidence = Math.min(0.9, 0.6 + angryCount * 0.1);
      suggestion = "用户情绪激动，建议优先处理并考虑转人工";
    } else if (negativeCount > positiveCount + 1) {
      emotionLevel = "negative";
      confidence = Math.min(0.85, 0.5 + (negativeCount - positiveCount) * 0.1);
      suggestion = "用户表达不满，建议快速响应并提供解决方案";
    } else if (positiveCount > negativeCount) {
      emotionLevel = "positive";
      confidence = Math.min(0.85, 0.5 + (positiveCount - negativeCount) * 0.1);
    } else {
      emotionLevel = "neutral";
      confidence = 0.5;
    }

    const keywords = this.extractSentimentKeywords(words);

    return {
      emotionLevel,
      confidence,
      keywords,
      suggestion
    };
  }

  private extractWords(text: string): string[] {
    // 简单的中文分词：提取连续中文字符
    return text.match(/[\u4e00-\u9fa5]+/g) ?? [];
  }

  private extractSentimentKeywords(words: string[]): string[] {
    const sentimentWords = [
      ...this.angryWords,
      ...this.negativeWords,
      ...this.positiveWords
    ];
    const found = new Set<string>();
    for (const word of words) {
      if (sentimentWords.includes(word)) {
        found.add(word);
      }
    }
    return Array.from(found).slice(0, 10);
  }
}
