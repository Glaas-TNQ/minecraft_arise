package com.luca.arise.shadow;

import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.FxConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.ModSounds;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Un'ombra <em>evocata</em>: l'incarnazione temporanea di una {@link ShadowData}.
 *
 * <p>Estende {@link TamableAnimal} per una ragione pratica: proprietario, ordine di stare fermo e
 * i goal "difendi chi ti possiede" esistono già e sono collaudati. La discendenza da Animal porta
 * con sé riproduzione e cibo, che qui vengono semplicemente negati.
 *
 * <p>Questa entità non è la fonte di verità: lo è la {@link ShadowData} nell'attachment del
 * giocatore. Se muore o viene richiamata, l'entità sparisce e i dati restano.
 */
public class ShadowEntity extends TamableAnimal {

	/** Il colore va sincronizzato al client per poter tingere il modello: dato synched. */
	private static final EntityDataAccessor<Integer> DATA_COLOR =
			SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);

	private UUID shadowId;

	public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COLOR, ShadowData.DEFAULT_COLOR);
	}

	public int getColor() {
		return this.entityData.get(DATA_COLOR);
	}

	public void setColor(int color) {
		this.entityData.set(DATA_COLOR, color);
	}

	public static AttributeSupplier.Builder createAttributes() {
		ShadowConfig config = ShadowConfig.DEFAULT;

		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, config.movementSpeed())
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, config.followRange());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
		// Segue da vicino ma non incolla: 8 blocchi di distanza massima, 3 per fermarsi.
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.15, 8.0F, 3.0F));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));

		// I goal "difendi il proprietario" di vanilla reagiscono solo al momento del colpo: se il
		// bersaglio cambia, o se il mob sta puntando il giocatore senza averlo ancora colpito, le
		// ombre restano ferme a guardare. Questo goal è quello che le rende davvero utili.
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
				10, true, false, (target, level) -> shouldEngage(target)));
	}

	/**
	 * Decide se questa ombra deve prendere l'iniziativa contro un bersaglio.
	 *
	 * <p>In aggressiva attacca <em>qualsiasi</em> creatura, mucche comprese: è una postura da dare
	 * di proposito, e serve anche a farsi strada in un allevamento quando si vuole. In difensiva
	 * serve un ostile che stia dando la caccia al proprietario — è la lettura di "attaccano quando
	 * vengo attaccato" che scatta anche <em>prima</em> che il colpo arrivi. In passiva niente.
	 *
	 * <p>Restano intoccabili in ogni postura: il proprietario, gli altri giocatori, le altre ombre
	 * e gli animali addomesticati dallo stesso giocatore. Un esercito che sbrana il tuo lupo non è
	 * una meccanica, è un bug.
	 */
	private boolean shouldEngage(LivingEntity target) {
		if (!(this.getOwner() instanceof ServerPlayer owner) || target == owner) {
			return false;
		}

		if (target instanceof Player || target instanceof ShadowEntity) {
			return false;
		}

		if (target instanceof OwnableEntity ownable && ownable.getOwner() == owner) {
			return false;
		}

		return switch (ShadowManager.stance(owner)) {
			case AGGRESSIVE -> true;
			case DEFENSIVE -> target instanceof Enemy && target instanceof Mob mob && mob.getTarget() == owner;
			case PASSIVE -> false;
		};
	}

	/** Applica i dati all'entità appena evocata, con la vita piena. */
	public void applyData(ShadowData data, ServerPlayer owner) {
		applyData(data, owner, true);
	}

	/**
	 * Scrive negli attributi dell'entità le statistiche derivate dai dati dell'ombra.
	 *
	 * @param healToFull vero all'evocazione; falso quando l'ombra sale di livello mentre combatte,
	 *                   dove la vita guadagnata si aggiunge ma quella persa non si recupera
	 */
	public void applyData(ShadowData data, ServerPlayer owner, boolean healToFull) {
		ShadowConfig config = AriseConfig.get().shadows();

		this.shadowId = data.id();
		this.setOwner(owner);
		this.setTame(true, false);
		this.setCustomName(Component.translatable("arise.shadow.entity_name",
				data.displayName(), data.rank(config).label(), data.level()));

		float healthBefore = this.getMaxHealth();

		setAttribute(Attributes.MAX_HEALTH, data.maxHealth(config));
		setAttribute(Attributes.ATTACK_DAMAGE, data.attackDamage(config));
		setAttribute(Attributes.MOVEMENT_SPEED, config.movementSpeed());
		setAttribute(Attributes.FOLLOW_RANGE, config.followRange());
		setColor(data.color());

		if (healToFull) {
			this.setHealth(this.getMaxHealth());
			return;
		}

		float gained = this.getMaxHealth() - healthBefore;
		if (gained > 0) {
			this.setHealth(this.getHealth() + gained);
		}
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	private void setAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public UUID getShadowId() {
		return shadowId;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			spawnAura();
			return;
		}

		// Le ombre non sopravvivono all'assenza del proprietario: se il giocatore esce, l'entità
		// se ne va e i dati restano nell'esercito. Evita eserciti orfani che vagano nel mondo.
		if (this.tickCount % 40 == 0 && !(this.getOwner() instanceof ServerPlayer)) {
			this.discard();
		}
	}

	/**
	 * L'aura continua: polvere del colore dell'ombra che le sale attorno.
	 *
	 * <p>Generata <strong>sul client</strong> e non spedita dal server. Con un esercito fuori e un
	 * particellare ogni tre tick, la stessa cosa fatta con {@code sendParticles} sarebbero decine di
	 * pacchetti al secondo per niente: sono particelle puramente decorative, il server non ha
	 * bisogno di sapere che esistono.
	 */
	private void spawnAura() {
		FxConfig fx = AriseConfig.get().fx();
		if (!fx.shadowAura() || fx.auraIntervalTicks() <= 0
				|| this.tickCount % fx.auraIntervalTicks() != 0) {
			return;
		}

		RandomSource random = this.getRandom();
		this.level().addParticle(AriseFx.dust(getColor(), 0.8F),
				this.getX() + (random.nextDouble() - 0.5) * this.getBbWidth(),
				this.getY() + random.nextDouble() * this.getBbHeight(),
				this.getZ() + (random.nextDouble() - 0.5) * this.getBbWidth(),
				0.0, 0.02, 0.0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SHADOW_AMBIENT;
	}

	/** Raro: un esercito che borbotta ogni cinque secondi diventa insopportabile. */
	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return ModSounds.SHADOW_HURT;
	}

	/**
	 * Nessun suono di morte da vanilla: quello dell'ombra che si sfalda lo suona
	 * {@link AriseFx#shadowFell}, insieme al particellare, e due suoni sovrapposti farebbero
	 * pasticcio.
	 */
	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	/**
	 * Alla morte l'ombra torna nell'esercito invece di sparire per sempre.
	 *
	 * <p>Scelta di design: perdere definitivamente un'ombra per una distrazione sarebbe punitivo in
	 * un sistema che ruota tutto attorno all'accumulo. La penalità, se servirà, sarà un tempo di
	 * recupero, non la cancellazione.
	 */
	@Override
	public void die(DamageSource damageSource) {
		if (!this.level().isClientSide() && this.getOwner() instanceof ServerPlayer owner) {
			ShadowManager.onSummonedDied(owner, this);
		}

		super.die(damageSource);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);

		if (shadowId != null) {
			output.store("ShadowId", UUIDUtil.CODEC, shadowId);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shadowId = input.read("ShadowId", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	/** Non deve sparire per distanza: la gestione della sua vita passa solo da noi. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	/**
	 * Un'ombra non attacca il suo padrone né le altre ombre dello stesso padrone, e in postura
	 * passiva non attacca nessuno.
	 */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (target == this.getOwner()) {
			return false;
		}

		if (this.getOwner() instanceof ServerPlayer owner
				&& ShadowManager.stance(owner) == ShadowStance.PASSIVE) {
			return false;
		}

		if (target instanceof ShadowEntity other && other.getOwner() == this.getOwner()) {
			return false;
		}

		return super.canAttack(target);
	}
}
