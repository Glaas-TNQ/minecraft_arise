package com.luca.arise.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * L'assortimento che l'Abyss Shop mostra a <em>un</em> giocatore.
 *
 * <p>Per giocatore e non globale, e non per pigrizia: nella finzione il negozio e' una finestra del
 * Sistema, non un banco in piazza. In pratica evita la corsa fra due giocatori sullo stesso pezzo,
 * che in multiplayer significherebbe che il secondo click perde sempre.
 *
 * @param rotation  quale finestra temporale ha generato queste voci; quando cambia, cambia tutto
 * @param refreshes quanti ritiri sono gia' stati pagati dentro questa rotazione
 */
public record ShopStock(long rotation, int refreshes, List<ShopOffer> offers) {

	public static final Codec<ShopStock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("rotation").forGetter(ShopStock::rotation),
			Codec.INT.optionalFieldOf("refreshes", 0).forGetter(ShopStock::refreshes),
			ShopOffer.CODEC.listOf().fieldOf("offers").forGetter(ShopStock::offers)
	).apply(instance, ShopStock::new));

	public static final StreamCodec<ByteBuf, ShopStock> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Rotazione -1: nessuna finestra temporale reale, quindi il primo accesso rigenera. */
	public static final ShopStock EMPTY = new ShopStock(-1L, 0, List.of());

	public ShopStock {
		offers = List.copyOf(offers);
	}

	public Optional<ShopOffer> find(UUID id) {
		return offers.stream().filter(offer -> offer.id().equals(id)).findFirst();
	}

	/** Toglie una voce comprata. Le voci non si ripetono: quello che e' andato e' andato. */
	public ShopStock without(UUID id) {
		List<ShopOffer> updated = new ArrayList<>(offers);
		updated.removeIf(offer -> offer.id().equals(id));
		return new ShopStock(rotation, refreshes, updated);
	}
}
