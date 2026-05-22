# SlimeFunX

SlimeFunX is a fork of Slimefun4 that has grown into its own direction.

It is not a small patch set on top of old code. The goal is to keep the classic Slimefun feel that people are familiar with, while rebuilding large parts of the internals so the project is easier to maintain and less dependent on old fragile behavior.

For players, many of the machines, progression lines, and workflows should still feel familiar. For server owners and developers, the important difference is that the internal logic is clearer and many long-standing problems are handled more directly.

This project is not trying to turn Slimefun into something unrecognizable. It is trying to keep what was worth keeping, and replace the parts that kept causing trouble.

## What makes SFX different

The biggest difference is not a few extra items or some balance tweaks. The real difference is how the project is built internally.

### Machines run on explicit tick / state / session logic

Many machines now run on their own clear tick, state, and session flow instead of leaning too much on scattered world events, container snapshots, or temporary state guesses.

That leads to a few practical benefits:

- machine state is more stable
- behavior is easier to follow
- problems are easier to debug

### Block and machine data have their own data layer

SFX has dedicated block data, player data, and GPS data storage instead of treating vanilla `BlockState` as the only source of truth.

This is one of the biggest differences between SFX and many more traditional Slimefun branches. A lot of old block state problems, machine desync, and cases where visible state and internal state drift apart are tied to exactly this area.

### Many machine inventories are backed by virtual containers

Some machines do not write every step directly into a live block inventory. Instead, they work through virtual containers and internal state first.

That reduces unnecessary world interaction and helps avoid a number of inventory sync issues and strange edge cases.

### Energy and cargo are handled as topology-driven networks

Energy and cargo do not just scan nearby blocks and guess connections on the fly. They use more explicit topology and connectivity logic.

That makes the network state easier to reason about:

- connections are clearer
- conflicts and disconnects are easier to detect
- network behavior is more predictable

### Item identity is handled more strictly

SFX tries to rely less on display names and lore, and more on explicit item definitions and registry identity.

That means language changes, text cleanup, and GUI changes are much less likely to break item logic.

### The guide and machine GUIs were rebuilt with clarity in mind

A lot of guide pages, machine UIs, and status displays were rebuilt. The point was not just to make them look different, but to make them easier to understand.

The idea is simple:

- players should be able to tell what a machine is doing
- if something is blocked, that should be visible
- the guide should feel like a tool, not just a display page

### The project tries to avoid unnecessary world interaction

This is an important part of SFX even if it is not something that fits well into a slogan.

Many systems try to avoid:

- unnecessary block state updates
- relying too much on world events to keep machine logic alive
- touching the real world state when an internal state model is enough

The goal is not to claim that everything is magically lag-free. The goal is to reduce the kind of overhead and edge cases that should not have existed in the first place.

## Major areas of change so far

Some of the bigger changes in SFX so far include:

- a more explicit machine tick / update system
- rebuilt and cleaned-up guide and GUI pages
- stricter item identity handling with less dependence on display names and lore
- virtual-container-backed machine internals
- explicit energy and cargo topology handling
- dedicated block data, player data, and GPS data layers
- fewer unnecessary world interactions
- a number of balance adjustments where old behavior felt too rough or too weak
- classic-style additions and rewrites such as Bio Generator II and a rewritten jetpack system
- newer systems such as Ancient Altar, GPS, Industrial Miner, Hologram Projector, and Infused Hopper integrated into the same runtime structure

## What this project wants to keep

SFX is not trying to throw away the classic Slimefun experience.

It wants to keep the parts people actually came for:

- familiar progression
- familiar machines
- familiar automation loops
- the classic Slimefun feel

If the only goal was to be different for the sake of being different, this project would not be worth doing.

## What this project is trying to fix

SFX is much more interested in dealing with the old problems that players and server owners have seen for years, such as:

- block state issues
- machine desync
- strange inventory and container edge cases
- item logic depending too much on display names and lore
- unclear energy and cargo network behavior
- machine logic that depends too heavily on the surrounding world state

In short, this project is not trying to replace the Slimefun experience with something else. It is trying to keep the fun parts and gradually replace the parts that kept breaking down.

## Current status

SlimeFunX is no longer a tiny side branch with a few edits.

At this point it is better described as its own implementation line:

- formally, it is still a fork of Slimefun4
- practically, it has already diverged a long way from upstream
- it does not try to preserve upstream code structure
- it is not pretending to be the official project

If you want something that stays as close as possible to official upstream Slimefun4, this repository is not that.

If you want to see what a Slimefun project looks like when the classic gameplay is kept but the runtime underneath it is heavily rebuilt, that is what this repository is for.

## Build

Current environment:

- Java 21
- Gradle
- Paper 1.21.x

Compile:

```bash
./gradlew compileJava
```

Build:

```bash
./gradlew build
```

## Installation

Build the plugin jar and place it in your server's `plugins` directory.

Released builds can be found on the Releases page.

## Relation to Slimefun4

SlimeFunX comes from Slimefun4, but it is not a lightweight compatibility branch.

The more accurate description is that it is a heavily diverged fork that tries to keep the classic gameplay experience while rebuilding the core runtime underneath it.

For players, it tries to stay familiar. For the internals, it no longer follows the old upstream model of stacking more patches on top of old assumptions.
