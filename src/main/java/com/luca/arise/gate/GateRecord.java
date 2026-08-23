package com.luca.arise.gate;

import java.util.UUID;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Un varco visto dalla mappa: dov'è, che rango ha, quanto gli resta.
 *
 * <p>Non è il varco — quello è l'entità nel mondo, e resta l'unica verità. Questo è l'appunto
 * che se ne prende perché la mappa possa mostrarlo anche quando il suo chunk è scaricato e
 * l'entità non si può interrogare.
 *
 * @param id             l'UUID dell'entità, per ritrovarla e per non scriverla due volte
 * @param dimension      dove sta
 * @param pos            dove sta, al blocco
 * @param rank           il rango, cioè il colore con cui si disegna
 * @param theme          il tema del Gate che c'è dietro
 * @param remainingTicks quanti tick gli restavano l'ultima volta che si è fatto sentire
 * @param seenAt         il tempo di gioco di quell'ultima volta
 */
public record GateRecord(UUID id, ResourceKey<Level> dimension, BlockPos pos, Rank rank,
		GateTheme theme, int remainingTicks, long seenAt) {

	public static final Codec<GateRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(GateRecord::id),
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(GateRecord::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(GateRecord::pos),
			Rank.CODEC.fieldOf("rank").forGetter(GateRecord::rank),
			GateTheme.CODEC.fieldOf("theme").forGetter(GateRecord::theme),
			Codec.INT.fieldOf("remaining").forGetter(GateRecord::remainingTicks),
			Codec.LONG.fieldOf("seen_at").forGetter(GateRecord::seenAt)
	).apply(instance, GateRecord::new));

	/** L'appunto preso adesso su questo varco. Richiede un preventivo: un varco senza non esiste. */
	public static GateRecord of(GateEntity varco) {
		GateOffer offer = varco.offer();

		return new GateRecord(varco.getUUID(), varco.level().dimension(), varco.blockPosition(),
				offer.rank(), offer.theme(), varco.remainingTicks(), varco.level().getGameTime());
	}
}
