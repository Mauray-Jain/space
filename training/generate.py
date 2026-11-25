"""
Synthetic training sentences for the MiniLM intent + word tagger (PLAN.md §2).

Written from templates and word lists here, never from testdata/ (CLAUDE.md: never train on the
test set). The only contact with the test set is dedupe.py dropping exact duplicates of its inputs.

Each example: {"text", "intent", "tags"} where tags are per word (see WORD below):
O, B-TITLE, I-TITLE, B-ITEM, I-ITEM, B-SEARCH, I-SEARCH.
"""
import json, random, re, sys

random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 7)
WORD = re.compile(r"[^\W_]+|[^\w\s]|_")  # same split as the app's tokenizer

INTENTS = ["note", "task", "reminder", "event", "list", "question_list", "question_search"]

def pick(xs): return random.choice(xs)

# ---------- vocabulary ----------
PEOPLE = ["mom", "dad", "grandma", "grandpa", "Ananya", "Vikram", "Meera", "Kabir", "Sara", "Tom", "the landlord", "the plumber",
          "my sister", "my brother", "Neha", "Arjun", "Ishaan", "the electrician", "the accountant", "Dr Rao", "Zoya", "Leo", "the school"]
ACTIONS = [
    "call {p}", "text {p}", "email {p}", "message {p}", "ping {p}", "pay the internet bill", "pay the water bill", "pay rent",
    "pay the credit card bill", "water the garden", "feed the fish", "walk the dog", "take out the recycling", "check the mail",
    "book a cab", "book flight tickets", "renew my licence", "renew the subscription", "fix the shelf", "return the books",
    "clean the kitchen", "submit the report", "send the slides", "send {p} the photos", "pick up the parcel", "pick up {p}",
    "collect the laundry", "charge the car", "buy a present for {p}", "order a cake", "print the boarding pass",
    "sign the lease", "schedule a haircut", "make a doctor appointment", "start the washing machine", "turn off the geyser",
    "lock the back door", "take my medicine", "take the vitamins", "drink some water", "stretch", "meditate", "go for a run",
    "go to the bank", "leave for the station", "back up the laptop", "update the budget", "reply to {p}", "ask {p} about the trip",
    "tell {p} about dinner", "wish {p} happy birthday", "review the contract", "prepare the presentation", "finish the essay",
    "read chapter four", "study for the exam", "practice guitar", "file the taxes", "transfer money to {p}", "withdraw cash",
    "post the letter", "drop the keys at {p}'s", "refill the water filter", "top up the metro card", "recharge my phone",
    "iron the shirts", "vacuum the living room", "mop the floor", "cut the vegetables", "defrost the chicken", "check the oven",
    "move the car", "change the bedsheets", "invite {p} for lunch", "register for the workshop", "cancel the gym trial",
    "follow up with {p}", "confirm the booking", "visit {p}", "get the bike serviced", "get my eyes tested",
]
EVENTS = ["dentist", "dentist appointment", "doctor appointment", "eye checkup", "team meeting", "standup", "project review",
          "client call", "interview", "yoga class", "piano lesson", "football practice", "gym", "badminton", "haircut",
          "parent teacher meeting", "flight to Pune", "train to Jaipur", "bus to Goa", "movie with {p}", "dinner with {p}",
          "lunch with {p}", "coffee with {p}", "drinks with {p}", "{p}'s wedding", "{p}'s birthday party", "board meeting",
          "book club", "concert", "farewell party", "school play", "vet appointment", "car service", "wedding anniversary dinner",
          "physiotherapy", "therapy session", "tax consultant", "bank appointment", "housewarming at {p}'s", "plumber", "electrician visit"]
ITEMS = ["milk", "eggs", "bread", "butter", "cheese", "yogurt", "curd", "paneer", "rice", "atta", "sugar", "salt", "tea", "coffee",
         "bananas", "apples", "oranges", "grapes", "mangoes", "tomatoes", "onions", "potatoes", "garlic", "ginger", "spinach",
         "coriander", "lemons", "chillies", "carrots", "cucumber", "chicken", "fish", "pasta", "noodles", "oats", "cereal",
         "honey", "jam", "ketchup", "biscuits", "chips", "almonds", "dal", "oil", "ghee", "soap", "shampoo", "toothpaste",
         "detergent", "tissues", "toilet paper", "dish soap", "batteries", "bin bags", "juice", "soda", "water bottles",
         "ice cream", "peanut butter", "olive oil", "green tea", "brown bread", "oat milk", "frozen peas", "cat food", "dog food",
         "paper towels", "baking soda", "cling film", "tea bags", "garbage bags", "almond milk", "chocolate chips", "rice flour",
         "coconut oil", "hand wash", "kitchen roll", "cotton buds", "soy sauce", "corn flakes", "sparkling water", "dish tabs",
         "capsule coffee", "face wash", "baby wipes", "light bulbs", "aa batteries", "trash bags"]
QUANT = ["2 litres of {i}", "a dozen {i}", "1 kg of {i}", "a packet of {i}", "two {i}", "a bottle of {i}", "half a kg of {i}", "3 {i}"]
PAIRS = ["salt and pepper", "bread and butter", "mac and cheese", "fish and chips", "milk and cookies"]
HOLIDAYS = ["diwali", "holi", "christmas", "eid", "new year's day", "my birthday", "our anniversary", "new year's eve", "raksha bandhan"]
PLACES = ["the pharmacy", "the supermarket", "the office", "the bank", "home", "work", "the mall", "the station", "the airport"]

DAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
def clock():
    h = random.randint(1, 12)
    return pick([f"{h}", f"{h} pm", f"{h} am", f"{h}pm", f"{h}:{pick(['15','30','45','05'])}", f"{h}:30 pm", f"{random.randint(13,22)}:{pick(['00','30'])}"])
def time_phrase():
    return pick([
        f"at {clock()}", f"tomorrow at {clock()}", f"on {pick(DAYS)} at {clock()}", f"{pick(DAYS)} at {clock()}",
        f"{pick(DAYS)} {clock()}", "tomorrow", "tonight", "this evening", "tomorrow morning", "tomorrow evening", "tomorrow night",
        "this afternoon", "in the morning", f"in {random.randint(2,50)} minutes", f"in {random.randint(2,9)} hours", "in an hour",
        "in half an hour", f"in {random.randint(2,10)} days", "in a week", "next week", "next month", f"next {pick(DAYS)}",
        f"this {pick(DAYS)}", f"on {pick(DAYS)}", f"on the {random.randint(1,28)}{pick(['st','nd','rd','th'])}",
        f"on {pick(['jan','feb','mar','apr','may','june','july','aug','sep','oct','nov','dec'])} {random.randint(1,28)}",
        f"on {random.randint(1,28)} {pick(['january','march','may','august','october','december'])}", "by eod", "by end of week",
        "at noon", "at midnight", "this weekend", "around 4", "at about 7", f"at {random.randint(1,11)}ish", "at half past 2",
        "at quarter to 9", f"{pick(DAYS)} morning", f"{pick(DAYS)} evening", "by the end of the month", f"on {random.randint(1,28)}/{random.randint(1,12)}",
    ])
def repeat_phrase():
    return pick(["every day", "daily", "every morning", "every evening", f"every {pick(DAYS)}", f"every {pick(DAYS)} and {pick(DAYS)}",
                 "every other week", f"every other {pick(DAYS)}", "on weekdays", "weekdays", "every month", "every year",
                 f"every {random.randint(2,5)} days", f"every {random.randint(2,6)} months", "twice a day", "every weekend",
                 f"on the {random.randint(1,28)}th of every month", f"every {random.randint(1,31)}th", f"first {pick(DAYS)} of every month",
                 f"every {pick(DAYS)} at {clock()}", f"every day at {clock()}"])

# ---------- building examples ----------
def words(s): return WORD.findall(s)

class Ex:
    def __init__(self): self.parts = []
    def add(self, text, tag="O"):
        if text: self.parts.append((text, tag))
        return self
    def build(self, intent):
        out_words, tags = [], []
        for text, tag in self.parts:
            ws = words(text)
            for i, w in enumerate(ws):
                out_words.append(w)
                tags.append("O" if tag == "O" else ("B-" if i == 0 else "I-") + tag)
        text = " ".join(t for t, _ in self.parts)
        text = re.sub(r"\s+([,.?!])", r"\1", text)
        assert words(text) == out_words, (text, out_words)
        return {"text": text, "intent": intent, "tags": tags}

def action():
    return pick(ACTIONS).replace("{p}", pick(PEOPLE))
def event():
    return pick(EVENTS).replace("{p}", pick(PEOPLE))

def reminder():
    a, t = action(), (repeat_phrase() if random.random() < 0.25 else time_phrase())
    form = random.randrange(12)
    e = Ex()
    if form == 0: e.add("remind me to").add(a, "TITLE").add(t)
    elif form == 1: e.add("remind me").add(t).add("to").add(a, "TITLE")
    elif form == 2: e.add(t).add("remind me to").add(a, "TITLE")
    elif form == 3: e.add(pick(["don't forget to", "dont forget to", "do not forget to"])).add(a, "TITLE").add(t)
    elif form == 4: e.add("remember to").add(a, "TITLE").add(t)
    elif form == 5: e.add(pick(["set a reminder", "set a reminder for", "please set a reminder"])).add(t).add("to").add(a, "TITLE")
    elif form == 6: e.add(a, "TITLE").add(t)
    elif form == 7: e.add(t).add(a, "TITLE")
    elif form == 8: e.add(pick(["can you remind me to", "please remind me to", "pls remind me to", "i need to", "i have to", "i must"])).add(a, "TITLE").add(t)
    elif form == 10:  # other ways of asking to be reminded, about a thing rather than an action
        thing = pick(["the gas bill", "the car insurance", "the school fees", "the passport renewal", "the dentist", "the visa forms",
                      "the parcel", "the rent", "the library books", "the tax return", "the broadband plan", "the vet visit"])
        e.add(pick(["ping me about", "nudge me about", "buzz me about", "alert me about", "remind me about", "give me a shout about",
                    "could you ping me about", "can you nudge me about", "pls ping me about", "let me know about"])).add(thing, "TITLE").add(t)
    elif form == 11:
        e.add(pick(["don't let me forget to", "dont let me forget to", "make sure i", "make sure to", "be sure to", "do not let me forget to"])).add(a, "TITLE").add(t)
    else: e.add(a, "TITLE").add(t).add(pick(["please", "thanks", "ok", ""]))
    return e.build("reminder")

def event_ex():
    v, t = event(), (repeat_phrase() if random.random() < 0.2 else time_phrase())
    e = Ex()
    form = random.randrange(5)
    if form == 0: e.add(v, "TITLE").add(t)
    elif form == 1: e.add(t).add(v, "TITLE")
    elif form == 2: e.add(v, "TITLE").add(pick(["is", "is on", "at", ""])).add(t)
    elif form == 3: e.add(pick(["daily", "weekly", "monthly"]) + " " + pick(["standup", "sync", "review", "check-in", "call"]), "TITLE").add(f"at {clock()}")
    else: e.add(v.capitalize(), "TITLE").add(t)
    return e.build("event")

def task():
    a = action()
    e = Ex()
    form = random.randrange(12)
    if form == 0: e.add(a, "TITLE")
    elif form == 1: e.add(pick(["remind me to", "remember to", "don't forget to", "need to", "i need to", "i have to", "gotta", "todo"])).add(a, "TITLE")
    elif form == 2: e.add(a.capitalize() + ".", "TITLE") if False else e.add(a.capitalize(), "TITLE").add(".")
    elif form == 3:
        loc = f"when i'm at {pick(PLACES)}" if random.random() < 0.6 else pick(["when i get home", "when i get to work", "when i reach the office"])
        e.add(pick(["remind me to", "", "don't forget to"])).add(f"{a} {loc}", "TITLE")
    elif form == 4: e.add(f"buy {pick(['a gift', 'a present', 'flowers', 'a card', 'new shoes', 'a charger'])} for {pick(PEOPLE)}", "TITLE")
    elif form == 5: e.add(f"buy {pick(['new tyres', 'a helmet', 'sunscreen', 'a power bank', 'snacks'])} before the {pick(['trip', 'flight', 'match', 'weekend'])}", "TITLE")
    elif form == 6: e.add(f"get the {pick(['car', 'bike', 'ac', 'fridge', 'laptop', 'phone screen'])} {pick(['serviced', 'fixed', 'repaired', 'cleaned', 'checked'])}", "TITLE")
    elif form == 7: e.add(f"{pick(['call', 'text', 'email'])} {pick(PEOPLE)} and {pick(PEOPLE)}", "TITLE")
    elif form == 8: e.add(a, "TITLE").add(pick(["asap", "soon", "sometime", ""]))
    elif form == 9:  # the message is part of the title
        p, clause = pick(PEOPLE), pick(["i'll be late", "the meeting moved to friday", "we're out of milk", "dinner is at 8",
                                         "the keys are with the guard", "i got the tickets", "the car is fixed", "i can't make it"])
        verb = pick([f"tell {p}", f"let {p} know", f"remind {p} that", f"text {p} that", f"message {p} saying", f"ask {p} whether"])
        e.add(f"{verb} {clause}", "TITLE")
    elif form == 10:
        thing = pick(["the charger", "the umbrella", "the lunch box", "my glasses", "the gift", "the documents", "the spare key"])
        e.add(pick(["don't let me forget", "dont let me forget", "make sure i take", "make sure i pack", "don't forget"])).add(thing, "TITLE")
    else:  # hedged: still something to do
        e.add(pick(["i think i should", "maybe i should", "i should probably", "i really need to", "i ought to", "probably should",
                    "i guess i should"])).add(a, "TITLE")
    return e.build("task")

def items_list():
    n = random.choice([1, 1, 2, 2, 3, 3, 4])
    its = []
    for _ in range(n):
        r = random.random()
        if r < 0.15: its.append(pick(QUANT).replace("{i}", pick(ITEMS)))
        elif r < 0.22: its.append(pick(PAIRS))
        else: its.append(pick(ITEMS))
    return its

def shopping():
    its = items_list()
    e = Ex()
    lead = pick(["buy", "grab", "get", "need", "we need", "i need", "pick up", "buy some", "get some", "we're out of", "out of",
                 "add", "put", "we need more", "running low on", "we are out of", "we've run out of", "we ran out of", "almost out of"])
    if lead.endswith("some"):
        e.add(lead.replace(" some", "")).add("some")
        its = [pick(ITEMS) for _ in its]
    elif lead in ("add", "put"): pass
    else: e.add(lead)
    no_commas = random.random() < 0.25
    for i, it in enumerate(its):
        if i > 0:
            if i == len(its) - 1: e.add(pick([", and", "and", ","]) if not no_commas else "and")
            else: e.add("," if not no_commas else "")
        e.add(it, "ITEM")
    if lead in ("add", "put"):
        e.parts.insert(0, (lead, "O"))
        e.add(pick(["to the shopping list", "to the grocery list", "on the list", "to my list", "on the grocery list", "to groceries"]))
    return e.build("list")

def question_list():
    """Questions answered by opening the shopping list. Built from parts so there are hundreds of different ones."""
    whose = pick(["my", "the", "our", "my", "the"])
    kind = pick(["grocery", "shopping", "groceries", "supermarket", "market"])
    lst = pick(["list", "list", "list items", ""]).strip()
    thing = f"{whose} {kind} {lst}".strip()
    who = pick(["i", "we"])
    q = pick([
        f"{pick(['what is on', 'whats on', 'what is in', 'what all is on', 'read me', 'show me', 'show', 'open', 'check', 'pull up', 'can you show me', 'let me see', 'display'])} {thing}",
        f"what {pick(['do', 'else do', 'all do', 'exactly do', 'should'])} {who} {pick(['need to buy', 'have to buy', 'need to get', 'need from the store', 'need from the shop', 'need from the market', 'buy', 'pick up', 'get from the supermarket'])}",
        f"{pick(['anything', 'is there anything', 'what is', 'whats'])} {pick(['left to buy', 'left on the list', 'still to buy', 'pending on the list'])}",
        f"what {pick(['groceries', 'items', 'things', 'stuff'])} {pick(['do', 'should'])} {who} {pick(['need', 'buy', 'get'])}",
        f"{pick(['do', 'did'])} {who} {pick(['need anything', 'have anything to buy', 'need to buy anything'])} {pick(['from the store', 'from the shop', 'today', ''])}".strip(),
        f"{pick(['how many things are on', 'how long is', 'what is left on'])} {thing}",
    ])
    q = q.replace("what is on", pick(["what's on", "what is on", "whats on"]))
    return Ex().add(q + pick(["?", "", "?", " please"])).build("question_list")

def question_search():
    q = random.randrange(8)
    e = Ex()
    thing = pick(["dentist", "flight to Pune", "train ticket", "wifi password", "parking spot", "locker code", "gym timing",
                  "electricity bill", "rent", "insurance policy", "passport number", "meeting with Kabir", "car service", "concert"])
    if q == 0: e.add(pick(["when is the", "when's the", "what time is the", "where is the", "what's the", "whats the"])).add(thing, "SEARCH")
    elif q == 1: e.add(pick(["find the", "search for the", "show me the", "look up the"])).add(pick(["screenshot of the", "note about the", "photo of the", ""])).add(thing, "SEARCH")
    elif q == 2: e.add(pick(["did i pay the", "did i book the", "did i renew the", "have i paid the"])).add(thing, "SEARCH")
    elif q == 3: e.add(pick(["where did i", "where did we", "when did i"])).add(pick(["park", "keep the keys", "save the receipt", "leave my umbrella"]), "SEARCH")
    elif q == 4: e.add("what was").add(pick(["the name of the cafe", "the locker code", "the wifi password"]), "SEARCH")
    elif q == 5: e.add(pick(["how much was the", "how much did i pay for the"])).add(thing, "SEARCH")
    elif q == 6: e.add(pick(["who is", "who's"])).add(pick(PEOPLE), "SEARCH")
    elif random.random() < 0.5: e.add("what did").add(pick(PEOPLE), "SEARCH").add("say")
    else: e.add("what did i note about").add(pick(PEOPLE), "SEARCH")
    return e.add(pick(["?", ""])).build("question_search")

FACT_SUBJECTS = ["the wifi password", "the gate code", "the locker number", "my seat", "the parking spot", "the bike lock code",
                 "Kabir's flat number", "the guest wifi", "the office extension", "my blood group", "the car plate"]
def note():
    k = random.randrange(12)
    e = Ex()
    if k == 0: e.add(f"{pick(FACT_SUBJECTS)} is {pick(['B4', 'hunter7', 'level 2', '9182', 'row 12 seat 4', 'O positive', 'MH 12 AB 3456', 'tiger22'])}")
    elif k == 1: e.add(f"{pick(['idea', 'book', 'movie to watch', 'gift idea', 'recipe', 'podcast', 'note', 'quote'])}: {pick(['a lamp that follows the sun', 'The Overstory', 'Dune part two', 'a wool scarf for dad', 'masala oats with peas', 'the daily stoic', 'be kind'])}")
    elif k == 2: e.add(f"{pick(PEOPLE)}'s number is {random.randint(90000,99999)} {random.randint(10000,99999)}")
    elif k == 3: e.add(pick(["the blue jacket is too small", "the new cafe near the station is good", "Kabir likes dark chocolate",
                             "the printer needs A4 paper", "this phone gets warm when charging", "the pasta needs more salt",
                             "the train was 20 minutes late today", "the landlord prefers bank transfer"]))
    elif k == 4:  # negations: no auto-action
        a, t = action(), time_phrase()
        e.add(pick(["i don't need to", "i dont need to", "no need to", "don't", "dont", "i won't", "we don't have to", "i do not have to",
                    "not sure if i should", "no longer need to", "never mind,", "stop reminding me to"])).add(a).add(pick([t, ""]))
    elif k == 5: e.add(pick(["cancel the", "cancelled the", "skip the"])).add(event()).add(pick([time_phrase(), ""]))
    elif k == 6: e.add(f"the {event()} {pick(['is cancelled', 'got cancelled', 'is postponed', 'is off', 'was moved'])}")
    elif k == 7:  # holidays and birthdays as the date
        e.add(pick(["", "remind me to", "don't forget to"])).add(action()).add(f"on {pick(HOLIDAYS)}")
    elif k == 8:  # long ramble
        bits = ["so i was thinking", "about the trip", "and maybe we should", "book the hotel early", "because prices go up",
                "next month", "and also ask mom", "if she wants to come", "on friday at 6", "we could leave early", "and take the train",
                "instead of driving", "it might be cheaper", "but i'm not sure", "let me check later", "anyway", "the weather looks fine"]
        e.add(" ".join(random.choice(bits) for _ in range(random.randint(16, 26))))
    elif k == 9: e.add(f"{pick(['recipe', 'article', 'link', 'video'])} {pick(['https://example.org/dal', 'www.example.com/notes', 'example.in/read'])}")
    elif k == 10:  # items you no longer need: not a shopping list
        its = " and ".join(pick(ITEMS) for _ in range(random.choice([1, 1, 2])))
        e.add(pick([f"we no longer need {its}", f"we don't need {its} anymore", f"no need for {its}", f"we have enough {its}",
                    f"already bought {its}", f"got the {its} already", f"skip the {its} this week", f"i don't need {its}",
                    f"we still have {its}", f"cancel the {its}", f"no more {its} needed"]))
    else:  # messages from apps and services (OTPs, deliveries, bank alerts): information, not to-dos
        n = random.randint(100000, 999999)
        e.add(pick([
            f"Your OTP is {n}. Valid for {random.randint(3, 15)} minutes.", f"{n} is your verification code. Do not share it.",
            f"Use {n} to log in. It expires in {random.randint(5, 10)} minutes.", f"Your order #{random.randint(1000, 99999)} has been delivered.",
            f"Your package will arrive by {pick(DAYS)}.", f"Rs {random.randint(100, 9999)} debited from your account on {random.randint(1, 28)} {pick(['Nov', 'Dec', 'Jan'])}.",
            f"Your booking is confirmed. PNR {random.randint(1000000000, 9999999999)}.", "Payment received. Thank you for shopping with us.",
            f"Your appointment request has been received. Ref {n}.", f"Login code: {n}", "Your table for 4 is confirmed.",
            f"Balance: Rs {random.randint(1000, 99999)}.", f"Your ride is arriving in {random.randint(2, 9)} minutes.",
        ]))
    return e.build("note")

MAKERS = [(reminder, 30), (event_ex, 12), (task, 16), (shopping, 14), (question_list, 5), (question_search, 7), (note, 18)]

def voiceify(ex):
    """Speech-to-text style: lowercase, no punctuation. Fillers are stripped by the app before the model."""
    keep = [(w, t) for w, t in zip(WORD.findall(ex["text"]), ex["tags"]) if re.match(r"[^\W_]", w) or w == "'"]
    text = ex["text"].lower()
    text = re.sub(r"[,.?!]", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    ws = WORD.findall(text)
    if len(ws) != len(keep): return ex
    tags = [t for _, t in keep]
    # A dropped first word of a span would leave an I- tag first: fix it.
    tags = [("B-" + t[2:]) if t.startswith("I-") and (i == 0 or tags[i - 1] == "O" or tags[i - 1][2:] != t[2:]) else t for i, t in enumerate(tags)]
    return {"text": text, "intent": ex["intent"], "tags": tags}

def main(n):
    makers, weights = zip(*MAKERS)
    seen = set()
    out = []
    while len(out) < n:
        ex = random.choices(makers, weights)[0]()
        if random.random() < 0.3: ex = voiceify(ex)
        if ex["text"] in seen: continue
        seen.add(ex["text"])
        out.append(ex)
    for ex in out: print(json.dumps(ex))

if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 30000)
