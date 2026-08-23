package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringRepresentable;

/**
 * Client → server: un'azione senza parametri, legata a un tasto.
 *
 * <p>Un solo pacchetto per tutte le azioni invece di uno per tipo: il numero di abilità crescerà,
 * e ognuna con il proprio payload sarebbe solo boilerplate. Il server valida comunque tutto.
 */
public record AriseActionPayload(Action action) implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		/** "Arise": tenta di estrarre l'ombra dal cadavere più vicino. */
		EXTRACT("extract"),
		/** Evoca quante più ombre consente il limite. */
		SUMMON("summon"),
		/** Richiama tutte le ombre evocate. */
		RECALL("recall"),
		/** Cicla la postura di combattimento dell'esercito. */
		STANCE("stance"),
		/**
		 * Apre il menu del Cacciatore.
		 *
		 * <p>Un menu con delle caselle nasce sul server e non sul client: e' il server a
		 * costruirlo, ad assegnargli un identificativo e a spedirne il contenuto. Il tasto puo'
		 * solo chiederlo.
		 */
		OPEN_GEAR("open_gear"),
		/**
		 * Apre la mappa del mondo.
		 *
		 * <p>Come il menu: il contenuto lo sa solo il server — città a duecentomila blocchi, varchi
		 * in chunk mai visti — quindi il tasto chiede, e la risposta è la mappa.
		 */
		OPEN_MAP("open_map"),
		ABILITY_1("ability_1"),
		ABILITY_2("ability_2"),
		ABILITY_3("ability_3"),
		ABILITY_4("ability_4");

		/** L'abilità legata a questa azione, oppure {@code null} se non è un'abilità. */
		public com.luca.arise.ability.Ability ability() {
			return switch (this) {
				case ABILITY_1 -> com.luca.arise.ability.Ability.SHADOW_STEP;
				case ABILITY_2 -> com.luca.arise.ability.Ability.SHADOW_EXCHANGE;
				case ABILITY_3 -> com.luca.arise.ability.Ability.MONARCH_DOMAIN;
				case ABILITY_4 -> com.luca.arise.ability.Ability.SOVEREIGN_AUTHORITY;
				default -> null;
			};
		}

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

	public static final CustomPacketPayload.Type<AriseActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AriseActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.fromCodec(Action.CODEC), AriseActionPayload::action,
					AriseActionPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
