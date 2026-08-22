package com.luca.arise.registry;

import com.luca.arise.AriseMod;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.workshop.Catalyst;
import com.luca.arise.workshop.LooseSoul;

import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * I componenti dati che la mod attacca agli {@code ItemStack}.
 *
 * <p>Ce n'e' uno solo, ed e' quello che trasforma una spada di ferro qualunque in "Lama del Titano
 * di rango B": il {@link GearPiece} viaggia dentro l'oggetto invece che in una lista a parte. Da
 * qui discende tutto il resto — il pezzo cade per terra, entra in un baule, si ripara
 * all'incudine, e il server sa comunque leggere che statistiche concede.
 *
 * <p>{@code GearPiece} implementa {@code TooltipProvider}, e
 * {@link ItemComponentTooltipProviderRegistry} e' cio' che dice al gioco di chiamarlo quando
 * disegna la descrizione. Niente righe scritte a mano nel {@code lore}: quelle sarebbero testo
 * congelato in una lingua sola, mentre cosi' il tooltip si traduce da solo e resta d'accordo con
 * il dato anche dopo che una gemma e' stata incastonata.
 */
public final class ModComponents {

	public static final DataComponentType<GearPiece> GEAR_PIECE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			AriseMod.id("gear_piece"),
			DataComponentType.<GearPiece>builder()
					.persistent(GearPiece.CODEC)
					.networkSynchronized(GearPiece.STREAM_CODEC)
					.build());

	/**
	 * L'anima in esubero, dentro l'oggetto che la porta.
	 *
	 * <p>Stessa forma del pezzo di equipaggiamento, e non per simmetria estetica: un'anima deve
	 * poter passare da una tramoggia dentro un macchinario, e l'unica cosa che sa attraversare una
	 * tramoggia e' un {@code ItemStack}. Un'anima che vivesse in una lista del giocatore non
	 * potrebbe essere automatizzata da niente.
	 */
	public static final DataComponentType<LooseSoul> SOUL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			AriseMod.id("soul"),
			DataComponentType.<LooseSoul>builder()
					.persistent(LooseSoul.CODEC)
					.networkSynchronized(LooseSoul.STREAM_CODEC)
					.build());

	/** Il grado del catalizzatore. Un campo solo, ma e' quello che distingue sei oggetti. */
	public static final DataComponentType<Catalyst> CATALYST = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			AriseMod.id("catalyst"),
			DataComponentType.<Catalyst>builder()
					.persistent(Catalyst.CODEC)
					.networkSynchronized(Catalyst.STREAM_CODEC)
					.build());

	private ModComponents() {
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
		// Dopo il nome dell'oggetto e prima di tutto il resto: le statistiche del pezzo sono la
		// ragione per cui lo si sta guardando, non una nota a pie' di pagina.
		ItemComponentTooltipProviderRegistry.addBefore(DataComponents.ATTRIBUTE_MODIFIERS, GEAR_PIECE);

		// Stesso posto per anime e catalizzatori: il livello di un'anima e il grado di un
		// catalizzatore sono l'unica ragione per cui li si sta guardando.
		ItemComponentTooltipProviderRegistry.addBefore(DataComponents.ATTRIBUTE_MODIFIERS, SOUL);
		ItemComponentTooltipProviderRegistry.addBefore(DataComponents.ATTRIBUTE_MODIFIERS, CATALYST);
	}
}
