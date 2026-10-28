"""Reproduce the baseline architecture inventory and PNG diagrams.

Requires Python 3.10+, matplotlib and networkx. No Android build is needed.
This is a lexical Kotlin analysis, not compiler-level symbol resolution.
"""

import csv
import json
import re
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patheffects as path_effects
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch
import networkx as nx


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "app/src/main/kotlin"
BASE = "ru.course.cityservices"
OUT = HERE / "images"
OUT.mkdir(exist_ok=True)
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11})


def strip_comments(text):
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


files = {}
symbols = {}
for path in sorted(SOURCE.rglob("*.kt")):
    text = path.read_text(encoding="utf-8")
    clean = strip_comments(text)
    package = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
    name = path.stem
    declarations = re.findall(
        r"^(?!(?:private|internal)\b)(?:data\s+|enum\s+|sealed\s+|abstract\s+)?"
        r"(?:class|object|interface|fun)\s+(\w+)", clean, re.M
    )
    files[name] = {
        "path": path.relative_to(ROOT).as_posix(),
        "package": package,
        "group": package.removeprefix(BASE).lstrip(".") or "root",
        "lines": len(text.splitlines()),
        "imports": re.findall(r"^import\s+([\w.*]+)", text, re.M),
        "declarations": declarations,
        "text": clean,
    }
    for declaration in declarations:
        symbols[package + "." + declaration] = name

imports = nx.DiGraph()
references = nx.DiGraph()
imports.add_nodes_from(files)
references.add_nodes_from(files)
evidence = []
for name, item in files.items():
    for imported in item["imports"]:
        if not imported.startswith(BASE + "."):
            continue
        candidates = [s for s in symbols if imported == s or imported.startswith(s + ".")]
        if not candidates:
            raise ValueError(f"Unresolved local import: {name}: {imported}")
        target = symbols[max(candidates, key=len)]
        if target != name:
            imports.add_edge(name, target)
            references.add_edge(name, target)
            evidence.append((name, target, "import", imported))
    body = re.sub(r"^(?:package|import)\s+[^\n]*", "", item["text"], flags=re.M)
    for symbol, target in symbols.items():
        package, short = symbol.rsplit(".", 1)
        if target != name and package == item["package"] and re.search(r"\b" + short + r"\b", body):
            references.add_edge(name, target)
            evidence.append((name, target, "same-package lexical reference", short))

packages = nx.DiGraph()
for item in files.values():
    packages.add_node(item["group"])
for source, target in references.edges:
    a, b = files[source]["group"], files[target]["group"]
    if a != b:
        packages.add_edge(a, b, weight=packages.get_edge_data(a, b, {}).get("weight", 0) + 1)

colors = {group: plt.get_cmap("tab20")(i) for i, group in enumerate(sorted(packages))}
# Avoid red node fills: red is reserved for cyclic edges.
colors["state"] = "#cbd5e1"

FRIENDLY = {
    "MainActivity": "Запуск приложения", "CityServicesApp": "Переходы между экранами",
    "ServiceLocator": "Управление работой приложения", "AppState": "Общее состояние",
    "Models": "Общие данные", "DomainModels": "Заявка и её статусы",
    "CityApi": "Правила общения с сервером", "FakeApi": "Учебный сервер",
    "AppDatabase": "База заявок", "RequestDao": "Чтение и запись заявок",
    "InstantConverter": "Хранение даты и времени", "SessionStore": "Сохранение входа",
    "AnalyticsTracker": "Журнал событий", "RequestValidator": "Проверка полей заявки",
    "TimeFormat": "Отображение времени", "LoginScreen": "Экран входа",
    "ServicesScreen": "Каталог услуг", "RequestsScreen": "История заявок",
    "ServiceDetailScreen": "Создание заявки", "NotificationsScreen": "Уведомления",
    "ServiceCard": "Карточка услуги", "StatusBadge": "Метка статуса", "Theme": "Оформление",
    "root": "Запуск и переходы", "core": "Управление приложением", "state": "Общее состояние",
    "network": "Учебный сервер и его правила", "storage": "Хранение на устройстве",
    "model": "Общие данные", "domain": "Правила работы с заявками", "analytic": "Журнал событий",
    "util": "Вспомогательные функции", "ui.screen": "Экраны", "ui.component": "Элементы экранов",
    "ui.theme": "Оформление",
}


def draw_graph(graph, filename, title, subtitle, positions=None, groups=None, size=(22, 17)):
    # Each source gets its own panel, so no unrelated arrows can intersect.
    # Repeated destinations are intentional: all original edges remain visible.
    names = sorted(graph, key=lambda n: (-graph.out_degree(n), n))
    columns = 3 if len(names) > 8 else 2
    rows = math.ceil(len(names) / columns)
    fig, axes = plt.subplots(rows, columns, figsize=(24, rows * 3.5), squeeze=False)
    fig.patch.set_facecolor("#f5f7fb")
    for ax, name in zip(axes.flat, names):
        ax.set(xlim=(0, 10), ylim=(0, 10))
        ax.axis("off")
        targets = sorted(graph.successors(name))
        color = colors.get(groups.get(name), "#d5e5f2") if groups else "#d5e5f2"
        ax.add_patch(FancyBboxPatch((.1, 3.9), 4.2, 2.1, boxstyle="round,pad=.12",
                                    facecolor=color, edgecolor="#64748b"))
        ax.text(2.2, 5.25, FRIENDLY.get(name, name), ha="center", va="center", fontsize=10.5, weight="bold")
        ax.text(2.2, 4.55, name, ha="center", color="#475569", fontsize=9)
        if not targets:
            ax.text(7.2, 5, "Не использует другие\nчасти на этой схеме", ha="center", va="center", color="#64748b", fontsize=10)
        for index, target in enumerate(targets):
            y = 9 - index * (8 / max(len(targets)-1, 1)) if len(targets)>1 else 5
            ax.add_patch(FancyBboxPatch((5.7, y-.43), 4.05, .86, boxstyle="round,pad=.08",
                                        facecolor="white", edgecolor="#cbd5e1"))
            ax.text(7.72, y+.13, FRIENDLY.get(target,target), ha="center", va="center", fontsize=9)
            ax.text(7.72, y-.2, target, ha="center", va="center", fontsize=8, color="#64748b")
            ax.add_patch(FancyArrowPatch((4.45, 5), (5.55, y), arrowstyle="-|>",
                                        mutation_scale=13, color="#526c90", linewidth=1.25))
    for ax in list(axes.flat)[len(names):]:
        ax.axis("off")
    fig.suptitle(title + "\n" + subtitle, fontsize=21, y=.995)
    fig.text(.03, .012, "Как читать: слева — часть приложения, справа — то, что она использует.\n"
             "Одна и та же часть может повторяться в разных карточках. Все связи исходного графа сохранены.", fontsize=12)
    fig.tight_layout(rect=(0,.04,1,.96), h_pad=2, w_pad=2)
    fig.savefig(OUT / filename, dpi=160)
    plt.close(fig)


# Place the file graph by package, with deterministic coordinates.
layout = {
    "root": (0, 4), "core": (0, 2), "state": (-3, 0), "network": (3, 0),
    "storage": (4, -3), "model": (0, -3), "domain": (-3, -3),
    "analytic": (5, 3), "util": (-5, -3), "ui.screen": (-5, 2),
    "ui.component": (-5, 5), "ui.theme": (3, 5),
}
positions = {}
for group in packages:
    names = sorted(n for n in files if files[n]["group"] == group)
    x, y = layout[group]
    for i, name in enumerate(names):
        positions[name] = (x + (i % 2) * 1.7, y - (i // 2) * 1.0)
draw_graph(imports, "01-imports.png", "Какие части кода используют друг друга",
           "Граф импортов: связи, явно указанные в начале файлов",
           positions, {n: v["group"] for n, v in files.items()}, (29, 22))
draw_graph(references, "02-file-dependencies.png", "Полная картина связей между файлами",
           "Учтены и явные импорты, и обращения внутри одной папки кода",
           positions, {n: v["group"] for n, v in files.items()}, (29, 22))
draw_graph(packages, "03-packages.png", "Как связаны основные части приложения",
           "Код сгруппирован по назначению; все группы входят в одно приложение",
           {g: layout[g] for g in packages}, size=(20, 15))


def boxes(filename, title, items, arrows, subtitle):
    descriptions = {
        "root": "Настройки сборки\ncity-services" if filename.startswith("04") else "Переходы между экранами\nи проверка новых статусов\nCityServicesApp",
        "app": "Приложение для Android\nединственный модуль :app",
        "ui": "Отрисовка экранов\nCompose, Activity, Material 3" if filename.startswith("04") else "Пять основных экранов\nВход, услуги, заявки, уведомления",
        "runtime": "Состояние экранов\nи выполнение фоновых задач\nLifecycle / Coroutines",
        "data": "Хранение на устройстве\nRoom — заявки\nDataStore — данные входа",
        "tests": "Проверка кода\nJUnit и Coroutines Test",
        "sdk": "Требования для сборки\nAndroid API 31–37\nJava 21; Gradle 9.7.1",
        "activity": "Запуск приложения\nMainActivity",
        "locator": "Управление операциями\nвход, отправка и обновление\nServiceLocator",
        "state": "Общие данные на экранах\nпользователь, услуги, уведомления\nAppState",
        "api": "Учебный сервер\nхранит заявки в памяти\nFakeApi",
        "db": "База заявок на устройстве\nRoom / RequestDao",
        "session": "Сохранённые данные входа\nSessionStore / DataStore",
        "analytics": "Журнал действий\nAnalyticsTracker",
        "form": "Форма новой заявки\nкомментарий и адрес\nServiceDetailScreen",
        "submit": "Отправка заявки\nкомментарий проверяется,\nадрес — нет",
        "fallback": "Если произошла ошибка\nсоздаётся местная копия\nс другим номером",
        "sync": "Обновление истории\nсравнение номеров заявок\nи их статусов",
        "vm": "Список на экране истории\nзагружается разово,\nне следит за изменениями базы",
        "notifications": "Уведомления на экранах\nо смене статуса заявки\nхранятся только в памяти",
    }
    items = {key: (x,y,descriptions.get(key,text),color) for key,(x,y,text,color) in items.items()}
    readable_arrows = {
        "include, не project dependency": "входит в сборку", "implementation": "использует",
        "implementation / ksp": "использует", "только test": "проверяется с помощью",
        "init": "подготавливает к работе", "restore / refresh / logout": "запускает операции",
        "suspend API": "отправляет запросы", "load / save / clear": "читает и сохраняет",
        "track": "записывает событие", "успешный ответ → insert": "сохраняет ответ",
        "insert": "сохраняет копию", "getRequests": "передаёт список заявок",
        "insert / replace": "обновляет записи", "сравнение old.status": "сообщает о новом статусе",
        "loadLocalRequests": "передаёт сохранённый список", "исключение": "если произошла ошибка",
    }
    arrows = [(a,b,readable_arrows.get(label,label)) for a,b,label in arrows]
    sources = list(dict.fromkeys(a for a, _, _ in arrows))
    rows = math.ceil(len(sources) / 2)
    fig, axes = plt.subplots(rows, 2, figsize=(25, max(7, rows * 6)), squeeze=False)
    for ax, source in zip(axes.flat, sources):
        ax.set(xlim=(0, 12), ylim=(0, 10))
        ax.axis("off")
        outgoing = [(b, label) for a,b,label in arrows if a == source]
        ax.add_patch(FancyBboxPatch((.1, 3.6), 4.2, 2.6, boxstyle="round,pad=.1",
                                    facecolor=items[source][3], edgecolor="#64748b"))
        ax.text(2.2, 4.9, items[source][2], ha="center", va="center", fontsize=11)
        for index, (target, label) in enumerate(outgoing):
            y = 8.8 - index * (7.6 / max(len(outgoing)-1,1)) if len(outgoing)>1 else 5
            ax.add_patch(FancyBboxPatch((7.2, y-.8), 4.5, 1.6, boxstyle="round,pad=.08",
                                        facecolor=items[target][3], edgecolor="#94a3b8"))
            ax.text(9.45, y, items[target][2], ha="center", va="center", fontsize=10)
            ax.add_patch(FancyArrowPatch((4.45,4.9), (7.05,y), arrowstyle="-|>", mutation_scale=16, color="#526c90"))
            ax.text(5.7, y*.65 + 4.9*.35 + .2, label, ha="center", fontsize=9,
                    bbox={"facecolor":"white", "edgecolor":"none", "alpha":.95})
    for ax in list(axes.flat)[len(sources):]:
        ax.axis("off")
    fig.suptitle(title + "\n" + subtitle, fontsize=21, y=.995)
    fig.text(.025,.015,"Каждая карточка показывает один участок работы. Повторяющиеся блоки обозначают одну и ту же часть приложения.", fontsize=12)
    fig.tight_layout(rect=(0,.04,1,.94), h_pad=3)
    fig.savefig(OUT / filename, dpi=160)
    plt.close(fig)


boxes("04-gradle-modules.png", "Из чего собирается приложение", {
    "root": (6, 8.5, "root project\ncity-services\nконфигурация плагинов", "#e2e8f0"),
    "app": (6, 6, ":app\nединственный Android-модуль\nmain + test", "#b9d4ed"),
    "ui": (2, 3.5, "Compose BOM 2026.08.00\nActivity 1.13.0\nMaterial 3 / UI", "#d4e8dc"),
    "runtime": (6, 3.5, "Lifecycle 2.11.0\nCoroutines 1.11.0", "#d4e8dc"),
    "data": (10, 3.5, "Room 2.8.4 + KSP\nDataStore 1.2.1", "#d4e8dc"),
    "tests": (2, 1, "testImplementation\nJUnit 4.13.2\nCoroutines Test 1.11.0", "#f6e4bb"),
    "sdk": (10, 1, "minSdk 31; target/compile 37\nJVM / toolchain 21\nGradle 9.7.1; AGP 9.4.0", "#e2e8f0"),
}, [("root","app","include, не project dependency"), ("app","ui","implementation"),
    ("app","runtime","implementation"), ("app","data","implementation / ksp"),
    ("app","tests","только test"), ("app","sdk","требования сборки")],
    "Один модуль приложения и библиотеки, которые помогают ему работать")

boxes("07-runtime-components.png", "Кто за что отвечает в приложении", {
    "activity": (2, 8.5, "MainActivity\nonCreate → init", "#d4e8dc"),
    "root": (6, 8.5, "CityServicesApp\nмаршруты + polling 30 с", "#d4e8dc"),
    "ui": (2, 6, "5 экранов Compose\nViewModel частично", "#d4e8dc"),
    "locator": (6, 5, "ServiceLocator\nсоздание зависимостей\n+ сценарии + sync", "#f6d5bd"),
    "state": (10, 8.5, "AppState\nCompose state\nсессия / каталог / уведомления", "#e1d7f2"),
    "api": (2, 2, "CityApi → FakeApi\nin-memory сервер\nзадержки / сбои / статусы", "#b9d4ed"),
    "db": (6, 2, "Room / RequestDao\nзаявки на диске\nsuspend List, не Flow", "#b9d4ed"),
    "session": (10, 2, "SessionStore / DataStore\nсессия на диске", "#b9d4ed"),
    "analytics": (10, 5, "AnalyticsTracker\nбуфер + Android Log", "#e2e8f0"),
}, [("activity","locator","init"), ("root","locator","restore / refresh / logout"),
    ("ui","locator","операции"), ("ui","state","чтение / изменение"),
    ("locator","state","изменение"), ("locator","api","suspend API"),
    ("locator","db","чтение / запись"), ("locator","session","load / save / clear"),
    ("locator","analytics","track")],
    "Стрелки показывают, к кому обращается каждая часть приложения")

boxes("08-request-data-flow.png", "Что происходит с заявкой после отправки", {
    "form": (2, 8.5, "ServiceDetailScreen\ncomment / address", "#d4e8dc"),
    "submit": (6, 8.5, "submitRequest\nвалидация комментария\nадрес не проверяется", "#f6d5bd"),
    "api": (10, 8.5, "FakeApi.createRequest\nсерверный ID\ncalls % 5 == 0: commit + timeout", "#b9d4ed"),
    "fallback": (6, 5.5, "catch Exception\nновая локальная запись\nID = currentTimeMillis", "#f6d5bd"),
    "db": (10, 3, "Room: requests\nRequestResponse = Entity\nнет pending / idempotency key", "#b9d4ed"),
    "sync": (2, 3, "refreshRequests\nсопоставление по ID\nобновление при смене статуса", "#f6d5bd"),
    "vm": (6, 1, "RequestsViewModel\nразовый List snapshot\nне наблюдает БД", "#d4e8dc"),
    "notifications": (2, 5.5, "AppState.notifications\nпри смене статуса\nтолько в памяти", "#e1d7f2"),
}, [("form","submit","поля формы"), ("submit","api","создание"),
    ("api","db","успешный ответ → insert"), ("submit","fallback","исключение"),
    ("fallback","db","insert"), ("api","sync","getRequests"),
    ("sync","db","insert / replace"), ("sync","notifications","сравнение old.status"),
    ("db","vm","loadLocalRequests")],
    "После потери ответа одна заявка может сохраниться дважды: на учебном сервере и на устройстве")

# Matrix is a less cluttered, exhaustive companion to the file diagrams.
names = sorted(files, key=lambda n: (files[n]["group"], n))
matrix = nx.to_numpy_array(references, nodelist=names)
fig, ax = plt.subplots(figsize=(17, 16))
ax.imshow(matrix, cmap="Blues", vmin=0, vmax=1)
ax.set_xticks(range(len(names)), names, rotation=70, ha="right")
ax.set_yticks(range(len(names)), names)
ax.set_xlabel("Зависимость (столбец)")
ax.set_ylabel("Потребитель (строка)")
ax.set_title("Все связи кода в одной таблице\nСиняя клетка: файл слева использует файл, указанный снизу", pad=25, fontsize=18)
ax.set_xticks([v-.5 for v in range(len(names)+1)], minor=True)
ax.set_yticks([v-.5 for v in range(len(names)+1)], minor=True)
ax.grid(which="minor", color="#e2e8f0", linewidth=.5)
fig.tight_layout()
fig.savefig(OUT / "09-dependency-matrix.png", dpi=160)
plt.close(fig)

models = {"Models", "DomainModels", "CityApi", "SessionStore"}
model_graph = references.edge_subgraph([(a,b) for a,b in references.edges if b in models]).copy()
model_positions = {}
consumers = sorted(set(model_graph) - models)
for i, name in enumerate(consumers):
    model_positions[name] = (0, -i * 1.5)
for i, name in enumerate(sorted(models & set(model_graph))):
    model_positions[name] = (6, -i * 4)
draw_graph(model_graph, "05-model-propagation.png", "Где используются общие данные приложения",
           "Показаны связи с файлами, в которых описаны заявки, услуги, статусы и данные входа",
           positions=model_positions, groups={n: files[n]["group"] for n in model_graph}, size=(24,18))

metrics = []
for name in files:
    metrics.append({"file": name, "package": files[name]["group"], "lines": files[name]["lines"],
                    "fan_in": references.in_degree(name), "fan_out": references.out_degree(name),
                    "transitive_dependents": len(nx.ancestors(references, name))})
ranked = sorted(metrics, key=lambda v: (-v["fan_in"], -v["transitive_dependents"], v["file"]))
fig, axes = plt.subplots(1, 2, figsize=(20, 10))
for ax, metric, title in zip(axes, ["fan_in", "transitive_dependents"],
                            ["Сколько файлов используют эту часть напрямую", "Сколько файлов связано с ней прямо или через другие части"]):
    data = sorted(metrics, key=lambda v: v[metric])[-12:]
    ax.barh([v["file"] for v in data], [v[metric] for v in data], color="#356c9b")
    ax.set_title(title)
    ax.set_xlabel("Количество исходных файлов")
    ax.xaxis.get_major_locator().set_params(integer=True)
fig.suptitle("Какие части приложения влияют на большее число файлов", fontsize=20)
fig.tight_layout(rect=(0,0,1,.94))
fig.savefig(OUT / "06-coupling-impact.png", dpi=160)
plt.close(fig)

with (HERE / "edges.csv").open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.writer(stream)
    writer.writerow(["consumer", "dependency", "kind", "symbol"])
    writer.writerows(sorted(evidence))

summary = {
    "method": "lexical imports plus same-package declaration references; no compiler resolution",
    "main_files": len(files), "main_lines": sum(v["lines"] for v in files.values()),
    "import_edges": imports.number_of_edges(), "reference_edges": references.number_of_edges(),
    "packages": packages.number_of_nodes(), "package_edges": packages.number_of_edges(),
    "file_scc": [sorted(c) for c in nx.strongly_connected_components(references) if len(c)>1],
    "package_scc": [sorted(c) for c in nx.strongly_connected_components(packages) if len(c)>1],
    "file_cycles": list(nx.simple_cycles(references)),
    "metrics": ranked,
    "files": {n: {k:v for k,v in item.items() if k != "text"} for n,item in files.items()},
    "external_import_roots": sorted({s.split('.')[0] for v in files.values() for s in v['imports'] if not s.startswith(BASE+'.')}),
}
(HERE / "analysis.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k:v for k,v in summary.items() if k not in {"files", "metrics"}}, ensure_ascii=False, indent=2))
print("METRICS:")
for row in ranked:
    print(row)

# Unified overview: control/access arrows, not a proposed layered architecture.
fig, ax = plt.subplots(figsize=(22, 16))
fig.patch.set_facecolor("#f5f7fb")
ax.set(xlim=(0, 22), ylim=(0, 16))
ax.axis("off")


def overview_box(x, y, width, height, title, detail, color):
    ax.add_patch(FancyBboxPatch((x,y), width,height, boxstyle="round,pad=.12",
                               facecolor=color, edgecolor="#64748b", linewidth=1.4, zorder=3))
    ax.text(x+width/2, y+height*.72, title, ha="center", va="center", fontsize=15, weight="bold", zorder=4)
    ax.text(x+width/2, y+height*.32, detail, ha="center", va="center", fontsize=11.5, color="#334155", zorder=4)


def overview_arrow(points, label, at, dashed=False):
    for index in range(len(points)-1):
        ax.add_patch(FancyArrowPatch(points[index], points[index+1],
                     arrowstyle="-|>" if index == len(points)-2 else "-",
                     mutation_scale=19, linewidth=1.6, color="#526c90",
                     linestyle="--" if dashed else "-", zorder=2))
    ax.text(*at, label, fontsize=11, ha="center", va="center", color="#334155",
            bbox={"facecolor":"#f5f7fb", "edgecolor":"none", "pad":3}, zorder=5)


ax.text(11,15.55,"Как устроено приложение «Городские услуги»",ha="center",fontsize=24,weight="bold")
ax.text(11,15.02,"Текущая архитектура: один Android-модуль, общее состояние и единый центр операций",ha="center",fontsize=14,color="#475569")
overview_box(.6,12,4.4,1.9,"Запуск приложения","MainActivity\nподготовка зависимостей", "#d4e8dc")
overview_box(6.6,12,8,1.9,"Экраны и переходы","CityServicesApp + 5 экранов Compose\nViewModel используются не на всех экранах", "#d4e8dc")
overview_box(16.5,9,4.7,3,"Общее состояние","AppState\nпользователь, услуги, выбор услуги,\nошибка и уведомления", "#e1d7f2")
overview_box(6.6,7.9,8,2.2,"Управление работой приложения","ServiceLocator\nсоздаёт зависимости, выполняет вход,\nотправку заявок и обновление истории", "#f6d5bd")
overview_box(.6,7.9,4.4,2.2,"Правила заявки","RequestValidator\nпроверка введённых данных", "#f6e4bb")
overview_box(.6,3.4,4.4,2.2,"Учебный сервер","CityApi → FakeApi\nуслуги, вход и заявки в памяти", "#b9d4ed")
overview_box(6.2,3.4,4.4,2.2,"База заявок","Room: AppDatabase / RequestDao\nзаявки хранятся на устройстве", "#b9d4ed")
overview_box(11.8,3.4,4.4,2.2,"Сохранение входа","SessionStore / DataStore\nданные пользователя на устройстве", "#b9d4ed")
overview_box(17.4,3.4,4,2.2,"Журнал событий","AnalyticsTracker\nпамять и Android Log", "#e2e8f0")
overview_box(2.2,.45,17.6,1.45,"Общие описания данных","Models: услуги, заявки и уведомления • DomainModels: статусы заявки\nRequestResponse одновременно используется сервером, базой и экраном истории", "#edf0f5")
overview_arrow([(5.15,13),(6.45,13)],"открывает",(5.8,13.4))
overview_arrow([(2.8,11.85),(2.8,11),(7.3,11),(7.3,10.25)],"создаёт инфраструктуру",(4.5,11.35))
overview_arrow([(10.8,11.85),(10.8,10.25)],"запускают операции",(12.1,11.05))
overview_arrow([(14.75,13),(18.85,13),(18.85,12.15)],"читают и изменяют",(16.6,13.4))
overview_arrow([(14.75,9),(16.35,9)],"изменяет",(15.55,9.4))
overview_arrow([(6.45,9),(5.15,9)],"проверяет поля",(5.8,9.55))
overview_arrow([(8,7.75),(8,6.9),(2.8,6.9),(2.8,5.75)],"обращается к серверу",(4.4,7.2))
overview_arrow([(9.5,7.75),(9.5,5.75)],"читает и записывает",(8.05,6.3))
overview_arrow([(12,7.75),(12,6.9),(14,6.9),(14,5.75)],"сохраняет сессию",(13.9,7.2))
overview_arrow([(14,7.75),(14,7.4),(19.4,7.4),(19.4,5.75)],"записывает события",(17.5,7.7))
ax.text(11,2.5,"Сервер, база и экраны используют общие описания данных, показанные ниже.",ha="center",fontsize=12,color="#475569")
fig.text(.05,.015,"Стрелки обозначают обращения между частями приложения. Обратные ответы не дублируются.\n"
         "Упрощённая обзорная схема: точные связи файлов приведены в графах и таблице первого этапа.",fontsize=11,color="#475569")
fig.savefig(OUT / "10-architecture-overview.png",dpi=160,bbox_inches="tight")
plt.close(fig)

# Single, non-repeated view of the same complete file dependency graph.
layers = [sorted(layer) for layer in nx.topological_generations(references)]
levels = {node: i for i, layer in enumerate(layers) for node in layer}
# Order each row by its neighbours to reduce crossings without hiding edges.
for _ in range(12):
    for indices in (range(1, len(layers)), range(len(layers)-2, -1, -1)):
        for i in indices:
            order = {n: j / max(len(row)-1, 1) for row in layers for j,n in enumerate(row)}
            layers[i].sort(key=lambda n: sum(order[v] for v in nx.all_neighbors(references,n)) /
                           max(references.degree(n),1))
positions = {n: ((j-(len(row)-1)/2)*4.6, -i*3.1)
             for i,row in enumerate(layers) for j,n in enumerate(row)}
outgoing = {n: sorted(references.successors(n), key=lambda v: positions[v][0]) for n in references}
incoming = {n: sorted(references.predecessors(n), key=lambda v: positions[v][0]) for n in references}
fig, ax = plt.subplots(figsize=(30,22))
fig.patch.set_facecolor("#f5f7fb")
ax.axis("off")
for source,target in references.edges:
    a,b = positions[source],positions[target]
    source_index = outgoing[source].index(target)
    target_index = incoming[target].index(source)
    source_offset = (source_index / max(len(outgoing[source])-1,1) - .5) * 3.5 if len(outgoing[source])>1 else 0
    target_offset = (target_index / max(len(incoming[target])-1,1) - .5) * 3.5 if len(incoming[target])>1 else 0
    color = ["#245b87", "#984b28", "#526f36", "#75509a", "#247c83"][list(files).index(source) % 5]
    curvature = .04 * (1 if source_index % 2 else -1) if levels[target]-levels[source]>1 else 0
    arrow = FancyArrowPatch((a[0]+source_offset,a[1]-.63),(b[0]+target_offset,b[1]+.67),arrowstyle="-|>",
                 connectionstyle=f"arc3,rad={curvature}",shrinkA=0,shrinkB=0,
                 mutation_scale=22,linewidth=1.7,color=color,alpha=1,zorder=2)
    arrow.set_path_effects([path_effects.Stroke(linewidth=4, foreground="#f5f7fb"), path_effects.Normal()])
    ax.add_patch(arrow)
for name,(x,y) in positions.items():
    ax.add_patch(FancyBboxPatch((x-2,y-.5),4,1,boxstyle="round,pad=.08",
                 facecolor=colors[files[name]["group"]],edgecolor="#64748b",linewidth=1.1,zorder=3))
    ax.text(x,y+.15,FRIENDLY[name],ha="center",va="center",fontsize=11,weight="bold",zorder=4)
    ax.text(x,y-.22,name,ha="center",va="center",fontsize=10,color="#334155",zorder=4)
xs,ys = zip(*positions.values())
ax.set(xlim=(min(xs)-3,max(xs)+3),ylim=(min(ys)-2,max(ys)+2))
ax.set_title("Общая схема зависимостей приложения\nКаждый файл показан один раз. Стрелка направлена к тому, что он использует.",
             fontsize=24,pad=30)
fig.text(.05,.025,"Все 23 файла и 42 связи сохранены. Блоки расположены по направлению зависимостей сверху вниз.\n"
         "Цвет блоков объединяет файлы одной группы. Входы и выходы стрелок разнесены; светлая обводка разделяет пересечения.",fontsize=14,color="#475569")
fig.savefig(OUT / "11-dependencies-overview.png",dpi=160,bbox_inches="tight")
plt.close(fig)
print("PNG:", len(list(OUT.glob('*.png'))))
