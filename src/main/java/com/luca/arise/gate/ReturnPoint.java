package com.luca.arise.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Da dove il giocatore è entrato in un Gate.
 *
 * <p>Salvato su disco di proposito: se il server cade mentre qualcuno è dentro un dungeon, al
 * rientro deve poter tornare a casa. Senza questo dato resterebbe in una dimensione vuota, sopra
 * a un'istanza che non esiste più, senza modo di uscirne.
 */
public record ReturnPoint(ResourceKey<Level> dimension, Vec3 position) {

	public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(ReturnPoint::dimension),
			Vec3.CODEC.fieldOf("position").forGetter(ReturnPoint::position)
	).apply(instance, ReturnPoint::new));
}
