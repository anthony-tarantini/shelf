export enum MediaType {
  EBOOK = 'EBOOK',
  AUDIOBOOK = 'AUDIOBOOK',
}

export interface Chapter {
  id: string;
  title: string;
  startTime?: number;
  endTime?: number;
  index?: number;
}

export interface MetadataRecord {
  id: string;
  bookId: string;
  title: string;
  description?: string;
  publisher?: string;
  published?: number;
  language?: string;
  genres: string[];
  moods: string[];
}

export interface Edition {
  id: string;
  bookId: string;
  format: MediaType;
  path: string;
  narrator?: string;
  translator?: string;
  isbn10?: string;
  isbn13?: string;
  asin?: string;
  pages?: number;
  totalTime?: number;
  size: number;
}

export interface EditionWithChapters {
  edition: Edition;
  chapters: Chapter[];
}

export interface MetadataAggregate {
  metadata: MetadataRecord;
  editions: EditionWithChapters[];
}

export interface BookRoot {
  id: string;
  title: string;
  coverPath?: string;
}

export interface BookAggregate {
  book: BookRoot;
  authors: AuthorRoot[];
  series: BookSeriesEntry[];
  metadata?: MetadataAggregate | null;
  userState?: BookUserState | null;
}

export interface BookPage {
  items: BookAggregate[];
  totalCount: number;
  page: number;
  size: number;
}

export type BookResult = BookSummary | BookAggregate;

export interface GlobalSearchResult {
  books: BookSummary[];
  authors: AuthorSummary[];
  series: SeriesSummary[];
}

export interface BookSummary {
  id: string;
  title: string;
  coverPath?: string;
  authorNames: string[];
  seriesName?: string;
  seriesIndex?: number;
  userState?: BookUserState | null;
}

export interface AuthorRoot {
  id: string;
  name: string;
  imagePath?: string;
}

export interface AuthorAggregate {
  author: AuthorRoot;
  books: BookSummary[];
}

export interface AuthorSummary {
  id: string;
  name: string;
  bookCount: number;
  imagePath?: string;
}

export interface ExternalAuthorResult {
  id: string;
  name: string;
  imageUrl?: string;
}

export interface AuthorPage {
  items: AuthorSummary[];
  totalCount: number;
  page: number;
  size: number;
}

export interface SeriesRoot {
  id: string;
  name: string;
  coverPath?: string;
}

export interface BookSeriesEntry {
  id: string;
  name: string;
  coverPath?: string;
  index?: number;
}

export interface SeriesAggregate {
  series: SeriesRoot;
  books: BookSummary[];
  authors?: AuthorRoot[];
}

export interface SeriesSummary {
  id: string;
  name: string;
  coverPath?: string;
  bookCount: number;
}

export interface SeriesPage {
  items: SeriesSummary[];
  totalCount: number;
  page: number;
  size: number;
}

export interface LibraryRoot {
  id: string;
  userId: string;
  title: string;
}

export interface LibraryAggregate {
  library: LibraryRoot;
  bookIds: string[];
}

export interface LibrarySummary {
  id: string;
  userId: string;
  title: string;
  bookCount: number;
}

export enum UserRole {
  ADMIN = 'ADMIN',
  USER = 'USER',
}

export interface UserRoot {
  id: string;
  email: string;
  username: string;
  role: UserRole;
}

export interface UserResponse {
  user: UserRoot;
  token: string;
}

export type ReadingProgressKind = 'EBOOK' | 'AUDIOBOOK';

export interface ReadingProgress {
  kind?: ReadingProgressKind | null;
  cfi?: string | null;
  positionSeconds?: number | null;
  durationSeconds?: number | null;
  progressPercent?: number | null;
}

export type ReadStatus = 'UNREAD' | 'READING' | 'FINISHED' | 'ABANDONED' | 'QUEUED';

export interface BookUserState {
  readStatus: ReadStatus;
}

export interface StagedSeries {
  name: string;
  index?: number;
}

export interface StagedEditionMetadata {
  storagePath?: string;
  isbn10?: string;
  isbn13?: string;
  asin?: string;
  narrator?: string;
  pages?: number;
  totalTime?: number;
}

export interface StagedBook {
  id: string;
  userId: string;
  title: string;
  authors: string[];
  authorSuggestions: Record<string, AuthorRoot[]>;
  selectedAuthorIds: Record<string, string | null>;
  storagePath: string;
  coverPath?: string;
  description?: string;
  publisher?: string;
  publishYear?: number;
  genres: string[];
  moods: string[];
  series: StagedSeries[];
  ebookMetadata?: StagedEditionMetadata;
  audiobookMetadata?: StagedEditionMetadata;
  mediaType: MediaType;
  chapters: Chapter[];
  size: number;
  createdAt: string;
}

export interface StagedBookPage {
  items: StagedBook[];
  totalCount: number;
  page: number;
  size: number;
}

export type ImportScanStatus = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface FailedFileDetail {
  fileName: string;
  errorMessage: string;
}

export interface WarningDetail {
  fileName: string;
  field: string;
  message: string;
}

export interface ImportScanProgress {
  runId: string;
  status: ImportScanStatus;
  sourcePath: string;
  totalFiles: number;
  queuedFiles: number;
  completedFiles: number;
  failedFiles: number;
  failedFileDetails: FailedFileDetail[];
  startedAt: string;
  finishedAt?: string | null;
}

export type BatchStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface BatchProgress {
  runId: string;
  status: BatchStatus;
  action: string;
  totalItems: number;
  completedItems: number;
  failedItems: number;
  failedItemDetails: FailedFileDetail[];
  warningItems: number;
  warningDetails: WarningDetail[];
  startedAt: string;
  finishedAt?: string | null;
}

export enum StagedBatchAction {
  PROMOTE = 'PROMOTE',
  DELETE = 'DELETE',
}

export interface StagedBatchRequest {
  ids: string[];
  action: StagedBatchAction;
}

// Podcast types
export interface PodcastSummary {
  id: string;
  seriesId: string;
  seriesTitle: string;
  feedUrl: string;
  episodeCount: number;
  autoSanitize: boolean;
  autoFetch: boolean;
  lastFetchedAt?: string;
  version: number;
  coverPath?: string;
}

export enum CredentialStatus {
  HAS_CREDENTIAL = 'HAS_CREDENTIAL',
  NO_CREDENTIAL = 'NO_CREDENTIAL',
}

export interface EpisodeEntry {
  bookId: string;
  title: string;
  season: number;
  episode: number;
  sanitizationStatus?: string;
  coverPath?: string;
  totalTime?: number;
  publishedAt?: string;
}

export interface PodcastAggregate {
  podcast: SavedPodcastRoot;
  seriesId: string;
  seriesTitle: string;
  episodes: EpisodeEntry[];
  credential: CredentialStatus;
}

export interface SavedPodcastRoot {
  id: string;
  seriesId: string;
  feedUrl: string;
  feedToken: string;
  feedTokenExpiresAt?: string;
  autoSanitize: boolean;
  autoFetch: boolean;
  lastFetchedAt?: string;
  fetchIntervalMinutes: number;
  version: number;
}

export type SavedPodcastAggregate = PodcastAggregate;

// Backward compatibility aliases to minimize breaking changes during migration
export interface ExternalContributor {
  id: string;
  name: string;
  type?: string;
}

export interface ExternalGenre {
  id: string;
  name: string;
}

export interface ExternalPublisher {
  id: string;
  name: string;
}

export interface ExternalSeries {
  id: string;
  name: string;
  description?: string;
  position?: number;
}

export interface ExternalBook {
  isbn10?: string;
  isbn13?: string;
  publisher?: ExternalPublisher;
  contributors?: ExternalContributor[];
  asin?: string;
}

export interface ExternalEbook extends ExternalBook {
  pages?: number;
}

export interface ExternalAudiobook extends ExternalBook {
  seconds?: number;
}

export interface ExternalMetadata {
  id: string;
  title: string;
  contributors?: ExternalContributor[];
  description?: string;
  publisher?: ExternalPublisher;
  releaseYear?: number;
  imageUrl?: string;
  genres?: ExternalGenre[];
  seriesName?: ExternalSeries[];
  defaultEbook?: ExternalEbook;
  defaultAudiobook?: ExternalAudiobook;
}
