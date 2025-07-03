# LiveObjects Implementation Summary

## Overview

This document summarizes the implementation of object message handling logic in the ably-java liveobjects module,
based on the JavaScript implementation in ably-js.

## JavaScript Implementation Analysis

### Flow Overview

The JavaScript implementation follows this flow:

1. **Entry Point**: `RealtimeChannel.processMessage()` receives protocol messages with `OBJECT` or `OBJECT_SYNC` actions
2. **Message Routing**: 
   - `OBJECT` action → `this._objects.handleObjectMessages()`
   - `OBJECT_SYNC` action → `this._objects.handleObjectSyncMessages()`
3. **State Management**: Objects have states: `initialized`, `syncing`, `synced`
4. **Buffering**: Non-sync messages are buffered when state is not `synced`
5. **Sync Processing**: Sync messages are applied to a data pool and then applied to objects
6. **Operation Application**: Individual operations are applied to objects with serial-based conflict resolution

### Key Components

- **ObjectsPool**: Manages live objects by objectId
- **SyncObjectsDataPool**: Temporarily stores sync data before applying to objects
- **Buffered Operations**: Queues operations during sync sequences
- **Serial-based Conflict Resolution**: Uses site serials to determine operation precedence

## Kotlin Implementation

### Files Modified/Created

1. **DefaultLiveObjects.kt** - Main implementation with state management and message handling
2. **LiveObjectImpl.kt** - Concrete implementations of LiveMap and LiveCounter

### Key Features Implemented

#### 1. State Management
```kotlin
private enum class ObjectsState {
  INITIALIZED,
  SYNCING,
  SYNCED
}
```

#### 2. Message Handling
- **handle()**: Main entry point for protocol messages
- **handleObjectMessages()**: Processes regular object messages
- **handleObjectSyncMessages()**: Processes sync messages

#### 3. Sync Processing
- **parseSyncChannelSerial()**: Extracts syncId and syncCursor from channel serial
- **startNewSync()**: Begins new sync sequence
- **endSync()**: Completes sync sequence and applies buffered operations
- **applySync()**: Applies sync data to objects pool

#### 4. Object Operations
- **applyObjectMessages()**: Applies individual operations to objects
- **createZeroValueObjectIfNotExists()**: Creates placeholder objects for operations
- **parseObjectId()**: Parses object IDs to determine type

#### 5. LiveObject Implementations
- **BaseLiveObject**: Abstract base class with common functionality
- **LiveMapImpl**: Concrete implementation for map objects
- **LiveCounterImpl**: Concrete implementation for counter objects

### Implementation Details

#### Serial-based Conflict Resolution
```kotlin
protected fun canApplyOperation(opSerial: String?, opSiteCode: String?): Boolean {
  val siteSerial = siteTimeserials[opSiteCode]
  return siteSerial == null || opSerial > siteSerial
}
```

#### CRDT Semantics for Maps
```kotlin
private fun canApplyMapOperation(mapEntrySerial: String?, opSerial: String?): Boolean {
  // For LWW CRDT semantics, operation should only be applied if its serial is strictly greater
  if (mapEntrySerial.isNullOrEmpty() && opSerial.isNullOrEmpty()) {
    return false
  }
  if (mapEntrySerial.isNullOrEmpty()) {
    return true
  }
  if (opSerial.isNullOrEmpty()) {
    return false
  }
  return opSerial > mapEntrySerial
}
```

#### State Transitions
1. **INITIALIZED** → **SYNCING**: When first sync message received
2. **SYNCING** → **SYNCED**: When sync sequence completes
3. **SYNCED**: Normal operation state

#### Buffering Strategy
- Regular object messages are buffered during sync sequences
- Buffered messages are applied after sync completion
- This ensures consistency and prevents race conditions

### Comparison with JavaScript

| Feature | JavaScript | Kotlin |
|---------|------------|--------|
| State Management | `ObjectsState` enum | `ObjectsState` enum |
| Object Pool | `ObjectsPool` class | `ConcurrentHashMap<String, LiveObject>` |
| Sync Data Pool | `SyncObjectsDataPool` class | `ConcurrentHashMap<String, ObjectState>` |
| Buffering | `_bufferedObjectOperations` array | `bufferedObjectOperations` list |
| Serial Parsing | `_parseSyncChannelSerial()` | `parseSyncChannelSerial()` |
| CRDT Logic | `_canApplyMapOperation()` | `canApplyMapOperation()` |

### Thread Safety

The Kotlin implementation uses:
- `ConcurrentHashMap` for thread-safe collections
- Immutable data structures where possible
- Proper synchronization for state changes

### Error Handling

- Validates object IDs and operation parameters
- Logs warnings for malformed messages
- Throws appropriate exceptions for invalid states
- Graceful handling of missing serials or site codes

### Future Enhancements

1. **Event Emission**: Implement proper event emission for object updates
2. **Lifecycle Events**: Add support for object lifecycle events (created, deleted)
3. **Garbage Collection**: Implement GC for tombstoned objects
4. **Performance Optimization**: Add caching and optimization for frequently accessed objects
5. **Testing**: Comprehensive unit and integration tests

## Compliance with Specification

The implementation follows the Ably LiveObjects specification (PR #333) and maintains compatibility with the JavaScript implementation while leveraging Kotlin's type safety and concurrency features. 
