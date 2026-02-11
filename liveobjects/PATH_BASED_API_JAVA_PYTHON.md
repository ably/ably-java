# LiveObjects Path-Based API Implementation Plan

## Executive Summary

This document outlines the implementation plan for building a **new path-based LiveObjects API from scratch**.
This is a clean-slate implementation that replaces the current object-manipulation approach with a modern, intuitive path-based design.

**Target SDKs:** Java/Kotlin, Python

---

## Core Architecture

### Fundamental Concepts

**Path-Based Access**
Objects are accessed via paths from the root, not by creating and managing object instances:

**Java:**
```java
// Path to nested value
StringPathObject colour = myObject.at("shape.colour").asStringPrimitive();
String value = colour.value(); // "red"
```

**Python:**
```python
# Path to nested value
colour = my_object.at("shape.colour").as_string_primitive()
value = colour.value()  # "red"
```

**Deferred Resolution**
`PathObject` wraps a path; resolution happens when methods are called:

**Java:**
```java
LiveMapPathObject foo = myObject.get("foo").asLiveMap(); // No resolution yet
foo.set("bar", Primitive.create("baz")); // Resolution happens here
```

**Python:**
```python
foo = my_object.get("foo").as_live_map()  # No resolution yet
foo.set("bar", Primitive.create("baz"))  # Resolution happens here
```

**Single Object Per Channel**
Each channel has one root object (always a LiveMap):

**Java:**
```java
LiveMapPathObject myObject = channel.getObject().get(); // The singular object
```

**Python:**
```python
my_object = await channel.object.get()  # The singular object
```

**Atomic Deep Creation**
Create nested structures in one operation:

**Java:**
```java
myObject.set("shape", LiveMap.create(Map.of(
  "name", Primitive.create("circle"),
  "colour", LiveMap.create(Map.of(
    "border", Primitive.create("red"),
    "fill", Primitive.create("blue")
  ))
)));
```

**Python:**
```python
my_object.set("shape", LiveMap.create({
    "name": Primitive.create("circle"),
    "colour": LiveMap.create({
        "border": Primitive.create("red"),
        "fill": Primitive.create("blue")
    })
}))
```

### Type System

```
Value (all types that can be stored)
├── Primitive (leaf values)
│   ├── String
│   ├── Number
│   ├── Boolean
│   ├── Binary (byte[])
└── Live (collaborative types)
    ├── LiveCounter
    ├── LiveMap
    └── LiveList
```

### Core Types

**PathObject**
Represents a path to a location in the object tree.

**Java:**
```java
public interface PathObject {
  String path();
  JsonValue compact() throws AblyException;

  StringPathObject asStringPrimitive();
  NumberPathObject asNumberPrimitive();
  BooleanPathObject asBooleanPrimitive();
  BinaryPathObject asBinaryPrimitive();

  LiveMapPathObject asLiveMap();
  LiveCounterPathObject asLiveCounter();
  LiveListPathObject asLiveList();

  Subscription subscribe(ObjectChangeListener listener, SubscriptionOptions options);
}

public interface StringPathObject extends PathObject {
  String value() throws AblyException;
}

public interface NumberPathObject extends PathObject {
  Double value() throws AblyException;
}

public interface BooleanPathObject extends PathObject {
  Boolean value() throws AblyException;
}

public interface BinaryPathObject extends PathObject {
  byte[] value() throws AblyException;
}

public interface LiveMapPathObject extends PathObject {
  PathObject get(String key);
  PathObject at(String path);
  LiveMap instance() throws AblyException;

  void set(String key, Value value, MessageOptions options) throws AblyException;
  void remove(String key, MessageOptions options) throws AblyException;

  Iterable<Map.Entry<String, PathObject>> entries();
  Iterable<String> keys();
  Iterable<PathObject> values();
  long size();
}

public interface LiveListPathObject extends PathObject {
  PathObject get(int index);
  long size();
}

public interface LiveCounterPathObject extends PathObject {
  Double value() throws AblyException;
  void increment(Number amount, MessageOptions options) throws AblyException;
  void decrement(Number amount, MessageOptions options) throws AblyException;
}
```

**Python:**
```python
from abc import ABC, abstractmethod
from typing import Optional, Iterable, Tuple, Any

class PathObject(ABC):
    @abstractmethod
    def path(self) -> str:
        pass

    @abstractmethod
    def compact(self) -> Any:
        pass

    @abstractmethod
    def as_string_primitive(self) -> 'StringPathObject':
        pass

    @abstractmethod
    def as_number_primitive(self) -> 'NumberPathObject':
        pass

    @abstractmethod
    def as_boolean_primitive(self) -> 'BooleanPathObject':
        pass

    @abstractmethod
    def as_binary_primitive(self) -> 'BinaryPathObject':
        pass

    @abstractmethod
    def as_live_map(self) -> 'LiveMapPathObject':
        pass

    @abstractmethod
    def as_live_counter(self) -> 'LiveCounterPathObject':
        pass

    @abstractmethod
    def as_live_list(self) -> 'LiveListPathObject':
        pass

    @abstractmethod
    def subscribe(
        self,
        listener: ObjectChangeListener,
        options: Optional[SubscriptionOptions] = None
    ) -> Subscription:
        pass


class StringPathObject(PathObject):
    @abstractmethod
    def value(self) -> str:
        pass


class NumberPathObject(PathObject):
    @abstractmethod
    def value(self) -> float:
        pass


class BooleanPathObject(PathObject):
    @abstractmethod
    def value(self) -> bool:
        pass


class BinaryPathObject(PathObject):
    @abstractmethod
    def value(self) -> bytes:
        pass


class LiveMapPathObject(PathObject):
    @abstractmethod
    def get(self, key: str) -> PathObject:
        pass

    @abstractmethod
    def at(self, path: str) -> PathObject:
        pass

    @abstractmethod
    def instance(self) -> Optional['LiveMap']:
        pass

    @abstractmethod
    def set(
        self,
        key: str,
        value: Value,
        options: Optional[MessageOptions] = None
    ):
        pass

    @abstractmethod
    def remove(self, key: str, options: Optional[MessageOptions] = None):
        pass

    @abstractmethod
    def entries(self) -> Iterable[Tuple[str, PathObject]]:
        pass

    @abstractmethod
    def keys(self) -> Iterable[str]:
        pass

    @abstractmethod
    def values(self) -> Iterable[PathObject]:
        pass

    @abstractmethod
    def size(self) -> int:
        pass


class LiveListPathObject(PathObject):
    @abstractmethod
    def get(self, index: int) -> PathObject:
        pass

    @abstractmethod
    def size(self) -> int:
        pass


class LiveCounterPathObject(PathObject):
    @abstractmethod
    def value(self) -> float:
        pass

    @abstractmethod
    def increment(
        self,
        amount: float,
        options: Optional[MessageOptions] = None
    ):
        pass

    @abstractmethod
    def decrement(
        self,
        amount: float,
        options: Optional[MessageOptions] = None
    ):
        pass
```

---

## Usage Examples

### Getting Started

**Java:**
```java
// Get channel
RealtimeChannel channel = client.channels.get("my-channel");

// Get root object (waits for sync)
LiveMapPathObject myObject = channel.getObject().get();

// Access nested primitive value
StringPathObject name = myObject.at("user.name").asStringPrimitive();
String nameValue = name.value(); // "Alice"

// Access nested counter
LiveCounterPathObject visits = myObject.at("stats.visits").asLiveCounter();
Double visitsValue = visits.value(); // 42.0
```

**Python:**
```python
# Get channel
channel = client.channels.get("my-channel")

# Get root object (waits for sync)
my_object = await channel.object.get()

# Access nested primitive value
name = my_object.at("user.name").as_string_primitive()
name_value = name.value()  # "Alice"

# Access nested counter
visits = my_object.at("stats.visits").as_live_counter()
visits_value = visits.value()  # 42.0
```

### Creating Objects

**Java:**
```java
// Create simple map
myObject.set("user", LiveMap.create(Map.of(
    "name", Primitive.create("Alice"),
    "age", Primitive.create(30),
    "active", Primitive.create(true)
)));

// Create nested structure
myObject.set("game", LiveMap.create(Map.of(
    "title", Primitive.create("Chess"),
    "players", LiveMap.create(Map.of(
        "alice", LiveMap.create(Map.of(
            "score", LiveCounter.create(0),
            "color", Primitive.create("white")
        )),
        "bob", LiveMap.create(Map.of(
            "score", LiveCounter.create(0),
            "color", Primitive.create("black")
        ))
    )),
    "status", Primitive.create("ongoing")
)));

// Create counter
myObject.set("visits", LiveCounter.create(0));
```

**Python:**
```python
# Create simple map
my_object.set("user", LiveMap.create({
    "name": Primitive.create("Alice"),
    "age": Primitive.create(30),
    "active": Primitive.create(True)
}))

# Create nested structure
my_object.set("game", LiveMap.create({
    "title": Primitive.create("Chess"),
    "players": LiveMap.create({
        "alice": LiveMap.create({
            "score": LiveCounter.create(0),
            "color": Primitive.create("white")
        }),
        "bob": LiveMap.create({
            "score": LiveCounter.create(0),
            "color": Primitive.create("black")
        })
    }),
    "status": Primitive.create("ongoing")
}))

# Create counter
my_object.set("visits", LiveCounter.create(0))
```

### Mutations

**Java:**
```java
// Update primitive value
myObject.at("user.name").asLiveMap().set(
    "name",
    Primitive.create("Bob")
);

// Or navigate to parent and set
LiveMapPathObject user = myObject.get("user").asLiveMap();
user.set("name", Primitive.create("Bob"));

// Increment counter
myObject.at("stats.visits").asLiveCounter().increment(1);

// With message options
MessageOptions options = new MessageOptions.Builder()
    .id("msg-123")
    .extras(Map.of("source", "mobile"))
    .build();

myObject.at("user").asLiveMap().set("status", Primitive.create("online"), options);

// Remove key
myObject.at("user").asLiveMap().remove("oldField");
```

**Python:**
```python
# Update primitive value
my_object.at("user").as_live_map().set(
    "name",
    Primitive.create("Bob")
)

# Or navigate to parent and set
user = my_object.get("user").as_live_map()
user.set("name", Primitive.create("Bob"))

# Increment counter
my_object.at("stats.visits").as_live_counter().increment(1)

# With message options
options = MessageOptions(id="msg-123", extras={"source": "mobile"})
my_object.at("user").as_live_map().set(
    "status",
    Primitive.create("online"),
    options
)

# Remove key
my_object.at("user").as_live_map().remove("old_field")
```

### Subscriptions

**Java:**
```java
// Subscribe to all changes (unlimited depth)
Subscription sub1 = myObject.subscribe(event -> {
    System.out.println("Changed at: " + event.object().path());
    System.out.println("By: " + event.message().getClientId());
    System.out.println("Operation: " + event.message().getOperation());
}, SubscriptionOptions.unlimited());

// Subscribe to specific path
Subscription sub2 = myObject.at("user.name").subscribe(event -> {
    StringPathObject name = event.object().asStringPrimitive();
    System.out.println("Name changed to: " + name.value());
}, SubscriptionOptions.unlimited());

// Subscribe with depth limit (only top-level changes)
Subscription sub3 = myObject.subscribe(event -> {
    System.out.println("Top-level change: " + event.object().path());
}, SubscriptionOptions.depth(1));

// Subscribe to counter changes
myObject.at("stats.visits").subscribe(event -> {
    LiveCounterPathObject visits = event.object().asLiveCounter();
    System.out.println("Visits: " + visits.value());
}, SubscriptionOptions.unlimited());

// Unsubscribe
sub1.unsubscribe();
```

**Python:**
```python
# Subscribe to all changes (unlimited depth)
def on_change(event):
    print(f"Changed at: {event.object.path()}")
    print(f"By: {event.message.client_id}")
    print(f"Operation: {event.message.operation}")

sub1 = my_object.subscribe(on_change, SubscriptionOptions.unlimited())

# Subscribe to specific path
def on_name_change(event):
    name = event.object.as_string_primitive()
    print(f"Name changed to: {name.value()}")

sub2 = my_object.at("user.name").subscribe(
    on_name_change,
    SubscriptionOptions.unlimited()
)

# Subscribe with depth limit (only top-level changes)
sub3 = my_object.subscribe(
    lambda event: print(f"Top-level change: {event.object.path()}"),
    SubscriptionOptions.with_depth(1)
)

# Subscribe to counter changes
def on_visits_change(event):
    visits = event.object.as_live_counter()
    print(f"Visits: {visits.value()}")

my_object.at("stats.visits").subscribe(
    on_visits_change,
    SubscriptionOptions.unlimited()
)

# Async iterator style (Python-specific)
async for event in my_object.subscribe_async():
    print(f"Event: {event.object.path()}")
    if some_condition:
        break

# Unsubscribe
sub1.unsubscribe()
```

### Collection Accessors

**Java:**
```java
LiveMapPathObject user = myObject.get("user").asLiveMap();

// Iterate entries
for (Map.Entry<String, PathObject> entry : user.entries()) {
    System.out.println(entry.getKey() + ": " + entry.getValue().path());
}

// Get keys
for (String key : user.keys()) {
    System.out.println("Key: " + key);
}

// Get values
for (PathObject value : user.values()) {
    System.out.println("Value at: " + value.path());
}

// Get size
long count = user.size();
System.out.println("User has " + count + " properties");
```

**Python:**
```python
user = my_object.get("user").as_live_map()

# Iterate entries
for key, value in user.entries():
    print(f"{key}: {value.path()}")

# Get keys
for key in user.keys():
    print(f"Key: {key}")

# Get values
for value in user.values():
    print(f"Value at: {value.path()}")

# Get size
count = user.size()
print(f"User has {count} properties")
```

### Compact Representation

**Java:**
```java
// Get JSON snapshot of entire object tree
JsonValue snapshot = myObject.compact();
System.out.println(snapshot.toString());
// {"user":{"name":"Alice","age":30},"stats":{"visits":42}}

// Get snapshot of nested object
JsonValue userSnapshot = myObject.get("user").compact();
System.out.println(userSnapshot.toString());
// {"name":"Alice","age":30}
```

**Python:**
```python
# Get JSON snapshot of entire object tree
snapshot = my_object.compact()
print(snapshot)
# {"user": {"name": "Alice", "age": 30}, "stats": {"visits": 42}}

# Get snapshot of nested object
user_snapshot = my_object.get("user").compact()
print(user_snapshot)
# {"name": "Alice", "age": 30}
```

### Instance API

**Java:**
```java
// Get specific object instance
LiveMapPathObject player = myObject.at("game.players.alice").asLiveMap();
LiveMap playerInstance = player.instance();

if (playerInstance != null) {
    // Get object ID
    String objectId = playerInstance.id();
    System.out.println("Player object ID: " + objectId);

    // Subscribe to this specific instance
    // (subscription persists even if this object moves to different path)
    playerInstance.subscribe(event -> {
        System.out.println("Player instance " + objectId + " changed");
    }, SubscriptionOptions.unlimited());

    // Mutate via instance
    playerInstance.set("score", LiveCounter.create(10));
}
```

**Python:**
```python
# Get specific object instance
player = my_object.at("game.players.alice").as_live_map()
player_instance = player.instance()

if player_instance:
    # Get object ID
    object_id = player_instance.id()
    print(f"Player object ID: {object_id}")

    # Subscribe to this specific instance
    # (subscription persists even if this object moves to different path)
    player_instance.subscribe(
        lambda event: print(f"Player instance {object_id} changed"),
        SubscriptionOptions.unlimited()
    )

    # Mutate via instance
    player_instance.set("score", LiveCounter.create(10))
```

### Complete Application Example

**Java:**
```java
public class GameApp {
    public static void main(String[] args) throws AblyException {
        // Initialize client
        AblyRealtime client = new AblyRealtime(options);
        RealtimeChannel channel = client.channels.get("game:123");

        // Get root object
        LiveMapPathObject game = channel.getObject().get();

        // Initialize game state
        game.set("state", Primitive.create("waiting"));
        game.set("players", LiveMap.create());
        game.set("score", LiveMap.create());
        game.set("timer", LiveCounter.create(60));

        // Add player
        game.at("players").asLiveMap().set("alice", LiveMap.create(Map.of(
            "name", Primitive.create("Alice"),
            "team", Primitive.create("red"),
            "health", LiveCounter.create(100),
            "position", LiveMap.create(Map.of(
                "x", Primitive.create(0),
                "y", Primitive.create(0)
            ))
        )));

        // Subscribe to game state changes
        game.at("state").subscribe(event -> {
            StringPathObject state = event.object().asStringPrimitive();
            System.out.println("Game state: " + state.value());

            if ("started".equals(state.value())) {
                startGameTimer(game);
            }
        }, SubscriptionOptions.unlimited());

        // Subscribe to player health
        game.at("players.alice.health").subscribe(event -> {
            LiveCounterPathObject health = event.object().asLiveCounter();
            System.out.println("Alice health: " + health.value());

            if (health.value() <= 0) {
                handlePlayerDeath("alice");
            }
        }, SubscriptionOptions.unlimited());

        // Subscribe to all player changes (depth 2 = players.*/*)
        game.at("players").subscribe(event -> {
            System.out.println("Player update at: " + event.object().path());
            System.out.println("By: " + event.message().getClientId());
        }, SubscriptionOptions.depth(2));

        // Update game state
        game.at("state").asLiveMap().set("state", Primitive.create("started"));

        // Update player position
        MessageOptions moveOptions = new MessageOptions.Builder()
            .id("move-" + System.currentTimeMillis())
            .extras(Map.of("action", "move", "timestamp", System.currentTimeMillis()))
            .build();

        game.at("players.alice.position").asLiveMap().set(
            "x",
            Primitive.create(10),
            moveOptions
        );

        // Damage player
        game.at("players.alice.health").asLiveCounter().decrement(25);

        // Get game snapshot
        JsonValue snapshot = game.compact();
        System.out.println("Game state: " + snapshot);
    }

    private static void startGameTimer(LiveMapPathObject game) {
        // Timer logic
    }

    private static void handlePlayerDeath(String playerId) {
        // Death handling logic
    }
}
```

**Python:**
```python
import asyncio
from ably import AblyRealtime

class GameApp:
    def __init__(self, client: AblyRealtime):
        self.client = client
        self.channel = client.channels.get("game:123")

    async def run(self):
        # Get root object
        game = await self.channel.object.get()

        # Initialize game state
        game.set("state", Primitive.create("waiting"))
        game.set("players", LiveMap.create())
        game.set("score", LiveMap.create())
        game.set("timer", LiveCounter.create(60))

        # Add player
        game.at("players").as_live_map().set("alice", LiveMap.create({
            "name": Primitive.create("Alice"),
            "team": Primitive.create("red"),
            "health": LiveCounter.create(100),
            "position": LiveMap.create({
                "x": Primitive.create(0),
                "y": Primitive.create(0)
            })
        }))

        # Subscribe to game state changes
        def on_state_change(event):
            state = event.object.as_string_primitive()
            print(f"Game state: {state.value()}")

            if state.value() == "started":
                self.start_game_timer(game)

        game.at("state").subscribe(on_state_change, SubscriptionOptions.unlimited())

        # Subscribe to player health
        def on_health_change(event):
            health = event.object.as_live_counter()
            print(f"Alice health: {health.value()}")

            if health.value() <= 0:
                self.handle_player_death("alice")

        game.at("players.alice.health").subscribe(
            on_health_change,
            SubscriptionOptions.unlimited()
        )

        # Subscribe to all player changes (depth 2 = players.*/*)
        def on_player_change(event):
            print(f"Player update at: {event.object.path()}")
            print(f"By: {event.message.client_id}")

        game.at("players").subscribe(
            on_player_change,
            SubscriptionOptions.with_depth(2)
        )

        # Update game state
        game.at("state").as_live_map().set("state", Primitive.create("started"))

        # Update player position
        move_options = MessageOptions(
            id=f"move-{time.time()}",
            extras={"action": "move", "timestamp": time.time()}
        )

        game.at("players.alice.position").as_live_map().set(
            "x",
            Primitive.create(10),
            move_options
        )

        # Damage player
        game.at("players.alice.health").as_live_counter().decrement(25)

        # Get game snapshot
        snapshot = game.compact()
        print(f"Game state: {snapshot}")

        # Async iteration over events (Python-specific)
        async for event in game.subscribe_async(SubscriptionOptions.unlimited()):
            print(f"Event at: {event.object.path()}")
            # Handle events...

    def start_game_timer(self, game):
        # Timer logic
        pass

    def handle_player_death(self, player_id: str):
        # Death handling logic
        pass


# Run the app
async def main():
    client = AblyRealtime(options)
    app = GameApp(client)
    await app.run()

asyncio.run(main())
```

---

## Key Design Decisions

### 1. Type-Safe Path Resolution
Rather than a single generic `PathObject<T>`, we use specific types for each value type:
- `StringPathObject`, `NumberPathObject`, etc. for primitives
- `LiveMapPathObject`, `LiveCounterPathObject`, etc. for live objects

This provides type safety and prevents invalid operations (e.g., can't call `increment()` on a string).

### 2. Explicit Type Assertions
Navigation returns a generic `PathObject`, which must be explicitly cast to the expected type:
```java
PathObject generic = myObject.get("user");
LiveMapPathObject user = generic.asLiveMap(); // Explicit cast
```

This makes the developer's expectations clear and fails fast if the type doesn't match.

### 3. Path-Based Subscriptions
Subscriptions are attached to paths, not specific object instances. This means:
- Subscription survives object replacement at that path
- Can subscribe before object exists
- Depth control allows filtering nested changes

Instance subscriptions are still available via `instance().subscribe()` for tracking specific objects regardless of path.

### 4. Deferred Resolution
PathObjects don't resolve until a method requiring the actual value is called. This allows:
- Lightweight path construction
- Chaining without intermediate lookups
- Subscriptions to non-existent paths

### 5. Atomic Deep Creation
Static factory methods (`LiveMap.create()`, `LiveCounter.create()`) return creator objects that can be nested. When passed to `set()`, all CREATE operations are batched into a single message.

### 6. No Object ID Exposure (Except Instance API)
Object IDs are internal details. Developers work with paths. The `instance()` method exposes IDs for advanced use cases (tracking specific objects across path changes).

---

## Migration from Current API

### Old API (to be removed):
```java
// Get root
LiveMap root = channel.objects().getRoot();

// Create orphaned object
LiveMap nested = channel.objects().createMap();

// Assign to make reachable
root.set("foo", LiveMapValue.of(nested));

// Subscribe to instance
nested.subscribe(update -> {
    // Need to re-subscribe if object replaced
});
```

### New API:
```java
// Get root (singular)
LiveMapPathObject root = channel.object().get();

// Create and assign atomically
root.set("foo", LiveMap.create());

// Subscribe to path (survives replacement)
root.at("foo").subscribe(event -> {
    // Automatically tracks object at this path
}, SubscriptionOptions.unlimited());
```

