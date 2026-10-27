#!/usr/bin/env python3
"""Run bounded smoke checks for the active local Ponderer client instances."""

import runpy
import sys
from pathlib import Path


if __name__ == "__main__":
    launcher = Path(__file__).resolve().with_name("test.py")
    sys.argv = [str(launcher), "--smoke", *sys.argv[1:]]
    runpy.run_path(str(launcher), run_name="__main__")
