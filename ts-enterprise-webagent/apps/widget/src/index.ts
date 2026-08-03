export { WebAgentWidget } from "./web-agent-widget";

const TAG_NAME = WebAgentWidget.elementName;

if (!customElements.get(TAG_NAME)) {
  customElements.define(TAG_NAME, WebAgentWidget);
}
