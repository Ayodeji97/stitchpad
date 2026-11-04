import math
import unittest
from sections import section_perimeter


def prism(radius=150, sides=128, x=0):
    vertices = [(x + radius * math.cos(2 * math.pi * i / sides), radius * math.sin(2 * math.pi * i / sides), z)
                for z in (-100, 100) for i in range(sides)]
    faces = []
    for i in range(sides):
        j = (i + 1) % sides
        faces.extend([(i, j, i + sides), (j, j + sides, i + sides)])
    return vertices, faces


class SectionTests(unittest.TestCase):
    def test_polygon_circumference_matches_analytic_mesh(self):
        vertices, faces = prism()
        expected = 2 * 128 * 150 * math.sin(math.pi / 128)
        self.assertAlmostEqual(section_perimeter(vertices, faces, 1.234), expected, places=7)

    def test_missing_face_is_rejected_not_silently_accepted(self):
        vertices, faces = prism()
        with self.assertRaisesRegex(ValueError, 'open'):
            section_perimeter(vertices, faces[1:], 1.234)

    def test_disconnected_arm_and_torso_loops_are_rejected(self):
        left, lf = prism(x=-400)
        right, rf = prism(x=400)
        with self.assertRaisesRegex(ValueError, 'Multiple'):
            section_perimeter(left + right, lf + [tuple(i + len(left) for i in f) for f in rf], 1.234)

    def test_coplanar_and_outside_planes_are_rejected(self):
        vertices, faces = prism()
        for plane in (-100, 100, 200):
            with self.subTest(plane=plane), self.assertRaises(ValueError):
                section_perimeter(vertices, faces, plane)

    def test_units_scale_linearly(self):
        vertices, faces = prism()
        mm = section_perimeter(vertices, faces, 1.234)
        metres = section_perimeter([tuple(v / 1000 for v in p) for p in vertices], faces, .001234)
        self.assertAlmostEqual(mm, metres * 1000, places=6)

    def test_duplicate_faces_and_nonfinite_values_rejected(self):
        vertices, faces = prism()
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            section_perimeter(vertices, faces + faces[:1], 1.234)
        vertices[0] = (float('nan'), 0, 0)
        with self.assertRaisesRegex(ValueError, 'finite'):
            section_perimeter(vertices, faces, 1.234)


if __name__ == '__main__':
    unittest.main()
