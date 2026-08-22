package com.luca.arise.network;

import java.util.UUID;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringRepresentable;

/**
 * Client → server: un'operazione su <em>una</em> ombra.
 *
 * <p>Viaggia l'id dell'ombra, non la sua posizione in lista: la lista può cambiare tra il momento
 * in cui il client la disegna e quello in cui il pacchetto arriva, e un indice sfasato agirebbe
 * sull'ombra sbagliata.
 *
 * <p>{@code name} e {@code color} valgono solo per le rispettive azioni; sono qui invece che in
 * due pacchetti separati perché il contratto resta uno solo e il server valida comunque tutto.
 */
public record ShadowActionPayload(UUID shadowId, Action action, String name, int color)
		implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		SUMMON("summon"),
		RECALL("recall"),
		/** Congeda: rimuove l'ombra e rimborsa una parte del suo valore. */
		DISMISS("dismiss"),
		RENAME("rename"),
		RECOLOR("recolor"),
		/** Sale di un livello pagando in soul coin, senza combattere. */
		UPGRADE("upgrade");

		public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);

		private final String name;

		Action(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Limite sul nome: un nome lungo quanto un romanzo sfonderebbe l'HUD e il pacchetto. */
	public static final int MAX_NAME_LENGTH = 32;

	public static final CustomPacketPayload.Type<ShadowActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("shadow_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShadowActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					UUIDUtil.STREAM_CODEC, ShadowActionPayload::shadowId,
					ByteBufCodecs.fromCodec(Action.CODEC), ShadowActionPayload::action,
					ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), ShadowActionPayload::name,
					ByteBufCodecs.INT, ShadowActionPayload::color,
					ShadowActionPayload::new);

	public static ShadowActionPayload of(UUID shadowId, Action action) {
		return new ShadowActionPayload(shadowId, action, "", 0);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
