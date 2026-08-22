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
	/** Indossare pezzi di equipaggiamento. */
	EQUIP,
	/** Completare Gate. */
	CLEAR_GATE,
	/** Comprare all'Abyss Shop. */
	BUY,
	/** Mettere piede in un'Associazione dei Cacciatori. */
	VISIT_CITY;

	/**
	 * Vero se il progresso e' un <em>valore raggiunto</em> invece di un conteggio.
	 *
	 * <p>"Arriva al livello 3" non e' "sali di livello tre volte": chi comincia gia' al livello 2
	 * deve dover salire una volta sola. Per questi il contatore si sostituisce, non si somma.
	 */
	public boolean absolute() {
		return this == REACH_LEVEL;
	}
}
