package com.luca.arise.shadow;

import com.luca.arise.config.ShadowConfig;
import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * Come combatte un'ombra. Quattro modi, decisi da <em>chi era</em> prima di morire.
 *
 * <p>Fino a questo blocco le ombre erano tutte la stessa cosa: un mischia che segue e picchia. Un
 * esercito di venti era venti volte lo stesso soldato, e sceglierne quattro da portare fuori non
 * voleva dire niente perche' erano intercambiabili. L'archetipo e' la risposta: quattro verbi
 * diversi — <em>tenere la linea</em>, <em>inseguire</em>, <em>colpire da lontano</em>,
 * <em>incassare</em> — e una squadra che ha senso comporre.
 *
 * <p>Nel manhwa questa distinzione c'e' da sempre: Igris comanda i cavalieri, Tusk i maghi, Iron e
 * Tank sono bestie, Beru e' una categoria a se'. Qui sono quattro perche' quattro sono le ombre che
 * si possono avere fuori insieme.
 *
 * <p>L'archetipo si decide <strong>una volta sola</strong>, al momento dell'estrazione, e viene
 * salvato con l'ombra. Ricavarlo ogni volta dal tipo di mob non si puo': dal solo
 * {@link net.minecraft.world.entity.EntityType} non si risale ne' alla vita ne' alla forma
 * dell'entita' senza costruirne una, e costruire un'entita' per disegnare una riga di lista sarebbe
 * assurdo. Al momento dell'estrazione invece il cadavere e' li', e sa tutto di se'.
 */
public enum ShadowArchetype implements StringRepresentable {

	/**
	 * La Guardia: mischia equilibrata, e l'unico che si mette <em>in mezzo</em>. Quando qualcosa
	 * prende di mira il Monarca, la guardia va a piazzarsi fra i due.
	 */
	GUARD("guard", 0xFFE8F2FF),

	/** La Bestia: veloce, mordace, fragile. Insegue lontano e salta addosso. */
	BEAST("beast", 0xFFE8A34F),

	/** Il Mago: poca vita, ma colpisce da sedici blocchi e si tiene alla larga dalla mischia. */
	MAGE("mage", 0xFFC77FE8),

	/** Il Colosso: lento e pieno di vita. Provoca: si prende addosso chi puntava il Monarca. */
	TANK("tank", 0xFF7FD97F);

	public static final Codec<ShadowArchetype> CODEC = StringRepresentable.fromEnum(ShadowArchetype::values);

	public static final StreamCodec<ByteBuf, ShadowArchetype> STREAM_CODEC =
			ByteBufCodecs.fromCodec(CODEC);

	private final String name;
	private final int color;

	ShadowArchetype(String name, int color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Colore ARGB del glifo nella schermata. */
	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.archetype." + name);
	}

	/** Una riga che spiega cosa fa in combattimento. Sta nel dettaglio dell'ombra. */
	public Component description() {
		return Component.translatable("arise.archetype." + name + ".desc");
	}

	/**
	 * I moltiplicatori di questo archetipo, presi dalla config.
	 *
	 * <p>Lo {@code switch} sta qui e non in {@link ShadowConfig.Legion} di proposito: cosi' la
	 * config non ha bisogno di conoscere l'enum, e la dipendenza fra i due package resta a senso
	 * unico. Aggiungere un archetipo e' comunque un errore di compilazione in questo punto, che e'
	 * esattamente dove serve accorgersene.
	 */
	public ShadowConfig.Archetype tuning(ShadowConfig.Legion legion) {
		return switch (this) {
			case GUARD -> legion.guard();
			case BEAST -> legion.beast();
			case MAGE -> legion.mage();
			case TANK -> legion.tank();
		};
	}

	/**
	 * A quale archetipo appartiene il mob appena ucciso.
	 *
	 * <p>Comodita' su {@link #ofSource}: qui il cadavere c'e' ancora, quindi si puo' anche chiedere
	 * all'entita' se sa colpire da lontano. E' la sola scorciatoia che l'elenco in config non copre,
	 * e serve ai mob di un'altra mod che nessuno ha ancora classificato a mano.
	 */
	public static ShadowArchetype of(LivingEntity victim, ShadowConfig.Behaviour behaviour) {
		ShadowArchetype byType = ofSource(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()),
				victim.getMaxHealth(), behaviour);

		if (byType == GUARD && (victim instanceof RangedAttackMob || victim instanceof CrossbowAttackMob)) {
			return MAGE;
		}

		if (byType == GUARD && victim.getArmorValue() >= behaviour.tankArmorThreshold()) {
			return TANK;
		}

		return byType;
	}

	/**
	 * A quale archetipo appartiene un'ombra che viene da questo mob, con questa vita di partenza.
	 *
	 * <p>Tre domande in quest'ordine, e l'ordine e' la regola: <em>prima quanto e' grosso, poi come
	 * colpisce, poi che forma ha.</em>
	 *
	 * <ol>
	 *   <li><strong>Colosso</strong> se ha molta vita. Prima di tutto il resto, cosi' il Warden e il
	 *       Guardian Anziano restano colossi invece di diventare maghi per via del loro attacco a
	 *       distanza;
	 *   <li><strong>Mago</strong> se sta nell'elenco dei lanciatori in config;
	 *   <li><strong>Bestia</strong> se e' piu' largo che alto. Il ragno non e' un {@code Animal} ma
	 *       nessuno lo chiamerebbe una guardia: la scatola di collisione dice quello che la
	 *       gerarchia delle classi non dice. Un tipo che il registro non conosce — perche' la mod
	 *       che lo forniva non c'e' piu' — non ha una scatola, e allora questa domanda si salta;
	 *   <li>altrimenti <strong>Guardia</strong>, che e' il caso di zombie, piglin bruti e in
	 *       generale di tutto cio' che sta in piedi su due gambe.
	 * </ol>
	 *
	 * @param sourceType      il mob d'origine
	 * @param sourceMaxHealth la vita del mob <em>vivo</em>, non quella gia' moltiplicata dell'ombra
	 */
	public static ShadowArchetype ofSource(Identifier sourceType, double sourceMaxHealth,
			ShadowConfig.Behaviour behaviour) {
		if (sourceMaxHealth >= behaviour.tankHealthThreshold()) {
			return TANK;
		}

		if (sourceType != null && behaviour.casterSet().contains(sourceType)) {
			return MAGE;
		}

		EntityType<?> type = sourceType == null
				? null
				: BuiltInRegistries.ENTITY_TYPE.getOptional(sourceType).orElse(null);

		if (type != null && (type.getCategory() == MobCategory.CREATURE
				|| type.getHeight() < type.getWidth() * 1.5F)) {
			return BEAST;
		}

		return GUARD;
	}
}
