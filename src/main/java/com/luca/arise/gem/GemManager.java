package com.luca.arise.gem;

import java.util.Optional;
import java.util.UUID;

import com.luca.arise.city.CityManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GemConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gear.GearItems;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gemme e incastonature, lato server.
 *
 * <p>La regola che da' forma a tutto il resto: <strong>incastonare si fa ovunque, estrarre una
 * gemma intatta solo al banco dell'Associazione</strong> (design §8.6). Mettere una gemma e' un
 * gesto libero; toglierla senza romperla e' un servizio, costa soul coin e costringe a tornare in
 * citta'. E' un pozzo di risorse e finalmente un motivo per viaggiare.
 *
 * <p>Distruggere una gemma per liberare l'incastonatura si puo' fare ovunque e non costa niente:
 * chi non vuole tornare in citta' paga in gemma invece che in monete.
 */
public final class GemManager {

	private GemManager() {
	}

	private static PlayerGear gear(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GEAR);
	}

	private static void set(ServerPlayer player, PlayerGear gear) {
		player.setAttached(ModAttachments.GEAR, gear);
		ProgressManager.applyAttributes(player);
	}

	// ---------------------------------------------------------------- operazioni

	/** Mette una gemma nella sacca. */
	public static Component grant(ServerPlayer player, Gem gem) {
		PlayerGear current = gear(player);
		int limit = AriseConfig.get().gems().pouchSize();

		if (current.pouch().size() >= limit) {
			return Component.translatable("arise.msg.gem.pouch_full", limit);
		}

		set(player, current.withGem(gem));
		return Component.translatable("arise.msg.gem.granted", gem.displayName());
	}

	/** Incastona una gemma della sacca in un pezzo. Si fa ovunque. */
	public static Component socket(ServerPlayer player, UUID gemId, UUID pieceId) {
		Component locked = QuestManager.require(player, Unlock.GEMS);
		if (locked != null) {
			return locked;
		}

		PlayerGear current = gear(player);
		Optional<Gem> gem = current.findGem(gemId);
		GearPiece piece = GearItems.piece(GearManager.findStack(player, pieceId));

		if (gem.isEmpty() || piece == null) {
			return Component.translatable("arise.msg.gem.unknown");
		}

		if (piece.freeSockets() <= 0) {
			return Component.translatable("arise.msg.gem.no_socket", piece.displayName());
		}

		// Prima si scrive nell'oggetto, poi si toglie la gemma dalla sacca: se il pezzo fosse
		// sparito nel frattempo, meglio una gemma ancora in sacca che una gemma svanita.
		if (!GearManager.replace(player, piece.withGem(gem.get()))) {
			return Component.translatable("arise.msg.gem.unknown");
		}

		set(player, gear(player).withoutGem(gemId));

		if (player.level() instanceof ServerLevel level) {
			AriseFx.gemSocketed(level, player.position(), gem.get().rank());
		}

		return Component.translatable("arise.msg.gem.socketed",
				gem.get().displayName(), piece.displayName());
	}

	/**
	 * Estrae una gemma intatta. Solo al banco dell'Associazione, e a pagamento.
	 *
	 * <p>Le anime si tolgono <em>dopo</em> aver verificato che ci sia posto nella sacca, per la
	 * stessa ragione per cui il negozio verifica lo zaino prima di incassare.
	 */
	public static Component extract(ServerPlayer player, UUID gemId) {
		GemConfig config = AriseConfig.get().gems();

		if (!CityManager.atAssociation(player, config.benchRadius())) {
			return Component.translatable("arise.msg.gem.needs_bench");
		}

		PlayerGear current = gear(player);
		GearPiece host = hostOf(player, gemId);

		if (host == null) {
			return Component.translatable("arise.msg.gem.unknown");
		}

		Gem gem = host.gems().stream().filter(g -> g.id().equals(gemId)).findFirst().orElseThrow();

		if (current.pouch().size() >= config.pouchSize()) {
			return Component.translatable("arise.msg.gem.pouch_full", config.pouchSize());
		}

		long cost = (long) config.extractCost();
		if (!ProgressManager.spendSouls(player, cost)) {
			return Component.translatable("arise.msg.shop.no_souls", cost, ProgressManager.souls(player));
		}

		GearManager.replace(player, host.withoutGem(gemId));
		set(player, gear(player).withGem(gem));

		if (player.level() instanceof ServerLevel level) {
			AriseFx.gemExtracted(level, player.position(), gem.rank());
		}

		return Component.translatable("arise.msg.gem.extracted", gem.displayName(), cost);
	}

	/** Rompe una gemma incastonata per liberare il posto. Ovunque, gratis, e senza ritorno. */
	public static Component shatter(ServerPlayer player, UUID gemId) {
		PlayerGear current = gear(player);
		GearPiece host = hostOf(player, gemId);

		if (host != null) {
			Gem gem = host.gems().stream().filter(g -> g.id().equals(gemId)).findFirst().orElseThrow();
			GearManager.replace(player, host.withoutGem(gemId));
			ProgressManager.applyAttributes(player);
			return Component.translatable("arise.msg.gem.shattered", gem.displayName());
		}

		// Non incastonata: allora e' nella sacca, e buttarla via e' la stessa operazione.
		Optional<Gem> loose = current.findGem(gemId);
		if (loose.isEmpty()) {
			return Component.translatable("arise.msg.gem.unknown");
		}

		set(player, current.withoutGem(gemId));
		return Component.translatable("arise.msg.gem.shattered", loose.get().displayName());
	}

	/** Il pezzo che porta questa gemma, o {@code null} se non e' incastonata da nessuna parte. */
	private static GearPiece hostOf(ServerPlayer player, UUID gemId) {
		for (GearPiece piece : GearManager.owned(player)) {
			if (piece.gems().stream().anyMatch(gem -> gem.id().equals(gemId))) {
				return piece;
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- effetti passivi

	/**
	 * Quanto vale un effetto per questo giocatore, sommando le gemme <em>indossate</em> e
	 * applicando il tetto.
	 *
	 * <p>Il tetto e' la ragione per cui questa funzione esiste invece di una somma sparsa nei
	 * gestori: dieci anelli di rango S con quattro incastonature ciascuno sono quaranta gemme, e
	 * senza un limite il furto di vita arriverebbe al cento per cento.
	 */
	public static double effect(ServerPlayer player, GemType type) {
		GemConfig config = AriseConfig.get().gems();
		double total = 0.0;

		for (GearPiece piece : GearManager.worn(player)) {
			for (Gem gem : piece.gems()) {
				if (gem.type() == type) {
					total += gem.magnitude(config);
				}
			}
		}

		return Math.min(total, config.cap(type));
	}

	/** Vero se il giocatore ha almeno una gemma di questo tipo addosso. Evita conti inutili. */
	public static boolean has(ServerPlayer player, GemType type) {
		for (GearPiece piece : GearManager.worn(player)) {
			for (Gem gem : piece.gems()) {
				if (gem.type() == type) {
					return true;
				}
			}
		}

		return false;
	}

	/** Una gemma nuova, tirata al momento. */
	public static Gem roll(ServerPlayer player, GemType type, Rank rank) {
		AriseConfig config = AriseConfig.get();
		return GemRoll.roll(config.gems(), config.gear(), type, rank, player.level().getRandom());
	}
}
