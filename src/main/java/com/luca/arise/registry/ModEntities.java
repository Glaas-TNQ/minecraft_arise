package com.luca.arise.registry;

import com.luca.arise.AriseMod;
import com.luca.arise.gate.GateEntity;
import com.luca.arise.npc.ShopkeeperEntity;
import com.luca.arise.shadow.ShadowEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

	public static final ResourceKey<EntityType<?>> SHADOW_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, AriseMod.id("shadow"));

	public static final EntityType<ShadowEntity> SHADOW = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			SHADOW_KEY,
			EntityType.Builder.<ShadowEntity>of(ShadowEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.build(SHADOW_KEY));

	public static final ResourceKey<EntityType<?>> GATE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, AriseMod.id("gate"));

	/**
	 * Il varco: nessuna fisica, nessuna IA, solo un ingombro da cliccare.
	 *
	 * <p>Alto quanto una porta e largo poco più di un giocatore. La misura conta: è l'area su cui
	 * si clicca per aprire il pannello di analisi, e un varco troppo sottile sarebbe irritante da
	 * centrare.
	 */
	public static final EntityType<GateEntity> GATE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			GATE_KEY,
			EntityType.Builder.<GateEntity>of(GateEntity::new, MobCategory.MISC)
					.sized(1.6F, 2.6F)
					.build(GATE_KEY));

	public static final ResourceKey<EntityType<?>> SHOPKEEPER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, AriseMod.id("shopkeeper"));

	/**
	 * Chi sta dietro il bancone.
	 *
	 * <p>Le misure sono quelle di un giocatore, e devono esserlo: il modello e' l'umanoide vanilla
	 * con addosso una delle nove skin predefinite, e un ingombro diverso farebbe sembrare la
	 * bottega abitata da qualcuno di leggermente sbagliato.
	 */
	public static final EntityType<ShopkeeperEntity> SHOPKEEPER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			SHOPKEEPER_KEY,
			EntityType.Builder.<ShopkeeperEntity>of(ShopkeeperEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.build(SHOPKEEPER_KEY));

	private ModEntities() {
	}

	public static void init() {
		// MISC e non CREATURE: le ombre non devono comparire nello spawn naturale né contare
		// per i limiti di popolazione dei mob.
		FabricDefaultAttributeRegistry.register(SHADOW, ShadowEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SHOPKEEPER, ShopkeeperEntity.createAttributes());
	}
}
