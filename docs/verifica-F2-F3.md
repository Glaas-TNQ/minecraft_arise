# Verifica F2 + F3 — Interfaccia e prima ombra

Entrambe compilano. Nessuna delle due è verificata: F2 tocca il rendering e F3 tocca AI, spawn ed
entità custom — le tre aree dove la compilazione dice meno di niente.

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"
.\gradlew.bat runClient
```

Mondo in **Creativa con trucchi**. Preparazione in tre comandi:

```
/arise level 10          # punti da spendere e ~30% di probabilità di estrazione
/arise gear              # set completo di netherite incantato + cibo e mele d'oro
/arise spawn zombie 5    # cinque zombie in cerchio attorno a te
```

`/arise gear` accetta anche `iron` e `diamond` se ti serve un combattimento più lento.
`/arise spawn` accetta qualsiasi entità con completamento automatico (`skeleton`, `creeper`,
`ravager`, `iron_golem`…) e fino a 64 alla volta: comodo per vedere l'esercito reggere o crollare.

Per il grosso della verifica di F3 conviene passare in **Sopravvivenza** (`/gamemode survival`)
— in Creativa non ti attacca nessuno e i goal "difendi il padrone" non si vedono mai scattare.

## F2 — Interfaccia

| # | Cosa fare | Cosa deve succedere |
|---|---|---|
| 1 | Entra nel mondo | In alto a sinistra compare un riquadro: `Lv. 1` e una barra XP azzurra |
| 2 | `/arise xp 100` | La barra si riempie e si svuota a ogni livello; al centro-alto compare **LIVELLO n** che sfuma in ~2,5s |
| 3 | Guarda il riquadro con punti disponibili | Compare la riga gialla "n punti da spendere"; sparisce quando li hai spesi tutti |
| 4 | Premi **F1** | L'HUD della mod sparisce insieme al resto dell'interfaccia |
| 5 | Premi **K** | Si apre la schermata **Stato**; il gioco **non** va in pausa |
| 6 | Nella schermata, premi `+` su una statistica | Il numero sale subito e il valore reale tra parentesi cambia |
| 7 | Spendi tutti i punti | I bottoni `+` diventano grigi |
| 8 | Esc, poi K di nuovo | I dati sono coerenti con quelli veri (`/arise info` per confronto) |
| 9 | Opzioni → Comandi | Ci sono quattro voci di Arise, tutte rimappabili |

## F3 — La prima ombra

| # | Cosa fare | Cosa deve succedere |
|---|---|---|
| 10 | `/arise level 10`, poi uccidi uno zombie e **entro 15 secondi** premi **R** vicino al cadavere | Particelle scure, e o "Arise. Ombra di Zombie si unisce all'esercito (1/8)" o il fallimento con la percentuale |
| 11 | Premi R di nuovo sullo stesso punto | "Nessun cadavere da cui estrarre": ogni cadavere si tenta **una volta sola** |
| 12 | `/arise shadows` | Elenco con nome, livello, HP e danno di ogni ombra |
| 13 | Premi **G** | Le ombre compaiono attorno a te, sagome umanoidi nere con occhi azzurri, sparpagliate e non sovrapposte |
| 14 | Cammina | Ti seguono, si fermano a ~3 blocchi, ti raggiungono se ti allontani |
| 15 | Colpisci uno zombie e lasciati colpire | Le ombre attaccano chi ti ha attaccato e chi attacchi tu |
| 16 | Fatti uccidere un'ombra | "… è caduta. Tornerà all'evocazione." L'ombra resta in `/arise shadows` |
| 17 | Premi **G** di nuovo | L'ombra caduta ritorna |
| 18 | Premi **H** | Tutte spariscono con le particelle; `/arise shadows` le elenca ancora |
| 19 | Lascia che un'ombra uccida un mob | **Tu** prendi l'XP (controlla con `/arise info`) e il cadavere è estraibile |
| 20 | Evoca, poi esci dal mondo e rientra | Nessuna ombra vagante nel mondo, ma l'esercito è intatto |
| 21 | Muori con l'esercito pieno | L'esercito sopravvive alla morte |
| 22 | Prova a colpire una tua ombra | Non ti attacca in risposta |

## Dove mi aspetto problemi

In ordine di probabilità:

1. **Il renderer** (punto 13). Riuso il layer del modello zombie con una texture mia. Se il gioco
   crasha all'evocazione o l'ombra è invisibile/rosa-nera, è lì.
2. **Il posizionamento allo spawn** (13). Le ombre potrebbero comparire dentro un blocco.
3. **La targetizzazione** (15). I goal "difendi il padrone" di vanilla sono pensati per i lupi;
   potrebbero essere più passivi del previsto.
4. **La finestra di estrazione** (10). 15 secondi e 8 blocchi sono valori di primo tentativo.

Se qualcosa crasha, servono le ultime ~40 righe del log. Se invece "funziona ma è brutto"
(troppo lente, troppo deboli, texture sbagliata) è bilanciamento e aspetto: annota e si sistema.

## Numeri attuali (`config/arise.json`, blocco `shadows`)

| | |
|---|---|
| Probabilità estrazione | 25% al livello 1, +0,5% per livello, tetto 95% |
| Finestra | 300 tick (15 s), raggio 8 blocchi |
| Capienza esercito | 6 al livello 1, +0,25 per livello |
| Evocabili insieme | 4 — è un limite **tecnico**: ogni ombra fa pathfinding |
| Vita ombra | vita del mob d'origine × 1,5 |
| Danno ombra | danno del mob d'origine × 1,2 |

Il file esistente continua a funzionare: il blocco `shadows` è opzionale e viene riempito con i
default, quindi non perdi le modifiche fatte finora.
