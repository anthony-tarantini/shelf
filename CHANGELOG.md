# Changelog

## [0.1.0](https://github.com/anthony-tarantini/shelf/compare/v0.0.1...v0.1.0) (2026-05-06)


### Features

* **audible:** implement authentication, decryption, and ingestion orchestration (steps 2-4) ([eecf199](https://github.com/anthony-tarantini/shelf/commit/eecf199eb0a3ff32cd279f3cec5c49a35901736c))
* **audible:** implement modular frontend and backend infrastructure (steps 5-6) ([b986e39](https://github.com/anthony-tarantini/shelf/commit/b986e397323ddcda2214f7595773197c6551e71f))
* **audible:** implement real Amazon token exchange and dashboard integration ([c60d4aa](https://github.com/anthony-tarantini/shelf/commit/c60d4aa1dacf1a0de28b5e656b6298570ad61a01))
* **audible:** replace stubs with actual Audible API and FFmpeg decryption logic ([50782c2](https://github.com/anthony-tarantini/shelf/commit/50782c22b97a41ac57d9a08d954ae95164c36efe))
* **audible:** step 1 - add Audible credential types and adapter stubs ([5a872b1](https://github.com/anthony-tarantini/shelf/commit/5a872b1664f184ff329f2a8aa03d58b0cb375f49))
* **audible:** step 1 - wire Audible services, routes, and dependencies ([1ac08bf](https://github.com/anthony-tarantini/shelf/commit/1ac08bf0718ec75c18df9c38b8148794950afe92))
* implement podcast detail view and settings with token management (steps 12-13) ([0ab0ebd](https://github.com/anthony-tarantini/shelf/commit/0ab0ebd6a5b194deefffdb70dc74aecd08818e48))
* initial commit ([22acd15](https://github.com/anthony-tarantini/shelf/commit/22acd15f15bc828e8fad10c51f68f0eb3c949db6))
* **koreader:** ingest reading statistics from webdav uploads ([1f60a5c](https://github.com/anthony-tarantini/shelf/commit/1f60a5c4d2da00a229417e6667cffc23b9f3db65))
* move libation integration to import flow ([c5b0469](https://github.com/anthony-tarantini/shelf/commit/c5b0469e4751aa2b24cfba435f9c1db716ad10cb))
* **podcast:** add step-1 domain primitives and errors ([a782c60](https://github.com/anthony-tarantini/shelf/commit/a782c60d61673054a01de0181b2c327c7cbbfb9c))
* **podcast:** add step-10 private rss service and routes ([564b460](https://github.com/anthony-tarantini/shelf/commit/564b4606796647198b5409f695e5879f14f63ea2))
* **podcast:** add step-2 schemas and coverage tests ([d65937e](https://github.com/anthony-tarantini/shelf/commit/d65937eb3bc9543a3eb3cf2b34b5b8548cfec3e7))
* **podcast:** add step-3 domain models and tests ([e372b81](https://github.com/anthony-tarantini/shelf/commit/e372b81afca76d885098ee08d0f7ff208b12d4c4))
* **podcast:** add step-4 persistence adapters and tests ([c08f494](https://github.com/anthony-tarantini/shelf/commit/c08f4947b5768fef12907e3320b0b4ce6780bc0e))
* **podcast:** add step-5 repositories and tests ([3baa8e5](https://github.com/anthony-tarantini/shelf/commit/3baa8e59d7e1ff592fb426497310b6c19ada5d20))
* **podcast:** add step-6 feed parser and fetch adapter ([e9db25f](https://github.com/anthony-tarantini/shelf/commit/e9db25f99777ba882b8e3112f2e130be3a630d32))
* **podcast:** add step-7 service routes and tests ([919d4c3](https://github.com/anthony-tarantini/shelf/commit/919d4c394258ee8f27a7cd317364b152a3c4dd3d))
* **podcast:** add step-8 feed fetch worker and ingestion ([f2373b9](https://github.com/anthony-tarantini/shelf/commit/f2373b9cc1cabf5ab0768e4ba32b69ab3069e392))
* **podcast:** add step-9 encrypted feed credential persistence ([9c447c9](https://github.com/anthony-tarantini/shelf/commit/9c447c97ab4416a7da69d7df4d3f756fbc0483c8))
* **podcast:** pivot to Audible and MinusPod sidecar architecture ([ea8ba3f](https://github.com/anthony-tarantini/shelf/commit/ea8ba3f9bed0124f5ffaefb78c0152b274c31c96))
* **podcast:** replace audible sidecar with libation import pipeline ([d35ec89](https://github.com/anthony-tarantini/shelf/commit/d35ec89a2b0d383a1003d8fa1e4fae076a33c830))
* **ui:** add KOReader reading stats dashboard ([40d4158](https://github.com/anthony-tarantini/shelf/commit/40d4158dfaa5ec4f7067b59988a1c3a9d57281b3))
* **ui:** implement podcast dashboard and subscription flow (step 11) ([b96cfc6](https://github.com/anthony-tarantini/shelf/commit/b96cfc65a1a73b94f65cf1bb785155e5f0b8d581))


### Bug Fixes

* **audible:** use correct Amazon client_id and device_type for auth ([6db93b8](https://github.com/anthony-tarantini/shelf/commit/6db93b820ed62e44cd35dd0fcbbf5f0a7e911c29))
* **koreader:** map sqlite ingest failures to domain error and count rescaled rows ([e916fdd](https://github.com/anthony-tarantini/shelf/commit/e916fdd3896acb8aa51a0a455b7a873ab5b2b388))
* **podcast:** add HTTP timeouts and cap inbound feed size to 10 MB ([6cfc96a](https://github.com/anthony-tarantini/shelf/commit/6cfc96a348363d9dbe3337cfecf932add4baaa15))
* **podcast:** add schema version to RSS ETag and prevent storage orphans ([bae2117](https://github.com/anthony-tarantini/shelf/commit/bae21176c467d5a7ec9bac6de64ffdcfd6c96b75))
* **podcast:** address code review findings across podcast feature ([6848a1d](https://github.com/anthony-tarantini/shelf/commit/6848a1d910e0d2af6b46b1152ef3f9d0b7c594af))
* **podcast:** cap RSS range responses to 10 MB to prevent memory abuse ([07af985](https://github.com/anthony-tarantini/shelf/commit/07af98597b596a81ed440c1c27137ca2fe2e6f47))
* **podcast:** dedupe libation imports across runs and enforce encryption secret policy ([7d0fee9](https://github.com/anthony-tarantini/shelf/commit/7d0fee96b22e69b44a1b37fb9482bc923f8e7012))
* **podcast:** harden libation and podcast route regressions ([2aca86d](https://github.com/anthony-tarantini/shelf/commit/2aca86d042259555082bdd7d63d44116a1494a07))
* **podcast:** harden rss delivery and ingestion pipeline ([1980ca5](https://github.com/anthony-tarantini/shelf/commit/1980ca55a82ab5cc81f6570f24093445f46c5104))
* rss fix ([8c1b470](https://github.com/anthony-tarantini/shelf/commit/8c1b4704e4a1129731f131fe4b26f345106cdfbe))
* **security:** use fixed salt for PBKDF2 key derivation and harden XML parser ([0b60efa](https://github.com/anthony-tarantini/shelf/commit/0b60efadf9243a93249ff297b5112f4d3db9f533))
* **test:** use temp storage root instead of hardcoded ./storage in import tests ([593956b](https://github.com/anthony-tarantini/shelf/commit/593956b598dd9f280b650a67a31dac74a31a5dd6))
* **ui:** replace hardcoded strings with i18n and add token confirmation dialogs ([18eb654](https://github.com/anthony-tarantini/shelf/commit/18eb654d9f5a9aa7b2ba50edf167ffa5ae472eaa))

## Changelog

All notable changes to this project will be documented in this file.
