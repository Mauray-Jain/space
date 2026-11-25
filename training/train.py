"""
Fine-tunes MiniLM-L6 (sentence-transformers/all-MiniLM-L6-v2, Apache-2.0) on generated sentences:
one head picks the intent from [CLS], one tags each word (title / item / search). Exports ONNX, int8.

    python3 generate.py 30000 7 > gen.jsonl
    python3 dedupe.py ../testdata/understanding_test.jsonl gen.jsonl train.jsonl
    python3 train.py train.jsonl out/
"""
import json, os, random, re, sys
import torch
from torch import nn
from transformers import AutoModel, AutoTokenizer

# sentence-transformers/all-MiniLM-L6-v2 at revision 1110a243fdf4706b3f48f1d95db1a4f5529b4d41 (Apache-2.0),
# downloaded to a folder (MINILM_BASE): config.json, model.safetensors (sha256 53aa5117…), tokenizer files, vocab.txt.
BASE = os.environ["MINILM_BASE"]
INTENTS = ["note", "task", "reminder", "event", "list", "question_list", "question_search"]
TAGS = ["O", "B-TITLE", "I-TITLE", "B-ITEM", "I-ITEM", "B-SEARCH", "I-SEARCH"]
WORD = re.compile(r"[^\W_]+|[^\w\s]|_")
MAX_LEN = 64

class Tagger(nn.Module):
    def __init__(self):
        super().__init__()
        self.bert = AutoModel.from_pretrained(BASE)
        h = self.bert.config.hidden_size
        self.intent = nn.Linear(h, len(INTENTS))
        self.tags = nn.Linear(h, len(TAGS))
        self.drop = nn.Dropout(0.1)

    def forward(self, input_ids, attention_mask):
        x = self.bert(input_ids=input_ids, attention_mask=attention_mask, token_type_ids=torch.zeros_like(input_ids)).last_hidden_state
        x = self.drop(x)
        return self.intent(x[:, 0]), self.tags(x)

def encode(tok, ex):
    words = WORD.findall(ex["text"])
    enc = tok(words, is_split_into_words=True, truncation=True, max_length=MAX_LEN)
    labels, prev = [], None
    for wid in enc.word_ids():
        labels.append(-100 if wid is None or wid == prev else TAGS.index(ex["tags"][wid]))
        prev = wid
    return enc["input_ids"], labels, INTENTS.index(ex["intent"])

def batches(data, size):
    random.shuffle(data)
    for i in range(0, len(data), size):
        chunk = data[i:i + size]
        n = max(len(c[0]) for c in chunk)
        ids = torch.tensor([c[0] + [0] * (n - len(c[0])) for c in chunk])
        mask = torch.tensor([[1] * len(c[0]) + [0] * (n - len(c[0])) for c in chunk])
        labels = torch.tensor([c[1] + [-100] * (n - len(c[1])) for c in chunk])
        intents = torch.tensor([c[2] for c in chunk])
        yield ids, mask, labels, intents

def main(train_file, out):
    torch.manual_seed(7); random.seed(7)
    torch.set_num_threads(os.cpu_count())
    tok = AutoTokenizer.from_pretrained(BASE)
    data = [encode(tok, json.loads(l)) for l in open(train_file)]
    held = data[:1000]; data = data[1000:]
    model = Tagger()
    opt = torch.optim.AdamW(model.parameters(), lr=6e-5, weight_decay=0.01)
    epochs = int(os.environ.get("EPOCHS", 3))
    steps = epochs * ((len(data) + 63) // 64)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=6e-5, total_steps=steps, pct_start=0.1)
    ce = nn.CrossEntropyLoss(ignore_index=-100)
    step = 0
    for epoch in range(epochs):
        model.train()
        for ids, mask, labels, intents in batches(data, 64):
            li, lt = model(ids, mask)
            loss = ce(li, intents) + ce(lt.reshape(-1, len(TAGS)), labels.reshape(-1))
            opt.zero_grad(); loss.backward(); opt.step(); sched.step(); step += 1
            if step % 100 == 0: print(f"epoch {epoch} step {step}/{steps} loss {loss.item():.4f}", flush=True)
        model.eval()
        with torch.no_grad():
            ok_i = ok_t = n_t = 0
            for ids, mask, labels, intents in batches(list(held), 128):
                li, lt = model(ids, mask)
                ok_i += (li.argmax(-1) == intents).sum().item()
                m = labels != -100
                ok_t += ((lt.argmax(-1) == labels) & m).sum().item(); n_t += m.sum().item()
            print(f"held-out: intent {ok_i / len(held):.4f}, tags {ok_t / n_t:.4f}", flush=True)

    os.makedirs(out, exist_ok=True)
    model.eval()
    ids = torch.tensor([[101, 2000, 102]]); mask = torch.ones_like(ids)
    torch.onnx.export(model, (ids, mask), f"{out}/intent-fp32.onnx", input_names=["input_ids", "attention_mask"],
                      output_names=["intent_logits", "tag_logits"],
                      dynamic_axes={"input_ids": {1: "n"}, "attention_mask": {1: "n"}, "tag_logits": {1: "n"}}, opset_version=17, dynamo=False)
    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(f"{out}/intent-fp32.onnx", f"{out}/intent-minilm.onnx", weight_type=QuantType.QInt8)
    tok.save_vocabulary(out)
    print("saved", flush=True)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
