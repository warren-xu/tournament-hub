import { redirect } from "next/navigation";

// Tournaments are the home page now; keep old links and bookmarks working.
export default function TournamentsPage() {
  redirect("/");
}
