"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiCallError } from "@/lib/client-api";
import { buttonClass } from "@/components/ui";

/**
 * Local shortcut onto the backend's dev-profile login. Driving an auction needs
 * several signed-in captains at once, which is not workable with real Discord
 * accounts. Hidden unless NEXT_PUBLIC_DEV_AUTH is set.
 */
export function DevSignIn() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [admin, setAdmin] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  if (process.env.NEXT_PUBLIC_DEV_AUTH !== "true") return null;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!username.trim()) return;
    setPending(true);
    setError(null);
    try {
      await api(
        `/api/dev/login?username=${encodeURIComponent(username.trim())}&admin=${admin}`,
        { method: "POST" },
      );
      router.push("/");
      router.refresh();
    } catch (err) {
      setError(
        err instanceof ApiCallError
          ? err.message
          : "Could not reach the backend. Is it running?",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <form
      onSubmit={submit}
      className="mt-10 border border-dashed border-line px-5 py-5"
    >
      <p className="eyebrow">Local development only</p>
      <p className="mt-2 text-sm text-muted">
        Sign in as anyone without Discord. Open a second browser profile to bid
        against yourself.
      </p>

      <div className="mt-4 flex gap-2">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="username"
          aria-label="Development username"
          className="tabular min-w-0 flex-1 border border-line bg-ink px-3 py-2 text-sm text-bone placeholder:text-dim focus:border-accent focus:outline-none"
        />
        <button
          type="submit"
          disabled={pending || !username.trim()}
          className={buttonClass("default")}
        >
          {pending ? "…" : "Enter"}
        </button>
      </div>

      <label className="mt-3 flex items-center gap-2 text-sm text-muted">
        <input
          type="checkbox"
          checked={admin}
          onChange={(e) => setAdmin(e.target.checked)}
          className="size-4 accent-[var(--color-accent)]"
        />
        Sign in as an admin (can run the draft)
      </label>

      {error ? (
        <p role="alert" className="mt-3 text-sm text-signal">
          {error}
        </p>
      ) : null}
    </form>
  );
}
