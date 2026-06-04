<template>
  <router-view v-slot="{ Component }">
    <transition name="dissolve" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup></script>

<style>
#app {
  height: 100dvh;
  overflow: hidden;
  background: var(--base);
}
.dissolve-enter-active,
.dissolve-leave-active {
  transition: opacity var(--dur-normal) var(--ease-soft),
              filter var(--dur-normal) var(--ease-soft);
}
.dissolve-enter-from,
.dissolve-leave-to {
  opacity: 0;
  filter: blur(4px);
}
</style>
