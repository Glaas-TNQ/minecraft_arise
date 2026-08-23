package com.luca.arise.shadow;

import java.util.Optional;
import java.util.UUID;

import com.luca.arise.progress.Rank;
import com.luca.arise.config.ShadowConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Un'ombra <em>a riposo</em>: dati puri, nessuna entità nel mondo.
 *
 * <p>È il cuore dell'architettura descritta nel design §3.5. Un esercito di cinquanta ombre
 * "parcheggiate" come entità vere sarebbe un disastro di prestazioni e un pozzo di bug (chunk
 * scaricati, despawn, morti fuori schermo). Qui l'esercito è una lista di record nell'attachment
 * del giocatore, e diventa entità solo al momento dell'evocazione.
 *
 * <p>{@code baseMaxHealth} e {@code baseAttackDamage} sono i valori dell'ombra appena estratta:
 * non cambiano mai. Le statistiche effettive si ricavano applicando la crescita del livello, così
 * ritoccare il bilanciamento in config riscala l'intero esercito senza migrazioni.
 */
public record ShadowData(UUID id, Identifier sourceType, ShadowArchetype archetype, int level, long xp,
		double baseMaxHealth, double baseAttackDamage, Optional<String> customName, int color,
		Optional<NamedShadow> named) {

	/** Colore predefinito: il nero-blu della texture, cioè "nessuna tinta". */
	public static final int DEFAULT_COLOR = 0xFFFFFF;

	/**
	 * L'ombra qualunque: nove argomenti, come e' sempre stato.
	 *
	 * <p>Le ombre nominate sono sette in tutto e nascono da un punto solo — {@link NamedShadow#create()}
	 * — mentre le altre nascono da un cadavere, da un'anima arruolata o da una prova. Obbligare
	 * dodici punti di costruzione a scrivere {@code Optional.empty()} per un campo che riguarda
	 * sette casi su tutti sarebbe rumore, e il rumore si legge male.
	 */
	public ShadowData(UUID id, Identifier sourceType, ShadowArchetype archetype, int level, long xp,
			double baseMaxHealth, double baseAttackDamage, Optional<String> customName, int color) {
		this(id, sourceType, archetype, level, xp, baseMaxHealth, baseAttackDamage, customName, color,
				Optional.empty());
	}

	public static final Codec<ShadowData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(ShadowData::id),
			Identifier.CODEC.fieldOf("source_type").forGetter(ShadowData::sourceType),
			// Opzionale: le ombre estratte prima che gli archetipi esistessero diventano Guardie,
			// che e' il comportamento che avevano gia' — mischia che segue e picchia.
			ShadowArchetype.CODEC.optionalFieldOf("archetype", ShadowArchetype.GUARD)
					.forGetter(ShadowData::archetype),
			Codec.INT.fieldOf("level").forGetter(ShadowData::level),
			// Opzionale: le ombre estratte prima che il livellamento esistesse ripartono da zero
			// invece di rendere illeggibile l'intero esercito salvato.
			Codec.LONG.optionalFieldOf("xp", 0L).forGetter(ShadowData::xp),
			Codec.DOUBLE.fieldOf("max_health").forGetter(ShadowData::baseMaxHealth),
			Codec.DOUBLE.fieldOf("attack_damage").forGetter(ShadowData::baseAttackDamage),
			// Opzionali: le ombre estratte prima di questi campi si caricano senza migrazioni.
			Codec.STRING.optionalFieldOf("custom_name").forGetter(ShadowData::customName),
			Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(ShadowData::color),
			// Opzionale come tutti gli altri campi arrivati dopo: un esercito salvato prima che le
			// ombre nominate esistessero si rilegge intero, e nessuna delle sue ombre e' nominata.
			NamedShadow.CODEC.optionalFieldOf("named").forGetter(ShadowData::named)
	).apply(instance, ShadowData::new));

	/**
	 * Nome mostrato al giocatore: "Ombra di <mob>".
	 *
	 * <p>Risolto al momento della visualizzazione e non salvato, così un'ombra estratta da un mob
	 * di un'altra mod resta corretta anche se quella mod cambia le sue traduzioni.
	 */
	public Component displayName() {
		// Un'ombra nominata porta il suo nome e nessun altro, anche se il grado consentirebbe di
		// ribattezzarla: Igris si chiama Igris. E' l'unico posto in cui il nome non e' una scelta.
		if (named.isPresent()) {
			return named.get().label();
		}

		return customName
				.filter(name -> !name.isBlank())
				.<Component>map(Component::literal)
				.orElseGet(this::defaultName);
	}

	private Component defaultName() {
		Component source = BuiltInRegistries.ENTITY_TYPE.getOptional(sourceType)
				.map(EntityType::getDescription)
				.orElseGet(() -> Component.literal(sourceType.getPath()));

		return Component.translatable("arise.shadow.name", source);
	}

	public ShadowData withName(String name) {
		String trimmed = name == null ? "" : name.trim();
		return new ShadowData(id, sourceType, archetype, level, xp, baseMaxHealth, baseAttackDamage,
				trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed), color, named);
	}

	public ShadowData withColor(int newColor) {
		return new ShadowData(id, sourceType, archetype, level, xp, baseMaxHealth, baseAttackDamage,
				customName, newColor, named);
	}

	/** Cambia archetipo. Serve solo ai comandi di prova: in gioco lo decide il cadavere. */
	public ShadowData withArchetype(ShadowArchetype newArchetype) {
		return new ShadowData(id, sourceType, newArchetype, level, xp, baseMaxHealth, baseAttackDamage,
				customName, color, named);
	}

	/** Sale di un livello senza passare dall'esperienza: è quello che si compra coi soul coin. */
	public ShadowData withLevelUp() {
		return new ShadowData(id, sourceType, archetype, level + 1, 0L, baseMaxHealth, baseAttackDamage,
				customName, color, named);
	}

	/**
	 * Quanto il danno pesa piu' della vita, quando si misura un'ombra.
	 *
	 * <p>Quattro, e non e' un numero tirato a caso: e' quante volte un punto di danno vale un punto
	 * di vita in uno scontro fra due entita' che si picchiano. Sta qui come costante perche' la
	 * stessa formula serve in tre posti — il rango dell'ombra, il suo grado, e l'anteprima del
	 * cadavere prima di estrarlo — e se i tre divergessero l'anteprima direbbe una bugia.
	 */
	public static final double DAMAGE_WEIGHT = 4.0;

	/** Quanto "pesa" questa ombra. Il danno conta più della vita perché decide gli scontri. */
	public double powerScore() {
		return baseMaxHealth + baseAttackDamage * DAMAGE_WEIGHT;
	}

	public Rank rank(ShadowConfig config) {
		return Rank.fromScore(powerScore(), config.rankThresholds());
	}

	public double maxHealth(ShadowConfig config) {
		return baseMaxHealth * config.leveling().statMultiplier(level)
				* archetype.tuning(config.legion()).health();
	}

	public double attackDamage(ShadowConfig config) {
		return baseAttackDamage * config.leveling().statMultiplier(level)
				* archetype.tuning(config.legion()).damage();
	}

	/**
	 * Quanto vale <em>adesso</em>: base, livello e archetipo tutti insieme.
	 *
	 * <p>Distinta da {@link #powerScore()}, che guarda solo il mob d'origine. Il rango risponde
	 * “da cosa viene” e non cambia mai; il grado risponde “quanto vale” e sale
	 * combattendo. Due domande diverse, due numeri diversi, entrambi ricavati e nessuno salvato.
	 */
	public double effectivePower(ShadowConfig config) {
		return maxHealth(config) + attackDamage(config) * DAMAGE_WEIGHT;
	}

	/** Il grado nella scala del manhwa, da Normale a Gran Maresciallo. */
	public ShadowGrade grade(ShadowConfig config) {
		return ShadowGrade.fromPower(effectivePower(config), config.legion().gradeThresholds());
	}

	/**
	 * Il moltiplicatore che quest'ombra regala alle <em>altre</em> in campo, se comanda.
	 *
	 * <p>Zero sotto il grado di Comandante. Non si applica a se stessa: un Gran Maresciallo che si
	 * potenzia da solo renderebbe la squadra un posto solo con dentro il più forte.
	 */
	public double auraDamage(ShadowConfig config) {
		return grade(config).commandSteps() * config.legion().commanderDamageBonus();
	}

	public double auraHealth(ShadowConfig config) {
		return grade(config).commandSteps() * config.legion().commanderHealthBonus();
	}

	public long xpForNextLevel(ShadowConfig config) {
		return config.leveling().xpForNextLevel(level);
	}

	public boolean isMaxLevel(ShadowConfig config) {
		return level >= config.leveling().maxLevel();
	}

	/**
	 * Accredita XP e fa salire di livello quante volte serve.
	 *
	 * @return la stessa istanza se non è cambiato nulla, altrimenti quella aggiornata
	 */
	public ShadowData withXpGained(long amount, ShadowConfig config) {
		if (amount <= 0 || isMaxLevel(config)) {
			return this;
		}

		long newXp = xp + amount;
		int newLevel = level;
		long needed = config.leveling().xpForNextLevel(newLevel);

		while (newXp >= needed && newLevel < config.leveling().maxLevel()) {
			newXp -= needed;
			newLevel++;
			needed = config.leveling().xpForNextLevel(newLevel);
		}

		if (newLevel >= config.leveling().maxLevel()) {
			newXp = 0;
		}

		return new ShadowData(id, sourceType, archetype, newLevel, newXp, baseMaxHealth, baseAttackDamage,
				customName, color, named);
	}
}
