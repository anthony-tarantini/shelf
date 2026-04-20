import eslint from '@eslint/js';
import {defineConfig} from 'eslint/config';
import tseslint from 'typescript-eslint';
import svelte from "eslint-plugin-svelte";
import svelteParser from "svelte-eslint-parser";
import globals from "globals";

export default defineConfig(
    {
        ignores: [".svelte-kit/", "build/", ".vercel/"]
    },
    eslint.configs.recommended,
    tseslint.configs.recommended,
    ...svelte.configs["flat/recommended"],
    {
        files: ["**/*.ts", "**/*.js"],
        languageOptions: {
            parser: tseslint.parser,
            globals: {
                ...globals.browser,
                ...globals.node
            }
        }
    },
    {
        files: ["**/*.svelte"],
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.node // Add this if you have +page.server.ts files
            },
            parser: svelteParser,
            parserOptions: {
                parser: tseslint.parser,
                extraFileExtensions: [".svelte"],
            },
        },
    },
    {
        files: ["src/lib/components/**/*.svelte", "src/routes/**/+page.svelte", "src/routes/**/+page.ts"],
        rules: {
            "no-restricted-imports": ["error", {
                "paths": [{
                    "name": "jsonwebtoken",
                    "message": "🚫 Security Risk: Use +page.server.ts instead."
                }],
                "patterns": [{
                    "group": ["$lib/server/*"],
                    "message": "🚫 Architecture Violation: Client cannot import from server directory."
                }]
            }]
        }
    },
    {
        files: ["**/*.svelte"],
        rules: {
            "svelte/no-at-html-tags": "warn",
            "svelte/prefer-svelte-reactivity": "error",
            "svelte/valid-compile": "error"
        }
    }
);