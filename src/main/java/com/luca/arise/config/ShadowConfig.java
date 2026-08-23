package com.luca.arise.config;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Bilanciamento dell'esercito d'ombra.
 *
 * <p>Sotto-oggetto {@code "shadows"} di {@code config/arise.json}. È un blocco a sé perché il codec
 * di un record ha un tetto di 16 campi; per la stessa ragione il livellamento è annidato ancora
 * più sotto, in {@link Leveling}.
 *
 * <p>I campi sono <strong>obbligatori</strong> di proposito: le chiavi mancanti in un file vecchio
 * vengono riempite dai default prima del parsing, in {@code AriseConfig.withDefaults}. Con
 * {@code optionalFieldOf} il codec le accetterebbe in lettura ma le ometterebbe in scrittura
 * quando coincidono col default, e resterebbero invisibili nel file — quindi non modificabili.
 */
public record ShadowConfig(
		/** Probabilità base di estrarre l'ombra da un nemico ucciso. */
		double extractionChanceBase,
		/** Quanto la probabilità cresce per ogni livello del giocatore. */
		double extractionChancePerLevel,
		/** Per quanti tick un cadavere resta estraibile. */
		int extractionWindowTicks,
		/** Entro quanti blocchi si può estrarre un'ombra. */
		double extractionRange,
		/** Ombre conservabili al livello 1. */
		int baseCapacity,
		/** Capienza aggiuntiva per livello. */
		double capacityPerLevel,
		/** Quante ombre possono stare evocate insieme. Limite tecnico oltre che di design. */
		int maxSummoned,
		/** Vita dell'ombra = vita massima del mob d'origine × questo. */
		double healthFactor,
		/** Danno dell'ombra = danno stimato del mob d'origine × questo. */
		double damageFactor,
		/** Velocità di movimento dell'ombra evocata. */
		double movementSpeed,
		/** Raggio entro cui l'ombra cerca bersagli. */
		double followRange,
		/**
		 * Quanto resta a terra un'ombra caduta prima di poter essere rievocata.
		 *
		 * <p>Mille duecento tick, cioe' un minuto. Non e' una punizione: e' l'unica conseguenza di
		 * una morte in questa mod che costi qualcosa senza togliere niente per sempre. Senza,
		 * mandare l'esercito a morire e rievocarlo subito sarebbe sempre la mossa migliore, e la
		 * postura aggressiva non sarebbe una scelta.
		 */
		int downtimeTicks,
		/** Punteggio minimo per ciascun rango, da E a S. */
		List<Double> rankThresholds,
		/** Crescita delle ombre con l'esperienza. */
		Leveling leveling,
		/** Quanto costano in soul coin le operazioni sull'esercito. */
		Costs costs,
		/** Archetipi, gradi, aura di comando e dono del Monarca. */
		Legion legion) {

	/**
	 * Come si comporta un archetipo: quattro moltiplicatori sulle statistiche di base e la taglia.
	 *
	 * <p>Sono moltiplicatori e non valori assoluti perche' l'ombra li applica <em>sopra</em> a cio'
	 * che le viene dal mob d'origine: un Colosso estratto da un ravager e un Colosso estratto da un
	 * golem restano diversi fra loro, ed e' quello che si vuole.
	 */
	public record Archetype(
			/** Moltiplicatore sulla vita. */
			double health,
			/** Moltiplicatore sul danno. */
			double damage,
			/** Moltiplicatore sulla velocita' di movimento. */
			double speed,
			/** Moltiplicatore sul raggio in cui cerca bersagli. */
			double followRange,
			/** Taglia dell'entita'. Vanilla ha {@code minecraft:scale}, e si vede a colpo d'occhio. */
			double scale,
			/** Resistenza al contraccolpo. L'attributo vanilla si ferma a uno. */
			double knockbackResistance) {

		public static final Codec<Archetype> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("health").forGetter(Archetype::health),
				Codec.DOUBLE.fieldOf("damage").forGetter(Archetype::damage),
				Codec.DOUBLE.fieldOf("speed").forGetter(Archetype::speed),
				Codec.DOUBLE.fieldOf("follow_range").forGetter(Archetype::followRange),
				Codec.DOUBLE.fieldOf("scale").forGetter(Archetype::scale),
				Codec.DOUBLE.fieldOf("knockback_resistance").forGetter(Archetype::knockbackResistance)
		).apply(instance, Archetype::new));
	}

	/**
	 * I numeri dei comportamenti: come si riconosce un archetipo, e cosa fa quando e' in campo.
	 *
	 * <p>Stanno insieme e non sparsi nei quattro {@link Archetype} perche' ognuno riguarda un solo
	 * archetipo: una {@code lanceRange} su una Bestia non vorrebbe dire niente, e un campo che per
	 * tre valori su quattro e' rumore e' un campo messo nel posto sbagliato.
	 */
	public record Behaviour(
			/** Vita del mob d'origine oltre la quale l'ombra e' un Colosso. */
			double tankHealthThreshold,
			/** Armatura del mob d'origine oltre la quale l'ombra e' un Colosso. */
			int tankArmorThreshold,
			/**
			 * Mob le cui ombre sono Maghi.
			 *
			 * <p>L'elenco e' la <em>fonte di verita'</em>, non un complemento: l'interfaccia vanilla
			 * {@code RangedAttackMob} si puo' chiedere solo a un'entita' viva, e l'archetipo va
			 * deciso anche quando di viva non c'e' piu' niente — un'anima arruolata dall'Officina e'
			 * un id e due numeri. Cosi' i due percorsi danno la stessa risposta, ed e' anche l'unico
			 * modo che un server ha di classificare i mob di un'altra mod.
			 *
			 * <p>Blaze, ghast ed evoker sono nell'elenco anche se sparano: {@code RangedAttackMob}
			 * non lo implementano, e senza l'elenco resterebbero fuori comunque.
			 */
			List<Identifier> casters,
			/** Entro quanti blocchi il Colosso si prende addosso chi puntava il Monarca. */
			double tauntRadius,
			/** Ogni quanti tick il Colosso provoca. */
			int tauntIntervalTicks,
			/** Fin dove arriva la lancia d'ombra del Mago. */
			double lanceRange,
			/** Sotto questa distanza il Mago indietreggia invece di colpire. */
			double lanceMinRange,
			/** Ogni quanti tick il Mago lancia. */
			int lanceIntervalTicks,
			/** Danno della lancia = danno dell'ombra per questo. */
			double lanceDamageFactor,
			/** Entro quanti blocchi dal punto d'ordine restano le ombre in posizione. */
			double holdRadius,
			/** Fin dove si puo' indicare un bersaglio all'esercito. */
			double focusRange,
			/** Entro quanti blocchi la Guardia va a mettersi fra il Monarca e chi lo punta. */
			double interposeRange) {

		public static final Behaviour DEFAULT = new Behaviour(
				40.0, 8,
				List.of(
						Identifier.withDefaultNamespace("skeleton"),
						Identifier.withDefaultNamespace("stray"),
						Identifier.withDefaultNamespace("bogged"),
						Identifier.withDefaultNamespace("witch"),
						Identifier.withDefaultNamespace("pillager"),
						Identifier.withDefaultNamespace("piglin"),
						Identifier.withDefaultNamespace("drowned"),
						Identifier.withDefaultNamespace("blaze"),
						Identifier.withDefaultNamespace("ghast"),
						Identifier.withDefaultNamespace("evoker"),
						Identifier.withDefaultNamespace("illusioner"),
						Identifier.withDefaultNamespace("shulker"),
						Identifier.withDefaultNamespace("guardian"),
						Identifier.withDefaultNamespace("breeze"),
						Identifier.withDefaultNamespace("snow_golem"),
						Identifier.withDefaultNamespace("llama"),
						Identifier.withDefaultNamespace("trader_llama")),
				12.0, 60, 16.0, 5.0, 40, 0.8, 8.0, 48.0, 12.0);

		public static final Codec<Behaviour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("tank_health_threshold").forGetter(Behaviour::tankHealthThreshold),
				Codec.INT.fieldOf("tank_armor_threshold").forGetter(Behaviour::tankArmorThreshold),
				Identifier.CODEC.listOf().fieldOf("casters").forGetter(Behaviour::casters),
				Codec.DOUBLE.fieldOf("taunt_radius").forGetter(Behaviour::tauntRadius),
				Codec.INT.fieldOf("taunt_interval_ticks").forGetter(Behaviour::tauntIntervalTicks),
				Codec.DOUBLE.fieldOf("lance_range").forGetter(Behaviour::lanceRange),
				Codec.DOUBLE.fieldOf("lance_min_range").forGetter(Behaviour::lanceMinRange),
				Codec.INT.fieldOf("lance_interval_ticks").forGetter(Behaviour::lanceIntervalTicks),
				Codec.DOUBLE.fieldOf("lance_damage_factor").forGetter(Behaviour::lanceDamageFactor),
				Codec.DOUBLE.fieldOf("hold_radius").forGetter(Behaviour::holdRadius),
				Codec.DOUBLE.fieldOf("focus_range").forGetter(Behaviour::focusRange),
				Codec.DOUBLE.fieldOf("interpose_range").forGetter(Behaviour::interposeRange)
		).apply(instance, Behaviour::new));

		public Behaviour {
			casters = List.copyOf(casters);
		}

		/** L'elenco dei lanciatori come insieme, per la ricerca: la classificazione e' un contains. */
		public Set<Identifier> casterSet() {
			return Set.copyOf(casters);
		}
	}

	/**
	 * L'esercito come formazione: archetipi, gradi, aura di comando, dono del Monarca.
	 *
	 * <p>Un blocco a se' per la solita ragione del tetto di sedici campi, ma anche perche' e'
	 * l'unico pezzo di bilanciamento che riguarda l'esercito <em>insieme</em> invece che l'ombra
	 * singola.
	 */
	public record Legion(
			/** La Guardia: mischia equilibrata. E' anche il caso di chi non si classifica altrove. */
			Archetype guard,
			/** La Bestia: veloce e fragile. */
			Archetype beast,
			/** Il Mago: colpisce da lontano, incassa male. */
			Archetype mage,
			/** Il Colosso: lento e duro. */
			Archetype tank,
			/** I numeri dei comportamenti. */
			Behaviour behaviour,
			/** Potenza minima per ciascun grado, da Normale a Gran Maresciallo. */
			List<Double> gradeThresholds,
			/** Danno in piu' per le altre ombre in campo, per ogni gradino sopra Comandante. */
			double commanderDamageBonus,
			/** Vita in piu' per le altre ombre in campo, per ogni gradino sopra Comandante. */
			double commanderHealthBonus,
			/**
			 * Quanta parte del danno del Monarca passa a ogni ombra evocata.
			 *
			 * <p>Si legge dall'attributo del giocatore e non dalle sue statistiche: cosi' ci
			 * finiscono dentro anche l'equipaggiamento, le gemme e l'arma in mano, senza doverli
			 * sommare a mano. Nel manhwa la forza dell'esercito <em>e'</em> la forza del Monarca, e
			 * senza questo un'ombra estratta all'inizio resta inutile per sempre.
			 */
			double monarchDamageShare,
			/** Quanta parte della vita del Monarca passa a ogni ombra evocata. */
			double monarchHealthShare,
			/**
			 * Se la squadra non riempie i posti, si completa con le ombre piu' forti disponibili.
			 *
			 * <p>Con questo a falso il tasto di evocazione manda fuori <em>solo</em> la squadra, che
			 * e' il comportamento che vuole chi la squadra se l'e' composta con cura.
			 */
			boolean fillFromRoster) {

		public static final Legion DEFAULT = new Legion(
				new Archetype(1.00, 1.00, 1.00, 1.0, 1.00, 0.00),
				new Archetype(0.75, 1.20, 1.30, 1.5, 0.85, 0.00),
				new Archetype(0.60, 1.00, 1.00, 1.6, 0.95, 0.00),
				new Archetype(1.90, 0.80, 0.80, 1.0, 1.25, 0.60),
				Behaviour.DEFAULT,
				List.of(0.0, 60.0, 120.0, 220.0, 380.0, 600.0, 900.0, 1400.0),
				0.10, 0.08, 0.35, 0.25, true);

		public static final Codec<Legion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Archetype.CODEC.fieldOf("guard").forGetter(Legion::guard),
				Archetype.CODEC.fieldOf("beast").forGetter(Legion::beast),
				Archetype.CODEC.fieldOf("mage").forGetter(Legion::mage),
				Archetype.CODEC.fieldOf("tank").forGetter(Legion::tank),
				Behaviour.CODEC.fieldOf("behaviour").forGetter(Legion::behaviour),
				Codec.DOUBLE.listOf().fieldOf("grade_thresholds").forGetter(Legion::gradeThresholds),
				Codec.DOUBLE.fieldOf("commander_damage_bonus").forGetter(Legion::commanderDamageBonus),
				Codec.DOUBLE.fieldOf("commander_health_bonus").forGetter(Legion::commanderHealthBonus),
				Codec.DOUBLE.fieldOf("monarch_damage_share").forGetter(Legion::monarchDamageShare),
				Codec.DOUBLE.fieldOf("monarch_health_share").forGetter(Legion::monarchHealthShare),
				Codec.BOOL.fieldOf("fill_from_roster").forGetter(Legion::fillFromRoster)
		).apply(instance, Legion::new));

		public Legion {
			gradeThresholds = List.copyOf(gradeThresholds);
		}
	}

	/** Prezzi in soul coin. Annidati per non sfondare il tetto di 16 campi del codec. */
	public record Costs(
			/** Costo base per far salire un'ombra di un livello. */
			long upgradeBase,
			/** Quanto il costo cresce per ogni livello già raggiunto. */
			long upgradePerLevel,
			/** Costo di un rinominare. */
			long rename,
			/** Costo di un cambio colore. */
			long recolor,
			/** Percentuale del valore restituita quando si congeda un'ombra. */
			double dismissRefund) {

		public static final Costs DEFAULT = new Costs(50L, 25L, 20L, 30L, 0.25);

		public static final Codec<Costs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("upgrade_base").forGetter(Costs::upgradeBase),
				Codec.LONG.fieldOf("upgrade_per_level").forGetter(Costs::upgradePerLevel),
				Codec.LONG.fieldOf("rename").forGetter(Costs::rename),
				Codec.LONG.fieldOf("recolor").forGetter(Costs::recolor),
				Codec.DOUBLE.fieldOf("dismiss_refund").forGetter(Costs::dismissRefund)
		).apply(instance, Costs::new));

		/** Costo per portare un'ombra dal livello indicato a quello successivo. */
		public long upgradeCost(int level) {
			return upgradeBase + upgradePerLevel * (level - 1);
		}
	}

	/** Come crescono le ombre combattendo. */
	public record Leveling(
			/** XP necessaria a un'ombra per passare dal livello 1 al 2. */
			double xpBase,
			/** Esponente della curva. */
			double xpExponent,
			/** Quota dell'XP di un'uccisione che va alle ombre evocate che non hanno colpito. */
			double xpShare,
			/** Crescita percentuale delle statistiche per ogni livello dell'ombra. */
			double statGrowthPerLevel,
			/** Livello massimo di un'ombra. */
			int maxLevel) {

		public static final Leveling DEFAULT = new Leveling(30.0, 1.4, 0.5, 0.08, 50);

		public static final Codec<Leveling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("xp_base").forGetter(Leveling::xpBase),
				Codec.DOUBLE.fieldOf("xp_exponent").forGetter(Leveling::xpExponent),
				Codec.DOUBLE.fieldOf("xp_share").forGetter(Leveling::xpShare),
				Codec.DOUBLE.fieldOf("stat_growth_per_level")
						.forGetter(Leveling::statGrowthPerLevel),
				Codec.INT.fieldOf("max_level").forGetter(Leveling::maxLevel)
		).apply(instance, Leveling::new));

		/** XP necessaria per passare da questo livello al successivo. */
		public long xpForNextLevel(int level) {
			return Math.max(1L, (long) (xpBase * Math.pow(level, xpExponent)));
		}

		/** Moltiplicatore delle statistiche a questo livello. */
		public double statMultiplier(int level) {
			return 1.0 + statGrowthPerLevel * (level - 1);
		}
	}

	public static final ShadowConfig DEFAULT = new ShadowConfig(
			0.25, 0.007, 300, 8.0, 6, 0.25, 4, 1.5, 1.2, 0.32, 32.0, 1200,
			List.of(0.0, 30.0, 60.0, 110.0, 180.0, 280.0), Leveling.DEFAULT, Costs.DEFAULT,
			Legion.DEFAULT);

	public static final Codec<ShadowConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("extraction_chance_base")
					.forGetter(ShadowConfig::extractionChanceBase),
			Codec.DOUBLE.fieldOf("extraction_chance_per_level")
					.forGetter(ShadowConfig::extractionChancePerLevel),
			Codec.INT.fieldOf("extraction_window_ticks")
					.forGetter(ShadowConfig::extractionWindowTicks),
			Codec.DOUBLE.fieldOf("extraction_range")
					.forGetter(ShadowConfig::extractionRange),
			Codec.INT.fieldOf("base_capacity")
					.forGetter(ShadowConfig::baseCapacity),
			Codec.DOUBLE.fieldOf("capacity_per_level")
					.forGetter(ShadowConfig::capacityPerLevel),
			Codec.INT.fieldOf("max_summoned")
					.forGetter(ShadowConfig::maxSummoned),
			Codec.DOUBLE.fieldOf("health_factor")
					.forGetter(ShadowConfig::healthFactor),
			Codec.DOUBLE.fieldOf("damage_factor")
					.forGetter(ShadowConfig::damageFactor),
			Codec.DOUBLE.fieldOf("movement_speed")
					.forGetter(ShadowConfig::movementSpeed),
			Codec.DOUBLE.fieldOf("follow_range")
					.forGetter(ShadowConfig::followRange),
			Codec.INT.fieldOf("downtime_ticks")
					.forGetter(ShadowConfig::downtimeTicks),
			Codec.DOUBLE.listOf().fieldOf("rank_thresholds")
					.forGetter(ShadowConfig::rankThresholds),
			Leveling.CODEC.fieldOf("leveling")
					.forGetter(ShadowConfig::leveling),
			Costs.CODEC.fieldOf("costs")
					.forGetter(ShadowConfig::costs),
			Legion.CODEC.fieldOf("legion")
					.forGetter(ShadowConfig::legion)
	).apply(instance, ShadowConfig::new));

	public ShadowConfig {
		rankThresholds = List.copyOf(rankThresholds);
	}

	/** Quante ombre può conservare un giocatore di questo livello. */
	public int capacityAt(int level) {
		return baseCapacity + (int) (capacityPerLevel * (level - 1));
	}

	/**
	 * Il tetto della probabilita' di estrazione: un'ombra puo' sempre dissolversi.
	 *
	 * <p>Non e' bilanciamento, e' una promessa: il verbo che da' il nome alla mod resta un tiro di
	 * dado fino all'ultimo livello, e un Cacciatore al massimo non e' uno che estrae sempre — e'
	 * uno che sbaglia una volta su venti.
	 */
	public static final double EXTRACTION_CEILING = 0.95;

	/**
	 * Probabilità di estrazione per un giocatore di questo livello.
	 *
	 * <p>Il tetto era irraggiungibile: con 0,005 a livello serviva il livello 141, e il massimo e'
	 * 100. Una costante che nessuno puo' toccare non e' un tetto, e' una riga di codice morta —
	 * il giocatore al livello massimo falliva ancora un'estrazione su quattro senza nessun modo di
	 * migliorare. A 0,007 il centesimo livello vale 94,3%, e il tetto si tocca appena dopo:
	 * esiste, si vede arrivare, e resta una promessa mantenuta.
	 */
	public double extractionChanceAt(int level) {
		return Math.min(EXTRACTION_CEILING,
				extractionChanceBase + extractionChancePerLevel * (level - 1));
	}
}
