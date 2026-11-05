#!/usr/bin/env python3
"""Render synthetic native Anny bodies for detector diagnostics, never an accuracy cohort.
Optional dependencies: ../measurement_engine/requirements.txt and Pillow==12.1.1.
Only native Anny rig/topology/assets; no images or reconstruction checkpoints.
"""
from pathlib import Path
import json
import anny
import numpy as np
import torch
from PIL import Image, ImageDraw


def main():
    model = anny.Anny(rig='anny', topology='anny', local_changes='none', facial_actions='none').to('cpu', torch.float32)
    poses = {}
    for suffix, angle in [('L', -1.05), ('R', 1.05)]:
        transform = torch.eye(4).unsqueeze(0)
        c, s = np.cos(angle), np.sin(angle)
        transform[0, :3, :3] = torch.tensor([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=torch.float32)
        poses['upperarm01.' + suffix] = transform
    with torch.no_grad():
        output = model(pose_parameters=poses, pose_parameterization='local-ref')
    vertices, faces = output['vertices'][0].numpy(), model.faces.numpy()
    root = Path(__file__).resolve().parent / 'assets' / 'fixtures'
    root.mkdir(parents=True, exist_ok=True)
    height = float(vertices[:, 2].max() - vertices[:, 2].min())
    for name, axis, depth in [('front', 0, 1), ('side', 1, 0)]:
        image = Image.new('RGB', (1200, 1400), (232, 228, 219))
        draw = ImageDraw.Draw(image)
        scale = 1200 / height
        pixels = np.stack([600 + (vertices[:, axis] - (vertices[:, axis].min() + vertices[:, axis].max()) / 2) * scale,
                           1300 - (vertices[:, 2] - vertices[:, 2].min()) * scale], axis=1)
        order = np.argsort(vertices[faces][:, :, depth].mean(1))
        if name == 'front':
            order = order[::-1]
        for index in order:
            face = faces[index]
            triangle = vertices[face]
            normal = np.cross(triangle[1] - triangle[0], triangle[2] - triangle[0])
            normal /= max(np.linalg.norm(normal), 1e-8)
            brightness = .55 + .45 * abs(float(normal @ np.array([.3, .4, .866])))
            colour = tuple(int(channel * brightness) for channel in (166, 140, 121))
            draw.polygon([tuple(pixel) for pixel in pixels[face]], fill=colour)
        image.save(root / f'{name}.png')
    (root / 'metadata.json').write_text(json.dumps({'synthetic': True, 'source': 'native Anny 0.6.2',
        'heightMm': height * 1000, 'accuracyEvidence': False}, indent=2) + '\n')
    print(f'Synthetic diagnostics written to {root}. These are not customer measurements.')


if __name__ == '__main__':
    main()
