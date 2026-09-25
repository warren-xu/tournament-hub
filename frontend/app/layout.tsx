import type { Metadata } from "next";
import { Inter, Montserrat } from "next/font/google";
import { SiteHeader } from "@/components/site-header";
import { getMe } from "@/lib/server-api";
import "./globals.css";

// Montserrat for headings, Inter for text.
const display = Montserrat({
  variable: "--font-heading",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
});

const body = Inter({
  variable: "--font-body",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Warrenament — Valorant tournament hub",
  description:
    "Player profiles, team rosters and a live auction draft for Valorant tournaments.",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const me = await getMe();

  return (
    <html
      lang="en"
      className={`${display.variable} ${body.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <a href="#main-content" className="sr-only focus:not-sr-only focus:bg-panel focus:p-4">Skip to content</a>
        <SiteHeader me={me} />
        <main id="main-content" className="flex-1">{children}</main>
        <footer className="border-t border-line-soft py-6">
          <div className="mx-auto max-w-[1400px] px-6">
            <p className="text-xs text-dim">
              Warrenament — not affiliated with Riot Games.
            </p>
          </div>
        </footer>
      </body>
    </html>
  );
}
