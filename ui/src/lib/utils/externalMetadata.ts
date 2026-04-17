import type {ExternalContributor, ExternalMetadata} from '$lib/types/models';

function isAuthorContributor(type?: string) {
  if (!type) return true;
  const normalized = type.toUpperCase();
  return (
    normalized === 'AUTHOR' ||
    normalized.endsWith('EXTERNALAUTHOR') ||
    (!normalized.includes('NARRATOR') && !normalized.includes('TRANSLATOR'))
  );
}

export function getExternalAuthorNames(metadata: ExternalMetadata): string[] {
  const candidates: ExternalContributor[] = [
    ...(metadata.contributors ?? []),
    ...(metadata.defaultEbook?.contributors ?? []),
    ...(metadata.defaultAudiobook?.contributors ?? []),
  ];

  return [...new Set(candidates.filter((c) => isAuthorContributor(c.type)).map((c) => c.name))];
}
