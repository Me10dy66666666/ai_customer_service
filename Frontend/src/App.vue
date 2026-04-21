<template>
  <div class="app-root">
    <router-view></router-view>
  </div>
</template>

<script setup>
</script>

<style>
/* 顶级容器：占满视口且不产生页面级滚动，仅内部内容区可滚动 */
html, body {
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

#app {
  height: 100%;
  overflow: hidden;
}

.app-root {
  height: 100%;
  overflow: hidden;
}
</style>
