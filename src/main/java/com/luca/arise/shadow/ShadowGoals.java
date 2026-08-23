package com.luca.arise.shadow;

import java.util.EnumSet;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.fx.AriseFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * I comportamenti che distinguono un'ombra dall'altra, e quelli che eseguono gli ordini.
 *
 * <p>Tutti in un file solo, e non uno per classe come il resto del progetto: sono cinque goal da
 * quaranta righe che parlano fra loro e con {@link ShadowEntity}, e cinque file avrebbero cinque
 * volte la stessa intestazione per lo stesso concetto. Il criterio resta quello di sempre — un file
 * per idea — e l'idea qui e' una: <em>cosa fa un'ombra oltre a seguire e picchiare.</em>
 *
 * <p>Nota comune a tutti: nessuno di questi goal viene registrato in modo condizionale. L'archetipo
 * di un'ombra si conosce solo in {@code applyData}, che gira <em>dopo</em> il costruttore e quindi
 * dopo {@code registerGoals}; registrarli a seconda dell'archetipo vorrebbe dire ricostruire i goal
 * a evocazione avvenuta, che e' un'operazione delicata su un'entita' gia' nel mondo. Si registrano
 * tutti, e ognuno controlla in {@code canUse} se e' il suo turno. Costa un confronto fra enum ogni
 * pochi tick.
 */
public final class ShadowGoals {

	private ShadowGoals() {
	}

	private static ShadowConfig.Behaviour behaviour() {
		return AriseConfig.get().shadows().legion().behaviour();
	}

	// ---------------------------------------------------------------- il Colosso

	/**
	 * La provocazione: chi puntava il Monarca finisce a picchiare il Colosso.
	 *
	 * <p>E' l'unica cosa che in questa mod fa davvero la differenza fra vivere e morire in un Gate
	 * di rango alto, ed e' il motivo per cui un Colosso vale un posto in squadra anche se il suo
	 * danno e' l'ottanta per cento di quello di una Guardia. Senza, "tanta vita" e' una statistica
	 * che non serve a nessuno: i mob continuano a colpire il giocatore, che di vita ne ha venti.
	 */
	public static class Taunt extends Goal {

		private final ShadowEntity shadow;
		private int cooldown;

		public Taunt(ShadowEntity shadow) {
			this.shadow = shadow;
			// Nessun flag: provocare non muove ne' guarda, quindi non deve escludere nient'altro.
			setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			// In passiva niente: la provocazione tira addosso dei nemici, e tirarseli addosso e'
			// una forma di combattere. Un esercito a cui e' stato detto di non combattere non deve
			// far succedere niente premendo il tasto sbagliato.
			return shadow.archetype() == ShadowArchetype.TANK
					&& shadow.getOwner() instanceof ServerPlayer owner
					&& ShadowManager.stance(owner) != ShadowStance.PASSIVE;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void tick() {
			if (cooldown-- > 0) {
				return;
			}

			ShadowConfig.Behaviour behaviour = behaviour();
			cooldown = behaviour.tauntIntervalTicks();

			if (!(shadow.getOwner() instanceof ServerPlayer owner)
					|| !(shadow.level() instanceof ServerLevel level)) {
				return;
			}

			double radius = behaviour.tauntRadius();
			List<Mob> stolen = level.getEntitiesOfClass(Mob.class,
					shadow.getBoundingBox().inflate(radius),
					mob -> mob != shadow && mob.getTarget() == owner && mob.canAttack(shadow));

			if (stolen.isEmpty()) {
				return;
			}

			for (Mob mob : stolen) {
				mob.setTarget(shadow);
			}

			shadow.setTarget(stolen.get(0));
			AriseFx.shadowTaunt(level, shadow.position(), shadow.getColor());
		}
	}

	// ---------------------------------------------------------------- il Mago

	/**
	 * La lancia d'ombra: danno a distanza senza un proiettile.
	 *
	 * <p>Un proiettile vero vorrebbe dire un'entita' in piu' da registrare, sincronizzare e
	 * renderizzare, per un effetto che a sedici blocchi nessuno vede volare. Qui il colpo e'
	 * istantaneo e si <em>disegna</em>: una fila di particelle dal mago al bersaglio, con
	 * {@code sendParticles} a conteggio zero, che e' il modo di disegnare una linea invece di una
	 * nuvola. Il danno pretende linea di vista, quindi un muro protegge davvero.
	 */
	public static class Lance extends Goal {

		private final ShadowEntity shadow;
		private int cooldown;

		public Lance(ShadowEntity shadow) {
			this.shadow = shadow;
			setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (shadow.archetype() != ShadowArchetype.MAGE) {
				return false;
			}

			LivingEntity target = shadow.getTarget();
			ShadowConfig.Behaviour behaviour = behaviour();

			// La linea di vista sta qui e non solo nel colpo, ed e' importante: questo goal occupa
			// lo sguardo, e finche' occupa lo sguardo la mischia non puo' partire. Un Mago che
			// "prende la mira" su un bersaglio dietro a un muro resterebbe fermo per sempre.
			return target != null && target.isAlive()
					&& shadow.distanceToSqr(target) <= behaviour.lanceRange() * behaviour.lanceRange()
					&& shadow.getSensing().hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void stop() {
			cooldown = 0;
		}

		@Override
		public void tick() {
			LivingEntity target = shadow.getTarget();
			if (target == null) {
				return;
			}

			shadow.lookAt(target, 30.0F, 30.0F);

			if (cooldown-- > 0) {
				return;
			}

			ShadowConfig.Behaviour behaviour = behaviour();
			cooldown = behaviour.lanceIntervalTicks();

			if (!(shadow.level() instanceof ServerLevel level) || !shadow.getSensing().hasLineOfSight(target)) {
				return;
			}

			float damage = (float) (shadow.getAttributeValue(
					net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
					* behaviour.lanceDamageFactor());

			AriseFx.shadowLance(level, shadow.getEyePosition(), target.position(), shadow.getColor());
			target.hurtServer(level, level.damageSources().indirectMagic(shadow, shadow), damage);
		}
	}

	/**
	 * Il Mago indietreggia: sotto la distanza minima si sposta invece di menare le mani.
	 *
	 * <p>Senza questo un Mago fa esattamente quello che faceva prima — corre addosso al nemico — e
	 * con il sessanta per cento della vita di una Guardia muore in tre colpi. La distanza <em>e'</em>
	 * il suo archetipo.
	 */
	public static class KeepDistance extends Goal {

		private final ShadowEntity shadow;
		private Vec3 retreat;

		public KeepDistance(ShadowEntity shadow) {
			this.shadow = shadow;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (shadow.archetype() != ShadowArchetype.MAGE) {
				return false;
			}

			LivingEntity target = shadow.getTarget();
			if (target == null || !target.isAlive()) {
				return false;
			}

			double minimum = behaviour().lanceMinRange();
			if (shadow.distanceToSqr(target) > minimum * minimum) {
				return false;
			}

			// Il punto di ritirata: dritto all'indietro rispetto al bersaglio, il doppio della
			// distanza minima. Se non e' raggiungibile la navigazione se ne accorge da sola.
			Vec3 away = shadow.position().subtract(target.position());
			if (away.lengthSqr() < 1.0E-4) {
				return false;
			}

			retreat = shadow.position().add(away.normalize().scale(minimum * 2.0));
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return retreat != null && !shadow.getNavigation().isDone();
		}

		@Override
		public void start() {
			shadow.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.4);
		}

		@Override
		public void stop() {
			retreat = null;
		}
	}

	// ---------------------------------------------------------------- la Guardia

	/**
	 * Mettersi in mezzo: la Guardia si piazza fra il Monarca e chi lo sta puntando.
	 *
	 * <p>Attaccare quel nemico lo fanno gia' tutti. Quello che fa solo la Guardia e' <em>occupare
	 * lo spazio</em>: due blocchi davanti al Monarca sulla linea del nemico. In un corridoio di un
	 * Gate questo significa che il mob deve passarle attraverso, e in pratica non passa.
	 */
	public static class Interpose extends Goal {

		private final ShadowEntity shadow;
		private LivingEntity threat;

		public Interpose(ShadowEntity shadow) {
			this.shadow = shadow;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (shadow.archetype() != ShadowArchetype.GUARD
					|| !(shadow.getOwner() instanceof ServerPlayer owner)
					|| !(shadow.level() instanceof ServerLevel level)) {
				return false;
			}

			// Se sta gia' picchiando qualcuno, il posto giusto e' dove si trova.
			if (shadow.getTarget() != null && shadow.getTarget().isAlive()) {
				return false;
			}

			double radius = behaviour().interposeRange();
			AABB around = owner.getBoundingBox().inflate(radius);

			threat = level.getEntitiesOfClass(Mob.class, around,
							mob -> mob instanceof Enemy && mob.getTarget() == owner)
					.stream()
					.min((a, b) -> Double.compare(a.distanceToSqr(owner), b.distanceToSqr(owner)))
					.orElse(null);

			return threat != null;
		}

		@Override
		public boolean canContinueToUse() {
			return threat != null && threat.isAlive() && !shadow.getNavigation().isDone()
					&& shadow.getTarget() == null;
		}

		@Override
		public void start() {
			if (!(shadow.getOwner() instanceof ServerPlayer owner) || threat == null) {
				return;
			}

			Vec3 toward = threat.position().subtract(owner.position());
			if (toward.lengthSqr() < 1.0E-4) {
				return;
			}

			Vec3 spot = owner.position().add(toward.normalize().scale(2.0));
			shadow.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);
		}

		@Override
		public void stop() {
			threat = null;
		}
	}

	// ---------------------------------------------------------------- gli ordini

	/**
	 * Il fuoco concentrato: tutte le ombre in campo su un bersaglio solo.
	 *
	 * <p>E' l'ordine che mancava, ed e' quello che nel manhwa <em>e'</em> il personaggio: il Monarca
	 * indica e l'esercito esegue. Senza, quattro ombre in uno scontro con quattro nemici ne
	 * prendono uno a testa e vincono tutti e quattro i duelli a meta'; con, il primo cade in due
	 * secondi e gli altri tre combattono contro quattro.
	 *
	 * <p>Priorita' zero nel selettore dei bersagli: un ordine esplicito batte qualunque cosa
	 * l'ombra avrebbe scelto da sola, postura compresa.
	 */
	public static class Focus extends Goal {

		private final ShadowEntity shadow;
		private LivingEntity target;

		public Focus(ShadowEntity shadow) {
			this.shadow = shadow;
			setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			target = resolve();
			return target != null;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity current = resolve();
			return current != null && current == target;
		}

		@Override
		public void start() {
			shadow.setTarget(target);
		}

		@Override
		public void tick() {
			// Ri-assegnato a ogni battito: qualunque altro goal glielo tolga, l'ordine lo rimette.
			if (target != null && shadow.getTarget() != target) {
				shadow.setTarget(target);
			}
		}

		@Override
		public void stop() {
			if (shadow.getTarget() == target) {
				shadow.setTarget(null);
			}

			target = null;
		}

		/** L'entita' indicata dall'ordine, o {@code null} se l'ordine non c'e' piu' o non vale. */
		private LivingEntity resolve() {
			if (!(shadow.getOwner() instanceof ServerPlayer owner)
					|| !(shadow.level() instanceof ServerLevel level)) {
				return null;
			}

			return ShadowManager.orders(owner).focus()
					.map(level::getEntity)
					.filter(entity -> entity instanceof LivingEntity living && living.isAlive()
							&& !isProtected(living, owner))
					.map(LivingEntity.class::cast)
					.orElse(null);
		}
	}

	/**
	 * Tenere la posizione: l'ombra resta attorno al punto ordinato invece di seguire.
	 *
	 * <p>Non e' "stai fermo": dentro il raggio si muove e combatte normalmente, e quando ne esce
	 * — perche' ha inseguito qualcosa — torna. E' la differenza fra una sentinella e una statua, e
	 * senza di essa un ordine di posizione servirebbe solo a farsi ammazzare le ombre ferme.
	 *
	 * <p>Va di pari passo con la soppressione di {@code FollowOwnerGoal} in {@link ShadowEntity}:
	 * un'ombra che tiene la posizione <em>e</em> segue il Monarca non farebbe ne' l'una ne' l'altra
	 * cosa.
	 */
	public static class Hold extends Goal {

		private final ShadowEntity shadow;

		public Hold(ShadowEntity shadow) {
			this.shadow = shadow;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos post = shadow.holdPost();
			if (post == null) {
				return false;
			}

			double radius = behaviour().holdRadius();
			return shadow.distanceToSqr(Vec3.atCenterOf(post)) > radius * radius;
		}

		@Override
		public boolean canContinueToUse() {
			return shadow.holdPost() != null && !shadow.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos post = shadow.holdPost();
			if (post != null) {
				shadow.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.2);
			}
		}
	}

	// ---------------------------------------------------------------- comune

	/**
	 * Vero se questa entita' non va mai toccata da un'ombra di questo proprietario.
	 *
	 * <p>Sta qui perche' la usano sia {@link ShadowEntity} sia i goal, e due copie della stessa
	 * regola sono due occasioni per dimenticarsene una: un esercito che sbrana il lupo del suo
	 * padrone non e' una meccanica, e' un bug.
	 */
	public static boolean isProtected(LivingEntity target, Player owner) {
		if (target == owner || target instanceof Player || target instanceof ShadowEntity) {
			return true;
		}

		return target instanceof OwnableEntity ownable && ownable.getOwner() == owner;
	}
}
