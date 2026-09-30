"use client";

import { Client, type IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./client-api";
import type {
  AuctionMessage,
  AuctionSnapshot,
  BidError,
  LotView,
} from "./types";

export type ConnectionState = "connecting" | "live" | "reconnecting" | "offline";

/** How the last player went, kept on screen until the next one goes up. */
export interface LastResult {
  lot: LotView;
  note: string | null;
}

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";

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
  const [rejection, setRejection] = useState<BidError | null>(null);
  const [lastResult, setLastResult] = useState<LastResult | null>(null);

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

      // A closed lot leaves the block; the result panel carries it from here.
      const lotIsCurrent = message.type !== "LOT_CLOSED" && message.lot !== null;

      return {
        ...current,
        status: message.status ?? current.status,
        teams: message.teams ?? current.teams,
        currentLot: lotIsCurrent ? message.lot : null,
        // Newest bid on top; a new player starts with a clean list.
        recentBids:
          message.type === "LOT_OPENED" ? []
            : message.type === "BID_PLACED" && message.bid ? [message.bid, ...current.recentBids]
              : current.recentBids,
        // A player who goes unsold is still to be placed, so they go back on the count.
        pendingLots:
          message.type === "LOT_OPENED"
            ? Math.max(0, current.pendingLots - 1)
            : message.type === "LOT_CLOSED" && message.lot?.status === "UNSOLD"
              ? current.pendingLots + 1
              : current.pendingLots,
      };
    });

    // Whose turn it is and their pick change on these, and neither rides in the message
    // itself: fetch the room's state rather than guess.
    if (message.type === "STATUS_CHANGED" || message.type === "LOT_CLOSED") void resync();

    if (message.type === "LOT_OPENED") {
      // A new player: nothing of the last one carries over.
      setLastResult(null);
      setRejection(null);
    }

    if (message.type === "LOT_CLOSED" && message.lot) {
      setLastResult({ lot: message.lot, note: message.note });
    }

  }, [resync]);

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

  /** Sends a bid. The room (this captain included) hears about it from the broadcast. */
  const submitBid = useCallback(
    (lotId: number, amount: number) => {
      if (!clientRef.current?.connected) return;
      setRejection(null);
      clientRef.current?.publish({
        destination: `/app/auction/${auctionId}/bid`,
        body: JSON.stringify({ lotId, amount }),
      });
    },
    [auctionId],
  );

  return {
    snapshot,
    connection,
    rejection,
    lastResult,
    submitBid,
    resync,
    applySnapshot,
    clockOffset,
  };
}
