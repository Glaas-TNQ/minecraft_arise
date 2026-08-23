package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.luca.arise.gem.Gem;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Un pezzo di equipaggiamento: il dato che viaggia <em>dentro</em> un {@code ItemStack}.
 *
 * <p>Il design §8.1 diceva l'opposto — l'equipaggiamento e' un dato e basta, disegnato da una
 * schermata nostra — e la sua premessa era che "oggetto" volesse dire "texture da disegnare a
 * mano". Non e' vero: il corpo dell'oggetto lo prestano gli item vanilla (vedi {@link GearItems}),
 * e il pezzo resta esattamente il record che era. Si guadagna tutto quello che un oggetto sa gia'
 * fare e che una lista non sapra' mai — cadere per terra, stare in un baule, finire nella mano
 * destra, comparire in un tooltip.
 *
 * <p>Le statistiche sono <em>congelate</em> al momento dell'estrazione invece di essere ricavate
 * da base e rango a ogni lettura. E' la scelta opposta a quella fatta per le ombre, e di
 * proposito: un'ombra deve poter essere riscalata cambiando la config, un pezzo di bottino no —
 * "l'anello che ho trovato ieri" deve restare quello che era ieri.
 *
 * @param sockets quante gemme il pezzo puo' reggere; lo decide il rango e non cambia mai piu'
 * @param gems    le gemme incastonate, al piu' {@code sockets}
 * @param unique  il pezzo unico da cui viene, se non e' stato tirato a caso (vedi {@link GearUnique})
 */
public record GearPiece(UUID id, GearBase base, Rank rank, GearAffix affix,
		Map<Stat, Double> stats, int sockets, List<Gem> gems,
		Optional<GearUnique> unique) implements TooltipProvider {

	public static final Codec<GearPiece> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(GearPiece::id),
			GearBase.CODEC.fieldOf("base").forGetter(GearPiece::base),
			Rank.CODEC.fieldOf("rank").forGetter(GearPiece::rank),
			GearAffix.CODEC.fieldOf("affix").forGetter(GearPiece::affix),
			Codec.unboundedMap(Stat.CODEC, Codec.DOUBLE).fieldOf("stats").forGetter(GearPiece::stats),
			Codec.INT.optionalFieldOf("sockets", 0).forGetter(GearPiece::sockets),
			// Opzionale: i pezzi tirati prima che le gemme esistessero si caricano senza migrazioni.
			Gem.CODEC.listOf().optionalFieldOf("gems", List.of()).forGetter(GearPiece::gems),
			// Opzionale come le gemme, e per lo stesso motivo: i pezzi salvati prima che i pezzi
			// unici esistessero si rileggono senza migrazioni.
			GearUnique.CODEC.optionalFieldOf("unique").forGetter(GearPiece::unique)
	).apply(instance, GearPiece::new));

	/** Per il componente dell'oggetto: niente registri di mezzo, solo enum, numeri e UUID. */
	public static final StreamCodec<ByteBuf, GearPiece> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Un pezzo appena tirato: incastonature vuote. */
	public static GearPiece of(UUID id, GearBase base, Rank rank, GearAffix affix,
			Map<Stat, Double> stats, int sockets) {
		return new GearPiece(id, base, rank, affix, stats, sockets, List.of(), Optional.empty());
	}

	public GearPiece {
		// EnumMap e non Map.copyOf: l'ordine di scorrimento diventa quello di dichiarazione delle
		// statistiche, quindi due pezzi uguali si leggono uguali invece di elencare le righe in
		// ordine sparso a ogni riavvio.
		EnumMap<Stat, Double> ordered = new EnumMap<>(Stat.class);
		ordered.putAll(stats);
		stats = Collections.unmodifiableMap(ordered);
		gems = List.copyOf(gems);
	}

	public int freeSockets() {
		return Math.max(0, sockets - gems.size());
	}

	public GearPiece withGem(Gem gem) {
		List<Gem> updated = new ArrayList<>(gems);
		updated.add(gem);
		return new GearPiece(id, base, rank, affix, stats, sockets, updated, unique);
	}

	public GearPiece withoutGem(UUID gemId) {
		List<Gem> updated = new ArrayList<>(gems);
		updated.removeIf(gem -> gem.id().equals(gemId));
		return new GearPiece(id, base, rank, affix, stats, sockets, updated, unique);
	}

	public GearSlot slot() {
		return base.slot();
	}

	/**
	 * "Diadema del Corvo". La composizione passa dalla traduzione, non da una concatenazione.
	 *
	 * <p>Un pezzo unico ha un nome proprio e se lo tiene: il colore lo distingue dal rango, perche'
	 * un Occhio dell'Oscurita' di rango E non e' un pezzo di rango E qualunque e non deve leggersi
	 * come tale.
	 */
	public Component displayName() {
		return unique
				.<Component>map(GearUnique::label)
				.orElseGet(() -> Component.translatable("arise.gear.name", base.label(), affix.label())
						.withStyle(rank.chatColor()));
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

	/**
	 * Le righe che il gioco disegna sotto il nome dell'oggetto.
	 *
	 * <p>Le scrive il componente, non un {@code lore} riempito al momento del tiro: cosi' la
	 * descrizione si traduce nella lingua di chi guarda e resta d'accordo con il dato anche dopo
	 * che una gemma e' stata incastonata.
	 *
	 * <p>Niente config qui dentro: il tooltip si disegna sul client, che su un server dedicato ha
	 * la <em>sua</em> copia dei numeri di bilanciamento. Tutto quello che si legge e' congelato nel
	 * pezzo e nelle sue gemme.
	 */
	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag,
			DataComponentGetter components) {
		out.accept(Component.translatable("arise.gear.tooltip.rank", rank.label())
				.withStyle(ChatFormatting.DARK_GRAY));

		unique.ifPresent(value -> out.accept(value.lore()
				.copy().withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)));

		statLines().forEach(out);

		for (Gem gem : gems) {
			out.accept(Component.translatable("arise.gear.tooltip.gem",
					gem.displayName(), gemStats(gem)));
		}

		int free = freeSockets();
		if (free > 0) {
			out.accept(Component.translatable("arise.gear.tooltip.sockets", free)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Le statistiche di una gemma su una riga sola, per il tooltip del pezzo che la porta. */
	private static Component gemStats(Gem gem) {
		MutableComponent result = Component.empty();
		boolean first = true;

		for (Map.Entry<Stat, Double> entry : gem.stats().entrySet()) {
			if (!first) {
				result.append(", ");
			}
			result.append(entry.getKey().format(entry.getValue()));
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
