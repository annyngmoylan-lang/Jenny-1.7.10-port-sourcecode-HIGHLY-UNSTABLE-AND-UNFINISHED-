# Port status

This build is an incomplete compatibility port, not feature parity with the supplied 1.12.2 mod.

## Source and assets

- The input JAR identifies itself as Fapcraft/Jenny Mod 1.1.0 for Forge 1.12.2.
- The project keeps a 298-class 1.12.2 reverse-engineered source baseline under `src/main/java` for reference. It is not compiled into the 1.7.10 JAR.
- The supplied ZIP has 128 Java files for GeckoLib and its examples, but no Jenny entity source. Its 915 `assets/sexmod` files match the staged assets byte for byte, including character textures, geometry, animation JSON, sound events, and audio files.
- The compiled 1.7.10 implementation is in `src/port/java` and uses Forge 1.7.10 / 10.13.4.1614.

## Implemented in the compatibility build

- Registers 12 character entity types, their spawn items, and limited biome spawning for Slime, Bee, and Manglelie.
- Gives those entities basic wandering and look-at AI.
- Reads the included Bedrock geometry and per-face UVs, including box UV layouts, and renders it using GeckoLib's world transform and pivot rotation order. Face UV mirroring and rotations now follow the source conventions, with index checks that fix the reported index-8 render crash. Jenny, Ellie, Bia, and Slime spawn with dressed models and can switch to their nude variants. The alternate Steve/player and item rigs are hidden by default to avoid drawing duplicate detached bodies.
- Resets OpenGL lighting, blend, depth, and color state around each rendered NPC, applies the entity lightmap, and normalizes face normals. This addresses the stale-state/emissive appearance in the port renderer; in-game shader verification is still outstanding.
- Samples supplied character animation JSON for position, rotation, and scale tracks, including static channel arrays, pre/post keyframes, hold-last-frame clips, and easing names present in the assets. NPCs automatically use `idle` / `walk` clips where available. Nearby players can use `/sexmod nearest <animation|auto|list>` or open an action screen by right-clicking an NPC. Jenny's screen offers source actions and prices (3 emeralds, 2 ender pearls, 2 diamonds, or 1 gold ingot to strip). Menus also map Bia, Ellie, Luna, Allie, Kobold, and Galath actions; Luna and Kobold purchases use their source item costs. Every included animation clip remains available in the paged selector. Purchases and model changes are validated and applied server-side; selected loops can queue behind supported outfit transitions.
- Ignores direct player damage so ordinary hits do not kill and remove these compatibility entities.
- Maps 231 keyframe cue names from the original entity sound handlers to sound events across the supported character types. Cues with no direct source sound mapping still have no sound or action behavior.
- Downloads Minecraft 1.7.10 client/server JARs over HTTPS with checksum checks. The asset-index task also uses Mojang's content-addressed HTTPS URL because ForgeGradle 1.2's old S3 endpoint is retired.

## Not yet ported

The action screen and Jenny action subset are compatibility behavior, not the original full state machine. Player participation and player rigs, dialogue, the original animated GUI, character-specific AI, scene movement and synced state, structures, combat variants, item/equipment interactions, many outfit variants, particles, and some timed sound effects are still missing. Other characters' menus expose clips directly; that does not recreate their source gameplay or guarantee every clip has its original prerequisites. This build remains an incomplete compatibility port and does not have one-to-one feature parity.

## Verification

`gradlew build` succeeds on Java 8 and produces the reobfuscated Forge 1.7.10 JAR. A client launch reaches Minecraft, but the development client tries to read the default `%AppData%\.minecraft\assets` directory, which is outside this workspace's granted filesystem access. I could not enter a world and visually confirm the models, menus, lighting, or animation poses in-game. Multiplayer and server behavior are also unverified.
