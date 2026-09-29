import { icsFile, tournamentEvent } from "@/lib/calendar";
import { getTournamentBySlug } from "@/lib/server-api";

/** The tournament as an .ics file, for Apple Calendar, Outlook and anything else that imports one. */
export async function GET(request: Request, ctx: { params: Promise<{ slug: string }> }) {
  const { slug } = await ctx.params;
  const tournament = await getTournamentBySlug(slug);
  const event = tournament ? tournamentEvent(tournament, new URL(request.url).origin) : null;
  if (!event) return new Response("No date has been set for this tournament yet.", { status: 404 });

  return new Response(icsFile(event), {
    headers: {
      "content-type": "text/calendar; charset=utf-8",
      "content-disposition": `attachment; filename="${slug}.ics"`,
      "cache-control": "no-store",
    },
  });
}
