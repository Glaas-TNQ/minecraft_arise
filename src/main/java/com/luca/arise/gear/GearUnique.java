package com.luca.arise.gear;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.config.GearConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * I pezzi che non si tirano: quelli che si ricevono.
 *
 * <p>Tutto il resto dell'equipaggiamento nasce da un'estrazione — base e rango danno un budget, il
 * budget si divide fra modificatori a caso — e va benissimo per il bottino, dove la varieta' <em>e'</em>
 * il premio. Ma un pezzo consegnato da un incarico ha un lavoro diverso: deve essere sempre lo
 * stesso, sempre indossabile, e deve valere la pena di essere ricordato. Un anello di rango E tirato
 * a caso non lo si ricorda; l'Occhio dell'Oscurita' si'.
 *
 * <p><strong>I numeri restano in config.</strong> Un pezzo unico non ha valori scritti a mano: ha
 * delle <em>quote</em> del budget che la config assegna a quel rango e a quello slot. Cosi' chi
 * abbassa {@code base_power} abbassa anche l'Occhio, e la regola che vieta i numeri di
 * bilanciamento nel codice resta in piedi. La somma delle quote supera l'unita' apposta: un pezzo
 * unico vale un quinto in piu' di uno normale del suo rango, ed e' l'unica cosa che lo rende un
 * regalo invece di un pezzo qualunque con un nome piu' bello.
 *
 * <p><strong>Due quote, non quattro.</strong> Il budget di un pezzo di rango E e' dieci punti di
 * potenza, e un pezzo tirato li mette tutti su una statistica sola. Spalmarli su tre o quattro
 * quote da' numeri che nessuno legge — piu' zero virgola due di fortuna, piu' l'uno e otto per
 * cento di velocita' d'attacco — e un regalo che <em>sembra</em> piu' debole di un elmo qualunque
 * pur costando di piu'. Due quote grosse, due numeri tondi.
 *
 * <p>L'{@link #aura} e' l'altra meta': un effetto che dura finche' il pezzo e' addosso. Un elmo che
 * si chiama Occhio dell'Oscurita' deve fare qualcosa nel buio, o e' solo un nome.
 */
public enum GearUnique implements StringRepresentable {

	/**
	 * Il primo pezzo che il Sistema consegna, e l'unico che ogni Cacciatore avra' avuto.
	 *
	 * <p>Un elmo e non un accessorio, e la ragione e' un difetto vero: gli slot del Cacciatore si
	 * aprono col rango, e un regalo finito in uno slot ancora chiuso e' un regalo che si guarda e
	 * basta — per giunta proprio mentre l'incarico successivo chiede di indossare qualcosa. Le
	 * cinque caselle di vanilla sono aperte sempre, a chiunque, dal primo minuto.
	 */
	EYE_OF_DARKNESS("eye_of_darkness", GearBase.HELM, Rank.E, MobEffects.NIGHT_VISION,
			Map.of(Stat.ENDURANCE, 0.8, Stat.VITALITY, 0.4));

	public static final Codec<GearUnique> CODEC = StringRepresentable.fromEnum(GearUnique::values);

	/** Il colore dei pezzi unici: lo stesso che vanilla usa per le cose rare. */
	public static final ChatFormatting COLOR = ChatFormatting.LIGHT_PURPLE;

	private final String name;
	private final GearBase base;
	private final Rank rank;
	private final Holder<MobEffect> aura;
	private final Map<Stat, Double> shares;

	GearUnique(String name, GearBase base, Rank rank, Holder<MobEffect> aura,
			Map<Stat, Double> shares) {
		this.name = name;
		this.base = base;
		this.rank = rank;
		this.aura = aura;
		this.shares = shares;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public GearBase base() {
		return base;
	}

	public Rank rank() {
		return rank;
	}

	/** L'effetto che dura finche' il pezzo e' indossato, o {@code null}. */
	public Holder<MobEffect> aura() {
		return aura;
	}

	public Component label() {
		return Component.translatable("arise.gear.unique." + name).withStyle(COLOR);
	}

	/** La riga di racconto sotto al nome. Un pezzo unico ha il diritto di dire da dove viene. */
	public Component lore() {
		return Component.translatable("arise.gear.unique." + name + ".lore");
	}

	/**
	 * Il pezzo vero e proprio, costruito adesso dai numeri che la config ha adesso.
	 *
	 * <p>Costruito e non conservato: due Cacciatori che ricevono l'Occhio ricevono due oggetti
	 * distinti, con UUID distinti, perche' il resto del sistema — incastonature, ritiro alla morte,
	 * ricerca nello zaino — riconosce i pezzi dall'UUID.
	 */
	public GearPiece piece(GearConfig config) {
		double power = config.power(rank, base.slot());
		EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);

		for (Map.Entry<Stat, Double> share : shares.entrySet()) {
			Stat stat = share.getKey();
			double value = Math.round(power * share.getValue() * config.step(stat) * 1000.0) / 1000.0;

			if (value != 0.0) {
				stats.put(stat, value);
			}
		}

		return new GearPiece(UUID.randomUUID(), base, rank, GearAffix.of(first(stats)), stats,
				config.sockets(rank), java.util.List.of(), java.util.Optional.of(this));
	}

	/** Il pezzo unico con questo nome, o {@code null}: e' il ponte fra la catena e il registro. */
	public static GearUnique byName(String name) {
		for (GearUnique unique : values()) {
			if (unique.name.equals(name)) {
				return unique;
			}
		}

		return null;
	}

	/** La statistica piu' grossa fa da suffisso, come per i pezzi tirati. */
	private static Stat first(Map<Stat, Double> stats) {
		Stat best = Stat.VITALITY;
		double top = Double.NEGATIVE_INFINITY;

		for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
			double weight = Math.abs(entry.getValue()) * (entry.getKey().percentage() ? 100.0 : 1.0);

			if (weight > top) {
				top = weight;
				best = entry.getKey();
			}
		}

		return best;
	}
}
