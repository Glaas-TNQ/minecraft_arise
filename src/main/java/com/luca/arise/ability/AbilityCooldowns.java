package com.luca.arise.ability;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Quando ogni abilità torna disponibile, in tick di gioco assoluti.
 *
 * <p>Sincronizzato verso il proprietario perché la barra dell'HUD deve disegnare il riempimento
 * senza chiedere niente al server. <strong>Non</strong> persistente: i tempi di ricarica sono
 * misurati sul tempo di gioco del mondo, e dopo un riavvio confronterebbero istanti che non
 * significano più la stessa cosa. Ripartire da tutto pronto è il comportamento sensato.
 */
public record AbilityCooldowns(Map<Ability, Long> readyAt) {

	public static final Codec<AbilityCooldowns> CODEC =
			Codec.unboundedMap(Ability.CODEC, Codec.LONG)
					.xmap(AbilityCooldowns::new, AbilityCooldowns::readyAt);

	public static final StreamCodec<ByteBuf, AbilityCooldowns> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final AbilityCooldowns EMPTY = new AbilityCooldowns(Map.of());

	public AbilityCooldowns {
		readyAt = Map.copyOf(readyAt);
	}

	public long readyAt(Ability ability) {
		return readyAt.getOrDefault(ability, 0L);
	}

	public boolean isReady(Ability ability, long now) {
		return now >= readyAt(ability);
	}

	public AbilityCooldowns with(Ability ability, long tick) {
		Map<Ability, Long> updated = new EnumMap<>(Ability.class);
		updated.putAll(readyAt);
		updated.put(ability, tick);
		return new AbilityCooldowns(updated);
	}

	/** Frazione di ricarica ancora da attendere, da 1 (appena usata) a 0 (pronta). */
	public float remainingFraction(Ability ability, long now, int totalTicks) {
		if (totalTicks <= 0) {
			return 0.0F;
		}

		long remaining = readyAt(ability) - now;
		return remaining <= 0 ? 0.0F : Math.min(1.0F, (float) remaining / totalTicks);
	}
}
