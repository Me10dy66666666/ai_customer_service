<template>
  <div class="admin-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="logo">AI 客服管理</div>
      <nav>
        <router-link to="/admin/knowledge-base" class="nav-item" active-class="active">
          知识库管理
        </router-link>
        <router-link to="/admin/work-orders" class="nav-item" active-class="active">
          工单管理
        </router-link>
        <router-link to="/admin/data-analysis" class="nav-item" active-class="active">
          数据洞察
        </router-link>
      </nav>
    </aside>

    <!-- Main Content -->
    <main class="content">
      <header class="header">
        <div class="user-info">管理员</div>
      </header>
      <div class="page-content">
        <router-view></router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100%;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

.sidebar {
  width: 250px;
  flex-shrink: 0;
  background-color: #2c3e50;
  color: white;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.logo {
  padding: 20px;
  font-size: 20px;
  font-weight: bold;
  text-align: center;
  border-bottom: 1px solid #34495e;
  flex-shrink: 0;
}

nav {
  flex: 1;
  padding: 20px 0;
  min-height: 0;
}

.nav-item {
  display: block;
  padding: 15px 20px;
  color: #bdc3c7;
  text-decoration: none;
  transition: background-color 0.3s;
}

.nav-item:hover, .nav-item.active {
  background-color: #34495e;
  color: white;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background-color: #f5f7fa;
  overflow: hidden;
}

.header {
  height: 60px;
  flex-shrink: 0;
  background-color: white;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.page-content {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
