package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.fx.AriseFx;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chi mette gli affissi addosso ai mob, e cosa succede quando li portano.
 *
 * <p>Tutta la logica di {@link MobAffix} sta qui e non sparsa nei gestori, per la stessa ragione
 * per cui i particellari passano tutti da {@code AriseFx}: sei comportamenti scritti in sei posti
 * diversi diventano sei comportamenti che nessuno sa piu' elencare.
 *
 * <p>Cinque dei sei si attaccano a un evento di danno che il gioco gia' intercetta e costano zero
 * quando non c'e' nessun affisso in giro. Il sesto — Volatile — ha bisogno di aspettare un secondo
 * fra la morte e lo scoppio, e per quello c'e' una coda: e' l'unico pezzo di questo file che gira
 * a ogni battito, ed e' vuota per tutta la partita tranne il secondo dopo che un Volatile cade.
 */
public final class GateAffixes {

	/** Uno scoppio in attesa: dove, quando, e in quale mondo. */
	private record Pending(ServerLevel level, Vec3 position, long atTick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private GateAffixes() {
	}

	// ---------------------------------------------------------------- assegnazione

	/**
	 * Decide se questo mob merita un affisso, e glielo mette.
	 *
	 * <p>Chiamata una volta per stanza, sul primo mob piazzato: e' cosi' che si rispetta la regola
	 * «mai piu' di un mob con affisso per stanza» senza doverla ricordare altrove.
	 */
	public static void apply(Mob mob, Rank rank, RandomSource random) {
		if (rank.ordinal() < MobAffix.MIN_RANK_ORDINAL) {
			return;
		}

		apply(mob, MobAffix.random(random));
	}

	/**
	 * Mette addosso a questo mob esattamente questo affisso.
	 *
	 * <p>Separata dal tiro perche' il comando di prova deve poter chiedere il Volatile e ottenere
	 * il Volatile. Un comando che tirasse a caso e poi sovrascrivesse farebbe la cosa giusta per
	 * sbaglio, e smetterebbe di farla il giorno in cui l'assegnazione cambia.
	 */
	public static void apply(Mob mob, MobAffix affix) {
		mob.setAttached(ModAttachments.MOB_AFFIX, affix);

		// Il nome sopra la testa e' meta' dell'affisso: un mob che si cura colpendo senza dirlo
		// non e' una regola nuova, e' un mob che non muore e non si capisce perche'.
		mob.setCustomName(affix.nameFor(mob.getType().getDescription()));
		mob.setCustomNameVisible(true);

		if (affix == MobAffix.SWIFT) {
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);

			if (speed != null) {
				speed.setBaseValue(speed.getBaseValue() * MobAffix.SWIFT_SPEED);
			}
		}
	}

	/** L'affisso di questo mob, o {@code null} se non ne ha. */
	public static MobAffix of(Entity entity) {
		return entity instanceof Mob mob ? mob.getAttached(ModAttachments.MOB_AFFIX) : null;
	}

	// ---------------------------------------------------------------- in combattimento

	/**
	 * Quello che succede quando un mob affisso <em>incassa</em> un colpo.
	 *
	 * <p>Il rimbalzo e' il pericolo: un Riflesso che rimanda danno provoca un altro evento di
	 * danno, e se quello rientrasse da qui si avrebbe una partita a ping pong che finisce con uno
	 * stack overflow. La guardia e' semplice — chi rimanda non e' mai affisso a sua volta, perche'
	 * gli affissi stanno solo sui mob di un Gate e il danno rimandato colpisce chi ha colpito.
	 */
	public static void onDamaged(LivingEntity victim, Entity attacker, float taken) {
		MobAffix affix = of(victim);

		if (affix == null || !(victim.level() instanceof ServerLevel level) || taken <= 0.0F) {
			return;
		}

		switch (affix) {
			case THORNED -> {
				if (attacker instanceof LivingEntity living && of(attacker) == null) {
					living.hurtServer(level, level.damageSources().thorns(victim),
							taken * MobAffix.THORNS_SHARE);
					AriseFx.affixPulse(level, living.position(), affix.color());
				}
			}
			case FROSTBOUND -> {
				// Solo la mischia: chi lo colpisce da lontano non lo sta toccando, e il gelo di
				// contatto che arrivasse a sedici blocchi sarebbe una punizione senza contromisura.
				if (attacker instanceof LivingEntity living
						&& living.distanceToSqr(victim) <= 16.0) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
							MobAffix.FROST_TICKS, MobAffix.FROST_LEVEL));
					AriseFx.affixPulse(level, living.position(), affix.color());
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Quello che succede quando un mob affisso <em>infligge</em> un colpo.
	 *
	 * @return il danno aggiuntivo da applicare, o zero
	 */
	public static void onDealt(Entity attacker, LivingEntity victim, float dealt) {
		MobAffix affix = of(attacker);

		if (affix == null || !(attacker instanceof LivingEntity living)
				|| !(living.level() instanceof ServerLevel level) || dealt <= 0.0F) {
			return;
		}

		switch (affix) {
			case THIRSTY -> {
				living.heal(dealt * MobAffix.THIRSTY_SHARE);
				AriseFx.affixPulse(level, living.position(), affix.color());
			}
			case SHADOW_EATER -> {
				// Il colpo e' gia' arrivato: qui si aggiunge la differenza. Solo contro le ombre,
				// ed e' il punto — questo affisso non e' piu' forte, e' piu' forte contro una
				// certa squadra, e per questo la squadra diventa una decisione.
				if (victim instanceof ShadowEntity) {
					victim.hurtServer(level, level.damageSources().magic(),
							dealt * (MobAffix.SHADOW_EATER_FACTOR - 1.0F));
					AriseFx.affixPulse(level, victim.position(), affix.color());
				}
			}
			default -> {
			}
		}
	}

	/** Quando un Volatile cade, mette in conto lo scoppio e lo annuncia. */
	public static void onDeath(LivingEntity victim) {
		if (of(victim) != MobAffix.VOLATILE || !(victim.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 where = victim.position();

		AriseFx.affixVolatileFuse(level, where, MobAffix.VOLATILE_RADIUS);
		PENDING.add(new Pending(level, where, level.getGameTime() + MobAffix.VOLATILE_FUSE));
	}

	// ---------------------------------------------------------------- il battito

	/**
	 * Gli scoppi in attesa che sono maturati.
	 *
	 * <p>Gira una volta per battito del server e per la stragrande maggioranza della partita non
	 * fa niente, perche' la lista e' vuota. E' il prezzo minimo per avere un secondo di preavviso,
	 * e il preavviso e' l'intera differenza fra questo affisso e una punizione arbitraria.
	 */
	public static void tick(long gameTime) {
		if (PENDING.isEmpty()) {
			return;
		}

		PENDING.removeIf(pending -> {
			if (gameTime < pending.atTick()) {
				return false;
			}

			detonate(pending.level(), pending.position());
			return true;
		});
	}

	private static void detonate(ServerLevel level, Vec3 where) {
		AriseFx.affixVolatileBlast(level, where, MobAffix.VOLATILE_RADIUS);

		AABB area = new AABB(where, where).inflate(MobAffix.VOLATILE_RADIUS);

		// Non tocca i mob: un affisso che uccide i suoi compagni farebbe del Volatile la cosa
		// migliore che possa capitare in una stanza affollata, che e' l'opposto di una minaccia.
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity instanceof net.minecraft.world.entity.player.Player
						|| entity instanceof ShadowEntity)) {
			target.hurtServer(level, level.damageSources().magic(), MobAffix.VOLATILE_DAMAGE);
		}

		level.sendParticles(ParticleTypes.EXPLOSION, where.x(), where.y() + 0.5, where.z(),
				1, 0.0, 0.0, 0.0, 0.0);
	}

	/** Dimentica gli scoppi in attesa in questo mondo: serve alla chiusura di un'istanza. */
	public static void forget(ServerLevel level) {
		PENDING.removeIf(pending -> pending.level() == level);
	}
}
