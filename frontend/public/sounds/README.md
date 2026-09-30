# Draft room sounds

Drop an `.mp3` here with one of these names and the draft room plays it instead of its
built-in sound. Delete the file to go back. No code changes or restarts needed; a viewer
picks new files up on their next page load.

| File | Plays when |
|---|---|
| `bid-1.mp3` | The first bid on the player up for auction |
| `bid-2.mp3` | The second bid on that player |
| `bid-3.mp3` | The third |
| `bid-4.mp3` | The fourth |
| `bid-5.mp3` | The fifth |
| `bid-6.mp3` | The sixth, and every bid after it |
| `round-start.mp3` | Bidding opens on a player |
| `tick.mp3` | Each of the last five seconds of the clock |
| `won.mp3` | Your team gets the player (only the captain who won hears it) |
| `time-up.mp3` | A player is settled and it wasn't your team (everyone else) |
| `lock-in.mp3` | A captain nominates a player |
| `your-turn.mp3` | It's your turn to nominate |

The bid levels climb like a kill banner and reset for each new player. Missing a level?
The highest one below it plays instead (with only `bid-1.mp3`, every bid plays that).
The number of levels is `BID_LEVELS` in `lib/sounds.ts`, and how loud they play is
`BID_VOLUME` there (0 to 1). `LOCK_IN_VOLUME` sets the lock-in sound's volume the same way.

Keep files short (under a second or two) and small: every viewer downloads them all on
their first click in the draft room.
