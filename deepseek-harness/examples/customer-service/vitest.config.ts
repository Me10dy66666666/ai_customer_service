import { defineConfig } from 'vitest/config'

/** Focused customer-service plugin gate without the repository-wide invariant harness. */
export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    include: [
      'packages/customer-service/tool-customer-service/tests/**/*.spec.ts',
      'packages/customer-service/tool-search-knowledge/tests/**/*.spec.ts',
      'packages/customer-service/agent-budget-guard/tests/**/*.spec.ts',
      'packages/customer-service/customer-service-gateway/tests/**/*.spec.ts',
    ],
    environment: 'node',
  },
})
