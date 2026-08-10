import { WebAgentWidget } from "./web-agent-widget.js";
export { WebAgentWidget };

const TAG_NAME = WebAgentWidget.elementName;

if (!customElements.get(TAG_NAME)) {
  customElements.define(TAG_NAME, WebAgentWidget);
}
