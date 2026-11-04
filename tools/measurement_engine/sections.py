"""Experimental geometry only. A correct mesh perimeter does not validate a body estimate."""
import math


def section_perimeter(vertices, faces, height, axis=2, tolerance=1e-7):
    """Perimeter of exactly one closed triangle-mesh/plane intersection, in input units.

    Input must be a preselected anatomical region in a canonical pose. Multiple
    loops (e.g. arms and torso) are rejected rather than summed or guessed.
    Planes through mesh vertices/coplanar edges are rejected: retry a documented
    offset rather than introducing ambiguous topology silently.
    """
    if axis not in (0, 1, 2) or not math.isfinite(height) or tolerance <= 0:
        raise ValueError('Invalid plane or tolerance')
    if not vertices or not faces:
        raise ValueError('Mesh is empty')
    for vertex in vertices:
        if len(vertex) != 3 or not all(math.isfinite(v) for v in vertex):
            raise ValueError('Vertices must be finite XYZ coordinates')
    segments = []
    for face in faces:
        if len(face) != 3 or any(isinstance(i, bool) or not isinstance(i, int) or i < 0 or i >= len(vertices) for i in face):
            raise ValueError('Faces must have three valid vertex indices')
        triangle = [vertices[i] for i in face]
        distances = [vertex[axis] - height for vertex in triangle]
        if all(v > tolerance for v in distances) or all(v < -tolerance for v in distances):
            continue
        if any(abs(value) <= tolerance for value in distances):
            raise ValueError('Plane intersects a vertex or coplanar edge')
        crossings = []
        for a, b in ((0, 1), (1, 2), (2, 0)):
            if distances[a] * distances[b] >= 0:
                continue
            fraction = distances[a] / (distances[a] - distances[b])
            crossings.append(tuple(triangle[a][k] + fraction * (triangle[b][k] - triangle[a][k]) for k in range(3)))
        if len(crossings) != 2:
            raise ValueError('Ambiguous intersection')
        segments.append(crossings)
    if not segments:
        raise ValueError('Plane misses mesh')
    nodes, neighbours, unique_edges = [], {}, set()
    def node_id(point):
        for index, existing in enumerate(nodes):
            if math.dist(existing, point) <= tolerance:
                return index
        nodes.append(point)
        return len(nodes) - 1
    length = 0.0
    for a, b in segments:
        ia, ib = node_id(a), node_id(b)
        edge = tuple(sorted((ia, ib)))
        if ia == ib or edge in unique_edges:
            raise ValueError('Degenerate or duplicate section edge')
        unique_edges.add(edge)
        neighbours.setdefault(ia, set()).add(ib)
        neighbours.setdefault(ib, set()).add(ia)
        length += math.dist(a, b)
    if any(len(edges) != 2 for edges in neighbours.values()):
        raise ValueError('Section is open or non-manifold')
    visited, pending = set(), [0]
    while pending:
        current = pending.pop()
        if current in visited:
            continue
        visited.add(current)
        pending.extend(neighbours[current] - visited)
    if len(visited) != len(nodes):
        raise ValueError('Multiple section loops: choose an anatomical region explicitly')
    return length
