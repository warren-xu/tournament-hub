import { redirect } from "next/navigation";
import { DevSignIn } from "./dev-signin";
import { Eyebrow } from "@/components/ui";
import { getMe } from "@/lib/server-api";

export const metadata = { title: "Sign in — Warrenament" };

export default async function SignInPage() {
  if (await getMe()) redirect("/");

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-20">
      <div className="max-w-md">
        <div className="flex items-center gap-3">
          <span aria-hidden className="block h-px w-10 bg-accent" />
          <Eyebrow>Sign in</Eyebrow>
        </div>
        <h1 className="mt-4 text-4xl uppercase leading-none tracking-tight">
          Identify yourself
        </h1>
        <p className="mt-4 text-sm leading-relaxed text-muted">
          Warrenament uses Discord so your handle and avatar come along with you,
          and so captains know exactly who they are bidding against.
        </p>

        <a
          href="/oauth2/authorization/discord"
          className="corner-cut-sm mt-8 flex w-full items-center justify-center gap-3 bg-accent px-5 py-3.5 font-display text-base font-semibold uppercase tracking-wider text-white transition-colors hover:bg-accent-deep"
        >
          Continue with Discord
        </a>

        <DevSignIn />
      </div>
    </div>
  );
}
