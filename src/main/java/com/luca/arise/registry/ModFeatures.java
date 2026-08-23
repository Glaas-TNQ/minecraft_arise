package com.luca.arise.registry;

import com.luca.arise.AriseMod;
import com.luca.arise.city.CityFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Le feature di generazione della mod: per ora le due passate della citta'.
 *
 * <p>La feature in se' sta nel registro fisso; la sua versione configurata e quella piazzata
 * sono dati, in {@code data/arise/worldgen/configured_feature} e {@code placed_feature}. Senza
 * modificatori di piazzamento una feature viene chiamata <em>una volta per chunk</em>, all'angolo
 * del chunk: e' esattamente l'unita' di lavoro che serve a una citta' tagliata a chunk.
 *
 * <p>L'aggancio ai biomi passa da Fabric: tutti quelli dell'Overworld, perche' non si sa in che
 * bioma cadra' una citta' e non ha importanza — la feature stessa scarta in un confronto ogni
 * chunk che non le appartiene.
 */
public final class ModFeatures {

	public static final ResourceKey<PlacedFeature> CITY_TERRACE_PLACED =
			ResourceKey.create(Registries.PLACED_FEATURE, AriseMod.id("city_terrace"));

	public static final ResourceKey<PlacedFeature> CITY_PLACED =
			ResourceKey.create(Registries.PLACED_FEATURE, AriseMod.id("city"));

	/** La spianata, prima della vegetazione. */
	public static final Feature<NoneFeatureConfiguration> CITY_TERRACE = Registry.register(
			BuiltInRegistries.FEATURE, AriseMod.id("city_terrace"), new CityFeature(false));

	/** La citta' intera, dopo tutto il resto. */
	public static final Feature<NoneFeatureConfiguration> CITY = Registry.register(
			BuiltInRegistries.FEATURE, AriseMod.id("city"), new CityFeature(true));

	private ModFeatures() {
	}

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.RAW_GENERATION, CITY_TERRACE_PLACED);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.TOP_LAYER_MODIFICATION, CITY_PLACED);
	}
}
