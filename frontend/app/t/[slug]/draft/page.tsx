import Link from "next/link";
import { notFound } from "next/navigation";
import { AuctionRoom } from "./auction-room";
import { EmptyState, Eyebrow, LinkButton, OfflineNotice } from "@/components/ui";
import {
  backendReachable,
  getAuctionByTournament,
  getMe,
  getRanks,
  getTournamentBySlug,
} from "@/lib/server-api";

export default async function DraftPage(props: PageProps<"/t/[slug]/draft">) {
  const { slug } = await props.params;

  if (!(await backendReachable())) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-12">
        <OfflineNotice />
      </div>
    );
  }

  const tournament = await getTournamentBySlug(slug);
  if (!tournament) notFound();

  const [me, auction, ranks] = await Promise.all([
    getMe(),
    getAuctionByTournament(tournament.id),
    getRanks(true),
  ]);

  if (!auction) {
    return (
      <div className="mx-auto max-w-[1400px] px-6 py-12">
        <Link
          href={`/t/${slug}`}
          className="font-display text-sm uppercase tracking-wider text-dim transition-colors hover:text-bone"
        >
          ← {tournament.name}
        </Link>
        <div className="mt-8">
          <EmptyState
            title="No draft yet"
            detail="An admin still has to create the auction and build the queue for this tournament."
            action={<LinkButton href={`/t/${slug}`}>Back to the tournament</LinkButton>}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[1400px] px-6 py-8">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Link
            href={`/t/${slug}`}
            className="font-display text-sm uppercase tracking-wider text-dim transition-colors hover:text-bone"
          >
            ← {tournament.name}
          </Link>
          <h1 className="mt-2 text-4xl uppercase leading-none tracking-tight">
            Auction draft
          </h1>
        </div>
        <div className="text-right">
          <Eyebrow>Rules</Eyebrow>
          <p className="tabular mt-1 text-sm text-muted">
            {tournament.creditBudget} credits · {tournament.rosterSize} per roster ·
            min bid {tournament.minBid}
          </p>
        </div>
      </div>

      <AuctionRoom
        initial={auction}
        me={me}
        ranks={ranks ?? []}
        rosterSize={tournament.rosterSize}
        creditBudget={tournament.creditBudget}
      />
    </div>
  );
}
