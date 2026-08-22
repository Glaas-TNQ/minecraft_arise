package com.luca.arise.quest;

/**
 * Cosa chiede un incarico.
 *
 * <p>Ogni voce corrisponde a un momento che il codice della mod gia' intercetta, ed e' un vincolo
 * voluto: un obiettivo che richiedesse un aggancio nuovo sarebbe un obiettivo che aspetta. Chi
 * fa avanzare il contatore lo fa chiamando {@link QuestManager#advance} da dove quel momento
 * succede gia'.
 */
public enum Objective {

	/** Arrivare a un passo dalla morte. L'unico che il giocatore non puo' cercare. */
	SURVIVE_DEATH,
	/** Raggiungere un livello. Il contatore e' il livello stesso, non quante volte si e' saliti. */
	REACH_LEVEL,
	/** Uccidere creature. */
	KILL,
	/** Estrarre ombre. */
	EXTRACT,
	/** Usare abilita'. */
	USE_ABILITY,
	/**
	 * Avere addosso pezzi di equipaggiamento.
	 *
	 * <p>E' un valore raggiunto e non un conteggio, perche' da quando i pezzi stanno nelle caselle
	 * indossare non e' piu' un <em>momento</em> che qualcuno intercetta: e' uno stato che si
	 * osserva. "Indossa due pezzi" vuol dire averne due addosso, non averne infilato due nel corso
	 * della partita.
	 */
	EQUIP,
	/** Completare Gate. */
	CLEAR_GATE,
	/** Comprare all'Abyss Shop. */
	BUY,
	/** Mettere piede in un'Associazione dei Cacciatori. */
	VISIT_CITY,

	// --- la Via dell'Artigiano: il secondo arco, quello che insegna l'Officina ----------------
	/** Parlare con qualcuno dietro un bancone del Quartiere del Mercato. */
	VISIT_MARKET,
	/** Coniare Monete d'Anima al Banco dell'Abisso. */
	MINT_COIN,
	/** Raccogliere Anime Erranti, comunque siano arrivate. */
	COLLECT_SOUL,
	/** Un giro di lavoro finito da un macchinario qualunque. */
	MACHINE_WORK,
	/** Una fusione riuscita nel Crogiolo. */
	FUSE_SOUL,
	/** Oggetti usciti dalla Fucina d'Ombra. */
	FORGE_SMELT,
	/** Soul coin munti dal Pozzo dell'Abisso. */
	WELL_YIELD,
	/** Un'anima arruolata nell'esercito. */
	ENLIST_SOUL,
	/** Un varco comprato dal Sensale. */
	BUY_GATE;

	/**
	 * Vero se il progresso e' un <em>valore raggiunto</em> invece di un conteggio.
	 *
	 * <p>"Arriva al livello 3" non e' "sali di livello tre volte": chi comincia gia' al livello 2
	 * deve dover salire una volta sola. Per questi il contatore si sostituisce, non si somma.
	 */
	public boolean absolute() {
		return this == REACH_LEVEL || this == EQUIP;
	}
}
