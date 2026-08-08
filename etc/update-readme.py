#!/usr/bin/env python3
"""
Splice a generated benchmark block into README.md, between the marker comments.

    ./etc/update-readme.py README.md target/benchmark.md

Everything between BENCHMARK:START and BENCHMARK:END is machine-generated - hand edits
there are overwritten on the next run. The surrounding prose is hand-written and is
never touched.
"""
import sys

START = "<!-- BENCHMARK:START -->"
END = "<!-- BENCHMARK:END -->"


def main(readme_path, block_path):
    readme = open(readme_path).read()
    block = open(block_path).read().strip()

    start = readme.find(START)
    end = readme.find(END)
    if start == -1 or end == -1:
        sys.exit(f"ERROR: {readme_path} is missing the {START} / {END} markers")
    if end < start:
        sys.exit(f"ERROR: {END} appears before {START} in {readme_path}")

    updated = readme[:start + len(START)] + "\n\n" + block + "\n\n" + readme[end:]
    if updated == readme:
        print("README unchanged")
        return
    open(readme_path, "w").write(updated)
    print(f"README updated from {block_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
