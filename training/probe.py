"""
Quick look at how the model reads phrasings that are in neither the training templates nor the test set.
Not a test: prints the model's reading next to what a person would expect, to find gaps in generate.py.

    python3 probe.py ../models/intent-minilm.onnx ../models/intent-vocab.txt
"""
import re, sys
import numpy as np, onnxruntime as ort
from transformers import BertTokenizerFast

INTENTS = ["note", "task", "reminder", "event", "list", "question_list", "question_search"]
TAGS = ["O", "B-TITLE", "I-TITLE", "B-ITEM", "I-ITEM", "B-SEARCH", "I-SEARCH"]
WORD = re.compile(r"[^\W_]+|[^\w\s]|_")

# (sentence, expected intent, expected title / items / search). "Round 1" were probed before the 4th training run
# and drove its new templates; "round 2" were written before looking at the 4th model.
PROBES = [
    ("could you ping me about the electricity bill on sunday", "reminder", "the electricity bill"),
    ("gotta renew my passport before march", "reminder", "renew my passport"),
    ("yo grab some avocados and a loaf of sourdough", "list", ["avocados", "a loaf of sourdough"]),
    ("parking is on level B3 near the lifts", "note", None),
    ("meeting with the architect next tuesday 11am", "event", "meeting with the architect"),
    ("whats left on the groceries", "question_list", None),
    ("where did i keep my spare keys", "question_search", "my spare keys"),
    ("i think i should probably call the insurance people", "task", "call the insurance people"),
    ("hair appointment saturday", "event", "hair appointment"),
    ("don't let me forget the charger", "task", "the charger"),
    ("we are out of detergent and coffee pods", "list", ["detergent", "coffee pods"]),
    ("feed the cat at 7 every evening", "reminder", "feed the cat"),
    ("the movie was boring", "note", None),
    ("cancel my 5pm", "note", None),
    ("note to self buy a new kettle", "task", "buy a new kettle"),
    ("tell meera i'll be late", "task", "tell meera i'll be late"),
    # round 2
    ("nudge me to stretch in 40 minutes", "reminder", "stretch"),
    ("can u remind me abt the vet tmrw", "reminder", "the vet"),
    ("we're running out of shampoo, also need cotton pads", "list", ["shampoo", "cotton pads"]),
    ("lunch w/ priya thursday 1pm", "event", "lunch w/ priya"),
    ("let dad know the car is ready", "task", "let dad know the car is ready"),
    ("what's on the list", "question_list", None),
    ("when's my next haircut", "question_search", "haircut"),
    ("my seat is 14C on the flight", "note", None),
    ("make sure i water the tomatoes tonight", "reminder", "water the tomatoes"),
    ("probably need to sort out the gym membership", "task", "sort out the gym membership"),
    ("swimming every tuesday at 6", "event", "swimming"),
    ("idk if i should text him", "note", None),
    ("pick up dry cleaning on the way home", "task", "pick up dry cleaning on the way home"),
    ("milk eggs coffee", "list", ["milk", "eggs", "coffee"]),
    ("book flights to goa for diwali", "task", "book flights to goa for diwali"),
    ("did i already pay the maid", "question_search", "the maid"),
]

def main(model, vocab):
    tok = BertTokenizerFast(vocab, do_lower_case=True)
    s = ort.InferenceSession(model)
    ok = 0
    for text, want, want_what in PROBES:
        words = WORD.findall(text)
        e = tok(words, is_split_into_words=True)
        il, tl = s.run(None, {"input_ids": np.array([e["input_ids"]]), "attention_mask": np.array([e["attention_mask"]])})
        tags, prev = [], None
        for i, w in enumerate(e.word_ids()):
            if w is not None and w != prev: tags.append((words[w], TAGS[tl[0][i].argmax()]))
            prev = w
        intent = INTENTS[il[0].argmax()]
        title = " ".join(w for w, t in tags if t.endswith("TITLE"))
        search = " ".join(w for w, t in tags if t.endswith("SEARCH"))
        items, cur = [], []
        for w, t in tags:
            if t == "B-ITEM": cur and items.append(" ".join(cur)); cur = [w]
            elif t == "I-ITEM": cur.append(w)
            else: cur and items.append(" ".join(cur)); cur = []
        cur and items.append(" ".join(cur))
        got = items if intent == "list" else (search if intent == "question_search" else (title or None))
        norm = lambda x: [re.sub(r"\s+", "", i.lower()) for i in x] if isinstance(x, list) else (re.sub(r"\s+", "", x.lower()) if x else None)
        good = intent == want and (want_what is None or norm(got) == norm(want_what))
        ok += good
        print(("ok  " if good else "BAD ") + f"{intent:16s} {str(got)[:40]:42s} {text}")
    print(f"{ok}/{len(PROBES)} as expected")

if __name__ == "__main__":
    main(*sys.argv[1:3])
