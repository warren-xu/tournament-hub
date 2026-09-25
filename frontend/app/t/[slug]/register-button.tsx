"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { buttonClass, Tag } from "@/components/ui";
import { api, ApiCallError } from "@/lib/client-api";
import type { RegistrationStatus } from "@/lib/types";

export function RegisterButton({
  tournamentId,
  signedIn,
  registered,
  status,
}: {
  tournamentId: number;
  signedIn: boolean;
  registered: boolean;
  status: RegistrationStatus | null;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!signedIn) {
    return (
      <a href="/signin" className={buttonClass("default")}>
        Sign in to register
      </a>
    );
  }

  if (registered) {
    return (
      <div className="flex items-center gap-2">
        <Tag tone={status === "APPROVED" ? "accent" : "outline"}>
          {status === "APPROVED" ? "In the pool" : `Registration ${status?.toLowerCase()}`}
        </Tag>
      </div>
    );
  }

  async function register() {
    setPending(true);
    setError(null);
    try {
      await api(`/api/tournaments/${tournamentId}/registrations`, {
        method: "POST",
      });
      router.refresh();
    } catch (err) {
      setError(
        err instanceof ApiCallError ? err.message : "Could not register.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <div>
      <button
        onClick={register}
        disabled={pending}
        className={buttonClass("default")}
      >
        {pending ? "Registering…" : "Register for the draft"}
      </button>
      {error ? (
        <p role="alert" className="mt-2 max-w-60 text-sm text-signal">
          {error}
        </p>
      ) : null}
    </div>
  );
}
