package com.luca.arise.event;

import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemType;
import com.luca.arise.shadow.ShadowManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gli effetti passivi delle gemme, agganciati ai momenti in cui succedono.
 *
 * <p>Nessuno di questi richiede un mixin, e non e' un caso: i cinque effetti sono stati scelti
 * <em>perche'</em> capitano in punti che la mod gia' guarda — un colpo inferto, un colpo incassato,
 * un nemico che cade. Un passivo che avesse richiesto un aggancio nuovo sarebbe stato un passivo
 * per un'altra volta (design §8.6).
 *
 * <p>Ametista e zaffiro non stanno qui ma in {@link ProgressEvents}, dove XP e soul coin vengono
 * calcolati: moltiplicarli altrove avrebbe voluto dire accreditarli due volte e poi correggerli.
 */
public final class GemEvents {

	/**
	 * Impedisce che le spine si rimbalzino da sole.
	 *
	 * <p>Il danno da spine e' danno come tutti gli altri, quindi rientra da questo stesso evento.
	 * Contro un nemico normale la catena si ferma subito, ma fra due portatori di diaspro — o
	 * contro qualunque cosa rimandi indietro il danno — non si fermerebbe. Un flag durante la
	 * restituzione e' il modo piu' piccolo di chiudere la porta.
	 */
	private static boolean reflecting;

	private GemEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}

			if (entity instanceof ServerPlayer victim) {
				thorns(level, victim, source, taken);
				return;
			}

			if (source.getEntity() instanceof ServerPlayer attacker) {
				lifesteal(attacker, taken);
			}
		});
	}

	/** Ematite: una quota del danno inferto torna come vita. */
	private static void lifesteal(ServerPlayer attacker, float dealt) {
		if (reflecting || !GemManager.has(attacker, GemType.HEMATITE)) {
			return;
		}

		double share = GemManager.effect(attacker, GemType.HEMATITE);
		if (share <= 0.0) {
			return;
		}

		// Curare oltre il massimo non fa niente di male: heal si ferma da solo, e non vale la
		// pena di un controllo in piu' su un percorso che gira a ogni colpo.
		attacker.heal((float) (dealt * share));
	}

	/** Diaspro: una quota del danno incassato torna a chi l'ha inferto. */
	private static void thorns(ServerLevel level, ServerPlayer victim, DamageSource source, float taken) {
		if (reflecting || !(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}

		if (attacker == victim || !GemManager.has(victim, GemType.JASPER)) {
			return;
		}

		double share = GemManager.effect(victim, GemType.JASPER);
		float amount = (float) (taken * share);

		if (amount <= 0.0F) {
			return;
		}

		reflecting = true;
		try {
			attacker.hurtServer(level, victim.damageSources().thorns(victim), amount);
		} finally {
			reflecting = false;
		}
	}

	/**
	 * Ossidiana: alla morte di un nemico, una probabilita' che l'ombra si estragga da sola.
	 *
	 * <p>Chiamata da {@link ProgressEvents} subito dopo che l'uccisione e' stata registrata, cosi'
	 * il cadavere e' gia' fra i candidati e l'estrazione automatica passa esattamente dalla stessa
	 * porta di quella a comando — stesse probabilita', stesso tetto d'esercito, stessi effetti.
	 */
	public static void onKill(ServerPlayer player) {
		if (!GemManager.has(player, GemType.OBSIDIAN)) {
			return;
		}

		double chance = GemManager.effect(player, GemType.OBSIDIAN);
		if (chance <= 0.0 || player.level().getRandom().nextDouble() >= chance) {
			return;
		}

		Component result = ShadowManager.extract(player);
		player.sendSystemMessage(Component.translatable("arise.msg.gem.auto_extract", result));
	}
}
