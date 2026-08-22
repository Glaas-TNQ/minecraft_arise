package com.luca.arise.workshop;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Un'anima in esubero: quella che non e' entrata nell'esercito.
 *
 * <p>Viaggia dentro un {@code ItemStack} come componente, esattamente come {@code GearPiece}. La
 * scelta e' la stessa e per le stesse ragioni: un oggetto sa gia' cadere per terra, stare in una
 * cassa, passare da una tramoggia. Un'anima che vivesse in una lista non potrebbe entrare in un
 * macchinario senza che ci inventassimo un'interfaccia apposta.
 *
 * <p>Vita e danno sono <em>congelati</em> al momento della cattura, come per l'equipaggiamento e
 * al contrario delle ombre: l'anima e' bottino, e il bottino non si riscala sotto i piedi di chi
 * l'ha raccolto. Il <em>livello</em> invece cresce, ed e' l'unica cosa che il Crogiolo cambia.
 *
 * <p>Vita e danno viaggiano <em>separati</em> invece che riassunti in un punteggio unico, e non
 * e' un dettaglio: un'anima si puo' arruolare, e un'ombra ha bisogno di due numeri, non di uno.
 * Riassumerli qui avrebbe voluto dire re-inventarli all'arruolamento, cioe' consegnare al
 * giocatore un'ombra diversa da quella che gli era stata promessa nel tooltip.
 *
 * @param sourceType il mob da cui viene: decide potenza e nome
 * @param level      quanto e' stata fusa; e' anche il livello che avra' da ombra arruolata
 * @param health     vita base congelata, gia' moltiplicata per i fattori di config
 * @param damage     danno base congelato, idem
 * @param traits     i tratti guadagnati nel Crogiolo, al piu' tre
 */
public record LooseSoul(UUID id, Identifier sourceType, int level, double health, double damage,
		List<SoulTrait> traits) implements TooltipProvider {

	/** Nessuna anima regge piu' di tre tratti: oltre, il tooltip diventa una tabella. */
	public static final int MAX_TRAITS = 3;

	public static final Codec<LooseSoul> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(LooseSoul::id),
			Identifier.CODEC.fieldOf("source_type").forGetter(LooseSoul::sourceType),
			Codec.INT.fieldOf("level").forGetter(LooseSoul::level),
			Codec.DOUBLE.fieldOf("health").forGetter(LooseSoul::health),
			Codec.DOUBLE.fieldOf("damage").forGetter(LooseSoul::damage),
			SoulTrait.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(LooseSoul::traits)
	).apply(instance, LooseSoul::new));

	public static final StreamCodec<ByteBuf, LooseSoul> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public LooseSoul {
		traits = List.copyOf(traits);
	}

	/** Un'anima appena catturata: livello uno, nessun tratto. */
	public static LooseSoul of(Identifier sourceType, double health, double damage) {
		return new LooseSoul(UUID.randomUUID(), sourceType, 1, health, damage, List.of());
	}

	/**
	 * Il punteggio di potenza, con lo stesso metro di {@code ShadowData.powerScore}.
	 *
	 * <p>Deve essere identico: un'anima di zombie e un'ombra di zombie devono dire lo stesso
	 * rango, altrimenti arruolare farebbe cambiare colore alla riga sotto gli occhi di chi guarda.
	 */
	public double power() {
		return health + damage * 4.0;
	}

	public Rank rank(List<Double> thresholds) {
		return Rank.fromScore(power(), thresholds);
	}

	/**
	 * Quanto rende quest'anima come operaia.
	 *
	 * <p>Potenza <em>e</em> livello, moltiplicati. Serve che le due strade contino entrambe: chi
	 * cattura un'anima forte parte avanti, chi fonde anime scarse la raggiunge lavorando. Se
	 * contasse solo la potenza, il Crogiolo non servirebbe a niente per l'officina.
	 */
	public double vigor() {
		return power() * (1.0 + 0.35 * (level - 1));
	}

	public boolean has(SoulTrait trait) {
		return traits.contains(trait);
	}

	/** Aggiunge un tratto se c'e' posto e se non c'e' gia'. */
	public LooseSoul with(SoulTrait trait, int maxTraits) {
		if (traits.contains(trait) || traits.size() >= Math.min(maxTraits, MAX_TRAITS)) {
			return this;
		}

		List<SoulTrait> updated = new ArrayList<>(traits);
		updated.add(trait);
		return new LooseSoul(id, sourceType, level, health, damage, updated);
	}

	public LooseSoul withLevel(int newLevel) {
		return new LooseSoul(id, sourceType, Math.max(1, newLevel), health, damage, traits);
	}

	/**
	 * "Anima di Zombie". Il nome del mob si risolve adesso e non si salva, come per le ombre: se
	 * la mod d'origine cambia le sue traduzioni, l'anima resta corretta.
	 */
	public Component displayName() {
		Component source = BuiltInRegistries.ENTITY_TYPE.getOptional(sourceType)
				.map(EntityType::getDescription)
				.orElseGet(() -> Component.literal(sourceType.getPath()));

		return Component.translatable("arise.soul.name", source);
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag,
			DataComponentGetter components) {
		out.accept(Component.translatable("arise.soul.tooltip.level", level)
				.withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("arise.soul.tooltip.vigor", (int) Math.round(vigor()))
				.withStyle(ChatFormatting.DARK_GRAY));

		for (SoulTrait trait : traits) {
			out.accept(Component.translatable("arise.soul.tooltip.trait", trait.label(),
					trait.description()));
		}

		out.accept(Component.translatable("arise.soul.tooltip.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
