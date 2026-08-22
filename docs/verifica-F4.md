# Verifica F4 — L'esercito

Ranghi, crescita delle ombre e schermata di gestione. Nuovo tasto: **J**.

## Prima di iniziare: azzera l'esercito vecchio

```
/arise shadows clear
```

Le ombre estratte finora hanno il livello del giocatore al momento dell'estrazione (era il
comportamento di F3). Ora le ombre nascono **sempre al livello 1** e crescono combattendo, quindi
quelle vecchie risulterebbero molto più forti del dovuto e falserebbero il test. I dati si caricano
comunque senza errori: è solo bilanciamento incoerente.

## Preparazione

```
/arise level 20
/arise gear
/gamemode survival
```

## Controlli

| # | Cosa fare | Cosa deve succedere |
|---|---|---|
| 1 | Estrai un'ombra da un pollo (`/arise spawn chicken 1`) | Messaggio con **rango E** |
| 2 | Estrai da uno zombie | **Rango D** |
| 3 | `/arise spawn iron_golem 1`, uccidilo, estrai | **Rango A** |
| 4 | `/arise shadows` | Ogni riga inizia col rango tra parentesi quadre |
| 5 | Premi **J** | Schermata Esercito: una riga per ombra, rango colorato, nome, Lv./HP/danno, barra XP, bottone **Evoca** |
| 6 | Clicca **Evoca** su una riga | L'ombra compare, il bottone della riga diventa **Richiama**, il nome diventa azzurro |
| 7 | Clicca **Richiama** | Sparisce e il bottone torna **Evoca** |
| 8 | Evoca 4 ombre, poi prova a evocarne una quinta | "Puoi tenere evocate al massimo 4 ombre" e il bottone non fa nulla |
| 9 | Con più di 6 ombre, usa `<` `>` o la **rotella** | Le pagine cambiano e mostrano "Pagina n di m" |
| 10 | Con la schermata aperta, premi **H** (richiama tutte) | I bottoni si aggiornano **da soli**, senza chiudere e riaprire |
| 11 | Evoca, poi uccidi qualche mob | Sopra la testa dell'ombra il nome mostra `[rango] Lv.n` |
| 12 | Continua a uccidere | La barra XP nella schermata J si riempie; a livello 2 compare "… è salita al livello 2" |
| 13 | Guarda le statistiche dopo il livello 2 | HP e danno sono cresciuti dell'8% |
| 14 | Lascia che sia **l'ombra** a uccidere | Prende XP piena; le altre evocate prendono metà |
| 15 | Fai salire di livello un'ombra mentre è evocata | Anche l'entità nel mondo diventa più forte subito, senza richiamarla |
| 16 | Esci e rientra nel mondo | Livelli e XP delle ombre sono intatti; nulla è evocato |
| 17 | Muori | L'esercito e i livelli delle ombre sopravvivono |

## Dove mi aspetto problemi

1. **Il ricalcolo dei bottoni** (punti 6, 10). La schermata si aggiorna a ogni tick controllando se
   lo stato è cambiato; se i bottoni restano indietro o lampeggiano, il problema è lì.
2. **Le soglie dei ranghi** (1-3). I numeri sono a occhio: se quasi tutto risulta E o D, vanno
   abbassati in `config/arise.json` → `shadows.rank_thresholds`.
3. **Il ritmo di crescita** (12). `xp_base` 30 con esponente 1,4 potrebbe essere troppo lento o
   troppo veloce; è la cosa più probabile da ritoccare.

## Nuovi parametri in `config/arise.json`

**Il blocco `shadows` non compare in questa sessione.** Il primo tentativo di correzione non
funzionava: `optionalFieldOf` di DFU *omette* un campo in scrittura quando il valore coincide col
default, quindi i parametri restavano invisibili nel file. Ora i default vengono fusi nel JSON
**prima** del parsing e i campi del codec sono obbligatori, così finiscono sempre nel file scritto.
La correzione è compilata ma entra in vigore **al prossimo avvio del client**.

| Parametro | Default | Cosa fa |
|---|---|---|
| `shadows.rank_thresholds` | `[0, 30, 60, 110, 180, 280]` | Punteggio minimo per E, D, C, B, A, S |
| `shadows.leveling.xp_base` | 30 | XP per passare da Lv.1 a Lv.2 |
| `shadows.leveling.xp_exponent` | 1.4 | Ripidità della curva |
| `shadows.leveling.xp_share` | 0.5 | Quota che prendono le ombre che non hanno colpito |
| `shadows.leveling.stat_growth_per_level` | 0.08 | +8% HP e danno per livello |
| `shadows.leveling.max_level` | 50 | Tetto |

Il punteggio di rango è `HP base + danno base × 4` — il danno pesa di più perché è quello che
decide gli scontri.
