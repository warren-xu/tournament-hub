/** Every card is a plain square; rendering, collisions and hit areas share these vertices. */
export const CARD_SQUARE = "0% 0%, 100% 0%, 100% 100%, 0% 100%";

/** Card border colors, independent of the official rank badge artwork. */
const COLORS: Record<string, string> = {
  iron: "#a6b0b8",
  bronze: "#d49b70",
  silver: "#c9d7df",
  gold: "#e3c46f",
  platinum: "#83cec9",
  diamond: "#b8a1ed",
  ascendant: "#8ad7b3",
  immortal: "#f294a4",
  radiant: "#f3e5a3",
};

export function rankStyle(name: string | null) {
  const division = (name ?? "").trim().toLowerCase().replace(/\s+\d+$/, "");
  return { color: COLORS[division] ?? "#a9b6c1" };
}

/** Role colors supplement the named main agent and its portrait. */
export function agentRoleColor(role?: string) {
  const colors: Record<string, string> = {
    duelist: "#f0a28f", initiator: "#b4cc8f", controller: "#bcaaf0", sentinel: "#88cbd9",
  };
  return colors[role?.toLowerCase() ?? ""] ?? "#bdc8d0";
}
