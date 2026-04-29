# VelocityQueue

A Velocity proxy plugin that provides a 2b2t-style queue for routing players from a queue server to a main server when slots become available.

## Features

- Separate normal and priority queues
- Automatic queue routing when the configured main server is full
- Periodic chat, action bar, and title queue position updates
- Tab list header/footer updates with queue status and estimated wait placeholders
- Optional server-list hover query showing queue counts
- Runtime config reload command

## Requirements

- Java 21
- Velocity proxy
- A configured queue server and main server in `velocity.toml`

## Building

Use the Gradle wrapper:

```powershell
.\gradlew.bat build
```

The plugin jar is written to:

```text
build/libs/
```

## Running Locally

This project uses `xyz.jpenilla.run-velocity` for local Velocity testing:

```powershell
.\gradlew.bat runVelocity
```

The local Velocity files are created under `run/`.

## Installation

1. Build the plugin.
2. Copy the generated jar from `build/libs/` into your Velocity proxy `plugins/` directory.
3. Start the proxy once to generate `plugins/queue/config.yml`.
4. Edit `config.yml` so `queue-server` and `main-server` match server names in `velocity.toml`.
5. Restart the proxy or run `/queue reload`.

## Configuration

Default configuration is stored in `src/main/resources/config.yml`.

Important options:

```yaml
queue-server: "login"
main-server: "main"
main-server-slots: 200
messages:
  interval: 10
```

- `queue-server`: server players wait on while queued
- `main-server`: destination server players are queued for
- `main-server-slots`: maximum online count allowed before queueing begins
- `messages.interval`: seconds between queue position notifications

### Placeholders

Tab list placeholders:

- `%status%`
- `%position%`
- `%wait%`
- `%online%`

Server-list query placeholders:

- `%priority%`
- `%regular%`
- `%totalinqueue%`
- `%maxplayers%`
- `%online%`

## Commands

```text
/queue
/join
/leave
/queue version
/queue reload
```

`/queue`, `/join`, and `/leave` all join the queue.

## Permissions

```text
queue.admin     Allows use of /queue commands
queue.priority  Places the player in the priority queue
queue.bypass    Bypasses queue routing
```

## Development Notes

- Main plugin class: `me.queue.Main`
- Plugin id: `queue`
- Plugin data folder: `plugins/queue`
- Build system: Gradle Kotlin DSL
