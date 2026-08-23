package com.luca.arise.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.luca.arise.gate.GateLayout;
import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Forma e contenuto dei Gate.
 *
 * <p>Le tabelle dei mob stanno in config e non nel codice perché sono la leva più ovvia da
 * toccare: si cambia il carattere di un rango senza ricompilare, e ci si possono mettere i mob di
 * altre mod installate.
 *
 * <p>La geometria sta nel sotto-blocco {@link Shape}, sia perché il codec di un record regge al
 * massimo 16 campi, sia perché separa davvero due cose diverse: com'è fatto un dungeon e cosa ci
 * si trova dentro.
 */
public record GateConfig(
		/** Geometria: quante stanze, quanto grandi, come sono disposte. */
		Shape shape,
		/** Mob per stanza al rango E. */
		int mobsPerRoom,
		/** Mob in più per stanza a ogni rango sopra E. */
		double mobsPerRoomPerRank,
		/** Vita del boss = vita del mob scelto × questo. */
		double bossHealthMultiplier,
		/** Danno del boss = danno del mob scelto × questo. */
		double bossDamageMultiplier,
		/** XP di completamento al rango E. */
		double clearXpBase,
		/** Quanto le ricompense crescono a ogni rango. */
		double clearXpPerRank,
		/** Soul coin di completamento al rango E. */
		double clearSoulsBase,
		/** Per quanti tick un varco resta aperto prima di svanire da solo. */
		int offerLifetimeTicks,
		/** Attesa fra due usi della Bussola dell'Abisso. */
		int compassCooldownTicks,
		/** Chi popola le stanze, per rango. */
		Map<Rank, List<Identifier>> mobs,
		/** Chi può essere il boss, per rango. */
		Map<Rank, List<Identifier>> bosses,
		/** Quando e dove i varchi si aprono da soli. */
		SpawnConfig spawn,
		/** Cosa lascia un Gate completato. */
		LootConfig loot) {

	/** La geometria di un Gate. */
	public record Shape(
			/** Numero minimo di stanze sul percorso principale, boss inclusa. */
			int minRooms,
			/** Numero massimo di stanze sul percorso principale. */
			int maxRooms,
			/** Lato interno di una stanza normale. Dispari, così ha un centro esatto. */
			int roomSize,
			/** Lato interno di una sala ampia e della stanza del boss. */
			int hallSize,
			/** Altezza interna di una stanza normale. */
			int roomHeight,
			/** Altezza interna di una sala ampia. */
			int hallHeight,
			/** Numero minimo di stanze laterali senza uscita. */
			int minBranches,
			/** Numero massimo di stanze laterali. */
			int maxBranches,
			/** Passo della griglia. Deve superare {@code hallSize} di almeno tre blocchi. */
			int cellSize,
			/** Quota del pavimento nella dimensione dei Gate. */
			int floorY,
			/** Distanza fra un'istanza e l'altra. Deve superare l'ingombro massimo di un layout. */
			int regionSpacing) {

		public static final Shape DEFAULT = new Shape(5, 8, 17, 25, 6, 10, 1, 3, 36, 64, 8192);

		public static final Codec<Shape> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("min_rooms").forGetter(Shape::minRooms),
				Codec.INT.fieldOf("max_rooms").forGetter(Shape::maxRooms),
				Codec.INT.fieldOf("room_size").forGetter(Shape::roomSize),
				Codec.INT.fieldOf("hall_size").forGetter(Shape::hallSize),
				Codec.INT.fieldOf("room_height").forGetter(Shape::roomHeight),
				Codec.INT.fieldOf("hall_height").forGetter(Shape::hallHeight),
				Codec.INT.fieldOf("min_branches").forGetter(Shape::minBranches),
				Codec.INT.fieldOf("max_branches").forGetter(Shape::maxBranches),
				Codec.INT.fieldOf("cell_size").forGetter(Shape::cellSize),
				Codec.INT.fieldOf("floor_y").forGetter(Shape::floorY),
				Codec.INT.fieldOf("region_spacing").forGetter(Shape::regionSpacing)
		).apply(instance, Shape::new));
	}

	private static Identifier mc(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static Map<Rank, List<Identifier>> defaultMobs() {
		Map<Rank, List<Identifier>> mobs = new LinkedHashMap<>();
		mobs.put(Rank.E, List.of(mc("zombie"), mc("skeleton")));
		mobs.put(Rank.D, List.of(mc("zombie"), mc("skeleton"), mc("spider")));
		mobs.put(Rank.C, List.of(mc("husk"), mc("stray"), mc("cave_spider"), mc("creeper")));
		mobs.put(Rank.B, List.of(mc("pillager"), mc("witch"), mc("vindicator"), mc("husk")));
		mobs.put(Rank.A, List.of(mc("vindicator"), mc("blaze"), mc("piglin_brute"), mc("witch")));
		mobs.put(Rank.S, List.of(mc("piglin_brute"), mc("blaze"), mc("wither_skeleton"), mc("evoker")));
		return mobs;
	}

	private static Map<Rank, List<Identifier>> defaultBosses() {
		Map<Rank, List<Identifier>> bosses = new LinkedHashMap<>();
		bosses.put(Rank.E, List.of(mc("zombie"), mc("skeleton")));
		bosses.put(Rank.D, List.of(mc("husk"), mc("vindicator")));
		bosses.put(Rank.C, List.of(mc("vindicator"), mc("witch")));
		bosses.put(Rank.B, List.of(mc("evoker"), mc("wither_skeleton")));
		bosses.put(Rank.A, List.of(mc("ravager"), mc("wither_skeleton")));
		bosses.put(Rank.S, List.of(mc("ravager"), mc("iron_golem")));
		return bosses;
	}

	public static final GateConfig DEFAULT = new GateConfig(
			Shape.DEFAULT, 3, 1.5, 6.0, 2.0, 120.0, 1.8, 60.0, 6000, 100,
			defaultMobs(), defaultBosses(), SpawnConfig.DEFAULT, LootConfig.DEFAULT);

	private static final Codec<Map<Rank, List<Identifier>>> MOB_TABLE =
			Codec.unboundedMap(Rank.CODEC, Identifier.CODEC.listOf());

	public static final Codec<GateConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Shape.CODEC.fieldOf("shape").forGetter(GateConfig::shape),
			Codec.INT.fieldOf("mobs_per_room").forGetter(GateConfig::mobsPerRoom),
			Codec.DOUBLE.fieldOf("mobs_per_room_per_rank").forGetter(GateConfig::mobsPerRoomPerRank),
			Codec.DOUBLE.fieldOf("boss_health_multiplier").forGetter(GateConfig::bossHealthMultiplier),
			Codec.DOUBLE.fieldOf("boss_damage_multiplier").forGetter(GateConfig::bossDamageMultiplier),
			Codec.DOUBLE.fieldOf("clear_xp_base").forGetter(GateConfig::clearXpBase),
			Codec.DOUBLE.fieldOf("clear_xp_per_rank").forGetter(GateConfig::clearXpPerRank),
			Codec.DOUBLE.fieldOf("clear_souls_base").forGetter(GateConfig::clearSoulsBase),
			Codec.INT.fieldOf("offer_lifetime_ticks").forGetter(GateConfig::offerLifetimeTicks),
			Codec.INT.fieldOf("compass_cooldown_ticks").forGetter(GateConfig::compassCooldownTicks),
			MOB_TABLE.fieldOf("mobs").forGetter(GateConfig::mobs),
			MOB_TABLE.fieldOf("bosses").forGetter(GateConfig::bosses),
			SpawnConfig.CODEC.fieldOf("spawn").forGetter(GateConfig::spawn),
			LootConfig.CODEC.fieldOf("loot").forGetter(GateConfig::loot)
	).apply(instance, GateConfig::new));

	public GateConfig {
		mobs = Map.copyOf(mobs);
		bosses = Map.copyOf(bosses);
	}

	// Scorciatoie sulla geometria: il resto del codice non deve sapere che è annidata.

	public int minRooms() {
		return shape.minRooms();
	}

	public int maxRooms() {
		return shape.maxRooms();
	}

	public int minBranches() {
		return shape.minBranches();
	}

	public int maxBranches() {
		return shape.maxBranches();
	}

	public int cellSize() {
		return shape.cellSize();
	}

	public int floorY() {
		return shape.floorY();
	}

	public int regionSpacing() {
		return shape.regionSpacing();
	}

	public int roomHeight() {
		return shape.roomHeight();
	}

	/** Metà del lato interno: il centro è a questa distanza da ogni parete. */
	public int halfRoom(GateLayout.Kind kind) {
		return switch (kind) {
			case HALL, BOSS -> shape.hallSize() / 2;
			default -> shape.roomSize() / 2;
		};
	}

	public int heightOf(GateLayout.Kind kind) {
		return switch (kind) {
			case HALL, BOSS -> shape.hallHeight();
			default -> shape.roomHeight();
		};
	}

	public List<Identifier> mobsFor(Rank rank) {
		List<Identifier> list = mobs.get(rank);
		return list == null || list.isEmpty() ? DEFAULT.mobs().get(Rank.E) : list;
	}

	public List<Identifier> bossesFor(Rank rank) {
		List<Identifier> list = bosses.get(rank);
		return list == null || list.isEmpty() ? DEFAULT.bosses().get(Rank.E) : list;
	}

	public int mobsPerRoom(Rank rank) {
		return (int) Math.round(mobsPerRoom + mobsPerRoomPerRank * rank.ordinal());
	}

	public long clearXp(Rank rank) {
		return (long) (clearXpBase * Math.pow(clearXpPerRank, rank.ordinal()));
	}

	public long clearSouls(Rank rank) {
		return (long) (clearSoulsBase * Math.pow(clearXpPerRank, rank.ordinal()));
	}
}
