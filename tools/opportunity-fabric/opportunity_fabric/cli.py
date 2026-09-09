from __future__ import annotations
import argparse, json
from . import __version__
from .inventory import detect_local
from .registry import PLUGINS
from .policy import POLICIES
from .quantus_tools import source_preflight, build_plan, execute_build


def dump(obj):
    print(json.dumps(obj, ensure_ascii=False, indent=2))


def main():
    p = argparse.ArgumentParser(prog='of', description=f'Opportunity Fabric v{__version__}')
    sub = p.add_subparsers(dest='cmd', required=True)
    sub.add_parser('inventory')
    sub.add_parser('policies')
    ev = sub.add_parser('evaluate')
    ev.add_argument('opportunity', choices=sorted(PLUGINS))
    ev.add_argument('--provider', default='local', choices=sorted(POLICIES))
    sub.add_parser('status')
    sub.add_parser('quantus-preflight')
    qp = sub.add_parser('quantus-build-plan')
    qp.add_argument('--jobs', type=int, default=2)
    qb = sub.add_parser('quantus-build')
    qb.add_argument('--jobs', type=int, default=2)
    qb.add_argument('--execute', action='store_true', help='Actually clone/build/benchmark the official miner source.')
    args = p.parse_args()

    if args.cmd == 'inventory':
        dump(detect_local().to_dict()); return
    if args.cmd == 'policies':
        dump(POLICIES); return
    if args.cmd == 'evaluate':
        r = detect_local(); r.provider = args.provider
        dump(PLUGINS[args.opportunity].evaluate(r).to_dict()); return
    if args.cmd == 'status':
        r = detect_local()
        dump({'version':__version__,'resource':r.to_dict(), 'opportunities':{k:v.evaluate(r).to_dict() for k,v in PLUGINS.items()}}); return
    if args.cmd == 'quantus-preflight':
        dump(source_preflight()); return
    if args.cmd == 'quantus-build-plan':
        dump(build_plan(args.jobs)); return
    if args.cmd == 'quantus-build':
        if not args.execute:
            dump({'executed': False, 'plan': build_plan(args.jobs), 'hint': 'Re-run with --execute to perform the build.'}); return
        dump(execute_build(args.jobs)); return

if __name__ == '__main__':
    main()
