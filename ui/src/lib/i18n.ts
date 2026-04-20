import i18n, {type Config} from 'sveltekit-i18n';

type Payload = Record<string, unknown>;

const config: Config<Payload> = ({
    loaders: [
        {
            locale: 'en',
            key: 'common',
            loader: async () => (
                await import('./i18n/en/common.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'errors',
            loader: async () => (
                await import('./i18n/en/errors.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'library',
            loader: async () => (
                await import('./i18n/en/library.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'books',
            loader: async () => (
                await import('./i18n/en/books.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'authors',
            loader: async () => (
                await import('./i18n/en/authors.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'import',
            loader: async () => (
                await import('./i18n/en/import.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'login',
            routes: ['/login'],
            loader: async () => (
                await import('./i18n/en/login.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'register',
            loader: async () => (
                await import('./i18n/en/register.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'setup',
            routes: ['/setup'],
            loader: async () => (
                await import('./i18n/en/setup.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'series',
            loader: async () => (
                await import('./i18n/en/series.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'settings',
            loader: async () => (
                await import('./i18n/en/settings.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'metadata',
            loader: async () => (
                await import('./i18n/en/metadata.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'admin',
            loader: async () => (
                await import('./i18n/en/admin.json')
            ).default,
        },
        {
            locale: 'en',
            key: 'progress',
            loader: async () => (
                await import('./i18n/en/progress.json')
            ).default,
        }
    ]
});

export const {t, locale, locales, loading, loadTranslations} = new i18n(config);
