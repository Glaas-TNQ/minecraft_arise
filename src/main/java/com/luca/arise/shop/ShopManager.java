package com.luca.arise.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GearConfig;
import com.luca.arise.config.ShopConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * L'Abyss Shop, lato server.
 *
 * <p>L'assortimento non viene salvato voce per voce come una decisione presa una volta: viene
 * <em>tirato da un seed</em> costruito su giocatore e finestra temporale. Salvarlo serve solo a
 * ricordare cosa e' gia' stato comprato e quanti ritiri sono stati pagati; se il file sparisse,
 * la stessa finestra temporale rigenererebbe le stesse voci. E' la stessa idea di
 * {@code GateOffer}: il seed e' il dato, il resto e' conseguenza.
 *
 * <p>Il negozio e' caro di proposito. La fonte principale dell'equipaggiamento sono i Gate (design
 * §8.5); questo e' il posto dove si va quando il bottino non ha sorriso.
 */
public final class ShopManager {

	private ShopManager() {
	}

	// ---------------------------------------------------------------- assortimento

	/**
	 * L'assortimento attuale, rigenerato se la finestra temporale e' cambiata.
	 *
	 * <p>Pigro di proposito: nessun tick del server tiene d'occhio i negozi di tutti: si scopre che
	 * la rotazione e' passata nel momento in cui qualcuno apre la schermata.
	 */
	public static ShopStock stock(ServerPlayer player) {
		ShopStock current = player.getAttachedOrCreate(ModAttachments.SHOP);
		long rotation = rotation(player);

		if (current.rotation() == rotation) {
			return current;
		}

		ShopStock fresh = generate(player, rotation, 0);
		player.setAttached(ModAttachments.SHOP, fresh);
		return fresh;
	}

	/** Quale finestra temporale e' quella corrente. */
	private static long rotation(ServerPlayer player) {
		int period = Math.max(1, AriseConfig.get().shop().rotationTicks());
		return player.level().getServer().overworld().getGameTime() / period;
	}

	/** Quanti tick mancano al prossimo cambio, per il conto alla rovescia nella schermata. */
	public static long ticksToRotation(ServerPlayer player) {
		int period = Math.max(1, AriseConfig.get().shop().rotationTicks());
		long now = player.level().getServer().overworld().getGameTime();
		return period - Math.floorMod(now, period);
	}

	private static ShopStock generate(ServerPlayer player, long rotation, int refreshes) {
		AriseConfig config = AriseConfig.get();
		ShopConfig shop = config.shop();
		GearConfig gear = config.gear();

		RandomSource random = RandomSource.create(seed(player.getUUID(), rotation, refreshes));
		Rank base = GearManager.hunterRank(player);

		int total = Math.max(1, shop.offerCount());
		int sealed = Math.clamp(shop.sealedCount(), 0, total);
		List<ShopOffer> offers = new ArrayList<>(total);

		for (int i = 0; i < total; i++) {
			Rank rank = shift(base, random, shop);

			// I sigillati in coda: la schermata li mostra in fondo, dove si finisce a guardare
			// quando nessuna delle voci visibili convince.
			if (i >= total - sealed) {
				offers.add(ShopOffer.sealed(rank, sealedPrice(shop, gear, rank)));
			} else {
				GearPiece piece = GearRoll.rollAny(gear, rank, random);
				offers.add(ShopOffer.visible(piece, price(shop, gear, piece.rank(), piece.slot(), random)));
			}
		}

		return new ShopStock(rotation, refreshes, offers);
	}

	/**
	 * Il seed di un assortimento.
	 *
	 * <p>Giocatore, finestra temporale e numero di ritiri: cambiarne uno solo cambia tutte le voci,
	 * e due giocatori nella stessa ora vedono negozi diversi.
	 */
	private static long seed(UUID player, long rotation, int refreshes) {
		return player.getMostSignificantBits() * 31L
				+ player.getLeastSignificantBits()
				+ rotation * 1000003L
				+ refreshes * 7919L;
	}

	/** Il rango di una voce: quello del Cacciatore, con qualche scarto in su e in giu'. */
	private static Rank shift(Rank base, RandomSource random, ShopConfig shop) {
		double roll = random.nextDouble();
		int delta = 0;

		if (roll < shop.rankUpChance()) {
			delta = 1;
		} else if (roll < shop.rankUpChance() + shop.rankDownChance()) {
			delta = -1;
		}

		return Rank.values()[Math.clamp(base.ordinal() + delta, 0, Rank.values().length - 1)];
	}

	// ---------------------------------------------------------------- prezzi

	/**
	 * Il prezzo di un pezzo visibile.
	 *
	 * <p>Ricavato dalla stessa potenza che ha generato il pezzo, non da una tabella a parte: se un
	 * giorno si ritocca il valore di uno slot in config, il prezzo lo segue da solo invece di
	 * restare indietro in silenzio.
	 */
	private static long price(ShopConfig shop, GearConfig gear, Rank rank, GearSlot slot,
			RandomSource random) {
		double jitter = 1.0 + (random.nextDouble() * 2.0 - 1.0) * shop.priceJitter();
		return Math.max(1L, (long) (shop.priceBase() * factor(gear, rank, slot) * jitter));
	}

	/** Il prezzo di un sigillato: la media degli slot, scontata perche' il rischio lo prende chi compra. */
	private static long sealedPrice(ShopConfig shop, GearConfig gear, Rank rank) {
		double sum = 0.0;
		for (GearSlot slot : GearSlot.values()) {
			sum += factor(gear, rank, slot);
		}

		double average = sum / GearSlot.values().length;
		return Math.max(1L, (long) (shop.priceBase() * average * shop.sealedFactor()));
	}

	/** Quante volte un pezzo vale piu' di uno di rango E in uno slot da moltiplicatore 1. */
	private static double factor(GearConfig gear, Rank rank, GearSlot slot) {
		double base = gear.basePower();
		return base <= 0.0 ? 1.0 : gear.power(rank, slot) / base;
	}

	public static long refreshPrice(ServerPlayer player) {
		return AriseConfig.get().shop().refreshPrice(stock(player).refreshes());
	}

	// ---------------------------------------------------------------- operazioni

	/** Compra una voce. */
	public static Component buy(ServerPlayer player, UUID offerId) {
		Component locked = QuestManager.require(player, Unlock.SHOP);
		if (locked != null) {
			return locked;
		}

		ShopStock stock = stock(player);
		Optional<ShopOffer> found = stock.find(offerId);

		if (found.isEmpty()) {
			return Component.translatable("arise.msg.shop.gone");
		}

		ShopOffer offer = found.get();
		GearConfig gear = AriseConfig.get().gear();

		if (!ProgressManager.spendSouls(player, offer.price())) {
			return Component.translatable("arise.msg.shop.no_souls",
					offer.price(), ProgressManager.souls(player));
		}

		GearPiece piece = offer.piece()
				.orElseGet(() -> GearRoll.rollAny(gear, offer.rank(), player.level().getRandom()));

		player.setAttached(ModAttachments.SHOP, stock.without(offerId));
		GearManager.grant(player, piece);
		QuestManager.advance(player, Objective.BUY);

		if (player.level() instanceof ServerLevel level) {
			AriseFx.shopDeal(level, player.position(), piece.rank());
		}

		return Component.translatable("arise.msg.shop.bought", piece.displayName(), offer.price());
	}

	/** Paga per rivedere l'assortimento senza aspettare la rotazione. */
	public static Component refresh(ServerPlayer player) {
		ShopStock stock = stock(player);
		long price = AriseConfig.get().shop().refreshPrice(stock.refreshes());

		if (!ProgressManager.spendSouls(player, price)) {
			return Component.translatable("arise.msg.shop.no_souls", price, ProgressManager.souls(player));
		}

		player.setAttached(ModAttachments.SHOP, generate(player, stock.rotation(), stock.refreshes() + 1));

		if (player.level() instanceof ServerLevel level) {
			AriseFx.shopRefreshed(level, player.position());
		}

		return Component.translatable("arise.msg.shop.refreshed", price);
	}

	/** Il suono di apertura. La schermata la apre il client da solo: legge un attachment. */
	public static void announceOpen(ServerPlayer player) {
		stock(player);

		if (player.level() instanceof ServerLevel level) {
			AriseFx.shopOpened(level, player.position());
		}
	}
}
