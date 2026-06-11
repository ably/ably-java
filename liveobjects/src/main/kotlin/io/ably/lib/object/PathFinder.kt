package io.ably.lib.`object`

/**
 * Computes every path from the root map to a target object by walking the
 * objects graph on demand (over objectId references), instead of maintaining
 * incremental parent references like ably-js does (RTLO3f/RTLO4g/RTLO4h).
 * RTLO4f-equivalent observable behavior; cycle-safe.
 *
 * Spec: RTLO4f (equivalent)
 */
internal object PathFinder {

  /**
   * Returns all paths (as segment lists) from the root map to the object with
   * [targetObjectId]. The root itself yields a single empty path.
   */
  internal fun findFullPaths(bridge: ObjectsBridge, targetObjectId: String): List<List<String>> {
    val root = bridge.getRootNode() ?: return emptyList()
    if (targetObjectId == root.objectId) return listOf(emptyList())
    val result = mutableListOf<List<String>>()
    walk(bridge, root, targetObjectId, currentPath = mutableListOf(), visited = mutableSetOf(), result)
    return result
  }

  private fun walk(
    bridge: ObjectsBridge,
    map: MapNode,
    targetObjectId: String,
    currentPath: MutableList<String>,
    visited: MutableSet<String>,
    result: MutableList<List<String>>,
  ) {
    if (!visited.add(map.objectId)) return // cycle guard
    for ((key, data) in map.entries()) {
      val refId = data.objectId ?: continue
      if (refId == targetObjectId) {
        result.add(currentPath + key)
        continue
      }
      val refNode = bridge.getNode(refId)
      if (refNode is MapNode) {
        currentPath.add(key)
        walk(bridge, refNode, targetObjectId, currentPath, visited, result)
        currentPath.removeAt(currentPath.size - 1)
      }
    }
    visited.remove(map.objectId)
  }
}
