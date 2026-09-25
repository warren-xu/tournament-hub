import type { NextConfig } from "next";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  images: {
    qualities: [75, 90],
    // Discord serves every avatar from this host.
    remotePatterns: [
      // Discord avatars.
      { protocol: "https", hostname: "cdn.discordapp.com" },
      // Agent portraits, from the synced roster.
      { protocol: "https", hostname: "media.valorant-api.com" },
    ],
  },

  // Proxy the Spring Boot API through this origin so the session cookie is
  // first-party on every request. No CORS config, no token handling in the browser.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${BACKEND}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${BACKEND}/oauth2/:path*` },
      { source: "/login/:path*", destination: `${BACKEND}/login/:path*` },
    ];
  },
};

export default nextConfig;
