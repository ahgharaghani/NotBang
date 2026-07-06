# 🤠 NotBang

The Wild West card game **Bang!** as a multiplayer Java webapp. Create a room, share the
4-letter code with 3–7 friends, and shoot it out in the browser — no installs needed.

## Quick start

Requirements: Java 21+ and Maven.

```bash
mvn package
java -jar target/notbang-1.0.0.jar
```

Then open <http://localhost:8080> (set the `PORT` env variable to change the port).
One player clicks **Create game** and shares the room code; everyone else joins with it.

For development you can also run it straight from Maven:

```bash
mvn compile exec:java -Dexec.mainClass=com.notbang.Main
```

## How it works

- **Backend** — Java 21, [Javalin](https://javalin.io) (embedded Jetty). The full game
  engine lives in `com.notbang.game` and is pure Java with no framework dependencies.
- **Multiplayer** — a single WebSocket endpoint (`/ws`). Each player receives a
  personalized view of the game state: you never receive other players' hands or hidden
  roles over the wire, so cheating via devtools is impossible.
- **Frontend** — vanilla HTML/CSS/JS served from the same jar. No build step.
- **Reconnects** — refresh the page mid-game and you get your seat and hand back.

## What's implemented

- The full 80-card base deck: Bang!, Missed!, Beer, Panic!, Cat Balou, Duel, Gatling,
  Indians!, Stagecoach, Wells Fargo, Saloon, General Store, Barrel, Scope, Mustang,
  Jail, Dynamite and all 5 weapons.
- Roles (Sheriff, Deputies, Outlaws, Renegade) with proper win conditions, elimination
  rewards and penalties.
- 13 characters: Bart Cassidy, Black Jack, Calamity Janet, El Gringo, Jourdonnais,
  Lucky Duke, Paul Regret, Rose Doolan, Sid Ketchum, Slab the Killer, Suzy Lafayette,
  Vulture Sam and Willy the Kid.
- Distance & weapon range, turn phases, hand-limit discards, "draw!" checks
  (Barrel, Jail, Dynamite), dying-with-Beer saves, duels, and more.

Simplifications vs. the tabletop game: Lucky Duke auto-picks the better "draw!" result,
and characters whose ability requires extra draw-phase choices (Jesse Jones, Kit Carlson,
Pedro Ramirez) are not in the pool.

## Tests

```bash
mvn test
```

Includes unit tests for the rules plus a fuzz test that plays 300 complete random games
and checks invariants (card conservation, health bounds, termination) after every action.
