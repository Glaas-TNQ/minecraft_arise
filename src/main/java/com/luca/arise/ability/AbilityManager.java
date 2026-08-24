package com.luca.arise.ability;

import java.util.List;
import java.util.Map;

import com.luca.arise.config.AbilityConfig;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.mana.ManaManager;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.StatThreshold;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Esecuzione delle abilità. Interamente lato server.
 *
 * <p>Il client manda solo "ho premuto il tasto n": livello, tempo di ricarica e soul coin li
 * verifica qui. È la stessa regola di tutto il resto della mod, e qui conta il doppio — un
 * teletrasporto validato dal client è un invito a barare.
 */
public final class AbilityManager {

	/**
	 * Raggio della cupola del Dominio, in blocchi.
	 *
	 * <p>Solo estetico: il potenziamento raggiunge tutte le ombre evocate ovunque siano, ma
	 * disegnare una cupola larga sessantaquattro blocchi sarebbe illeggibile. Questa è la misura
	 * che si capisce guardandola.
	 */
	private static final double DOMAIN_VISUAL_RADIUS = 4.5;

	private AbilityManager() {
	}

	public static AbilityCooldowns cooldowns(ServerPlayer player) {
		AbilityCooldowns cooldowns = player.getAttached(ModAttachments.COOLDOWNS);
		return cooldowns == null ? AbilityCooldowns.EMPTY : cooldowns;
	}

	/**
	 * Verifica i requisiti e, se sono soddisfatti, esegue l'abilità.
	 *
	 * @return il messaggio da mostrare al giocatore
	 */
	public static Component use(ServerPlayer player, Ability ability) {
		Component locked = QuestManager.require(player, Unlock.ABILITIES);
		if (locked != null) {
			return locked;
		}

		int level = ProgressManager.get(player).level();
		if (level < ability.requiredLevel()) {
			return Component.translatable("arise.msg.ability.locked",
					ability.label(), ability.requiredLevel());
		}

		long now = player.level().getGameTime();
		AbilityCooldowns cooldowns = cooldowns(player);

		if (!cooldowns.isReady(ability, now)) {
			long seconds = Math.max(1, (cooldowns.readyAt(ability) - now) / 20);
			return Component.translatable("arise.msg.ability.cooldown", ability.label(), seconds);
		}

		if (ability.soulCost() > 0 && !ProgressManager.spendSouls(player, ability.soulCost())) {
			return Component.translatable("arise.msg.ability.no_souls",
					ability.soulCost(), ProgressManager.souls(player));
		}

		AbilityConfig config = AriseConfig.get().abilities();

		// Il volo si paga da solo, dentro il suo gestore: e' l'unica abilita' che non e' un momento
		// ma uno stato, e il suo costo continua dopo che questo metodo e' finito da un pezzo.
		if (ability == Ability.SHADOW_FLIGHT) {
			Component toggled = FlightManager.toggle(player);

			if (toggled != null) {
				player.setAttached(ModAttachments.COOLDOWNS,
						cooldowns.with(ability, now + cooldownFor(player, ability, config)));
			}

			return toggled;
		}

		// Il Mana si spende prima dell'effetto e si rende se l'effetto non c'e' stato, come i soul
		// coin. Prima dei soul coin no: quelli sono gia' stati tolti sopra, e un rifiuto per Mana
		// dopo aver preso le anime sarebbe un prelievo per niente — per questo il rimborso delle
		// anime sta anche su questo ramo.
		int manaCost = config.mana().cost(ability);

		if (!ManaManager.spend(player, manaCost)) {
			if (ability.soulCost() > 0) {
				ProgressManager.addSouls(player, ability.soulCost());
			}

			return Component.translatable("arise.msg.ability.no_mana", ability.label(), manaCost,
					ManaManager.current(player));
		}

		Component result = switch (ability) {
			case SHADOW_STEP -> shadowStep(player, config);
			case SHADOW_EXCHANGE -> shadowExchange(player);
			case MONARCH_DOMAIN -> monarchDomain(player, config);
			case SOVEREIGN_AUTHORITY -> sovereignAuthority(player, config);
			case SHADOW_FLIGHT -> null;   // gia' gestito sopra: qui non ci si arriva
		};

		// Il tempo di ricarica parte solo se l'abilità ha davvero fatto qualcosa: uno scambio
		// senza ombre da scambiare non deve costare trenta secondi di attesa.
		if (result != null) {
			player.setAttached(ModAttachments.COOLDOWNS,
					cooldowns.with(ability, now + cooldownFor(player, ability, config)));
			QuestManager.advance(player, Objective.USE_ABILITY);
			return result;
		}

		// Rimborso, se l'abilità è fallita dopo aver pagato.
		if (ability.soulCost() > 0) {
			ProgressManager.addSouls(player, ability.soulCost());
		}

		ManaManager.refund(player, manaCost);

		return Component.translatable("arise.msg.ability.failed", ability.label());
	}

	// ---------------------------------------------------------------- effetti

	/**
	 * Scatto in avanti, fermandosi prima di finire dentro un muro.
	 *
	 * <p>Avanza mezzo blocco alla volta verificando che l'ingombro del giocatore ci stia: passare
	 * attraverso le pareti sarebbe comodo dentro un Gate e rovinerebbe ogni dungeon.
	 */
	/**
	 * Quanto dura la ricarica di quest'abilita' per questo Cacciatore.
	 *
	 * <p>La terza soglia di Agilita' dimezza quella del Passo d'ombra, e solo quella: dimezzarle
	 * tutte trasformerebbe una statistica di movimento in uno sconto generale, che e' esattamente
	 * il genere di bonus che non si nota e non fa scegliere niente.
	 */
	private static int cooldownFor(ServerPlayer player, Ability ability, AbilityConfig config) {
		int base = config.cooldownTicks(ability);

		if (ability == Ability.SHADOW_STEP
				&& ProgressManager.reached(player, StatThreshold.AGILITY_STEP_HASTE)) {
			return Math.max(1, base / 2);
		}

		return base;
	}

	private static Component shadowStep(ServerPlayer player, AbilityConfig config) {
		Vec3 direction = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		if (direction.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 from = player.position();
		Vec3 best = from;

		// La seconda soglia di Agilita' allunga il balzo di meta'. Il muro lo ferma lo stesso: si
		// arriva piu' lontano, non si attraversa di piu'.
		double reach = config.dashDistance()
				* (1.0 + (ProgressManager.reached(player, StatThreshold.AGILITY_STEP_REACH)
						? StatThreshold.STEP_REACH_BONUS
						: 0.0));

		for (double distance = 0.5; distance <= reach; distance += 0.5) {
			Vec3 candidate = from.add(direction.scale(distance));
			AABB box = player.getBoundingBox().move(candidate.subtract(from));

			if (!player.level().noCollision(player, box)) {
				break;
			}

			best = candidate;
		}

		if (best == from) {
			return null;
		}

		player.teleportTo(best.x(), best.y(), best.z());
		AriseFx.dash(player.level(), from, best);

		return Component.translatable("arise.msg.ability.used", Ability.SHADOW_STEP.label());
	}

	/** Scambia posto con l'ombra evocata più lontana: una via di fuga, o un modo per aprirsi la strada. */
	private static Component shadowExchange(ServerPlayer player) {
		ShadowEntity target = null;
		double bestDistance = -1.0;

		for (ShadowEntity shadow : summonedShadows(player)) {
			double distance = shadow.distanceToSqr(player);
			if (distance > bestDistance) {
				bestDistance = distance;
				target = shadow;
			}
		}

		if (target == null) {
			return null;
		}

		ServerLevel level = player.level();
		Vec3 playerPosition = player.position();
		Vec3 shadowPosition = target.position();

		player.teleportTo(shadowPosition.x(), shadowPosition.y(), shadowPosition.z());
		target.snapTo(playerPosition.x(), playerPosition.y(), playerPosition.z(),
				target.getYRot(), target.getXRot());

		AriseFx.exchange(level, playerPosition, shadowPosition, target.getColor());

		return Component.translatable("arise.msg.ability.exchanged", target.getName());
	}

	/** Potenzia il Monarca e tutto l'esercito evocato. */
	private static Component monarchDomain(ServerPlayer player, AbilityConfig config) {
		int duration = config.domainDurationTicks();
		int boosted = 0;

		applyBuff(player, duration);
		for (ShadowEntity shadow : summonedShadows(player)) {
			applyBuff(shadow, duration);
			boosted++;
		}

		AriseFx.domain(player.level(), player.position(), DOMAIN_VISUAL_RADIUS);

		return Component.translatable("arise.msg.ability.domain", boosted);
	}

	private static void applyBuff(LivingEntity entity, int duration) {
		entity.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 1));
		entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, 0));
	}

	/** Piega i nemici nel raggio: rallentati, indeboliti, e visibili attraverso i muri. */
	private static Component sovereignAuthority(ServerPlayer player, AbilityConfig config) {
		ServerLevel level = player.level();
		double radius = config.authorityRadius();
		int duration = config.authorityDurationTicks();

		AABB area = player.getBoundingBox().inflate(radius);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
				entity -> entity instanceof Enemy && entity.isAlive());

		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 2));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1));
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0));
		}

		if (targets.isEmpty()) {
			return null;
		}

		AriseFx.authority(level, player.position(), radius);

		return Component.translatable("arise.msg.ability.authority", targets.size());
	}

	/** Le ombre di questo giocatore attualmente nel mondo, cercate attorno a lui. */
	private static List<ShadowEntity> summonedShadows(ServerPlayer player) {
		AABB area = player.getBoundingBox().inflate(64.0);
		return player.level().getEntitiesOfClass(ShadowEntity.class, area,
				shadow -> shadow.isAlive() && shadow.getOwner() == player);
	}

	/** Elimina i tempi di ricarica scaduti, per non tenerli in giro all'infinito. */
	public static void prune(ServerPlayer player, long now) {
		AbilityCooldowns cooldowns = cooldowns(player);
		if (cooldowns.readyAt().isEmpty()) {
			return;
		}

		Map<Ability, Long> alive = new java.util.EnumMap<>(Ability.class);
		for (Map.Entry<Ability, Long> entry : cooldowns.readyAt().entrySet()) {
			if (entry.getValue() > now) {
				alive.put(entry.getKey(), entry.getValue());
			}
		}

		if (alive.size() != cooldowns.readyAt().size()) {
			player.setAttached(ModAttachments.COOLDOWNS, new AbilityCooldowns(alive));
		}
	}
}
