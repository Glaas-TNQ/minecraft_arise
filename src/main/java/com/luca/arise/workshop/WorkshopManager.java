package com.luca.arise.workshop;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.shadow.ShadowData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Le anime in esubero, viste da fuori dell'Officina.
 *
 * <p>Sta qui la conversione fra i due modi in cui un'anima puo' esistere in questa mod: come
 * <em>ombra</em> nell'esercito e come <em>oggetto</em> nel mondo. E' un ponte a due sensi, ed e'
 * l'idea che tiene insieme il combattimento e l'automazione: quello che non entra nell'esercito
 * non si butta, si mette a lavorare; quello che ha lavorato abbastanza puo' rientrare.
 */
public final class WorkshopManager {

	private WorkshopManager() {
	}

	/** Mette lo stack nell'inventario, o per terra ai piedi del giocatore se non ci sta. */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/**
	 * Consegna un'anima e lo dice.
	 *
	 * <p>Il messaggio non e' cortesia: un oggetto che compare in fondo all'inventario mentre si sta
	 * combattendo passa inosservato, e un'anima raccolta senza accorgersene e' un'anima che verra'
	 * buttata via alla prima pulizia dello zaino.
	 */
	public static void grantSoul(ServerPlayer player, LooseSoul soul) {
		give(player, SoulItems.stack(soul));
		com.luca.arise.quest.QuestManager.advance(player, com.luca.arise.quest.Objective.COLLECT_SOUL);

		player.sendSystemMessage(Component.translatable("arise.msg.workshop.soul_gained",
				soul.displayName(), SoulItems.rankOf(soul).label()));
	}

	/**
	 * L'anima di un'ombra congedata.
	 *
	 * <p>Conserva vita, danno e livello dell'ombra: congedare non e' distruggere, e chi si e'
	 * portato dietro un'ombra fino al livello dodici non deve ritrovarsi in mano un'anima appena
	 * nata. Il resto del valore torna comunque in soul coin, come prima.
	 */
	public static LooseSoul soulOf(ShadowData shadow) {
		return new LooseSoul(shadow.id(), shadow.sourceType(), shadow.level(),
				shadow.baseMaxHealth(), shadow.baseAttackDamage(), java.util.List.of());
	}

	/**
	 * Il danno che avra' l'ombra nata da quest'anima.
	 *
	 * <p>E' l'unico punto in cui la Ferocia conta, ed e' anche l'unico tratto che non serve a
	 * niente dentro un macchinario: chi fonde per l'esercito e chi fonde per l'officina cercano
	 * due cose diverse, ed e' cosi' che si vede.
	 */
	public static double enlistedDamage(LooseSoul soul) {
		if (!soul.has(SoulTrait.FEROCIA)) {
			return soul.damage();
		}

		return soul.damage() * (1.0 + AriseConfig.get().workshop().traitPower(SoulTrait.FEROCIA));
	}
}
