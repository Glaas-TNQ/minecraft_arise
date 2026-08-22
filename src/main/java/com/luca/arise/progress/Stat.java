package com.luca.arise.progress;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Le statistiche del Sistema, e l'attributo vanilla su cui ciascuna agisce.
 *
 * <p>Qui stanno solo i <em>collegamenti</em> (quale attributo, quale operazione): quanto vale un
 * punto e qual e' il tetto sono numeri di bilanciamento e vivono nella config.
 */
public enum Stat implements StringRepresentable {
	/** Vita massima. */
	VITALITY("vitality", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE),
	/** Velocita' di movimento, in percentuale sulla base (l'operazione additiva secca qui e' inusabile). */
	AGILITY("agility", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	/** Danno da mischia. */
	STRENGTH("strength", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
	/** Armatura. */
	ENDURANCE("endurance", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE);

	public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

	private final String name;
	private final Holder<Attribute> attribute;
	private final AttributeModifier.Operation operation;
	private final Identifier modifierId;
	private final String translationKey;

	Stat(String name, Holder<Attribute> attribute, AttributeModifier.Operation operation) {
		this.name = name;
		this.attribute = attribute;
		this.operation = operation;
		// Id fisso: e' cio' che permette di rimpiazzare il modificatore invece di accumularlo.
		this.modifierId = AriseMod.id("stat/" + name);
		this.translationKey = "arise.stat." + name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Holder<Attribute> attribute() {
		return attribute;
	}

	public AttributeModifier.Operation operation() {
		return operation;
	}

	public Identifier modifierId() {
		return modifierId;
	}

	public String translationKey() {
		return translationKey;
	}
}
