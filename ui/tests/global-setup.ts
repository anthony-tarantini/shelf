import { request } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

export default async function globalSetup() {
	const backendUrl = process.env.BACKEND_URL || 'http://localhost:8081';
	const reqContext = await request.newContext();
	
	console.log(`Waiting for backend at ${backendUrl}...`);
	
	let ready = false;
	const maxRetries = 30;
	for (let i = 0; i < maxRetries; i++) {
		try {
			const res = await reqContext.get(`${backendUrl}/readiness`);
			if (res.ok()) {
				ready = true;
				break;
			}
		} catch {
			// ignore and retry
		}
		await new Promise(r => setTimeout(r, 1000));
	}
	
	if (!ready) {
		throw new Error(`Backend at ${backendUrl} did not become ready in time.`);
	}

	console.log('Backend is ready.');
	
	try {
		// Attempt to setup initial user (backend expects { data: { ... } } structure)
		const setupRes = await reqContext.post(`${backendUrl}/api/setup`, {
			data: {
				data: {
					email: 'admin@example.com',
					username: 'admin',
					password: 'adminpassword'
				}
			}
		});
		
		if (setupRes.ok()) {
			const responseJson = await setupRes.json();
			const data = responseJson.data; // Unwrap backend response
			console.log('Setup completed successfully');
			
			if (data && data.token) {
				storeAuth(data.token);
			}
		} else {
			console.log('Setup may have already been completed, attempting login...');
			const loginRes = await reqContext.post(`${backendUrl}/api/users/login`, {
				data: {
					data: {
						email: 'admin@example.com',
						password: 'adminpassword'
					}
				}
			});
			if (loginRes.ok()) {
				const responseJson = await loginRes.json();
				const data = responseJson.data; // Unwrap backend response
				if (data && data.token) {
					storeAuth(data.token);
				} else {
					throw new Error('Login succeeded but no token was returned.');
				}
			} else {
				throw new Error(`Failed to log in during setup: ${await loginRes.text()}`);
			}
		}
	} catch(e) {
		console.error('Error during setup', e);
		throw e;
	}
}

function storeAuth(token: string) {
	const storageState = {
		cookies: [],
		origins: [
			{
				origin: 'http://127.0.0.1:4173',
				localStorage: [
					{
						name: 'shelf_token',
						value: token
					}
				]
			}
		]
	};
	fs.mkdirSync(path.join(process.cwd(), 'tests', '.auth'), { recursive: true });
	fs.writeFileSync(path.join(process.cwd(), 'tests', '.auth', 'admin.json'), JSON.stringify(storageState));
}
