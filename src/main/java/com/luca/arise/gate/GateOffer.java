package com.luca.arise.gate;

import java.util.List;

import com.luca.arise.config.GateConfig;
import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Il preventivo di un Gate: tutto ciò che si può sapere <em>prima</em> di entrarci.
 *
 * <p>Il punto delicato è che queste informazioni devono essere <strong>vere</strong>. Un pannello
 * che annuncia sette stanze e poi ne trova quattro è peggio che non avere nessun pannello: la
 * decisione di entrare o no si baserebbe su niente.
 *
 * <p>La garanzia arriva dal {@code seed}. La pianta viene generata qui, alla comparsa del varco,
 * per contare stanze e sale — e viene <em>ricostruita</em> all'ingresso a partire dallo stesso
 * seme, quindi è la stessa pianta. Non si costruisce un solo blocco finché non si entra davvero:
 * il varco che nessuno attraversa non è costato nulla al mondo.
 *
 * @param rank    il rango, che decide difficoltà e ricompense
 * @param seed    il seme della pianta: è questo a rendere il preventivo un impegno
 * @param theme   di che cosa è fatto
 * @param rooms   stanze totali, ingresso e stanza del boss comprese
 * @param halls   quante di quelle stanze sono sale ampie
 * @param branches quante sono diramazioni senza uscita
 * @param mobs    chi può popolarlo
 * @param boss    chi lo custodisce — deciso ora, non all'ingresso
 * @param xp      XP di completamento
 * @param souls   soul coin di completamento
 * @param objective perche' ci si entra: e' cio' che decide come lo si percorre
 */
public record GateOffer(Rank rank, long seed, GateTheme theme, int rooms, int halls, int branches,
		List<Identifier> mobs, Identifier boss, long xp, long souls, GateObjective objective) {

	public static final Codec<GateOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Rank.CODEC.fieldOf("rank").forGetter(GateOffer::rank),
			Codec.LONG.fieldOf("seed").forGetter(GateOffer::seed),
			GateTheme.CODEC.fieldOf("theme").forGetter(GateOffer::theme),
			Codec.INT.fieldOf("rooms").forGetter(GateOffer::rooms),
			Codec.INT.fieldOf("halls").forGetter(GateOffer::halls),
			Codec.INT.fieldOf("branches").forGetter(GateOffer::branches),
			Identifier.CODEC.listOf().fieldOf("mobs").forGetter(GateOffer::mobs),
			Identifier.CODEC.fieldOf("boss").forGetter(GateOffer::boss),
			Codec.LONG.fieldOf("xp").forGetter(GateOffer::xp),
			Codec.LONG.fieldOf("souls").forGetter(GateOffer::souls),
			// Opzionale: i varchi gia' aperti quando gli obiettivi non esistevano si rileggono
			// interi, e chiedono la cosa che hanno sempre chiesto.
			GateObjective.CODEC.optionalFieldOf("objective", GateObjective.SOVEREIGN)
					.forGetter(GateOffer::objective)
	).apply(instance, GateOffer::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, GateOffer> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public GateOffer {
		mobs = List.copyOf(mobs);
	}

	/**
	 * Tira un Gate: genera la pianta per contarla, sceglie tema e boss, calcola le ricompense.
	 *
	 * <p>L'ordine dei sorteggi conta. La pianta pesca per prima, così ricostruirla altrove con lo
	 * stesso seme dà lo stesso risultato senza dover replicare anche il resto.
	 */
	public static GateOffer roll(GateConfig config, Rank rank, long seed) {
		RandomSource random = RandomSource.create(seed);
		GateLayout layout = GateLayout.generate(config, random);

		int halls = 0;
		int branches = 0;
		for (GateLayout.Kind kind : layout.rooms().values()) {
			if (kind == GateLayout.Kind.HALL) {
				halls++;
			} else if (kind == GateLayout.Kind.SIDE) {
				branches++;
			}
		}

		GateTheme[] themes = GateTheme.values();
		GateTheme theme = themes[random.nextInt(themes.length)];

		List<Identifier> bosses = config.bossesFor(rank);
		Identifier boss = bosses.get(random.nextInt(bosses.size()));

		return new GateOffer(rank, seed, theme, layout.rooms().size(), halls, branches,
				config.mobsFor(rank), boss, config.clearXp(rank), config.clearSouls(rank),
				GateObjective.roll(random, rank));
	}

	/** La pianta promessa da questo preventivo. Ricostruirla è deterministico: è il punto. */
	public GateLayout layout(GateConfig config) {
		return GateLayout.generate(config, RandomSource.create(seed));
	}

	/**
	 * Quante creature abitano questo varco, contate come le pianta il generatore.
	 *
	 * <p>Serve al bersaglio della Raccolta d'Essenza, e va calcolato <em>qui</em> e non dentro il
	 * Gate: il pannello di analisi deve poterlo dire prima di entrare, e per farlo ha solo il
	 * preventivo. La formula e' la stessa di {@code GateManager.populate} — se le due divergessero,
	 * il pannello prometterebbe un bersaglio che il varco non puo' soddisfare.
	 */
	public int inhabitants(GateConfig config) {
		int perRoom = config.mobsPerRoom(rank);
		int total = 0;

		for (GateLayout.Kind kind : layout(config).rooms().values()) {
			total += switch (kind) {
				case ENTRANCE, BOSS -> 0;
				case HALL -> perRoom + 2;
				case SIDE -> Math.max(1, perRoom - 1);
				default -> perRoom;
			};
		}

		return total;
	}

	/** Stanze del percorso principale: il totale meno le diramazioni. */
	public int mainRooms() {
		return rooms - branches;
	}
}
