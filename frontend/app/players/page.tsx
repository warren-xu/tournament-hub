import { redirect } from "next/navigation";

// The player pool is the home page now; keep old links and bookmarks working.
export default function PlayersPage() {
  redirect("/");
}
