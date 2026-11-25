"""Drops training sentences identical to a test-set input (leak guard). Reads only the test inputs."""
import json, re, sys
norm = lambda s: re.sub(r"[^\w]+", " ", s.lower()).strip()
test = {norm(json.loads(l)["input"]) for l in open(sys.argv[1]) if l.strip()}
kept = dropped = 0
with open(sys.argv[3], "w") as out:
    for l in open(sys.argv[2]):
        if norm(json.loads(l)["text"]) in test: dropped += 1; continue
        out.write(l); kept += 1
print(f"kept {kept}, dropped {dropped} identical to test inputs", file=sys.stderr)
