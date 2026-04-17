<script lang="ts">
	import { api } from '$lib/api/client';
	import type { UserResponse } from '$lib/types/models';
	import { resolve } from '$app/paths';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/auth.svelte';
	import { t } from '$lib/i18n';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import AuthCard from '$lib/components/ui/AuthCard.svelte';
	import FormField from '$lib/components/ui/FormField.svelte';

	let email = $state('');
	let username = $state('');
	let password = $state('');
	let confirmPassword = $state('');
	let loading = $state(false);
	let error = $state<string | null>(null);

	async function handleSubmit() {
		if (!email || !username || !password) {
			error = $t('register.error_fill_fields');
			return;
		}

		if (password !== confirmPassword) {
			error = $t('register.error_password_mismatch');
			return;
		}

		loading = true;
		error = null;

		const result = await api.post<UserResponse>('/setup', {
			email, username, password
		});

		loading = false;
		if (result.left) {
			error = result.left.message;
		} else if (result.right) {
			api.setToken(result.right.token);
			auth.setUser(result.right.user);
			goto(resolve('/admin/users')).then();
		}
	}
</script>

<AuthCard
	eyebrow={$t('setup.eyebrow')}
	title={$t('setup.page_title')}
	subtitle={$t('setup.page_subtitle')}
>
	<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-4">
		<FormField label={$t('register.label_username')} forId="username">
			<input id="username" type="text" bind:value={username} placeholder={$t('register.placeholder_username')} class="ui-input" required />
		</FormField>

		<FormField label={$t('register.label_email')} forId="email">
			<input id="email" type="email" bind:value={email} placeholder={$t('register.placeholder_email')} class="ui-input" required />
		</FormField>

		<FormField label={$t('register.label_password')} forId="password">
			<input id="password" type="password" bind:value={password} placeholder={$t('register.placeholder_password')} class="ui-input" required />
		</FormField>

		<FormField label={$t('register.label_confirm_password')} forId="confirmPassword">
			<input id="confirmPassword" type="password" bind:value={confirmPassword} placeholder={$t('register.placeholder_password')} class="ui-input" required />
		</FormField>

		{#if error}
			<StatusBanner kind="error" title={$t('setup.error_failed')} message={error} />
		{/if}

		<button
			type="submit"
			disabled={loading}
			class="mt-4 flex w-full items-center justify-center rounded-xl bg-primary px-4 py-3 text-lg font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted"
		>
			{#if loading}
				<span class="mr-2 h-5 w-5 border-3 border-white/30 border-t-white rounded-full animate-spin"></span>
				{$t('setup.loading_complete')}
			{:else}
				{$t('setup.button_complete')}
			{/if}
		</button>
	</form>
</AuthCard>
