import { browser } from '$app/environment';
import { t } from '$lib/i18n';

export type AppError = {
  type: string;
  message: string;
  code?: string;
  status?: number;
};

export type Either<E, T> = {
  left?: E;
  right?: T;
};

type RequestBody = FormData | Record<string, unknown> | Array<unknown> | string | null;

export class ApiClient {
  private readonly baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string = '/api') {
    this.baseUrl = baseUrl;
    if (browser) {
      this.token = localStorage.getItem('shelf_token');
    }
  }

  setToken(token: string | null) {
    this.token = token;
    if (browser) {
      if (token) {
        localStorage.setItem('shelf_token', token);
      } else {
        localStorage.removeItem('shelf_token');
      }
    }
  }

  getToken(): string | null {
    return this.token;
  }

  logout() {
    this.setToken(null);
  }

  private async request<T>(
    method: string,
    path: string,
    body?: RequestBody,
    isFormData: boolean = false,
    fetchFn: typeof fetch = fetch
  ): Promise<Either<AppError, T>> {
    const headers: Record<string, string> = {};

    if (!isFormData) {
      headers['Content-Type'] = 'application/json';
    }

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    try {
      // Logic-based Type Guard to satisfy Fetch's BodyInit requirement
      let finalBody: BodyInit | undefined;
      if (isFormData && body instanceof FormData) {
        finalBody = body;
      } else if (body) {
        finalBody = JSON.stringify(body);
      }

      const response = await fetchFn(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: finalBody,
      });

      if (response.status === 204) {
        return { right: {} as T };
      }

      const data = await response.json();

      if (response.ok) {
        // Handle Ktor's { data: ... } wrapper
        if (data && typeof data === 'object' && 'data' in data) {
          return { right: data.data as T };
        }
        return { right: data as T };
      } else {
        const errorData = data as Partial<AppError>;
        return { 
          left: { 
            type: errorData.type || t.get('errors.api.type'),
            message: errorData.message || t.get('errors.api.message'),
            code: errorData.code,
            status: response.status 
          } 
        };
      }
    } catch (error: unknown) {
      return {
        left: {
          type: t.get('errors.network.type'),
          message: error instanceof Error ? error.message : t.get('errors.network.message'),
          status: 500,
        },
      };
    }
  }

  get<T>(path: string, fetchFn: typeof fetch = fetch) {
    return this.request<T>('GET', path, undefined, false, fetchFn);
  }

  post<T>(path: string, body?: RequestBody, fetchFn: typeof fetch = fetch) {
    const requestBody = body ? { data: body } : undefined;
    return this.request<T>('POST', path, requestBody, false, fetchFn);
  }

  upload<T>(path: string, formData: FormData, fetchFn: typeof fetch = fetch) {
    return this.request<T>('POST', path, formData, true, fetchFn);
  }

  put<T>(path: string, body: RequestBody, fetchFn: typeof fetch = fetch) {
    const requestBody = body ? { data: body } : undefined;
    return this.request<T>('PUT', path, requestBody, false, fetchFn);
  }

  patch<T>(path: string, body: RequestBody, fetchFn: typeof fetch = fetch) {
    const requestBody = body ? { data: body } : undefined;
    return this.request<T>('PATCH', path, requestBody, false, fetchFn);
  }

  delete<T>(path: string, fetchFn: typeof fetch = fetch) {
    return this.request<T>('DELETE', path, undefined, false, fetchFn);
  }
}

export const api = new ApiClient();