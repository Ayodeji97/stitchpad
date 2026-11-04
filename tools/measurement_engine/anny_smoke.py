#!/usr/bin/env python3
"""Exercise a native Anny template offline. Never accepts a customer photograph."""
import contextlib
import importlib.metadata
import io
import json
import time


def main():
    # Library startup writes status messages; keep stdout a parseable JSON report.
    logs = io.StringIO()
    start = time.perf_counter()
    with contextlib.redirect_stdout(logs):
        import anny
        import torch
        from anny.anthropometry import Anthropometry
        model = anny.Anny(
            rig='anny', topology='anny', local_changes='none', facial_actions='none',
        ).to(device='cpu', dtype=torch.float32)
        loaded = time.perf_counter()
        with torch.no_grad():
            output = model()
            vertices = output['rest_vertices']
            geometry = Anthropometry(model)
            height = float(geometry.height(vertices)[0])
            waist = float(geometry.waist_circumference(vertices)[0])
        finished = time.perf_counter()
    print(json.dumps({
        'status': 'synthetic_template_only_not_customer_inference',
        'validated_for_tailoring': False,
        'photo_to_body_inference_implemented': False,
        'commercial_reconstruction_checkpoint_used': False,
        'device': 'cpu',
        'versions': {name: importlib.metadata.version(name) for name in ('anny', 'torch', 'numpy')},
        'topology': 'anny',
        'vertex_count': int(vertices.shape[1]),
        'face_count': int(model.faces.shape[0]),
        'load_seconds': loaded - start,
        'forward_and_geometry_seconds': finished - loaded,
        'template_height_mm': height * 1000,
        'template_waist_path_mm': waist * 1000,
        'warning': 'Numbers describe the default synthetic model, not a photographed person. No accuracy claim.',
    }, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
