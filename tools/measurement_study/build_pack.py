#!/usr/bin/env python3
"""Build two printable study booklets and a portable local prototype kit. No personal data."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph, Table, TableStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from content import CONTENT

ROOT = Path(__file__).resolve().parents[2]
PAGE_W, PAGE_H = A4
LEFT, RIGHT, TOP = 44, PAGE_W - 44, PAGE_H - 42
WIDTH = RIGHT - LEFT
INDIGO = colors.HexColor('#2C3E7C')
MUTED = colors.HexColor('#625C54')
BORDER = colors.HexColor('#CCC4B8')


def fonts():
    regular = Path('/System/Library/Fonts/Supplemental/Arial.ttf')
    bold = Path('/System/Library/Fonts/Supplemental/Arial Bold.ttf')
    if regular.exists() and bold.exists():
        pdfmetrics.registerFont(TTFont('Body', str(regular)))
        pdfmetrics.registerFont(TTFont('BodyBold', str(bold)))
        return 'Body', 'BodyBold'
    return 'Helvetica', 'Helvetica-Bold'


class Booklet:
    def __init__(self, path, locale):
        self.d = CONTENT[locale]
        self.body, self.bold = fonts()
        self.c = canvas.Canvas(str(path), pagesize=A4)
        self.c.setTitle(self.d['title'])
        self.c.setAuthor('StitchPad')
        self.y = TOP
        self.page = 0
        self.style = ParagraphStyle('body', fontName=self.body, fontSize=10, leading=14, textColor=colors.HexColor('#252320'))

    def p(self, value, size=10, bold=False, gap=8, x=LEFT, width=WIDTH, colour=None):
        style = ParagraphStyle('inline', parent=self.style, fontName=self.bold if bold else self.body,
                               fontSize=size, leading=size * 1.38, textColor=colour or self.style.textColor)
        paragraph = Paragraph(escape(value), style)
        _, height = paragraph.wrap(width, 900)
        if self.y - height < 50:
            raise ValueError(f'Page {self.page} overflow: {value[:60]}')
        paragraph.drawOn(self.c, x, self.y - height)
        self.y -= height + gap

    def heading(self, value):
        self.y -= 5
        self.p(value, size=12, bold=True, gap=9, colour=INDIGO)

    def line(self, label, height=30):
        self.p(label, size=9, gap=2, colour=MUTED)
        self.y -= height - 14
        self.c.setStrokeColor(BORDER)
        self.c.line(LEFT, self.y, RIGHT, self.y)
        self.y -= 10
        if self.y < 50: raise ValueError(f'Lines overflow on page {self.page}')

    def check(self, label):
        self.c.setStrokeColor(MUTED)
        self.c.rect(LEFT, self.y - 11, 9, 9)
        self.p(label, size=9.4, x=LEFT+17, width=WIDTH-17, gap=10)

    def table(self, rows, widths, heights=None):
        cells = [[Paragraph(escape(str(value)), ParagraphStyle('cell', parent=self.style, fontSize=9, leading=12,
                        fontName=self.bold if i == 0 else self.body)) for value in row] for i,row in enumerate(rows)]
        table = Table(cells, colWidths=widths, rowHeights=heights)
        table.setStyle(TableStyle([
            ('VALIGN',(0,0),(-1,-1),'TOP'),('BACKGROUND',(0,0),(-1,0),colors.HexColor('#EAEEF8')),
            ('LINEBELOW',(0,0),(-1,0),.7,INDIGO),('GRID',(0,1),(-1,-1),.4,BORDER),
            ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
            ('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8),
        ]))
        _, h = table.wrap(WIDTH, 900)
        if self.y - h < 50: raise ValueError(f'Table overflow page {self.page}')
        table.drawOn(self.c, LEFT, self.y-h)
        self.y -= h+12

    def new_page(self, title):
        if self.page: self.c.showPage()
        self.page += 1
        self.y = TOP
        self.c.setFillColor(MUTED)
        self.c.setFont(self.body,8)
        self.c.drawString(LEFT,25,self.d['footer'])
        self.c.drawRightString(RIGHT,25,f'{self.page} / 8')
        self.p(title,size=20,bold=True,gap=14,colour=colors.black)

    def build(self):
        d=self.d
        self.new_page(d['title'])
        self.p(d['subtitle'],size=13,colour=INDIGO)
        self.p(d['tag'],size=9,bold=True,colour=INDIGO)
        self.p(d['intro'])
        self.heading(d['roles_title'])
        for item in d['roles']: self.p(item,size=9.6,gap=7)
        self.heading(d['first_title'])
        for i,item in enumerate(d['first'],1): self.p(f'{i}. {item}',size=9.4,gap=7)
        self.p(d['start_note'],size=9,colour=MUTED)

        self.new_page(d['pages'][1])
        self.p(d['protocol_intro'],size=9.6)
        rows=[d['protocol_headers']]+[[f'{name}: {definition}','',''] for name,definition in d['protocol_fields']]
        self.table(rows,[WIDTH*.64,WIDTH*.18,WIDTH*.18])
        self.p(d['protocol_note'],size=9.2)
        for label in d['protocol_lines']: self.line(label,height=25)
        self.p(d['pose_note'],size=9,gap=6)
        self.p(d['height_note'],size=9,colour=MUTED)

        self.new_page(d['pages'][2])
        self.p(d['agreement_intro'],size=9.6)
        for label in d['agreement_lines']: self.line(label,height=25)
        for item in d['agreement_checks']: self.check(item)
        self.p(d['agreement_end'],size=9,colour=MUTED)
        for label in d['signature']: self.line(label,height=27)

        for number in [3,4]:
            self.new_page(d['pages'][number])
            self.p(d['reference_intro'],size=10)
            for label in d['reference_lines']: self.line(label,height=30)
            self.table([d['reference_headers']]+[[v,'',''] for v in d['reference_rows']],
                       [WIDTH*.45,WIDTH*.275,WIDTH*.275],heights=[32,43,43,43,43])
            for item in d['reference_checks']: self.check(item)
            self.p(d['reference_note'],size=9.5)
            self.line(d['notes'],height=43)
            self.line(d['sealed'],height=32)

        self.new_page(d['pages'][5])
        self.p(d['capture_intro'],size=9.5)
        for label in d['capture_lines']: self.line(label,height=26)
        self.p(d['mode_note'],size=9.2)
        self.table([d['capture_headers']]+[['','','','',''] for _ in range(6)],
            [WIDTH*.17,WIDTH*.12,WIDTH*.16,WIDTH*.17,WIDTH*.38],heights=[40]+[38]*6)
        self.p(d['capture_note'],size=9.2)
        self.p(d['filename_note'],size=9.2,colour=MUTED)

        self.new_page(d['pages'][6])
        self.p(d['review_intro'],size=9.7)
        for label in d['review_lines']: self.line(label,height=27)
        self.table([d['review_headers']]+[[v,'','',''] for v in d['review_rows']],
            [WIDTH*.29,WIDTH*.21,WIDTH*.29,WIDTH*.21],heights=[35,35,35,35])
        self.p(d['review_note'],size=9.5)
        for label in d['feedback']: self.line(label,height=33)

        self.new_page(d['pages'][7])
        self.p(d['close_intro'])
        for item in d['close_checks']: self.check(item)
        for label in d['return_lines']: self.line(label,height=28)
        self.heading(d['sources_title'])
        self.p(d['sources_note'],size=8.5,gap=6,colour=MUTED)
        for source in d['sources']: self.p(source,size=7.8,gap=6,colour=MUTED)
        self.c.save()


def build(output):
    output.mkdir(parents=True,exist_ok=True)
    for locale in CONTENT: Booklet(output/f'StitchPad-Tailor-Study-{locale.upper()}.pdf',locale).build()
    create_kit(output)
    return output


def create_kit(output):
    study = ROOT / 'tools' / 'measurement_study'
    capture = ROOT / 'tools' / 'measurement_capture'
    manifest = json.loads((capture / 'assets.lock.json').read_text())
    sources = ['index.html', 'style.css', 'app.mjs', 'copy.mjs', 'engine.mjs', 'detector.mjs', 'outline.mjs', 'assets.lock.json']
    kit = output / 'StitchPad-Tailor-Study-Kit.zip'
    prefix = 'StitchPad-Tailor-Study-Kit/'
    plans = subprocess.run(['node', str(study / 'pipeline_plans.mjs')], check=True, capture_output=True, text=True).stdout
    if len(json.loads(plans)['plans']) != 9:
        raise ValueError('Missing source pipeline plans')
    with zipfile.ZipFile(kit, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for locale in CONTENT:
            file = output / f'StitchPad-Tailor-Study-{locale.upper()}.pdf'
            archive.write(file, prefix + file.name)
        for name in ['START-HERE.txt', 'invitation.txt', 'start_session.py', 'protocol-template.json', 'COORDINATOR-NOTES.txt']:
            archive.write(study / name, prefix + name)
        for name in sources:
            archive.write(capture / name, prefix + 'capture/' + name)
        for item in manifest['files']:
            file = capture / 'assets' / item['path']
            data = file.read_bytes()
            if len(data) != item['bytes'] or hashlib.sha256(data).hexdigest() != item['sha256']:
                raise ValueError('Asset failed verification before packaging: ' + item['path'])
            archive.writestr(prefix + 'capture/assets/' + item['path'], data)
        mac = '#!/bin/sh\ncd "$(dirname "$0")" || exit 1\npython3 start_session.py\n'
        info = zipfile.ZipInfo(prefix + 'START-MAC.command')
        info.create_system = 3
        info.external_attr = 0o100755 << 16
        archive.writestr(info, mac)
        windows = '@echo off\r\ncd /d "%~dp0"\r\npy -3 start_session.py\r\nif errorlevel 1 python start_session.py\r\npause\r\n'
        archive.writestr(prefix + 'START-WINDOWS.cmd', windows)
        archive.writestr(prefix + 'pipeline-plans.json', plans)
        # The code-only importer is for Daniel. It is not a participant data file.
        for name in ['build_ledger.py', 'evaluate.py', 'README.md']:
            archive.write(ROOT / 'tools' / 'measurement_validation' / name, prefix + 'coordinator/' + name)
    shutil.copyfile(study / 'invitation.txt', output / 'Tailor-Invitation-EN-FR.txt')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    build(parser.parse_args().output)
