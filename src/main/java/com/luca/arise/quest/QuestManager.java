package com.luca.arise.quest;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * La catena degli incarichi, lato server.
 *
 * <p>Il cuore e' {@link #advance}: ogni sistema della mod, quando succede qualcosa di rilevante,
 * lo dice qui. Se quel qualcosa e' cio' che l'incarico corrente sta aspettando, il contatore
 * avanza; altrimenti non succede niente e non costa niente. Nessun sistema sa quali incarichi
 * esistono, e gli incarichi non sanno come funzionano i sistemi.
 *
 * <p>L'altra meta' e' {@link #has}, che e' il guardiano: prima di estrarre un'ombra, di lanciare
 * un'abilita' o di aprire un negozio si passa di qui. Lato server, sempre — il client puo'
 * nascondere un bottone, ma non e' lui a decidere.
 */
public final class QuestManager {

	private QuestManager() {
	}

	public static PlayerQuests get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.QUESTS);
	}

	private static void set(ServerPlayer player, PlayerQuests quests) {
		player.setAttached(ModAttachments.QUESTS, quests);
	}

	// ---------------------------------------------------------------- guardiano

	/** Vero se il giocatore ha gia' ricevuto questo sistema. */
	public static boolean has(ServerPlayer player, Unlock unlock) {
		return get(player).has(unlock);
	}

	/**
	 * Il messaggio di rifiuto se il sistema non e' ancora stato concesso, altrimenti {@code null}.
	 *
	 * <p>Restituisce un {@code Component} invece di un booleano perche' i gestori della mod
	 * parlano gia' questa lingua: {@code Component errore = QuestManager.require(...)} entra
	 * dentro un metodo esistente senza cambiargli la forma.
	 */
	public static Component require(ServerPlayer player, Unlock unlock) {
		return has(player, unlock) ? null : unlock.refusal();
	}

	// ---------------------------------------------------------------- avanzamento

	/**
	 * Segnala che e' successo qualcosa. Se e' quello che l'incarico corrente aspetta, avanza.
	 *
	 * @param amount quante volte e' successo; per gli obiettivi assoluti, il valore raggiunto
	 */
	public static void advance(ServerPlayer player, Objective objective, int amount) {
		PlayerQuests quests = get(player);
		Quest quest = quests.current();

		if (quest == null || quest.objective() != objective) {
			return;
		}

		int progress = quest.objective().absolute()
				? Math.max(quests.progress(), amount)
				: quests.progress() + amount;

		if (progress < quest.amount()) {
			set(player, quests.withProgress(progress));
			return;
		}

		complete(player, quests, quest);
	}

	/** Comodita' per il caso normale, che e' "e' successo una volta". */
	public static void advance(ServerPlayer player, Objective objective) {
		advance(player, objective, 1);
	}

	private static void complete(ServerPlayer player, PlayerQuests quests, Quest quest) {
		set(player, quests.next());

		if (quest.souls() > 0) {
			ProgressManager.addSouls(player, quest.souls());
		}

		if (quest.reward() != null) {
			net.minecraft.world.item.Item item = com.luca.arise.registry.ModItems.byPath(quest.reward());

			if (item != null) {
				com.luca.arise.workshop.WorkshopManager.give(player,
						new net.minecraft.world.item.ItemStack(item));
			}
		}

		if (quest.gearRank() != null) {
			Rank rank = rankOf(quest.gearRank());
			GearPiece piece = GearRoll.rollAny(AriseConfig.get().gear(), rank,
					player.level().getRandom());
			GearManager.grant(player, piece);
		}

		player.sendSystemMessage(Component.translatable("arise.msg.quest.completed", quest.title())
				.withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.translatable("arise.msg.quest.unlocked",
				quest.grants().label()).withStyle(ChatFormatting.GOLD));

		if (player.level() instanceof ServerLevel level) {
			AriseFx.questCompleted(level, player.position());
		}

		announceNext(player);
	}

	/** Il prossimo incarico, annunciato subito: la catena non deve mai lasciare senza un compito. */
	public static void announceNext(ServerPlayer player) {
		Quest next = get(player).current();

		if (next == null) {
			player.sendSystemMessage(Component.translatable("arise.msg.quest.chain_done")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}

		player.sendSystemMessage(Component.translatable("arise.msg.quest.next",
				next.title(), next.description()));
	}

	// ---------------------------------------------------------------- il risveglio

	/**
	 * Il colpo che avrebbe ucciso, e non uccide.
	 *
	 * <p>E' il primo incarico, e l'unico che non si puo' cercare: il Sistema arriva quando serve.
	 * Chiamato da {@code QuestEvents} dentro {@code ALLOW_DEATH}, che deve poter dire "no, questa
	 * morte non avviene".
	 *
	 * @return vero se il Sistema e' intervenuto e la morte va annullata
	 */
	public static boolean awaken(ServerPlayer player) {
		PlayerQuests quests = get(player);

		if (quests.current() != Quest.AWAKENING) {
			return false;
		}

		player.setHealth(player.getMaxHealth());
		player.removeAllEffects();
		player.setRemainingFireTicks(0);

		if (player.level() instanceof ServerLevel level) {
			AriseFx.awakening(level, player.position());
		}

		player.sendSystemMessage(Component.translatable("arise.msg.quest.awakening")
				.withStyle(ChatFormatting.AQUA));

		complete(player, quests, Quest.AWAKENING);
		return true;
	}

	// ---------------------------------------------------------------- prove

	/** Salta l'incarico corrente. Solo per i comandi di prova. */
	public static Component skip(ServerPlayer player) {
		PlayerQuests quests = get(player);
		Quest quest = quests.current();

		if (quest == null) {
			return Component.translatable("arise.msg.quest.chain_done");
		}

		complete(player, quests, quest);
		return Component.translatable("arise.msg.quest.skipped", quest.title());
	}

	/** Concede tutto. Per provare un sistema senza rifare la catena da capo. */
	public static Component grantAll(ServerPlayer player) {
		set(player, new PlayerQuests(Quest.count(), 0));
		return Component.translatable("arise.msg.quest.all_granted");
	}

	/** Rimette il giocatore al risveglio. */
	public static Component reset(ServerPlayer player) {
		set(player, PlayerQuests.INITIAL);
		return Component.translatable("arise.msg.quest.reset");
	}

	private static Rank rankOf(String name) {
		for (Rank rank : Rank.values()) {
			if (rank.getSerializedName().equals(name)) {
				return rank;
			}
		}

		return Rank.E;
	}
}
