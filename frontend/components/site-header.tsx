"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { Me } from "@/lib/types";
import { Avatar } from "./ui";

const NAV = [
  { href: "/", label: "Players" },
  { href: "/tournaments", label: "Tournaments" },
  { href: "/profile", label: "My Profile" },
];

const ADMIN_NAV = [{ href: "/admin", label: "Admin" }];

export function SiteHeader({ me }: { me: Me | null }) {
  const pathname = usePathname();
  const nav = me?.role === "ADMIN" ? [...NAV, ...ADMIN_NAV] : NAV;

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-ink/95 backdrop-blur-[2px]">
      <div className="mx-auto flex min-h-14 max-w-[1400px] flex-wrap items-center gap-x-6 gap-y-2 px-6 py-2">
        <Link href="/" className="flex items-center gap-2.5">
          <span aria-hidden className="block h-4 w-1.5 bg-accent" />
          <span className="font-display text-lg font-semibold uppercase tracking-[0.12em]">
            Warrenament
          </span>
        </Link>

        {/* Scrolls sideways on narrow screens. overflow-x forces overflow-y to scroll too, so
            nothing may poke out below the links, or a vertical scrollbar appears. */}
        <nav className="order-3 flex w-full items-center gap-1 overflow-x-auto overflow-y-hidden sm:order-none sm:w-auto" aria-label="Main">
          {nav.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : item.href === "/tournaments"
                  ? pathname.startsWith("/tournaments") || pathname.startsWith("/t/")
                  : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`relative inline-flex min-h-11 shrink-0 items-center px-3 py-2 font-display text-sm font-semibold uppercase tracking-wider transition-colors ${
                  active ? "text-bone" : "text-muted hover:text-bone"
                }`}
              >
                {item.label}
                {active ? (
                  <span
                    aria-hidden
                    className="absolute inset-x-3 bottom-0 block h-0.5 bg-accent"
                  />
                ) : null}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          {me ? (
            <>
              <div className="flex items-center gap-2.5">
                <Avatar src={me.avatarUrl} name={me.username} size={28} />
                <div className="hidden max-w-32 truncate leading-tight md:block">
                  <p className="text-sm text-bone">{me.username}</p>
                  {me.role === "ADMIN" ? (
                    <p className="font-display text-[0.625rem] font-semibold uppercase tracking-widest text-signal">
                      Admin
                    </p>
                  ) : null}
                </div>
              </div>
              <form action="/api/logout" method="post">
                <button
                  type="submit"
                  className="font-display text-sm font-semibold uppercase tracking-wider text-dim transition-colors hover:text-bone"
                >
                  Sign out
                </button>
              </form>
            </>
          ) : (
            <Link
              href="/signin"
              className="corner-cut-sm inline-flex min-h-11 items-center bg-accent px-4 py-2 font-display text-sm font-semibold uppercase tracking-wider text-white transition-colors hover:bg-accent-deep"
            >
              Sign in
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
