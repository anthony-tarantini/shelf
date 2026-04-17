<script lang="ts">
	import { api } from '$lib/api/client';
	import type { UserResponse } from '$lib/types/models';
	import { resolve } from '$app/paths'
	import { goto } from '$app/navigation';
	import { auth } from '$lib/auth.svelte';
	import {t} from '$lib/i18n';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import AuthCard from '$lib/components/ui/AuthCard.svelte';
	import FormField from '$lib/components/ui/FormField.svelte';

	let email = $state('');
	let password = $state('');
	let loading = $state(false);
	let error = $state<string | null>(null);

	async function handleSubmit() {
		if (!email || !password) {
			error = $t('login.error_required_fields');
			return;
		}

		loading = true;
		error = null;

		const result = await api.post<UserResponse>('/users/login', {
			email, password
		});

		loading = false;
		if (result.left) {
			error = result.left.message;
		} else if (result.right) {
			api.setToken(result.right.token);
			auth.setUser(result.right.user);
			goto(resolve('/')).then();
		}
	}
</script>

<AuthCard
	eyebrow={$t('login.eyebrow')}
	title={$t('login.page_title')}
	subtitle={$t('login.page_subtitle')}
>
	<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-6">
		<FormField label={$t('login.label_email')} forId="email">
			<input
				id="email"
				type="email"
				bind:value={email}
				placeholder={$t('login.placeholder_email')}
				class="ui-input"
				required
			/>
		</FormField>

		<FormField label={$t('login.label_password')} forId="password">
			<input
				id="password"
				type="password"
				bind:value={password}
				placeholder={$t('login.placeholder_password')}
				class="ui-input"
				required
			/>
		</FormField>

		{#if error}
			<StatusBanner kind="error" title={$t('login.error_failed')} message={error} />
		{/if}

		<button
			type="submit"
			disabled={loading}
			class="flex w-full items-center justify-center rounded-xl bg-primary px-4 py-3 text-lg font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted"
		>
			{#if loading}
				<span class="mr-2 h-5 w-5 border-3 border-white/30 border-t-white rounded-full animate-spin"></span>
				{$t('login.loading_signing_in')}
			{:else}
				{$t('login.button_sign_in')}
			{/if}
		</button>
	</form>

	{#snippet footer()}
				<p class="text-muted-foreground text-sm">
					{$t('login.footer_prompt')} 
					<a href={resolve("/register")} class="text-primary hover:text-primary/80 font-medium">{$t('login.link_create_account')}</a>
				</p>
	{/snippet}
</AuthCard>
