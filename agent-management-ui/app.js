const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

const API_BASE = "http://localhost:3001/api/v1/management";

const app = createApp({
  setup() {
    /* ── 状态 ── */
    const activeMenu = ref("dashboard");
    const loading = ref(false);
    const healthChecking = ref(false);
    const serverStatus = ref("checking");

    const agents = ref([]);
    const agentMetricsList = ref([]);
    const sessions = ref([]);
    const configData = ref(null);
    const healthStatus = ref({});

    const showAddAgentDialog = ref(false);
    const showDetailDialog = ref(false);
    const detailAgent = ref(null);

    const trendChartRef = ref(null);
    const pieChartRef = ref(null);
    const detailChartRef = ref(null);

    let trendChart = null;
    let pieChart = null;
    let detailChart = null;

    const newAgentForm = ref({
      name: "",
      type: "custom",
      description: "",
      difyApiKey: "",
      difyBaseUrl: "",
      difyWorkflowEndpoint: "/chat-messages",
      customModelMode: "mock",
      customModelName: "gpt-4.1-mini",
    });

    /* ── 时钟 ── */
    const currentTime = ref("");
    const fullTime = ref("");
    const updateTime = () => {
      const d = new Date();
      currentTime.value = d.toLocaleTimeString("zh-CN", { hour12: false });
      fullTime.value = d.toLocaleString("zh-CN");
    };
    setInterval(updateTime, 1000);
    updateTime();

    /* ── 页面标题 ── */
    const pageTitle = computed(() => {
      const titles = {
        dashboard: "总览仪表盘",
        agents: "Agent 管理",
        sessions: "会话监控",
        config: "系统配置",
      };
      return titles[activeMenu.value] || "总览仪表盘";
    });

    /* ── 指标卡片 ── */
    const summaryCards = computed(() => [
      {
        key: "total",
        label: "Agent 总数",
        value: agents.value.length,
        sub: `${agentMetricsList.value.filter((a) => a.enabled).length} 个在线`,
        icon: "Cpu",
        color: "linear-gradient(135deg, #4c6ef5, #3b5bdb)",
      },
      {
        key: "requests",
        label: "总请求数",
        value: agentMetricsList.value.reduce((s, a) => s + (a.totalRequests || 0), 0),
        sub: "今日累计",
        icon: "TrendCharts",
        color: "linear-gradient(135deg, #0ca678, #12b886)",
      },
      {
        key: "avgRt",
        label: "平均响应时间",
        value: agentMetricsList.value.length > 0
          ? `${Math.round(agentMetricsList.value.reduce((s, a) => s + (a.avgResponseTimeMs || 0), 0) / agentMetricsList.value.length)}ms`
          : "0ms",
        sub: "全部 Agent 均值",
        icon: "Timer",
        color: "linear-gradient(135deg, #f59f00, #fab005)",
      },
      {
        key: "success",
        label: "成功率",
        value: agentMetricsList.value.length > 0
          ? `${Math.round(agentMetricsList.value.reduce((s, a) => {
              const total = a.totalRequests || 0;
              const fail = a.fallbackCount || 0;
              return s + (total > 0 ? ((total - fail) / total) * 100 : 100);
            }, 0) / agentMetricsList.value.length)}%`
          : "100%",
        sub: "全部 Agent 均值",
        icon: "CircleCheck",
        color: "linear-gradient(135deg, #e03131, #f03e3e)",
      },
    ]);

    /* ── 排序后的 Agent 列表 ── */
    const sortedAgents = computed(() => {
      return agentMetricsList.value
        .map((m) => {
          const agent = agents.value.find((a) => a.id === m.agentId);
          const total = m.totalRequests || 0;
          const fail = m.fallbackCount || 0;
          const successRate = total > 0 ? Math.round(((total - fail) / total) * 100) : 100;
          return {
            ...m,
            name: agent?.name || m.agentId,
            type: agent?.type || "unknown",
            enabled: agent?.enabled ?? true,
            successRate,
          };
        })
        .sort((a, b) => a.avgResponseTimeMs - b.avgResponseTimeMs);
    });

    const allAgents = computed(() => agents.value);

    /* ── 指标映射 ── */
    const metricsMap = computed(() => {
      const map = {};
      for (const m of agentMetricsList.value) {
        map[m.agentId] = m;
      }
      return map;
    });

    const detailMetrics = computed(() => {
      if (!detailAgent.value) return null;
      const m = metricsMap.value[detailAgent.value.id];
      if (!m) return null;
      const total = m.totalRequests || 0;
      const fail = m.fallbackCount || 0;
      return {
        ...m,
        successRate: total > 0 ? Math.round(((total - fail) / total) * 100) : 100,
      };
    });

    /* ── API 调用 ── */
    const api = async (path) => {
      try {
        const r = await fetch(`${API_BASE}${path}`);
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return await r.json();
      } catch (e) {
        console.error(`API ${path} error:`, e);
        return null;
      }
    };

    const fetchAgents = async () => {
      const data = await api("/agents");
      if (data) agents.value = data.agents || [];
    };

    const fetchMetrics = async () => {
      const data = await api("/agents/metrics");
      if (data) agentMetricsList.value = data.metrics || [];
    };

    const fetchSessions = async () => {
      const data = await api("/sessions");
      if (data) sessions.value = data.sessions || [];
    };

    const fetchConfig = async () => {
      const data = await api("/config");
      if (data) configData.value = data;
    };

    const fetchHealth = async () => {
      const data = await api("/agents/health");
      if (data) healthStatus.value = data.health || {};
    };

    const collectMetrics = async () => {
      await fetch(`${API_BASE}/metrics/collect`, { method: "POST" }).catch(() => {});
      await fetchMetrics();
      ElMessage.success("指标采集完成");
    };

    const checkAllHealth = async () => {
      healthChecking.value = true;
      await fetchHealth();
      healthChecking.value = false;
      ElMessage.success("健康检查完成");
    };

    const refreshAll = async () => {
      loading.value = true;
      await Promise.all([fetchAgents(), fetchMetrics(), fetchSessions(), fetchConfig(), fetchHealth()]);
      loading.value = false;
      serverStatus.value = "online";
    };

    /* ── 操作 ── */
    const addAgent = () => {
      const form = newAgentForm.value;
      if (!form.name) {
        ElMessage.warning("请输入 Agent 名称");
        return;
      }
      const now = new Date().toISOString();
      const agent = {
        id: `custom-${Date.now()}`,
        name: form.name,
        type: form.type,
        description: form.description,
        enabled: true,
        createdAt: now,
        updatedAt: now,
      };
      if (form.type === "dify") {
        agent.difyConfig = {
          apiKey: form.difyApiKey,
          baseUrl: form.difyBaseUrl,
          workflowEndpoint: form.difyWorkflowEndpoint,
        };
      } else {
        agent.customConfig = {
          modelMode: form.customModelMode,
          modelName: form.customModelName,
        };
      }
      agents.value.push(agent);
      showAddAgentDialog.value = false;
      ElMessage.success(`Agent "${form.name}" 已接入`);
    };

    const removeAgent = (agent) => {
      ElMessageBox.confirm(`确定要移除 Agent "${agent.name}" 吗？`, "确认", {
        type: "warning",
      }).then(() => {
        agents.value = agents.value.filter((a) => a.id !== agent.id);
        ElMessage.success("已移除");
      }).catch(() => {});
    };

    const showAgentDetail = (agent) => {
      detailAgent.value = agent;
      showDetailDialog.value = true;
      nextTick(() => renderDetailChart(agent.id));
    };

    const deleteSession = (sessionId) => {
      sessions.value = sessions.value.filter((s) => s.sessionId !== sessionId);
      ElMessage.success("会话已清除");
    };

    const handleMenuSelect = (index) => {
      activeMenu.value = index;
      nextTick(() => {
        if (index === "dashboard") {
          renderTrendChart();
          renderPieChart();
        }
        if (index === "sessions") fetchSessions();
        if (index === "config") fetchConfig();
      });
    };

    /* ── 图表渲染 ── */
    const renderTrendChart = () => {
      if (!trendChartRef.value) return;
      if (!trendChart) {
        trendChart = echarts.init(trendChartRef.value);
      }
      const hours = [];
      for (let i = 23; i >= 0; i--) {
        const d = new Date();
        d.setHours(d.getHours() - i);
        hours.push(`${d.getHours().toString().padStart(2, "0")}:00`);
      }
      trendChart.setOption({
        tooltip: { trigger: "axis" },
        legend: { top: 0, textStyle: { color: "#a0aec0" } },
        grid: { top: 40, right: 20, bottom: 30, left: 50 },
        xAxis: {
          type: "category",
          data: hours,
          axisLabel: { color: "#718096" },
          axisLine: { lineStyle: { color: "#2d2e3e" } },
        },
        yAxis: {
          type: "value",
          axisLabel: { color: "#718096" },
          splitLine: { lineStyle: { color: "#2d2e3e" } },
        },
        series: [
          {
            name: "请求数",
            type: "line",
            smooth: true,
            data: Array.from({ length: 24 }, () => Math.floor(Math.random() * 100 + 10)),
            itemStyle: { color: "#4c6ef5" },
            areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: "rgba(76,110,245,0.3)" },
              { offset: 1, color: "rgba(76,110,245,0.05)" },
            ]) },
          },
          {
            name: "降级数",
            type: "line",
            smooth: true,
            data: Array.from({ length: 24 }, () => Math.floor(Math.random() * 10 + 2)),
            itemStyle: { color: "#f03e3e" },
          },
        ],
      });
    };

    const renderPieChart = () => {
      if (!pieChartRef.value) return;
      if (!pieChart) {
        pieChart = echarts.init(pieChartRef.value);
      }
      const customCount = agents.value.filter((a) => a.type === "custom").length || 1;
      const difyCount = agents.value.filter((a) => a.type === "dify").length || 0;
      pieChart.setOption({
        tooltip: { trigger: "item" },
        legend: { bottom: 0, textStyle: { color: "#a0aec0" } },
        series: [
          {
            type: "pie",
            radius: ["50%", "75%"],
            center: ["50%", "45%"],
            itemStyle: { borderRadius: 4 },
            label: { color: "#a0aec0" },
            data: [
              { value: customCount, name: "自定义 Agent", itemStyle: { color: "#4c6ef5" } },
              { value: difyCount, name: "Dify Agent", itemStyle: { color: "#f59f00" } },
            ],
          },
        ],
      });
    };

    const renderDetailChart = (agentId) => {
      if (!detailChartRef.value) return;
      if (detailChart) detailChart.dispose();
      detailChart = echarts.init(detailChartRef.value);
      const points = Array.from({ length: 20 }, (_, i) => ({
        time: `${20 - i}m ago`,
        value: Math.floor(Math.random() * 50 + (20 - i) * 2),
      }));
      detailChart.setOption({
        tooltip: { trigger: "axis" },
        grid: { top: 10, right: 20, bottom: 30, left: 50 },
        xAxis: {
          type: "category",
          data: points.map((p) => p.time),
          axisLabel: { color: "#718096" },
        },
        yAxis: {
          type: "value",
          axisLabel: { color: "#718096" },
          splitLine: { lineStyle: { color: "#2d2e3e" } },
        },
        series: [
          {
            type: "line",
            smooth: true,
            data: points.map((p) => p.value),
            itemStyle: { color: "#0ca678" },
            areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: "rgba(12,166,120,0.3)" },
              { offset: 1, color: "rgba(12,166,120,0.05)" },
            ]) },
          },
        ],
      });
    };

    /* ── 窗口调整 ── */
    const resizeCharts = () => {
      trendChart?.resize();
      pieChart?.resize();
      detailChart?.resize();
    };
    window.addEventListener("resize", resizeCharts);

    /* ── 初始化 ── */
    onMounted(async () => {
      await refreshAll();
      nextTick(() => {
        renderTrendChart();
        renderPieChart();
      });
    });

    return {
      activeMenu, loading, healthChecking, serverStatus,
      agents, agentMetricsList, sessions, configData, healthStatus,
      showAddAgentDialog, showDetailDialog, detailAgent, newAgentForm,
      trendChartRef, pieChartRef, detailChartRef,
      currentTime, fullTime, pageTitle,
      summaryCards, sortedAgents, allAgents, metricsMap, detailMetrics,
      handleMenuSelect, refreshAll, collectMetrics, checkAllHealth,
      addAgent, removeAgent, showAgentDetail, deleteSession,
    };
  },
});

// 注册 Element Plus Icons
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component);
}

app.use(ElementPlus);
app.mount("#app");
