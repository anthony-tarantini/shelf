import { api } from './api/client';
import { type UserResponse, type UserRoot } from './types/models';

interface SetupStatusResponse {
  complete: boolean;
}

export class AuthState {
  private user = $state<UserRoot | null>(null);
  private initialized = $state(false);
  private setupComplete = $state<boolean | null>(null);

  constructor() {
    this.init().then();
  }

  get currentUser() { return this.user; }
  get isInitialized() { return this.initialized; }
  get isSetupRequired() { return this.setupComplete === false; }

  async init() {
    if (typeof window === 'undefined') return;
    
    // Check if initial setup is needed
    const setupStatus = await api.get<SetupStatusResponse>('/setup');
    if (setupStatus.right) {
      this.setupComplete = setupStatus.right.complete;
    } else {
      // Default to assuming setup is complete if check fails,
      // to avoid locking users out due to a transient API issue.
      this.setupComplete = true;
    }

    if (this.setupComplete) {
      // If we have a token, try to fetch the current user
      const result = await api.get<UserResponse>('/users');
      if (result.right) {
        this.user = result.right.user;
        api.setToken(result.right.token);
      } else {
        api.logout();
      }
    }
    this.initialized = true;
  }

  setUser(user: UserRoot | null) {
    this.user = user;
    if (user) {
      this.setupComplete = true;
    }
  }

  logout() {
    this.user = null;
    api.logout();
  }
}

export const auth = new AuthState();
