# RaindanceMC/guilds

- **`guilds-module`** — Rain's Flexible Modules — Guilds: Guilds: founded by players, with a ladder of ranks each member climbs, a bank, levels earned by playing, alliances and rivals, a guild home, guild/officer/ally chat and a tag in front of every member's name.
- **`guilds-standalone`** — Rain's Guilds: Rain's Guilds, as a plugin of its own: the guilds module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `guilds-standalone/target/RainsGuilds-0.16.0.jar` — also published as `de.raindancer.modules:guilds-standalone:0.16.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
