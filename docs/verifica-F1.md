# Verifica F1 — Il Sistema

Il codice compila, ma **compilare non è verificare**. Questa checklist richiede ~10 minuti e serve
a chiudere F1 davvero. Serve il tuo account Minecraft: il client di sviluppo fa il login vero, e
non posso farlo io al posto tuo.

## Avvio

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"
.\gradlew.bat runClient
```

Poi: Singleplayer → crea un mondo **in Creativa** con i trucchi attivi (servono per i comandi di
debug).

## Controlli

| # | Cosa fare | Cosa deve succedere | Perché conta |
|---|---|---|---|
| 1 | Guarda il log di avvio | Compare `Sistema inizializzato.` | La mod si carica |
| 2 | Controlla `run/config/arise.json` | Il file esiste, con i valori di bilanciamento | La config si scrive da sola |
| 3 | `/arise info` | Livello 1, XP 0/20, 0 punti, le 4 statistiche a 0 | Attachment creato e letto |
| 4 | `/arise xp 100` | Sali di più livelli in un colpo, con messaggio e suono per ogni salita | Il ciclo di level-up gestisce i guadagni grossi |
| 5 | `/arise spend vitality 5` poi guarda i cuori | +5 cuori (10 HP), e la vita attuale è piena, non a metà barra | Attributi applicati + la vita guadagnata è regalata |
| 6 | `/arise spend agility 20` e cammina | Sei percettibilmente più veloce (+30%) | `ADD_MULTIPLIED_BASE` funziona |
| 7 | `/arise info` di nuovo | I "valore attuale" riflettono i bonus | Lettura degli attributi reali |
| 8 | Uccidi qualche mob in Sopravvivenza | L'XP sale (verifica con `/arise info`); un mob più robusto vale di più | L'evento di morte attribuisce l'XP al giocatore giusto |
| 9 | **Muori** (`/kill`), poi rinasci e `/arise info` | Livello, XP, punti e statistiche sono **intatti**, e i cuori extra ci sono ancora | `copyOnDeath` + riapplicazione degli attributi al respawn. **È il controllo più importante** |
| 10 | Esci dal mondo, rientra, `/arise info` | Tutto intatto | Persistenza su disco |
| 11 | `/arise spend vitality 999` | Errore "punti insufficienti", niente cambia | Validazione lato server |
| 12 | `/arise reset` | Tutto a zero e i cuori tornano 10 | Rimozione dei modificatori |

## Se qualcosa non torna

Riporta **cosa hai fatto, cosa ti aspettavi e cosa è successo**, più le ultime righe del log se c'è
un errore. I punti 9 e 10 sono quelli dove è più probabile trovare problemi.

## Nota sul bilanciamento

I numeri di default (`config/arise.json`) sono un punto di partenza, non una proposta di
bilanciamento: `xp_base` 20 con esponente 1,6 rende i primi livelli molto rapidi, apposta, così la
verifica è veloce. Il tetto dell'agilità è a 100 punti (+150% velocità) ed è un limite tecnico, non
estetico — oltre ~3× la velocità base il client sfonda le collisioni.
