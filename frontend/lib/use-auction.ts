"use client";

import { Client, type IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./client-api";
import type {
  AuctionMessage,
  AuctionSnapshot,
  BidError,
  BidView,
  LotView,
} from "./types";

export type ConnectionState = "connecting" | "live" | "reconnecting" | "offline";

export interface FeedEntry {
  id: string;
  kind: "bid" | "event";
  text: string;
  amount?: number;
  at: string | null;
}

/** A closed lot with every bid opened, kept on screen until the next player goes up. */
export interface Reveal {
  lot: LotView;
  bids: BidView[];
  note: string | null;
}

/** One player dealt out by the random fill, waiting its turn on the roulette. */
export interface Assignment {
  lotId: number;
  username: string;
  avatarUrl: string | null;
  teamId: number;
  teamName: string | null;
}

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";
const MAX_FEED = 40;

/**
 * Subscribes to an auction and keeps a local mirror of its state.
 *
 * Two things this has to get right:
 *  - Messages can arrive out of order, so a lot update older than the one already
 *    rendered is dropped using the monotonic `version` the backend stamps on it.
 *  - After any reconnect the local mirror may have missed messages entirely, so it
 *    is thrown away and replaced with a fresh snapshot from REST.
 */
export function useAuction(auctionId: number, initial: AuctionSnapshot) {
  const [snapshot, setSnapshot] = useState(initial);
  const [connection, setConnection] = useState<ConnectionState>("connecting");
  const [feed, setFeed] = useState<FeedEntry[]>(() => seedFeed(initial.recentBids));
  const [rejection, setRejection] = useState<BidError | null>(null);
  const [reveal, setReveal] = useState<Reveal | null>(null);
  /** Dealt players queue up here; the room plays them out one at a time. */
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  /** What this captain has locked in, so the UI can say so after a refresh. */
  const [yourBid, setYourBid] = useState<number | null>(initial.yourBid);

  const clientRef = useRef<Client | null>(null);
  const lotVersion = useRef<{ lotId: number; version: number } | null>(
    initial.currentLot
      ? { lotId: initial.currentLot.lotId, version: initial.currentLot.version }
      : null,
  );
  /** serverNow - clientNow, so countdowns follow the server rather than a skewed clock. */
  const clockOffset = useRef(0);

  const applySnapshot = useCallback((next: AuctionSnapshot) => {
    clockOffset.current = new Date(next.serverTime).getTime() - Date.now();
    lotVersion.current = next.currentLot
      ? { lotId: next.currentLot.lotId, version: next.currentLot.version }
      : null;
    setSnapshot(next);
    setYourBid(next.yourBid);
    setFeed(seedFeed(next.recentBids));
  }, []);

  const resync = useCallback(async () => {
    try {
      applySnapshot(await api<AuctionSnapshot>(`/api/auctions/${auctionId}`));
    } catch {
      // The socket is the primary channel; a failed resync retries on next connect.
    }
  }, [auctionId, applySnapshot]);

  const onMessage = useCallback((message: AuctionMessage) => {
    clockOffset.current = new Date(message.serverTime).getTime() - Date.now();

    setSnapshot((current) => {
      // Drop anything we have already superseded for this lot.
      if (message.lot) {
        const seen = lotVersion.current;
        if (
          seen &&
          seen.lotId === message.lot.lotId &&
          message.lot.version < seen.version
        ) {
          return current;
        }
        lotVersion.current = {
          lotId: message.lot.lotId,
          version: message.lot.version,
        };
      }

      // A closed or dealt lot leaves the block; the reveal panel carries it from here.
      const lotIsCurrent =
        message.type !== "LOT_CLOSED"
        && message.type !== "RANDOM_ASSIGNED"
        && message.lot !== null;

      return {
        ...current,
        status: message.status ?? current.status,
        teams: message.teams ?? current.teams,
        currentLot: lotIsCurrent ? message.lot : null,
        // A player who draws no bids is still to be placed, so they go back on the count.
        pendingLots:
          (message.type === "LOT_OPENED" || message.type === "RANDOM_ASSIGNED")
            ? Math.max(0, current.pendingLots - 1)
            : message.type === "LOT_CLOSED" && message.lot?.status === "UNSOLD"
              ? current.pendingLots + 1
              : current.pendingLots,
      };
    });

    setFeed((current) => {
      const entry = feedEntryFor(message);
      if (!entry) return current;
      return [entry, ...current].slice(0, MAX_FEED);
    });

    if (message.type === "BID_LOCKED") setRejection(null);

    if (message.type === "LOT_OPENED") {
      // A fresh sealed round: nothing of the last one carries over.
      setReveal(null);
      setYourBid(null);
    }

    if (message.type === "LOT_CLOSED" && message.lot) {
      setReveal({
        lot: message.lot,
        bids: message.reveal ?? [],
        note: message.note,
      });
      setYourBid(null);
    }

    if (message.type === "RANDOM_ASSIGNED" && message.lot?.winningTeamId) {
      const lot = message.lot;
      setAssignments((current) => [
        ...current,
        {
          lotId: lot.lotId,
          username: lot.player.username,
          avatarUrl: lot.player.avatarUrl,
          teamId: lot.winningTeamId!,
          teamName: lot.winningTeamName,
        },
      ]);
    }
  }, []);

  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // The socket goes straight to the backend, which in production is a different
      // site, so the session cookie does not come along. A single-use ticket, fetched
      // through the same-origin proxy before every attempt (reconnects included), proves
      // who the bidder is. Signed out, the request 401s and the socket only watches.
      beforeConnect: async () => {
        try {
          const { ticket } = await api<{ ticket: string }>("/api/ws-ticket", { method: "POST" });
          client.connectHeaders = { ticket };
        } catch {
          client.connectHeaders = {};
        }
      },
      onConnect: () => {
        setConnection("live");
        client.subscribe(`/topic/auction/${auctionId}`, (frame: IMessage) =>
          onMessage(JSON.parse(frame.body) as AuctionMessage),
        );
        client.subscribe("/user/queue/errors", (frame: IMessage) => {
          setRejection(JSON.parse(frame.body) as BidError);
          // The bid never landed, so the optimistic echo has to come back off.
          setYourBid(null);
        });
        // Anything missed while disconnected is recovered here, not replayed.
        void resync();
      },
      onWebSocketClose: () =>
        setConnection((s) => (s === "live" ? "reconnecting" : s)),
      onStompError: () => setConnection("offline"),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [auctionId, onMessage, resync]);

  /** Sends a sealed bid. Sending another before the window closes replaces it. */
  const submitBid = useCallback(
    (lotId: number, amount: number) => {
      if (!clientRef.current?.connected) return;
      setRejection(null);
      setYourBid(amount);
      clientRef.current?.publish({
        destination: `/app/auction/${auctionId}/bid`,
        body: JSON.stringify({ lotId, amount }),
      });
    },
    [auctionId],
  );

  /** Called by the roulette once a dealt player has finished landing. */
  const consumeAssignment = useCallback((lotId: number) => {
    setAssignments((current) => current.filter((a) => a.lotId !== lotId));
  }, []);

  return {
    snapshot,
    connection,
    feed,
    rejection,
    reveal,
    assignments,
    consumeAssignment,
    yourBid,
    submitBid,
    resync,
    applySnapshot,
    clockOffset,
  };
}

function seedFeed(bids: BidView[]): FeedEntry[] {
  return bids.map((bid) => ({
    id: `bid-${bid.bidId}`,
    kind: "bid" as const,
    text: `${bid.teamName ?? "A team"} bid`,
    amount: bid.amount,
    at: bid.createdAt,
  }));
}

function feedEntryFor(message: AuctionMessage): FeedEntry | null {
  // A lock-in is logged without an amount: that is the whole point of a sealed bid.
  if (message.type === "BID_LOCKED" && message.note) {
    return {
      id: `lock-${message.serverTime}-${message.note}`,
      kind: "bid",
      text: message.note,
      at: message.serverTime,
    };
  }
  if (message.note) {
    return {
      id: `event-${message.type}-${message.serverTime}`,
      kind: "event",
      text: message.note,
      at: message.serverTime,
    };
  }
  return null;
}
