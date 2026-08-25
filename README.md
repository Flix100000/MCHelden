# MCHelden

Server-Mod für das **Helden Projekt Morten**.

Minecraft 1.21.1 · NeoForge 21.1.248 · Java 21

## Umfang

Die Mod baut ausschließlich Spielsysteme. Sie fasst keine Weltgeneration an, sperrt keine Items
und bringt kein eigenes Datapack mit — das übernimmt History Stages.

- Herzen-System (3 Leben, das 4. über das Bounty)
- Combat-Timer mit GUI-Sperre und Item-Verbrauchslimit
- Grave mit 50/50-Split
- Bounty als gegenseitiges Duell
- Phasensteuerung per Command (Aufbau, Krieg, Final War)
- Trennwand, Safezone, Spielzeit-Limit, ausgeglichener Startspawn

Das vollständige Regelwerk liegt in der Design-Spec unter `docs/` (nicht im Repo).

## Entwicklung

```
gradlew runClient
gradlew runServer
gradlew build
```

Bei Problemen mit Abhängigkeiten hilft `gradlew --refresh-dependencies`.

## Mappings

Die Mod nutzt die offiziellen Mojang-Mappings. Deren Lizenz steht unter
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md
