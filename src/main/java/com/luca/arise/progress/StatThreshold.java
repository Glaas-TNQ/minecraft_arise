package com.luca.arise.progress;

import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * Le soglie: il punto in cui spendere il centesimo punto cambia <em>cosa</em> puoi fare.
 *
 * <p>Quattro statistiche spendibili, tutte lineari, tutte utili a tutti: due punti di vita, quattro
 * decimi di danno, mezzo punto d'armatura. La critica documentata ai punti statistica puri e' che
 * «e' difficile notare un qualsiasi effetto diretto sul gioco mentre giochi», ed e' vera qui piu'
 * che altrove — a livello sessanta un Cacciatore ha speso centosettantasette punti e non e'
 * successo mai niente, e' solo diventato piu' grande.
 *
 * <p>La cura consolidata non e' abolire i punti: e' affiancarli a <strong>soglie con un effetto
 * qualitativo</strong>. Venticinque, cinquanta, cento — e a ognuna il gioco fa qualcosa che prima
 * non faceva. Una spesa smette di essere aritmetica e diventa una decisione: <em>mi mancano sette
 * punti a Vitalita' cinquanta, rinuncio alla Forza per arrivarci?</em>
 *
 * <p>Le tre soglie non sono in config, e la ragione e' la stessa per cui non lo e' la tabella degli
 * slot di equipaggiamento: sono <strong>struttura</strong>, non bilanciamento. I numeri che le
 * accompagnano — quanto lontano arriva il Passo d'ombra, quanto piu' forte colpiscono le ombre —
 * quelli si', e stanno dove stanno gli altri.
 *
 * <p>La schermata di stato le mostra <strong>prima</strong>: la barra verso il tetto e' diventata
 * una barra verso la prossima soglia, con scritto cosa da'. Vedere il lucchetto prima della chiave
 * e' meta' del motivo per spendere il punto.
 */
public enum StatThreshold {

	// ---- Vitalita': non morire, per motivi diversi

	/** La fame non uccide piu'. Ferisce, ma si ferma. */
	VITALITY_HUNGER("vitality_hunger", Stat.VITALITY, 25),
	/** Le cadute brevi non fanno piu' danno. */
	VITALITY_FALL("vitality_fall", Stat.VITALITY, 50),
	/** Un colpo letale ti lascia a mezzo cuore. Una volta ogni dieci minuti. */
	VITALITY_LAST_STAND("vitality_last_stand", Stat.VITALITY, 100),

	// ---- Forza: l'esercito, non il braccio

	/** Un'ombra in piu' in campo. */
	STRENGTH_COMMAND("strength_command", Stat.STRENGTH, 25),
	/** Le ombre colpiscono piu' forte. */
	STRENGTH_LEGION("strength_legion", Stat.STRENGTH, 50),
	/** Un'altra ombra in campo: due in piu' in tutto. */
	STRENGTH_HOST("strength_host", Stat.STRENGTH, 100),

	// ---- Resistenza: le cose che bruciano e avvelenano

	/** Il fuoco non ti brucia piu'. */
	ENDURANCE_FIRE("endurance_fire", Stat.ENDURANCE, 25),
	/** Il veleno non ti tocca. */
	ENDURANCE_POISON("endurance_poison", Stat.ENDURANCE, 50),
	/** Nemmeno la lava. */
	ENDURANCE_LAVA("endurance_lava", Stat.ENDURANCE, 100),

	// ---- Agilita': muoversi

	/** Correre annulla il danno da caduta. */
	AGILITY_SPRINT("agility_sprint", Stat.AGILITY, 25),
	/** Il Passo d'ombra arriva piu' lontano. */
	AGILITY_STEP_REACH("agility_step_reach", Stat.AGILITY, 50),
	/** E si ricarica in meta' tempo. */
	AGILITY_STEP_HASTE("agility_step_haste", Stat.AGILITY, 100);

	/** I tre gradini, uguali per tutte e quattro. */
	public static final List<Integer> STEPS = List.of(25, 50, 100);

	/**
	 * Fino a quanto <strong>danno</strong> una caduta viene perdonata. Non blocchi: danno.
	 *
	 * <p>La distinzione conta perche' in Minecraft il danno da caduta e' l'altezza meno tre, quindi
	 * sei di danno sono nove blocchi di caduta — e chi legge "sei" pensando ai blocchi tara la
	 * soglia alla meta' di quello che credeva.
	 */
	public static final float FALL_FORGIVEN = 6.0F;

	/** Quanto piu' forte colpiscono le ombre di un Monarca che ha superato la seconda soglia. */
	public static final double LEGION_DAMAGE_BONUS = 0.15;

	/** Quante ombre in piu' concede ogni soglia di Forza che ne concede una. */
	public static final int COMMAND_SLOTS = 1;

	/** Quanto piu' lontano arriva il Passo d'ombra oltre la seconda soglia di Agilita'. */
	public static final double STEP_REACH_BONUS = 0.5;

	/** Ogni quanti tick l'ultima difesa torna disponibile. Dieci minuti. */
	public static final long LAST_STAND_COOLDOWN = 12000L;

	/** Quanta vita resta dopo che l'ultima difesa e' intervenuta: mezzo cuore. */
	public static final float LAST_STAND_HEALTH = 1.0F;

	private final String name;
	private final Stat stat;
	private final int points;

	StatThreshold(String name, Stat stat, int points) {
		this.name = name;
		this.stat = stat;
		this.points = points;
	}

	public Stat stat() {
		return stat;
	}

	/** Quanti punti spesi in quella statistica servono. */
	public int points() {
		return points;
	}

	public String getSerializedName() {
		return name;
	}

	/** Il nome dell'effetto, breve: sta in una riga della schermata di stato. */
	public Component label() {
		return Component.translatable("arise.threshold." + name);
	}

	/** Cosa fa, per esteso. */
	public Component description() {
		return Component.translatable("arise.threshold." + name + ".desc");
	}

	/**
	 * Quante ombre puo' tenere in campo un Cacciatore con questi punti spesi.
	 *
	 * <p>Funzione pura, e non e' pedanteria: il numero serve al server per decidere, al client per
	 * disegnare la schermata dell'esercito, e alle prove per verificarlo. Tre chiamanti, di cui uno
	 * dall'altra parte della rete e uno senza un gioco avviato — se la regola stesse dentro un
	 * metodo che pretende un {@code ServerPlayer}, il client dovrebbe reimplementarla e prima o poi
	 * i due numeri divergerebbero. E' la stessa ragione per cui {@code callUpOrder} esiste
	 * separata da {@code callUp}.
	 */
	public static int summonLimit(PlayerProgress progress, int base) {
		int strength = progress.stat(Stat.STRENGTH);
		int limit = base;

		for (StatThreshold threshold : of(Stat.STRENGTH)) {
			if (threshold == STRENGTH_LEGION || strength < threshold.points()) {
				continue;
			}

			limit += COMMAND_SLOTS;
		}

		return limit;
	}

	/**
	 * Le soglie di una statistica, dalla piu' bassa alla piu' alta.
	 *
	 * <p>Ricavate scorrendo l'enum invece che scritte in una seconda tabella: due elenchi che
	 * devono restare d'accordo sono un elenco di troppo.
	 */
	public static List<StatThreshold> of(Stat stat) {
		return List.of(values()).stream().filter(threshold -> threshold.stat == stat).toList();
	}

	/**
	 * La prossima soglia non ancora raggiunta per questa statistica, o {@code null} se sono tutte
	 * dietro le spalle.
	 */
	public static StatThreshold next(Stat stat, int spent) {
		for (StatThreshold threshold : of(stat)) {
			if (spent < threshold.points) {
				return threshold;
			}
		}

		return null;
	}
}
