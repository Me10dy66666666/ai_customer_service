export interface AgentMessage {
  id: string;
  role: "user" | "agent" | "system";
  content: string;
  timestamp: number;
  /** 工单触发时由 agent 返回的 action */
  action?: WorkOrderAction | null;
}

export interface WorkOrderAction {
  action: "create_work_order";
  data: {
    title: string;
    description: string;
    type: "售前" | "售后";
    priority: "high" | "medium" | "low";
  };
}

export interface ChatRequest {
  user_input: string;
  history: string[];
  userType: number;
}

export interface ChatResponse {
  reply: string;
  action?: WorkOrderAction | null;
}

export interface WidgetConfig {
  /** 服务端 API 地址，默认 "http://localhost:3400/api/v1/agent/customerService" */
  apiEndpoint?: string;
  /** 占位提示文本 */
  placeholder?: string;
  /** 欢迎语 */
  welcomeMessage?: string;
  /** 用户类型 */
  userType?: number;
  /** 主题色 */
  themeColor?: string;
  /** 历史购买记录 */
  historyOrders?: string;
}
