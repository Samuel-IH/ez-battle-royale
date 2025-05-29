Dear AI Agent, here's what you need to know about this project, in a quick, to-the-point, no-nonsense format:

- It's a minecraft mod, that uses Forge.
- As such, it primarily uses Java.
- It's a mod that adds battle royale capability to any world, and provides tools
  to make it easy for admins or modpack devs to build battle royale experiences.
- Most of the mod's code can usually be found someplace under ./src/main/java/com/samuelih/ezroyale/
- The current version of this mod has builtin support for TacZ and Lost Cities via:
  - (TacZ) supported via custom loot tables that use TacZ weapons and attachments
  - (Lost Cities) supported via lost cities override that makes it use our loot table.

An overview of the current features, and how they're implemented:
- A storm that shrinks, and is configurable (ShrinkingStorm, Config, config/*)
- A small set of predefined teams, named by color, with commands that allow players to switch before the round starts.
- Teammates glow, but only when they're on your team.
- Hiding of player nametags, to prevent seeing players through walls.
- Custom per-round loot, implemened via tagging each lootable with a UUID and regenerating the loot each round (lazily).
- Team-respecting portal-like player pings, you can ping your team by hitting the ping key and they will see the notification.