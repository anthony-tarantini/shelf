import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
	globalSetup: './tests/global-setup',
	timeout: 30000,
	workers: 1,
	expect: {
		timeout: 10000,
	},
	webServer: {
		command: 'BACKEND_URL=http://127.0.0.1:8081 ENABLE_E2E_AUTH=true bun run dev --host 0.0.0.0 --port 4173',
		port: 4173,
		reuseExistingServer: !process.env.CI,
	},
	use: {
		baseURL: 'http://127.0.0.1:4173',
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure',
	},
	testDir: 'tests',
	testMatch: /(.+\.)?(test|spec)\.[jt]s/,
	projects: [
		{
			name: 'chromium',
			use: { ...devices['Desktop Chrome'] },
			testIgnore: /.*mobile-shell\.spec\.ts/,
		},
		{
			name: 'Mobile Chrome',
			use: { ...devices['Pixel 5'] },
			testMatch: /.*mobile-shell\.spec\.ts/,
		},
	],
});
