package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Un pezzo di equipaggiamento: un record, non un {@code ItemStack}.
 *
 * <p>Vedi design §8.1 per il perche'. In due righe: venti slot per sei ranghi per le varianti
 * fanno centinaia di texture da disegnare a mano, e quelle immagini non si possono generare. Come
 * per le ombre a riposo (§3.5), il pezzo posseduto e' un dato; a disegnarlo ci pensa la nostra
 * schermata.
 *
 * <p>Le statistiche sono <em>congelate</em> al momento dell'estrazione invece di essere ricavate
 * da base e rango a ogni lettura. E' la scelta opposta a quella fatta per le ombre, e di
 * proposito: un'ombra deve poter essere riscalata cambiando la config, un pezzo di bottino no —
 * "l'anello che ho trovato ieri" deve restare quello che era ieri.
 *
 * @param sockets incastonature disponibili; le gemme arrivano in B4, il numero si fissa qui
 */
public record GearPiece(UUID id, GearBase base, Rank rank, GearAffix affix,
		Map<Stat, Double> stats, int sockets) {

	public static final Codec<GearPiece> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(GearPiece::id),
			GearBase.CODEC.fieldOf("base").forGetter(GearPiece::base),
			Rank.CODEC.fieldOf("rank").forGetter(GearPiece::rank),
			GearAffix.CODEC.fieldOf("affix").forGetter(GearPiece::affix),
			Codec.unboundedMap(Stat.CODEC, Codec.DOUBLE).fieldOf("stats").forGetter(GearPiece::stats),
			Codec.INT.optionalFieldOf("sockets", 0).forGetter(GearPiece::sockets)
	).apply(instance, GearPiece::new));

	public GearPiece {
		// EnumMap e non Map.copyOf: l'ordine di scorrimento diventa quello di dichiarazione delle
		// statistiche, quindi due pezzi uguali si leggono uguali invece di elencare le righe in
		// ordine sparso a ogni riavvio.
		EnumMap<Stat, Double> ordered = new EnumMap<>(Stat.class);
		ordered.putAll(stats);
		stats = Collections.unmodifiableMap(ordered);
	}

	public GearSlot slot() {
		return base.slot();
	}

	/** "Diadema del Corvo". La composizione passa dalla traduzione, non da una concatenazione. */
	public Component displayName() {
		return Component.translatable("arise.gear.name", base.label(), affix.label())
				.withStyle(rank.chatColor());
	}

	/** Le righe dei modificatori, una per statistica, gia' formattate e tradotte. */
	public List<Component> statLines() {
		List<Component> lines = new ArrayList<>(stats.size());

		for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
			lines.add(Component.translatable("arise.gear.stat_line",
					entry.getKey().format(entry.getValue()),
					Component.translatable(entry.getKey().translationKey())));
		}

		return lines;
	}

	/** Le stesse righe su una riga sola, per la chat. */
	public Component statSummary() {
		MutableComponent result = Component.empty();
		boolean first = true;

		for (Component line : statLines()) {
			if (!first) {
				result.append(", ");
			}
			result.append(line);
			first = false;
		}

		return result;
	}

	/** Somma dei valori assoluti: serve solo a ordinare lo zaino dal pezzo piu' forte in giu'. */
	public double weight() {
		double sum = 0.0;
		for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
			// Le percentuali sono numeri piccolissimi accanto ai punti vita: senza riscalarle,
			// ordinare per potenza metterebbe sempre in fondo i pezzi di agilita'.
			sum += Math.abs(entry.getValue()) * (entry.getKey().percentage() ? 100.0 : 1.0);
		}
		return sum;
	}
}
