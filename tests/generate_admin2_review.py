#!/usr/bin/env python3
"""Regenerate the synthetic Admin-2 preview using a user-supplied World Bank ZIP.

The geometry archive is not redistributed. Survey inputs are synthetic committed
CSV fixtures. Python is used only by this optional development helper, not by
SurvEye's Stata/Java runtime. No GIS package is required for this helper.
"""
from __future__ import annotations
import argparse, csv, shutil, subprocess
from pathlib import Path

def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('boundaries', type=Path)
    p.add_argument('--output', type=Path, default=Path('examples/surveye_admin2_review.html'))
    p.add_argument('--replace', action='store_true')
    p.add_argument('--diagnostics', action='store_true', help='Use synthetic invalid/outside coordinate cases for browser regression QA.')
    p.add_argument('--uilanguage', choices=['english','arabic','urdu'], default='english')
    p.add_argument('--theme', choices=['worldbank','dark','clean','forest'], default='worldbank')
    args = p.parse_args()
    root = Path(__file__).resolve().parents[1]
    archive = args.boundaries.expanduser().resolve()
    output = args.output if args.output.is_absolute() else root / args.output
    output = output.resolve()
    if not archive.is_file():
        p.error(f'Boundary ZIP does not exist: {archive}')
    if '\t' in str(archive) or '\n' in str(archive) or '\t' in str(output) or '\n' in str(output):
        p.error('Paths must not contain tabs or line breaks.')
    if output.exists() and not args.replace:
        p.error(f'Output exists; use --replace to regenerate: {output}')
    java = shutil.which('java')
    if not java or not (root/'surveye.jar').is_file():
        p.error('Build surveye.jar first and ensure Java is available.')
    work = root/'build'; work.mkdir(exist_ok=True)
    cfg = (root/'tests/review_admin2_config.tsv.in').read_text(encoding='utf-8')
    cfg = cfg.replace('@BOUNDARY_ZIP@', str(archive))
    settings = dict(line.split('\t',1) for line in cfg.splitlines() if '\t' in line)
    settings.update(output=str(output), uilanguage=args.uilanguage, theme=args.theme)
    if args.diagnostics:
        with (root/'tests/review_admin2_data.csv').open(encoding='utf-8',newline='') as f:
            rows=list(csv.DictReader(f))
        for code, lat, lon in [('01','',''),('1','999','12'),('03','27.7172','85.3240'),('04','0','0')]:
            row=next(r for r in rows if r['region']==code)
            row.update(latitude=lat,longitude=lon)
        path=work/'review-coordinate-edge-cases.csv'
        with path.open('w',encoding='utf-8',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]))
            writer.writeheader();writer.writerows(rows)
        settings['data']=str(path)
    config=work/'review-admin2-generated-config.tsv'
    config.write_text(''.join(f'{k}\t{v}\n' for k,v in settings.items()),encoding='utf-8')
    subprocess.run([java,'-jar','surveye.jar','--config',str(config)],cwd=root,check=True)

if __name__=='__main__':
    main()
