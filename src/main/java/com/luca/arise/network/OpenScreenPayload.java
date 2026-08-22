package com.luca.arise.network;

import com.luca.arise.AriseMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: apri questa schermata.
 *
 * <p>Fin qui le schermate della mod si aprivano solo da un tasto, e andava bene finche' l'unico
 * modo di arrivarci era premerlo. Il Quartiere del Mercato cambia le cose: dietro un bancone c'e'
 * una persona, e cliccarla deve aprire il banco delle gemme senza che nessuno debba ricordarsi
 * quale lettera fosse.
 *
 * <p>Un pacchetto solo con dentro <em>quale</em> schermata, e non un pacchetto per schermata: non
 * portano dati, sono un ordine di due byte. Uno per ognuna sarebbe stata la stessa cosa scritta
 * quattro volte.
 */
public record OpenScreenPayload(Screen screen) implements CustomPacketPayload {

	/** Le schermate che il server puo' far aprire. */
	public enum Screen {
		/** Il banco delle gemme. */
		GEMS,
		/** L'Abyss Shop. */
		SHOP,
		/** La catena degli incarichi. */
		QUESTS
	}

	public static final CustomPacketPayload.Type<OpenScreenPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("open_screen"));

	/** Tre voci: un intero variabile basta, e {@code composite} accetta un codec piu' generico. */
	private static final StreamCodec<io.netty.buffer.ByteBuf, Screen> SCREEN =
			ByteBufCodecs.VAR_INT.map(
					index -> Screen.values()[Math.clamp(index, 0, Screen.values().length - 1)],
					Screen::ordinal);

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC =
			StreamCodec.composite(SCREEN, OpenScreenPayload::screen, OpenScreenPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
