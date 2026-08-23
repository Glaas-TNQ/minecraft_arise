package com.luca.arise.fx;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le due scritte che non stanno in chat: il titolo al centro e la riga sopra la hotbar.
 *
 * <p>Esiste per lo stesso motivo di {@link AriseFx}: perche' non finiscano sparse. In 26.2 non c'e'
 * un metodo che le mandi — {@code ServerPlayer} non ha ne' {@code displayClientMessage} ne'
 * {@code showTitle} — quindi ogni chiamata sarebbe stata tre pacchetti costruiti a mano nel posto
 * sbagliato, con i tempi copiati a occhio da un'altra parte del codice.
 *
 * <p>La differenza fra le due non e' l'importanza, e' la durata. Il titolo interrompe: si usa per i
 * momenti che vanno guardati, e sono pochissimi. La barra d'azione accompagna: si usa per i
 * contatori che cambiano spesso e che in chat sarebbero rumore.
 */
public final class Overlay {

	/** Comparsa, permanenza, dissolvenza, in tick. */
	private static final int TITLE_IN = 10;
	private static final int TITLE_STAY = 60;
	private static final int TITLE_OUT = 20;

	private Overlay() {
	}

	/**
	 * Titolo e sottotitolo al centro dello schermo.
	 *
	 * <p>L'animazione va per prima: mandata dopo, il titolo comparirebbe con i tempi di quello di
	 * prima — che in un gioco dove qualunque altra mod puo' aver mandato un titolo vuol dire tempi
	 * qualunque.
	 */
	public static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_IN, TITLE_STAY, TITLE_OUT));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** La riga sopra la hotbar. Sostituisce quella precedente, e sparisce da sola. */
	public static void actionBar(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(text));
	}
}
