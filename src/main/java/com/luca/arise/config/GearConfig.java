package com.luca.arise.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.luca.arise.gear.GearSlot;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Quanto vale un pezzo di equipaggiamento.
 *
 * <p>L'estrazione di un pezzo e' tutta qui dentro: si parte da un <em>budget di potenza</em>
 * (base × rango × slot), lo si divide fra i modificatori che il rango concede, e ogni pezzo di
 * budget diventa valore vero moltiplicandolo per il passo della statistica.
 *
 * <p>Il passo per statistica esiste perche' le unita' non sono confrontabili: un punto di vita e
 * un punto di resistenza al contraccolpo sono numeri lontanissimi (il secondo arriva a 1 e
 * l'attributo vanilla li' si ferma). Il passo e' il cambio fra "potenza" e unita' del gioco.
 */
public record GearConfig(
		/** Budget di potenza di un pezzo di rango E in uno slot da moltiplicatore 1. */
		double basePower,
		/** Moltiplicatore di potenza per rango, da E a S. */
		List<Double> powerPerRank,
		/** Quanti modificatori estrae un pezzo, per rango. */
		List<Integer> affixesPerRank,
		/** Quante incastonature ha un pezzo, per rango. */
		List<Integer> socketsPerRank,
		/** Quanto vale, in unita' di gioco, un punto di potenza speso su una statistica. */
		Map<Stat, Double> stepPerPower,
		/** Quanto pesa uno slot: gli anelli sono deboli perche' sono dieci. */
		Map<GearSlot, Double> slotPower,
		/** Di quanto puo' scostarsi un singolo modificatore dalla sua quota (0,25 = ±25%). */
		double affixVariance,
		/** Quante volte e' piu' probabile che esca una statistica affine alla base. */
		double affinityWeight) {

	public static final Codec<GearConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("base_power").forGetter(GearConfig::basePower),
			Codec.DOUBLE.listOf().fieldOf("power_per_rank").forGetter(GearConfig::powerPerRank),
			Codec.INT.listOf().fieldOf("affixes_per_rank").forGetter(GearConfig::affixesPerRank),
			Codec.INT.listOf().fieldOf("sockets_per_rank").forGetter(GearConfig::socketsPerRank),
			Codec.unboundedMap(Stat.CODEC, Codec.DOUBLE).fieldOf("step_per_power")
					.forGetter(GearConfig::stepPerPower),
			Codec.unboundedMap(GearSlot.CODEC, Codec.DOUBLE).fieldOf("slot_power")
					.forGetter(GearConfig::slotPower),
			Codec.DOUBLE.fieldOf("affix_variance").forGetter(GearConfig::affixVariance),
			Codec.DOUBLE.fieldOf("affinity_weight").forGetter(GearConfig::affinityWeight)
	).apply(instance, GearConfig::new));

	public static final GearConfig DEFAULT = createDefault();

	public GearConfig {
		powerPerRank = List.copyOf(powerPerRank);
		affixesPerRank = List.copyOf(affixesPerRank);
		socketsPerRank = List.copyOf(socketsPerRank);
		stepPerPower = Map.copyOf(stepPerPower);
		slotPower = Map.copyOf(slotPower);
	}

	private static GearConfig createDefault() {
		Map<Stat, Double> step = new EnumMap<>(Stat.class);
		step.put(Stat.VITALITY, 1.0);      // 1 punto di potenza = mezzo cuore
		step.put(Stat.AGILITY, 0.004);     // +0,4% di velocita' base
		step.put(Stat.STRENGTH, 0.2);
		step.put(Stat.ENDURANCE, 0.25);
		step.put(Stat.TOUGHNESS, 0.15);
		step.put(Stat.HASTE, 0.006);
		// L'attributo vanilla si ferma a 1: passo minuscolo, o un pezzo solo lo saturerebbe.
		step.put(Stat.RESOLVE, 0.01);
		step.put(Stat.LEAP, 0.006);
		step.put(Stat.REACH, 0.02);
		step.put(Stat.ABSORPTION, 0.5);
		step.put(Stat.FORTUNE, 0.08);
		step.put(Stat.IMPACT, 0.02);

		Map<GearSlot, Double> slots = new EnumMap<>(GearSlot.class);
		// L'arma porta gia' il danno base della lama vanilla: i nostri modificatori le si sommano
		// sopra, quindi il budget non deve essere il piu' grosso della lista.
		slots.put(GearSlot.WEAPON, 1.2);
		slots.put(GearSlot.HEAD, 1.0);
		slots.put(GearSlot.CHEST, 1.2);
		slots.put(GearSlot.LEGS, 1.0);
		slots.put(GearSlot.FEET, 0.8);
		slots.put(GearSlot.HANDS, 0.8);
		slots.put(GearSlot.BELT, 0.7);
		slots.put(GearSlot.SHOULDERS, 0.7);
		slots.put(GearSlot.CLOAK, 0.9);
		slots.put(GearSlot.NECKLACE, 1.1);
		slots.put(GearSlot.TALISMAN, 1.3);
		slots.put(GearSlot.EARRING, 0.5);
		// Deboli di proposito: sono dieci, e dieci volte un pezzo forte banalizzerebbe il resto.
		slots.put(GearSlot.RING, 0.35);

		return new GearConfig(10.0,
				List.of(1.0, 1.4, 2.0, 2.8, 4.0, 5.6),
				List.of(1, 1, 2, 3, 3, 4),
				List.of(0, 0, 1, 2, 3, 4),
				step, slots, 0.25, 3.0);
	}

	/** Il budget di potenza di un pezzo. */
	public double power(Rank rank, GearSlot slot) {
		return basePower * at(powerPerRank, rank, 1.0) * slotPower.getOrDefault(slot, 1.0);
	}

	public int affixes(Rank rank) {
		return Math.max(1, at(affixesPerRank, rank, 1));
	}

	public int sockets(Rank rank) {
		return Math.max(0, at(socketsPerRank, rank, 0));
	}

	public double step(Stat stat) {
		return stepPerPower.getOrDefault(stat, 0.0);
	}

	/** Legge una lista per rango tollerando che sia corta: la config e' modificabile a mano. */
	private static <T> T at(List<T> values, Rank rank, T fallback) {
		if (values.isEmpty()) {
			return fallback;
		}

		return values.get(Math.min(rank.ordinal(), values.size() - 1));
	}
}
