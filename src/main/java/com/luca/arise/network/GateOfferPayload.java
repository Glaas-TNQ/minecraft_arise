package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.luca.arise.gate.GateOffer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: il preventivo di un varco, in risposta a chi lo ha esaminato.
 *
 * <p>Non è sincronizzato con l'entità: viaggia solo quando qualcuno clicca, e solo verso di lui.
 * Un preventivo è una decina di campi, e mandarlo a chiunque passi a cinquanta blocchi da un varco
 * sarebbe traffico speso per un pannello che nessuno aprirà.
 *
 * <p>{@code entityId} è l'id di rete dell'entità: serve al client per rimandare indietro
 * "voglio entrare in <em>quel</em> varco" senza fidarsi di niente che abbia calcolato lui.
 */
public record GateOfferPayload(int entityId, GateOffer offer, int remainingTicks)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<GateOfferPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("gate_offer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GateOfferPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, GateOfferPayload::entityId,
					GateOffer.STREAM_CODEC, GateOfferPayload::offer,
					ByteBufCodecs.VAR_INT, GateOfferPayload::remainingTicks,
					GateOfferPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
