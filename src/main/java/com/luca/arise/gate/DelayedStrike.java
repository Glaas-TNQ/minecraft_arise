package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.fx.AriseFx;
import com.luca.arise.shadow.ShadowEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Un colpo ad area che arriva <em>dopo</em> essere stato annunciato.
 *
 * <p>E' il meccanismo che sta sotto il principio P4 del PRD — «niente succede senza preavviso» — e
 * sta in un posto solo perche' due cose diverse lo usano: lo scoppio di un mob Volatile e la
 * martellata del Sovrano. Due code separate sarebbero due modi di aspettare un secondo, e prima o
 * poi uno dei due si dimenticherebbe di disegnare l'anello.
 *
 * <p>Il contratto e' quello, ed e' tutto qui: <strong>chi programma un colpo disegna l'anello
 * adesso</strong>, e il colpo cade dove l'anello era. Un anello che compare e un danno che arriva
 * altrove sarebbero peggio di nessun anello.
 *
 * <p>La coda e' vuota per la stragrande maggioranza della partita, e il battito che la guarda esce
 * alla prima riga quando lo e'.
 */
public final class DelayedStrike {

	/** Un colpo in attesa: dove, quanto largo, quanto forte, e in quale mondo. */
	private record Pending(ServerLevel level, Vec3 position, double radius, float damage,
			long atTick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private DelayedStrike() {
	}

	/**
	 * Annuncia un colpo e lo mette in conto.
	 *
	 * <p>L'anello lo disegna questa, non il chiamante: e' l'unico modo di garantire che ogni colpo
	 * ritardato della mod sia annunciato, invece di sperare che chi lo programma se lo ricordi.
	 */
	public static void schedule(ServerLevel level, Vec3 where, double radius, float damage,
			int delayTicks) {
		AriseFx.telegraph(level, where, radius);
		PENDING.add(new Pending(level, where, radius, damage, level.getGameTime() + delayTicks));
	}

	/** I colpi maturati. Chiamata una volta per battito del server. */
	public static void tick(long gameTime) {
		if (PENDING.isEmpty()) {
			return;
		}

		PENDING.removeIf(pending -> {
			if (gameTime < pending.atTick()) {
				return false;
			}

			land(pending);
			return true;
		});
	}

	private static void land(Pending pending) {
		ServerLevel level = pending.level();
		Vec3 where = pending.position();

		AriseFx.telegraphStrike(level, where, pending.radius());

		AABB area = new AABB(where, where).inflate(pending.radius());

		// Colpisce solo il Monarca e il suo esercito. Un'area che ferisse anche i mob farebbe della
		// martellata del Sovrano la cosa migliore che possa capitare in una stanza affollata, e
		// dello scoppio di un Volatile un regalo: l'opposto di una minaccia, in entrambi i casi.
		//
		// E passa da `generic()` e non da `magic()`, che e' una correzione di sostanza. Il tipo di danno magico sta
		// nel tag `bypasses_armor`: la martellata del Sovrano avrebbe ignorato l'armatura, e con
		// lei tutta la Resistenza — cioe' una delle quattro statistiche spendibili non avrebbe
		// fatto niente contro l'attacco principale del boss piu' duro del gioco. Un giocatore che
		// avesse messo cento punti li' se ne sarebbe accorto e non avrebbe saputo perche'.
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity instanceof Player || entity instanceof ShadowEntity)) {
			target.hurtServer(level, level.damageSources().generic(), pending.damage());
		}
	}

	/** Dimentica i colpi in attesa in questo mondo: serve alla chiusura di un'istanza. */
	public static void forget(ServerLevel level) {
		PENDING.removeIf(pending -> pending.level() == level);
	}
}
