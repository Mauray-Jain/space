# Training the intent model (MiniLM)

The app understands typed and spoken captures with **MiniLM-L6** fine-tuned here: one head picks the
intent (note, task, reminder, event, list, question about the list, question that searches) and one tags
each word (title, list item, search words). Dates and times are not learned: the tagged sentence goes
to the date code (`app/.../understand/When.kt`), which follows the rules in `testdata/README.md`.

**The test set is never used for training** (CLAUDE.md). `generate.py` builds sentences from its own
templates and word lists; `dedupe.py` reads only the test *inputs*, to drop any generated sentence that
happens to be identical to one. The model is judged by `UnderstandingTest` on the test set.

## Reproduce

```bash
pip install torch transformers onnx onnxruntime
# Base model: sentence-transformers/all-MiniLM-L6-v2, revision 1110a243fdf4706b3f48f1d95db1a4f5529b4d41 (Apache-2.0).
# Download config.json, model.safetensors, tokenizer.json, tokenizer_config.json, special_tokens_map.json, vocab.txt into a folder.
python3 generate.py 30000 7 > gen.jsonl
python3 dedupe.py ../testdata/understanding_test.jsonl gen.jsonl train.jsonl
MINILM_BASE=path/to/base python3 train.py train.jsonl out/
cp out/intent-minilm.onnx ../models/intent-minilm.onnx
cp out/vocab.txt ../models/intent-vocab.txt
```

Training takes about 15 minutes on 4 CPU cores. The int8 model is committed in `models/` (the build
copies it into the APK), since it isn't published anywhere to download from.

## Size and memory

int8 ONNX ≈ 23 MB on disk. It's loaded when the capture sheet opens and closed when the capture is
saved or dismissed; nothing keeps it in memory in the background.
