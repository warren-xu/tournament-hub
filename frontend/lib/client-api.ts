"use client";

import type { ApiError } from "./types";

/** Thrown for any non-2xx response so callers can surface the backend's own wording. */
export class ApiCallError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiCallError";
  }
}

/**
 * Browser calls go to relative paths, which next.config.ts rewrites to Spring.
 * Same-origin means the session cookie rides along with no extra work.
 */
export async function api<T>(
  path: string,
  init: RequestInit & { json?: unknown } = {},
): Promise<T> {
  const { json, ...rest } = init;
  const res = await fetch(path, {
    ...rest,
    headers: {
      ...(json !== undefined ? { "content-type": "application/json" } : {}),
      ...rest.headers,
    },
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });

  const text = await res.text();
  const parsed: unknown = text ? safeParse(text) : null;

  if (!res.ok) {
    const message =
      (parsed as ApiError | null)?.message ?? `Request failed (${res.status})`;
    throw new ApiCallError(message, res.status);
  }

  return parsed as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
