#!/bin/sh
#
# Arise - client di sviluppo, per macOS e Linux.
#
# Su macOS l'estensione .command lo rende avviabile con un doppio clic: il Finder
# lo apre nel Terminale. Su Linux si lancia da terminale con ./avvia-client.command
# oppure sh avvia-client.command - dentro e' sh normale, l'estensione non cambia niente.
#
# L'equivalente per Windows e' avvia-client.bat.

# Il doppio clic parte dalla cartella home, non da qui.
cd "$(dirname "$0")" || exit 1

fine() {
    printf '\n  Premi Invio per chiudere questa finestra. '
    read -r _
    exit "$1"
}

# ---------------------------------------------------------------- cartella

if [ ! -f gradlew ]; then
    printf '\n  Questo file va tenuto nella cartella del progetto, accanto a gradlew.\n'
    printf '  Adesso si trova in: %s\n' "$(pwd)"
    fine 1
fi

# Il bit di esecuzione si perde con uno zip, una copia da Windows o un checkout
# fatto male. Rimetterlo costa niente e toglie di mezzo un "permission denied".
[ -x gradlew ] || chmod +x gradlew

# ---------------------------------------------------------------- Java 25

# La versione maggiore dichiarata da un eseguibile java, o niente se non parte.
versione_java() {
    "$1" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p'
}

trova_java() {
    # 1. Quello che hai gia' scelto tu, se e' la versione giusta.
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        [ "$(versione_java "$JAVA_HOME/bin/java")" = "25" ] && return 0
    fi

    # 2. macOS tiene un registro dei JDK installati e sa rispondere per versione.
    if [ -x /usr/libexec/java_home ]; then
        casa=$(/usr/libexec/java_home -v 25 2>/dev/null)
        if [ -n "$casa" ] && [ -x "$casa/bin/java" ]; then
            JAVA_HOME="$casa"
            export JAVA_HOME
            return 0
        fi
    fi

    # 3. Quello nel PATH. Se JAVA_HOME puntava altrove va tolto di mezzo, o Gradle
    #    userebbe comunque quello sbagliato.
    if command -v java >/dev/null 2>&1 && [ "$(versione_java java)" = "25" ]; then
        unset JAVA_HOME
        return 0
    fi

    return 1
}

if ! trova_java; then
    printf '\n  Serve Java 25, e qui non lo trovo.\n\n'
    printf '  Su macOS:  brew install --cask temurin@25\n'
    printf '  Altrimenti, da  https://adoptium.net  scegli la versione 25.\n\n'
    printf '  Se lo hai gia'"'"' installato in un posto suo, indicamelo cosi:\n'
    printf '    export JAVA_HOME=/percorso/del/jdk-25\n'
    fine 1
fi

# ---------------------------------------------------------------- avvio

cat <<'PROMEMORIA'

 ================================================================
  Avvio di Minecraft con la mod. Ricompila da solo: non serve
  fare build prima. La prima volta ci mette qualche minuto.

  Nessun login: entra con un account fittizio.
  Per fermarlo, chiudi la finestra del gioco.
 ================================================================

PROMEMORIA

./gradlew runClient
uscita=$?

printf '\n  Il gioco si e'"'"' chiuso.\n'
fine "$uscita"
