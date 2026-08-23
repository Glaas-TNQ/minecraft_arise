# -*- coding: utf-8 -*-
"""Il collaudo statico: i difetti che si vedono senza aprire il gioco.

Compilare non e' verificare, e nemmeno avviare il server lo e'. Ma fra "compila" e "ci ho giocato
due ore" c'e' una fascia larga di difetti che si trovano leggendo il progetto con metodo, e che
leggendolo a occhio non si trovano mai: una chiave di traduzione che nessuno ha scritto, un suono
che punta a un file che non esiste, un modello mancante, una ricetta che nomina un oggetto
sbagliato, un incarico che aspetta un obiettivo che nessuno fa avanzare.

Nessuno di questi da' errore. Danno testo grezzo sullo schermo, silenzio al posto di un suono, un
cubo viola al posto di un blocco, una partita che si ferma e non riparte.

    python tools/collaudo.py

Esce con zero se non ha trovato niente, con uno altrimenti: si puo' appendere a un hook o a una
pipeline senza altra colla.
"""
import collections
import glob
import io
import json
import os
import re
import sys

ROOT = "."
A = os.path.join(ROOT, "src/main/resources/assets/arise")
D = os.path.join(ROOT, "src/main/resources/data/arise")

problems = collections.OrderedDict()


def note(kind, message):
    problems.setdefault(kind, []).append(message)


def read_json(path):
    return json.load(io.open(path, encoding="utf-8"))


def java_files():
    for base in ("src/main/java", "src/client/java"):
        for path in glob.glob(os.path.join(ROOT, base, "**", "*.java"), recursive=True):
            yield path


SOURCES = [(path, io.open(path, encoding="utf-8").read()) for path in java_files()]


def where(needle):
    """I file in cui compare questo pezzo di codice, per intero e non come pezzo di un nome.

    Il confine di parola non e' un dettaglio: senza, "Objective.BUY" combacia dentro
    "Objective.BUY_GATE", e il collaudo inventa difetti che non esistono.
    """
    pattern = re.compile(re.escape(needle) + r"\b")
    return [os.path.relpath(p, ROOT).replace("\\", "/") for p, s in SOURCES if pattern.search(s)]


# Una firma di metodo a un livello di rientro: basta per questo codice, che e' tutto scritto
# nello stesso stile.
METHOD = re.compile(r"^\t(?:public|private|protected|static)[^;{]*\{", re.M)


def methods(source):
    """Spezza un file nei suoi metodi, per non confondere due parti della stessa classe."""
    starts = [m.start() for m in METHOD.finditer(source)]
    if not starts:
        return [source]

    starts.append(len(source))
    return [source[starts[i]:starts[i + 1]] for i in range(len(starts) - 1)]


def advancing_methods(caller, objective):
    """I metodi di un file che fanno avanzare questo obiettivo."""
    source = io.open(os.path.join(ROOT, caller), encoding="utf-8").read()
    pattern = re.compile(r"Objective\." + objective + r"\b")
    return [m for m in methods(source) if pattern.search(m)]


def vanilla_sound_files():
    """I nomi dei file di suono che Minecraft si porta dietro.

    Non stanno nel jar: stanno nell'indice degli asset, scaricati a parte e indirizzati per hash.
    Serve leggerli perche' i nostri eventi puntano a file vanilla, e un nome sbagliato non da'
    errore da nessuna parte — da' silenzio, in gioco, dove nessuno lo collega a un refuso.
    """
    indexes = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/assets/indexes/*.json")))

    if not indexes:
        note("collaudo incompleto",
             "indice degli asset non trovato: i suoni non sono stati controllati")
        return None

    entry = read_json(indexes[-1])["objects"].get("minecraft/sounds.json")
    if not entry:
        note("collaudo incompleto", "il sounds.json di Minecraft non e' nell'indice")
        return None

    digest = entry["hash"]
    path = os.path.expanduser("~/.gradle/caches/fabric-loom/assets/objects/%s/%s"
                              % (digest[:2], digest))

    names = set()
    for body in read_json(path).values():
        for sound in body.get("sounds", []):
            name = sound["name"] if isinstance(sound, dict) else sound
            names.add(name if ":" in name else "minecraft:" + name)

    return names


def registered(path, pattern):
    full = os.path.join(ROOT, path)
    if not os.path.exists(full):
        return []
    return re.findall(pattern, io.open(full, encoding="utf-8").read())


# ---------------------------------------------------------------- le due lingue
en = read_json(os.path.join(A, "lang/en_us.json"))
it = read_json(os.path.join(A, "lang/it_it.json"))

for key in sorted(set(en) - set(it)):
    note("lingue disallineate", "manca in it_it: " + key)
for key in sorted(set(it) - set(en)):
    note("lingue disallineate", "manca in en_us: " + key)


# ---------------------------------------------------------------- chiavi scritte a mano
CALL = re.compile(r'translatable\(\s*"([^"]+)"')

used_literal = {}
for path, source in SOURCES:
    for match in CALL.finditer(source):
        key = match.group(1)

        # Conta gli argomenti al primo livello di parentesi dopo la chiave.
        i = match.end()
        depth = 1
        args = 0
        while i < len(source) and depth > 0:
            ch = source[i]
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
                if depth == 0:
                    break
            elif ch == '"':
                i += 1
                while i < len(source) and source[i] != '"':
                    i += 2 if source[i] == "\\" else 1
            elif ch == "," and depth == 1:
                args += 1
            i += 1

        used_literal.setdefault(key, (args, os.path.relpath(path, ROOT).replace("\\", "/")))

for key, (args, origin) in sorted(used_literal.items()):
    if not key.startswith(("arise.", "block.arise", "item.arise", "entity.arise")):
        continue

    # Un letterale che finisce col punto e' un prefisso: la chiave vera la compone il codice.
    if key.endswith("."):
        continue

    if key not in en:
        note("traduzione mancante", "%s  (%s)" % (key, origin))
        continue

    slots = en[key].count("%s") + len(re.findall(r"%\d\$s", en[key]))
    if slots != args:
        note("argomenti che non tornano",
             "%s: il codice ne passa %s, la stringa ne aspetta %s  (%s)"
             % (key, args, slots, origin))


# ---------------------------------------------------------------- chiavi composte
# Prefisso, enum da cui vengono i nomi, suffissi che il codice attacca in coda.
DYNAMIC = [
    ("arise.rank.", "progress/Rank.java", [""]),
    ("arise.stat.", "progress/Stat.java", ["", ".effect", ".desc"]),
    ("arise.threshold.", "progress/StatThreshold.java", ["", ".desc"]),
    ("arise.quest.", "quest/Quest.java", ["", ".goal", ".lore", ".brief"]),
    ("arise.unlock.", "quest/Unlock.java", ["", ".hint"]),
    ("arise.herald.", "tutorial/HeraldPage.java", [""]),
    ("arise.npc.", "npc/Shopkeeper.java", ["", ".greeting", ".shop"]),
    ("arise.soul.trait.", "workshop/SoulTrait.java", ["", ".desc"]),
    ("block.arise.", "workshop/MachineKind.java", [""]),
    ("arise.machine.", "workshop/MachineKind.java", [".hint"]),
    ("arise.city.", "city/City.java", [""]),
    ("arise.stance.", "shadow/ShadowStance.java", [""]),
    ("arise.archetype.", "shadow/ShadowArchetype.java", ["", ".desc"]),
    ("arise.grade.", "shadow/ShadowGrade.java", [""]),
    ("arise.named.", "shadow/NamedShadow.java", ["", ".desc"]),
    ("arise.gem.type.", "gem/GemType.java", [""]),
    ("arise.gem.effect.", "gem/GemType.java", [""]),
    ("arise.ability.", "ability/Ability.java", [""]),
    ("arise.gate.theme.", "gate/GateTheme.java", [""]),
    ("arise.affix.", "gate/MobAffix.java", ["", ".desc"]),
    ("arise.objective.", "gate/GateObjective.java", ["", ".desc"]),
    ("arise.abyss.rule.", "gate/AbyssRule.java", ["", ".desc"]),
    ("arise.daily.", "daily/DailyTask.java", [""]),
    ("arise.gear.affix.", "gear/GearAffix.java", [""]),
    ("arise.gear.base.", "gear/GearBase.java", [""]),
    ("arise.gear.slot.", "gear/GearSlot.java", [""]),
    ("arise.gear.unique.", "gear/GearUnique.java", ["", ".lore"]),
    ("arise.landmark.", "city/Landmark.java", [""]),
]

NAME = re.compile(r'^\t[A-Z][A-Z_0-9]*\("([a-z_0-9]+)"', re.M)

for prefix, enum_path, suffixes in DYNAMIC:
    full = os.path.join(ROOT, "src/main/java/com/luca/arise", enum_path)

    if not os.path.exists(full):
        note("collaudo da aggiornare", "enum non trovato: " + enum_path)
        continue

    names = NAME.findall(io.open(full, encoding="utf-8").read())
    if not names:
        note("collaudo da aggiornare", "nessun nome estratto da " + enum_path)
        continue

    for name in names:
        for suffix in suffixes:
            key = prefix + name + suffix
            if key not in en:
                note("traduzione mancante",
                     "%s  (composta da %s)" % (key, os.path.basename(enum_path)))


# ---------------------------------------------------------------- suoni
vanilla_sounds = vanilla_sound_files()
sounds = read_json(os.path.join(A, "sounds.json"))

for event, body in sorted(sounds.items()):
    for entry in body.get("sounds", []):
        name = entry["name"] if isinstance(entry, dict) else entry
        full = name if ":" in name else "minecraft:" + name

        if vanilla_sounds is not None and full.startswith("minecraft:") \
                and full not in vanilla_sounds:
            note("suono inesistente", "%s → %s" % (event, name))

    subtitle = body.get("subtitle")
    if subtitle and subtitle not in en:
        note("traduzione mancante", "%s  (sottotitolo di %s)" % (subtitle, event))

mod_sounds = io.open(os.path.join(ROOT, "src/main/java/com/luca/arise/fx/ModSounds.java"),
                     encoding="utf-8").read()
declared_sounds = set(re.findall(r'create\("([^"]+)"\)', mod_sounds))

for event in sorted(declared_sounds - set(sounds)):
    note("suono non definito", event + " e' registrato in ModSounds ma non sta in sounds.json")
for event in sorted(set(sounds) - declared_sounds):
    note("suono orfano", event + " sta in sounds.json ma nessuno lo registra")


# ---------------------------------------------------------------- blocchi e oggetti
machine_paths = re.findall(
    r'\("([a-z_]+)", \d+, (?:true|false)',
    io.open(os.path.join(ROOT, "src/main/java/com/luca/arise/workshop/MachineKind.java"),
            encoding="utf-8").read())

item_paths = [p for p in registered("src/main/java/com/luca/arise/registry/ModItems.java",
                                    r'register\("([a-z_]+)"') if p and not p.endswith("_")]
item_paths += ["blueprint_" + p for p in machine_paths]

for path in machine_paths:
    for asset, folder in (("blockstate", "blockstates"), ("modello", "models/block"),
                          ("modello d'oggetto", "items")):
        if not os.path.exists(os.path.join(A, folder, path + ".json")):
            note("asset mancante", "%s: manca il %s (%s)" % (path, asset, folder))

    if not os.path.exists(os.path.join(D, "loot_table/blocks", path + ".json")):
        note("asset mancante", "%s: manca la loot table (il blocco non lascia niente)" % path)

    if ("block.arise." + path) not in en:
        note("traduzione mancante", "block.arise." + path)

for path in sorted(set(item_paths)):
    for folder in ("models/item", "items"):
        if not os.path.exists(os.path.join(A, folder, path + ".json")):
            note("asset mancante", "%s: manca %s/%s.json" % (path, folder, path))

    if ("item.arise." + path) not in en:
        note("traduzione mancante", "item.arise." + path)


# ---------------------------------------------------------------- ricette
vanilla_items = set()
client_jar = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar")

try:
    import zipfile

    with zipfile.ZipFile(client_jar) as archive:
        for name in archive.namelist():
            if name.startswith("assets/minecraft/items/") and name.endswith(".json"):
                vanilla_items.add("minecraft:" + os.path.basename(name)[:-5])
except Exception as error:
    note("collaudo incompleto", "elenco oggetti vanilla non leggibile: %s" % error)

known = vanilla_items | {"arise:" + p for p in set(item_paths) | set(machine_paths)}

for recipe_path in glob.glob(os.path.join(D, "recipe", "*.json")):
    recipe = read_json(recipe_path)
    name = os.path.basename(recipe_path)

    ids = list(recipe.get("key", {}).values())
    result = recipe.get("result", {})
    if isinstance(result, dict) and "id" in result:
        ids.append(result["id"])

    for item in ids:
        if isinstance(item, str) and vanilla_items and item not in known:
            note("ricetta rotta", "%s nomina %s, che non esiste" % (name, item))


# ---------------------------------------------------------------- la catena degli incarichi
quest_src = io.open(os.path.join(ROOT, "src/main/java/com/luca/arise/quest/Quest.java"),
                    encoding="utf-8").read()

QUEST = re.compile(
    r'^\t([A-Z][A-Z_0-9]*)\("([a-z_0-9]+)",\s*Objective\.([A-Z_]+),\s*[^,]+,\s*Unlock\.([A-Z_]+)',
    re.M)
chain = QUEST.findall(quest_src)

if not chain:
    note("collaudo da aggiornare", "la catena degli incarichi non si legge piu'")

grants = {}
for index, (const, name, objective, unlock) in enumerate(chain):
    if unlock in grants:
        note("sistema concesso due volte",
             "%s e' concesso sia da %s sia da %s" % (unlock, chain[grants[unlock]][0], const))
    grants[unlock] = index

IGNORED = ("quest/Objective.java", "quest/Quest.java")

for index, (const, name, objective, unlock) in enumerate(chain):
    callers = [f for f in where("Objective." + objective)
               if not any(f.endswith(x) for x in IGNORED)]

    # Un incarico puo' anche chiudersi a mano, senza passare da un obiettivo: e' il caso del
    # risveglio, che si completa dentro ALLOW_DEATH perche' deve poter annullare la morte.
    completed_directly = any(("Quest." + const) in s and "complete(" in s for _, s in SOURCES)

    if not callers and not completed_directly:
        note("incarico irraggiungibile",
             "%s (n. %d) aspetta %s, che nessuno fa mai avanzare: la catena si ferma qui"
             % (const, index + 1, objective))
        continue

    # Un compito non puo' chiedere un sistema che solo un incarico successivo concede.
    for caller in callers:
        needed = set()
        for method in advancing_methods(caller, objective):
            needed.update(re.findall(r"require\([^,]+,\s*Unlock\.([A-Z_]+)\)", method))

        for required in needed:
            if required in grants and grants[required] >= index:
                note("catena che si morde la coda",
                     "%s (n. %d) avanza in %s, che pero' chiede %s — concesso solo da %s (n. %d)"
                     % (const, index + 1, os.path.basename(caller), required,
                        chain[grants[required]][0], grants[required] + 1))

unlock_src = io.open(os.path.join(ROOT, "src/main/java/com/luca/arise/quest/Unlock.java"),
                     encoding="utf-8").read()

for unlock in re.findall(r'^\t([A-Z][A-Z_0-9]*)\("[a-z_0-9]+"\)', unlock_src, re.M):
    if chain and unlock not in grants:
        note("sistema mai concesso",
             "%s non e' concesso da nessun incarico: chi lo richiede resta chiuso fuori per sempre"
             % unlock)


# ---------------------------------------------------------------- registri e lato client
client_src = io.open(
    os.path.join(ROOT, "src/client/java/com/luca/arise/client/AriseModClient.java"),
    encoding="utf-8").read()

for entity in registered("src/main/java/com/luca/arise/registry/ModEntities.java",
                         r"EntityType<\w+> ([A-Z_]+) = Registry\.register"):
    if ("ModEntities." + entity) not in client_src:
        note("entita' senza renderer",
             "%s e' registrata ma nessuno la disegna: il client esplode appena ne vede una" % entity)

for menu in registered("src/main/java/com/luca/arise/registry/ModMenus.java",
                       r"ExtendedMenuType<\w+, \w+> ([A-Z_]+) = Registry\.register"):
    if ("ModMenus." + menu) not in client_src:
        note("menu senza schermata",
             "%s e' registrato ma nessuno lo disegna: aprirlo chiude il gioco" % menu)

payload_dir = os.path.join(ROOT, "src/main/java/com/luca/arise/network")
payloads_src = io.open(os.path.join(payload_dir, "ModPayloads.java"), encoding="utf-8").read()
client_payloads = io.open(
    os.path.join(ROOT, "src/client/java/com/luca/arise/client/network/ClientPayloads.java"),
    encoding="utf-8").read()

for path in glob.glob(os.path.join(payload_dir, "*Payload.java")):
    name = os.path.basename(path)[:-5]

    if (name + ".TYPE") not in payloads_src:
        note("pacchetto non registrato",
             "%s non compare in ModPayloads: mandarlo chiude la connessione" % name)
        continue

    if ("clientboundPlay().register(" + name) in payloads_src \
            and (name + ".TYPE") not in client_payloads:
        note("pacchetto senza destinatario", "%s va verso il client, ma nessuno lo riceve" % name)


# ---------------------------------------------------------------- referto
print("Collaudo di Arise — %d file Java, %d chiavi, %d suoni, %d incarichi"
      % (len(SOURCES), len(en), len(sounds), len(chain)))

if not problems:
    print("\nPulito: nessun difetto trovato.")
    sys.exit(0)

total = 0
for kind, items in problems.items():
    print("\n== %s (%d)" % (kind.upper(), len(items)))
    for item in items:
        print("   " + item)
        total += 1

print("\nTotale: %d" % total)
sys.exit(1)
