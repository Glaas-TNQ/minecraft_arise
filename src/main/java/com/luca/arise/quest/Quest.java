package com.luca.arise.quest;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * La catena degli incarichi del Sistema.
 *
 * <p><strong>L'ordine di dichiarazione e' la catena.</strong> Non c'e' un campo che punta
 * all'incarico precedente, e non serve: gli incarichi si affrontano uno dopo l'altro, quindi lo
 * stato di un giocatore e' un numero — a quale punto della lista e' arrivato — e non un insieme di
 * cose fatte. Un solo numero non puo' contraddirsi.
 *
 * <p>Ogni incarico concede un {@link Unlock}, e l'incarico successivo parla quasi sempre del
 * sistema appena concesso: si riceve l'esercito e il compito dopo e' estrarre un'ombra. E' il modo
 * piu' semplice di insegnare una mod senza scrivere un tutorial.
 *
 * @param objective cosa va fatto
 * @param amount    quante volte
 * @param grants    cosa si apre completandolo
 * @param souls     soul coin di ricompensa
 * @param gearRank  se non nullo, un pezzo di equipaggiamento di questo rango in regalo
 * @param reward    se non nullo, l'oggetto della mod regalato: e' il percorso del suo id
 */
public enum Quest implements StringRepresentable {

	/**
	 * Il risveglio.
	 *
	 * <p>Non si completa facendo qualcosa: si completa <em>morendo</em>, o meglio arrivando a un
	 * passo dal morire. E' l'unico incarico che il giocatore non puo' cercare, e per questo e' il
	 * primo.
	 */
	AWAKENING("awakening", Objective.SURVIVE_DEATH, 1, Unlock.SYSTEM, 0L, null),
	/** Il Sistema misura chi lo ha ricevuto. */
	FIRST_STEPS("first_steps", Objective.REACH_LEVEL, 3, Unlock.STATS, 50L, null),
	/** Abbastanza morti perche' qualcosa risponda. */
	THE_ARMY("the_army", Objective.KILL, 15, Unlock.ARMY, 100L, null),
	/** La prima ombra. */
	FIRST_SHADOW("first_shadow", Objective.EXTRACT, 1, Unlock.ABILITIES, 150L, null),
	/** Il potere si usa, non si possiede. */
	THE_POWER("the_power", Objective.USE_ABILITY, 3, Unlock.GEAR, 200L, "e"),
	/** Qualcosa addosso. */
	EQUIPPED("equipped", Objective.EQUIP, 1, Unlock.GATES, 250L, null),
	/** Il primo varco chiuso. */
	FIRST_GATE("first_gate", Objective.CLEAR_GATE, 1, Unlock.SHOP, 400L, null),
	/** Il primo affare. */
	THE_TRADE("the_trade", Objective.BUY, 1, Unlock.CITIES, 500L, null),
	/** L'Associazione dei Cacciatori. */
	THE_ASSOCIATION("the_association", Objective.VISIT_CITY, 1, Unlock.GEMS, 800L, "d"),

	// --- la Via dell'Artigiano ----------------------------------------------------------------
	// Il secondo arco insegna l'Officina un pezzo per volta, e ogni incarico apre esattamente
	// cio' che serve al successivo. I quattro Progetti sono la chiave di volta: un macchinario
	// non si costruisce senza, e il primo di ognuno arriva da qui.

	/** C'e' gente, dietro quei banconi. */
	THE_MARKET("the_market", Objective.VISIT_MARKET, 1, Unlock.COIN, 300L, null, null),
	/** Il denaro del Sistema diventa denaro da portarsi dietro. */
	THE_MINT("the_mint", Objective.MINT_COIN, 8, Unlock.WORKSHOP, 0L, null, null),
	/** Quello che l'esercito non tiene, non si butta. */
	THE_SURPLUS("the_surplus", Objective.COLLECT_SOUL, 3, Unlock.LURE, 400L, null,
			"blueprint_soul_lure"),
	/** La prima macchina che gira. */
	THE_LURE("the_lure", Objective.MACHINE_WORK, 1, Unlock.CRUCIBLE, 500L, null,
			"blueprint_soul_crucible"),
	/** Quattro anime in una. */
	THE_FUSION("the_fusion", Objective.FUSE_SOUL, 1, Unlock.FORGE, 700L, null,
			"blueprint_shadow_forge"),
	/** Fondere senza fuoco. */
	THE_FORGE("the_forge", Objective.FORGE_SMELT, 16, Unlock.WELL, 900L, null,
			"blueprint_abyss_well"),
	/** L'Abisso paga. */
	THE_WELL("the_well", Objective.WELL_YIELD, 200, Unlock.ENLIST, 1000L, "c", null),
	/** Un'anima che torna a combattere. */
	THE_RECRUIT("the_recruit", Objective.ENLIST_SOUL, 1, Unlock.SERVICES, 1200L, null, null),
	/** L'ultimo passo: un varco comprato invece che aspettato. */
	THE_BROKER("the_broker", Objective.BUY_GATE, 1, Unlock.MASTERY, 2000L, "b", null);

	public static final Codec<Quest> CODEC = StringRepresentable.fromEnum(Quest::values);

	private final String name;
	private final Objective objective;
	private final int amount;
	private final Unlock grants;
	private final long souls;
	private final String gearRank;
	private final String reward;

	Quest(String name, Objective objective, int amount, Unlock grants, long souls, String gearRank) {
		this(name, objective, amount, grants, souls, gearRank, null);
	}

	Quest(String name, Objective objective, int amount, Unlock grants, long souls, String gearRank,
			String reward) {
		this.name = name;
		this.objective = objective;
		this.amount = amount;
		this.grants = grants;
		this.souls = souls;
		this.gearRank = gearRank;
		this.reward = reward;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Objective objective() {
		return objective;
	}

	public int amount() {
		return amount;
	}

	public Unlock grants() {
		return grants;
	}

	public long souls() {
		return souls;
	}

	/** Il rango del pezzo in regalo, o {@code null} se questo incarico non ne da' nessuno. */
	public String gearRank() {
		return gearRank;
	}

	/**
	 * L'oggetto della mod in regalo, come percorso del suo id, o {@code null}.
	 *
	 * <p>E' una stringa e non un {@code Item} per una ragione di ordine: gli incarichi stanno nel
	 * pacchetto {@code quest}, e gli oggetti nel registro. Un enum che nel suo campo tenesse un
	 * oggetto registrato obbligherebbe la catena degli incarichi a essere caricata dopo il
	 * registro, e quell'ordine e' esattamente il genere di vincolo che non si vede finche' non si
	 * rompe.
	 */
	public String reward() {
		return reward;
	}

	public Component title() {
		return Component.translatable("arise.quest." + name);
	}

	public Component description() {
		return Component.translatable("arise.quest." + name + ".goal", amount);
	}

	/** Quanti incarichi ci sono in tutto. Serve alla schermata e ai messaggi. */
	public static int count() {
		return values().length;
	}
}
