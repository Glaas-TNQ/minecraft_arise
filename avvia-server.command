#!/bin/sh
#
# Arise - server di sviluppo, per macOS e Linux.
#
# Su macOS l'estensione .command lo rende avviabile con un doppio clic: il Finder
# lo apre nel Terminale. Su Linux si lancia da terminale con ./avvia-server.command
# oppure sh avvia-server.command - dentro e' sh normale, l'estensione non cambia niente.
#
# L'equivalente per Windows e' avvia-server.bat.

# Il doppio clic parte dalla cartella home, non da qui: senza questo, gradlew non
# si troverebbe e il mondo del server finirebbe in un posto a caso.
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

mkdir -p run

# ---------------------------------------------------------------- EULA

# La accetti tu rispondendo, non questo file al posto tuo.
if ! grep -qi 'eula=true' run/eula.txt 2>/dev/null; then
    printf '\n  Un server di Minecraft parte solo se accetti l'"'"'EULA di Mojang:\n'
    printf '    https://aka.ms/MinecraftEULA\n\n'
    printf '  La accetti? [s/N] '
    read -r risposta

    case "$risposta" in
        s | S | si | Si | SI | y | Y)
            printf 'eula=true\n' > run/eula.txt
            printf '  Accettata. Scritta in run/eula.txt, non te lo chiedo piu'"'"'.\n'
            ;;
        *)
            printf '\n  Senza EULA accettata il server non puo'"'"' partire. Non ho fatto niente.\n'
            fine 1
            ;;
    esac
fi

# ---------------------------------------------------------------- proprieta'

# Solo se il file non c'e' gia': se lo hai modificato tu, resta com'e'.
if [ ! -f run/server.properties ]; then
    printf 'online-mode=false\n' > run/server.properties
    printf '  Creato run/server.properties con online-mode=false,\n'
    printf '  altrimenti il client di sviluppo non riuscirebbe a entrare.\n'
fi

# ---------------------------------------------------------------- avvio

cat <<'PROMEMORIA'

 ================================================================
  Avvio del server. La prima volta ci mette qualche minuto.

  Quando compare "Done", in QUESTA finestra puoi scrivere comandi
  SENZA la barra davanti:

    op TUONOME                 ti da' i permessi da gamemaster
    arise city build rome      costruisce una citta'
    arise city build all       le costruisce tutte e cinque
    stop                       ferma il server

  Per giocarci: apri avvia-client.command e connettiti a  localhost
 ================================================================

PROMEMORIA

./gradlew runServer
uscita=$?

printf '\n  Il server si e'"'"' fermato.\n'
fine "$uscita"
