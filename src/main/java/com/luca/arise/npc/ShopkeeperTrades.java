package com.luca.arise.npc;

import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModItems;
import com.luca.arise.workshop.MachineKind;
import com.luca.arise.workshop.SoulItems;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;

/**
 * Cosa vende ciascuna bottega, e a che prezzo.
 *
 * <p>Tutti i prezzi sono in <strong>Monete d'Anima</strong>, e non e' un dettaglio di sapore: la
 * finestra di scambio di Minecraft sa confrontare oggetti, non numeri, quindi una valuta che non
 * sia un oggetto non ci puo' entrare. Da li' nasce la moneta, e da li' nasce il Banco che la conia.
 *
 * <p>Le scorte sono <strong>limitate</strong> — ogni voce ha un numero di usi — e si rifanno solo
 * quando l'NPC viene ricaricato dal disco. E' quello che tiene una bottega una bottega invece che
 * un distributore automatico: se vuoi trentadue lingotti subito, li devi fondere.
 *
 * <p>La tabella sta nel codice e non in config, contro la regola generale sui numeri di
 * bilanciamento, per la stessa ragione degli slot dell'equipaggiamento: e' <em>struttura</em>.
 * Cosa vende il Cavatore definisce cos'e' il Cavatore, e una config che potesse cambiarlo
 * renderebbe le nove botteghe nove nomi intercambiabili.
 */
public final class ShopkeeperTrades {

	/** Usi di una voce comune prima che si esaurisca. */
	private static final int COMMON = 16;

	/** Usi di una voce cara: poche, altrimenti il prezzo non conta. */
	private static final int SCARCE = 4;

	/** Quanto il prezzo si muove con la domanda. Zero: i prezzi del mercato non ballano. */
	private static final float STABLE = 0.0F;

	private ShopkeeperTrades() {
	}

	/**
	 * L'assortimento di una bottega.
	 *
	 * <p>{@code random} c'e' per le voci che variano da un NPC all'altro — oggi nessuna, ma il
	 * giorno in cui il Cartografo avra' due Progetti su quattro invece di tutti e quattro, questa
	 * firma non dovra' cambiare.
	 */
	public static MerchantOffers build(Shopkeeper role, RandomSource random) {
		MerchantOffers offers = new MerchantOffers();

		switch (role) {
			case SMELTER -> {
				sell(offers, 1, Items.COAL, 16, COMMON);
				sell(offers, 2, Items.IRON_INGOT, 6, COMMON);
				sell(offers, 3, Items.COPPER_INGOT, 12, COMMON);
				sell(offers, 4, Items.GOLD_INGOT, 4, COMMON);
				sell(offers, 6, Items.BLAZE_ROD, 2, SCARCE);
				buy(offers, Items.RAW_IRON, 12, 1, COMMON);
			}
			case QUARRIER -> {
				sell(offers, 1, Items.DEEPSLATE_BRICKS, 32, COMMON);
				sell(offers, 1, Items.POLISHED_DEEPSLATE, 32, COMMON);
				sell(offers, 3, Items.OBSIDIAN, 8, COMMON);
				sell(offers, 8, Items.CRYING_OBSIDIAN, 4, SCARCE);
				buy(offers, Items.COBBLESTONE, 64, 1, COMMON);
			}
			case SOUL_TRADER -> {
				sell(offers, 1, Items.SOUL_SAND, 16, COMMON);
				sell(offers, 2, Items.SOUL_SOIL, 16, COMMON);
				sell(offers, 7, Items.ECHO_SHARD, 2, SCARCE);
				sellStack(offers, 6, SoulItems.catalyst(Rank.E, 4), COMMON);
				sellStack(offers, 14, SoulItems.catalyst(Rank.D, 2), SCARCE);
			}
			case DRAFTSMAN -> {
				// I Progetti si ricomprano: il primo di ognuno lo regala la catena degli incarichi,
				// ma un macchinario in piu' costa, ed e' giusto che costi qui.
				sellStack(offers, 24, blueprint(MachineKind.LURE), SCARCE);
				sellStack(offers, 32, blueprint(MachineKind.CRUCIBLE), SCARCE);
				sellStack(offers, 40, blueprint(MachineKind.FORGE), SCARCE);
				sellStack(offers, 48, blueprint(MachineKind.WELL), SCARCE);
				// La Pergamena del Rimpianto sta qui e non all'Abyss Shop per due ragioni. La
				// prima e' che il Cartografo vende disegni e progetti, e una pergamena e' la cosa
				// piu' vicina a un progetto che si possa scrivere su un Cacciatore. La seconda e'
				// che il prezzo dev'essere fisso e leggibile: chi ha sbagliato la spesa deve poter
				// contare quanto gli costa rimediare, non tirarlo a sorte a ogni rotazione.
				// Trenta monete sono tremila soul coin, cioe' cinque Gate di rango C.
				sellStack(offers, 30, new ItemStack(ModItems.REGRET_SCROLL), SCARCE);
				sell(offers, 3, Items.HOPPER, 4, COMMON);
				sell(offers, 2, Items.CAULDRON, 2, COMMON);
				sell(offers, 3, Items.FURNACE, 4, COMMON);
			}
			case HERBALIST -> {
				sell(offers, 1, Items.COOKED_BEEF, 16, COMMON);
				sell(offers, 1, Items.BREAD, 24, COMMON);
				sell(offers, 5, Items.GOLDEN_APPLE, 2, SCARCE);
				sell(offers, 2, Items.ENDER_PEARL, 2, COMMON);
				sell(offers, 1, Items.TORCH, 32, COMMON);
				buy(offers, Items.ROTTEN_FLESH, 32, 1, COMMON);
			}
			default -> {
				// I servizi non vendono niente: vedi Shopkeeper.merchant().
			}
		}

		return offers;
	}

	// ---------------------------------------------------------------- aiuti

	private static ItemStack blueprint(MachineKind kind) {
		return new ItemStack(ModItems.blueprintOf(kind));
	}

	/** Monete in cambio di roba. */
	private static void sell(MerchantOffers offers, int price, ItemLike item, int count, int uses) {
		sellStack(offers, price, new ItemStack(item, count), uses);
	}

	private static void sellStack(MerchantOffers offers, int price, ItemStack result, int uses) {
		offers.add(new MerchantOffer(new ItemCost(ModItems.SOUL_COIN, price), result, uses, 0, STABLE));
	}

	/**
	 * Roba in cambio di monete: la bottega compra.
	 *
	 * <p>Serve perche' altrimenti la moneta avrebbe una sola direzione, e l'unico modo di averne
	 * sarebbe il Banco. Una bottega che compra da' un motivo per raccogliere anche quello che non
	 * serve a niente.
	 */
	private static void buy(MerchantOffers offers, ItemLike item, int count, int coins, int uses) {
		offers.add(new MerchantOffer(new ItemCost(item, count), ModItems.coins(coins), uses, 0, STABLE));
	}
}
