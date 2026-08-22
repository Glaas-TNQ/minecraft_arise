package com.luca.arise.npc;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.MarketConfig;
import com.luca.arise.event.CityEvents;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gear.GearManager;
import com.luca.arise.network.OpenScreenPayload;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModItems;
import com.luca.arise.workshop.WorkshopManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * I quattro banconi che non vendono niente.
 *
 * <p>Un servizio non apre la finestra di scambio: fa qualcosa, oppure apre una schermata della mod.
 * La differenza fra le due famiglie sta nel {@link Shopkeeper}, e questo file e' l'altra meta' —
 * cosa succede davvero quando si clicca.
 *
 * <p><strong>Tenere premuto lo shift cambia il verso.</strong> Al Banco si conia normalmente e si
 * riporta indietro con lo shift; allo Sportello si viaggia normalmente e si apre il negozio con lo
 * shift. E' l'unica convenzione da imparare in tutto il quartiere, ed e' scritta nel saluto che
 * ogni bottega manda al primo clic.
 */
public final class ShopkeeperServices {

	private ShopkeeperServices() {
	}

	public static void open(Shopkeeper role, ServerPlayer player, ShopkeeperEntity npc) {
		MarketConfig market = AriseConfig.get().market();
		boolean reverse = player.isShiftKeyDown();

		switch (role) {
			case BANKER -> bank(player, npc, market, reverse);
			case GATE_BROKER -> broker(player, npc, market);
			case JEWELLER -> screen(player, npc, OpenScreenPayload.Screen.GEMS, Unlock.GEMS);
			case CLERK -> clerk(player, npc, reverse);
			default -> player.sendSystemMessage(role.greeting());
		}
	}

	// ---------------------------------------------------------------- il Banco dell'Abisso

	/**
	 * Conia soul coin in monete, o riprende indietro le monete.
	 *
	 * <p>Non c'e' una schermata, e non serve: e' una domanda con una risposta sola — "quante ne
	 * vuoi" — e la risposta e' sempre "tutte quelle che posso". Un pannello con un cursore da
	 * trascinare sarebbe tre clic in piu' per lo stesso risultato.
	 */
	private static void bank(ServerPlayer player, ShopkeeperEntity npc, MarketConfig market,
			boolean redeem) {
		if (redeem) {
			redeem(player, npc, market);
			return;
		}

		Component locked = QuestManager.require(player, Unlock.COIN);
		if (locked != null) {
			player.sendSystemMessage(locked);
			return;
		}

		long owned = ProgressManager.souls(player);
		int affordable = (int) Math.min(market.mintBatch(), owned / Math.max(1L, market.coinValue()));

		if (affordable <= 0) {
			player.sendSystemMessage(Component.translatable("arise.msg.market.no_souls",
					market.coinValue()));
			return;
		}

		long cost = affordable * market.coinValue();
		if (!ProgressManager.spendSouls(player, cost)) {
			player.sendSystemMessage(Component.translatable("arise.msg.market.no_souls",
					market.coinValue()));
			return;
		}

		WorkshopManager.give(player, ModItems.coins(affordable));
		QuestManager.advance(player, Objective.MINT_COIN, affordable);
		deal(player, npc);

		player.sendSystemMessage(Component.translatable("arise.msg.market.minted", affordable, cost));
	}

	/** Il verso opposto: le monete tornano nel Sistema, con una trattenuta. */
	private static void redeem(ServerPlayer player, ShopkeeperEntity npc, MarketConfig market) {
		Inventory inventory = player.getInventory();
		int found = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.is(ModItems.SOUL_COIN)) {
				found += stack.getCount();
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		if (found == 0) {
			player.sendSystemMessage(Component.translatable("arise.msg.market.no_coins"));
			return;
		}

		long value = market.redeemValue(found);
		ProgressManager.addSouls(player, value);
		deal(player, npc);

		player.sendSystemMessage(Component.translatable("arise.msg.market.redeemed", found, value));
	}

	// ---------------------------------------------------------------- il Sensale dei Varchi

	/**
	 * Un varco su ordinazione, del rango del Cacciatore che lo chiede.
	 *
	 * <p>Il rango non si sceglie, e non e' una mancanza: un Sensale che vendesse varchi di rango S
	 * a un Cacciatore di rango E venderebbe una morte. Quello che vende e' la <em>comodita'</em> di
	 * non aspettare che un varco si apra da solo.
	 */
	private static void broker(ServerPlayer player, ShopkeeperEntity npc, MarketConfig market) {
		Component locked = QuestManager.require(player, Unlock.SERVICES);
		if (locked != null) {
			player.sendSystemMessage(locked);
			return;
		}

		if (!ProgressManager.spendSouls(player, market.gateFee())) {
			player.sendSystemMessage(Component.translatable("arise.msg.market.no_fee",
					market.gateFee(), ProgressManager.souls(player)));
			return;
		}

		Rank rank = GearManager.hunterRank(player);
		deal(player, npc);

		player.sendSystemMessage(GateManager.offer(player, rank));
		QuestManager.advance(player, Objective.BUY_GATE);
	}

	// ---------------------------------------------------------------- lo Sportello

	private static void clerk(ServerPlayer player, ShopkeeperEntity npc, boolean shop) {
		if (shop) {
			screen(player, npc, OpenScreenPayload.Screen.SHOP, Unlock.SHOP);
			return;
		}

		Component locked = QuestManager.require(player, Unlock.CITIES);
		if (locked != null) {
			player.sendSystemMessage(locked);
			return;
		}

		deal(player, npc);
		CityEvents.openHub(player);
	}

	// ---------------------------------------------------------------- aiuti

	/** Apre una schermata della mod, se il sistema che disegna e' gia' stato concesso. */
	private static void screen(ServerPlayer player, ShopkeeperEntity npc,
			OpenScreenPayload.Screen screen, Unlock required) {
		Component locked = QuestManager.require(player, required);

		if (locked != null) {
			player.sendSystemMessage(locked);
			return;
		}

		deal(player, npc);
		ServerPlayNetworking.send(player, new OpenScreenPayload(screen));
	}

	private static void deal(ServerPlayer player, ShopkeeperEntity npc) {
		if (player.level() instanceof ServerLevel level) {
			AriseFx.marketDeal(level, npc.position());
		}
	}
}
