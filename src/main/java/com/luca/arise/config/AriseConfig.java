package com.luca.arise.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luca.arise.AriseMod;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Tutti i numeri di bilanciamento, in un posto solo e modificabili senza ricompilare.
 *
 * <p>File: {@code config/arise.json}. Se manca viene scritto con i valori di default; se e'
 * illeggibile si usano i default e si logga l'errore, senza mai impedire l'avvio.
 */
public record AriseConfig(
		/** XP richiesta per passare dal livello 1 al 2; le successive scalano con l'esponente. */
		double xpBase,
		/** Esponente della curva: XP(n) = xpBase * n^xpExponent. */
		double xpExponent,
		/** Punti statistica guadagnati a ogni livello. */
		int pointsPerLevel,
		/** Livello massimo raggiungibile. */
		int maxLevel,
		/** XP fissa per ogni nemico ucciso. */
		double xpFlatPerKill,
		/** XP aggiuntiva per ogni punto di vita massima del nemico ucciso. */
		double xpPerMobMaxHealth,
		/** Quanto vale un punto speso, per statistica. */
		Map<Stat, Double> perPoint,
		/** Tetto di punti spendibili, per statistica. */
		Map<Stat, Integer> cap,
		/** Soul coin fissi per ogni nemico ucciso. */
		double soulsFlatPerKill,
		/** Soul coin aggiuntivi per ogni punto di vita massima del nemico. */
		double soulsPerMobMaxHealth,
		/** Bilanciamento dell'esercito d'ombra. */
		ShadowConfig shadows,
		/** Forma e contenuto dei Gate. */
		GateConfig gates,
		/** Tempi di ricarica e portata delle abilità. */
		AbilityConfig abilities,
		/** Aspetto delle ombre ed effetti: le uniche voci che valgono sul client. */
		FxConfig fx,
		/** Dove stanno le città e quanto in fretta si costruiscono. */
		CityConfig cities,
		/** Il rango del Cacciatore e il suo equipaggiamento. */
		HunterConfig hunter) {

	public static final Codec<AriseConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("xp_base").forGetter(AriseConfig::xpBase),
			Codec.DOUBLE.fieldOf("xp_exponent").forGetter(AriseConfig::xpExponent),
			Codec.INT.fieldOf("points_per_level").forGetter(AriseConfig::pointsPerLevel),
			Codec.INT.fieldOf("max_level").forGetter(AriseConfig::maxLevel),
			Codec.DOUBLE.fieldOf("xp_flat_per_kill").forGetter(AriseConfig::xpFlatPerKill),
			Codec.DOUBLE.fieldOf("xp_per_mob_max_health").forGetter(AriseConfig::xpPerMobMaxHealth),
			Codec.unboundedMap(Stat.CODEC, Codec.DOUBLE).fieldOf("per_point").forGetter(AriseConfig::perPoint),
			Codec.unboundedMap(Stat.CODEC, Codec.INT).fieldOf("cap").forGetter(AriseConfig::cap),
			Codec.DOUBLE.fieldOf("souls_flat_per_kill").forGetter(AriseConfig::soulsFlatPerKill),
			Codec.DOUBLE.fieldOf("souls_per_mob_max_health").forGetter(AriseConfig::soulsPerMobMaxHealth),
			ShadowConfig.CODEC.fieldOf("shadows").forGetter(AriseConfig::shadows),
			GateConfig.CODEC.fieldOf("gates").forGetter(AriseConfig::gates),
			AbilityConfig.CODEC.fieldOf("abilities").forGetter(AriseConfig::abilities),
			FxConfig.CODEC.fieldOf("fx").forGetter(AriseConfig::fx),
			CityConfig.CODEC.fieldOf("cities").forGetter(AriseConfig::cities),
			HunterConfig.CODEC.fieldOf("hunter").forGetter(AriseConfig::hunter)
	).apply(instance, AriseConfig::new));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arise.json");

	private static AriseConfig current = createDefault();

	public AriseConfig {
		perPoint = Map.copyOf(perPoint);
		cap = Map.copyOf(cap);
	}

	public static AriseConfig get() {
		return current;
	}

	public static AriseConfig createDefault() {
		Map<Stat, Double> perPoint = new EnumMap<>(Stat.class);
		perPoint.put(Stat.VITALITY, 2.0);    // +1 cuore ogni punto
		perPoint.put(Stat.AGILITY, 0.015);   // +1,5% di velocita' base
		perPoint.put(Stat.STRENGTH, 0.4);    // +0,4 danno
		perPoint.put(Stat.ENDURANCE, 0.5);   // +0,5 armatura

		Map<Stat, Integer> cap = new EnumMap<>(Stat.class);
		cap.put(Stat.VITALITY, 400);
		// Tetto stretto e voluto: oltre ~3x la velocita' base il client sfonda le collisioni e il
		// server rimbalza indietro il giocatore. 100 punti = +150%, cioe' 2,5x. Vedi DESIGN §3.2.
		cap.put(Stat.AGILITY, 100);
		cap.put(Stat.STRENGTH, 400);
		cap.put(Stat.ENDURANCE, 200);

		return new AriseConfig(20.0, 1.6, 3, 100, 2.0, 1.0, perPoint, cap, 1.0, 0.25,
				ShadowConfig.DEFAULT, GateConfig.DEFAULT, AbilityConfig.DEFAULT, FxConfig.DEFAULT,
				CityConfig.DEFAULT, HunterConfig.DEFAULT);
	}

	/** L'equipaggiamento, scorciatoia per la voce annidata. */
	public GearConfig gear() {
		return hunter.gear();
	}

	/** L'Abyss Shop, scorciatoia per la voce annidata. */
	public ShopConfig shop() {
		return hunter.shop();
	}

	/** Le gemme, scorciatoia per la voce annidata. */
	public GemConfig gems() {
		return hunter.gems();
	}

	/** L'Officina delle Anime, scorciatoia per la voce annidata. */
	public WorkshopConfig workshop() {
		return hunter.workshop();
	}

	/** Il rango di un Cacciatore di questo livello. */
	public Rank hunterRank(int level) {
		return hunter.rank(level);
	}

	public double perPoint(Stat stat) {
		return perPoint.getOrDefault(stat, 0.0);
	}

	public int cap(Stat stat) {
		return cap.getOrDefault(stat, Integer.MAX_VALUE);
	}

	/** Soul coin che vale l'uccisione di una creatura con questa vita massima. */
	public long soulsFor(double maxHealth) {
		return Math.max(1L, (long) (soulsFlatPerKill + maxHealth * soulsPerMobMaxHealth));
	}

	/** XP totale necessaria per passare da {@code level} al livello successivo. */
	public long xpForNextLevel(int level) {
		return Math.max(1L, (long) (xpBase * Math.pow(level, xpExponent)));
	}

	/** Carica la config da disco, scrivendo il file di default se non esiste. */
	public static void load() {
		try {
			if (Files.notExists(FILE)) {
				current = createDefault();
				save();
				AriseMod.LOGGER.info("Config non trovata: creata con i valori di default in {}", FILE);
				return;
			}

			JsonElement json = withDefaults(GSON.fromJson(Files.readString(FILE), JsonElement.class));
			current = CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> AriseMod.LOGGER.error("Config non valida: {}", error))
					.orElseGet(AriseConfig::createDefault);

			// Riscrittura dopo la lettura: i campi aggiunti da una versione successiva della mod
			// compaiono nel file coi loro default, invece di restare invisibili e non modificabili.
			// I valori gia' presenti sopravvivono perche' sono appena stati letti nel record.
			save();
		} catch (IOException | RuntimeException e) {
			AriseMod.LOGGER.error("Impossibile leggere la config, uso i valori di default", e);
			current = createDefault();
		}
	}

	/**
	 * Completa il JSON letto dal disco con i default per ogni chiave mancante.
	 *
	 * <p>È questo passaggio, e non {@code optionalFieldOf}, a rendere la config tollerante ai file
	 * vecchi. La differenza conta: un campo opzionale col default viene <em>omesso in scrittura</em>
	 * quando coincide col default, quindi resterebbe per sempre invisibile nel file e nessuno
	 * potrebbe modificarlo. Riempiendo prima del parsing, invece, i campi sono obbligatori nel codec
	 * e finiscono sempre nel file scritto.
	 */
	private static JsonElement withDefaults(JsonElement user) {
		return CODEC.encodeStart(JsonOps.INSTANCE, createDefault())
				.result()
				.map(defaults -> merge(defaults, user))
				.orElse(user);
	}

	/** Fusione ricorsiva: i valori dell'utente vincono, le chiavi che non ha vengono aggiunte. */
	private static JsonElement merge(JsonElement defaults, JsonElement user) {
		if (!(defaults instanceof JsonObject defaultObject) || !(user instanceof JsonObject userObject)) {
			return user == null || user.isJsonNull() ? defaults : user;
		}

		JsonObject result = defaultObject.deepCopy();
		for (Map.Entry<String, JsonElement> entry : userObject.entrySet()) {
			JsonElement fallback = result.get(entry.getKey());
			result.add(entry.getKey(), fallback == null
					? entry.getValue()
					: merge(fallback, entry.getValue()));
		}

		return result;
	}

	private static void save() {
		CODEC.encodeStart(JsonOps.INSTANCE, current)
				.resultOrPartial(error -> AriseMod.LOGGER.error("Impossibile serializzare la config: {}", error))
				.ifPresent(json -> {
					try {
						Files.createDirectories(FILE.getParent());
						Files.writeString(FILE, GSON.toJson(json));
					} catch (IOException e) {
						AriseMod.LOGGER.error("Impossibile scrivere la config", e);
					}
				});
	}
}
