package com.luca.arise.shadow;

import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.FxConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
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
 *
 * <p>Un solo tipo di entità per quattro modi di combattere: l'{@link ShadowArchetype archetipo} non
 * è una sottoclasse ma un dato sincronizzato, e i goal lo leggono. È la stessa scelta di §3.4 del
 * design — un archetipo scalato invece di una copia del mob — portata un passo più in là: un
 * modello solo da disegnare, quattro comportamenti da far vedere.
 */
public class ShadowEntity extends TamableAnimal {

	/** Il colore va sincronizzato al client per poter tingere il modello: dato synched. */
	private static final EntityDataAccessor<Integer> DATA_COLOR =
			SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);

	/**
	 * L'archetipo, come indice dell'enum.
	 *
	 * <p>Sincronizzato e non tenuto in un campo del server: il client ne ha bisogno per l'aura
	 * — un Mago e un Colosso non devono sfrigolare allo stesso modo — e i goal ne hanno bisogno
	 * prima che {@code applyData} finisca. Un solo posto dove sta scritto, letto da entrambi i lati.
	 */
	private static final EntityDataAccessor<Integer> DATA_ARCHETYPE =
			SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);

	private UUID shadowId;

	/**
	 * Quale delle sette ombre nominate e' questa, o {@code null} se e' una qualunque.
	 *
	 * <p>Un campo e non un dato sincronizzato: le tre cose che il nome cambia — quanto lontano
	 * provoca Iron, da dove tira Tusk, chi cura Beru — succedono tutte sul server, e il client non
	 * ha nessun bisogno di saperlo. Quello che il client deve vedere, il nome e il colore, arriva
	 * gia' dal {@code CustomName} e da {@code DATA_COLOR}.
	 */
	private NamedShadow named;

	public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COLOR, ShadowData.DEFAULT_COLOR);
		builder.define(DATA_ARCHETYPE, ShadowArchetype.GUARD.ordinal());
	}

	public int getColor() {
		return this.entityData.get(DATA_COLOR);
	}

	public void setColor(int color) {
		this.entityData.set(DATA_COLOR, color);
	}

	/** L'archetipo di quest'ombra. Mai {@code null}: un indice fuori scala vale Guardia. */
	public ShadowArchetype archetype() {
		ShadowArchetype[] values = ShadowArchetype.values();
		int index = this.entityData.get(DATA_ARCHETYPE);
		return index < 0 || index >= values.length ? ShadowArchetype.GUARD : values[index];
	}

	public void setArchetype(ShadowArchetype archetype) {
		this.entityData.set(DATA_ARCHETYPE, archetype.ordinal());
	}

	/**
	 * Il punto che quest'ombra deve tenere, o {@code null} se deve seguire.
	 *
	 * <p>Non è un campo dell'entità ma una lettura dell'ordine in corso del proprietario: l'ordine
	 * vale per l'esercito intero, e tenerne una copia per ombra vorrebbe dire quattro copie da
	 * aggiornare insieme.
	 */
	public BlockPos holdPost() {
		if (!(this.getOwner() instanceof ServerPlayer owner)) {
			return null;
		}

		return ShadowManager.orders(owner).hold().orElse(null);
	}

	public boolean isHolding() {
		return holdPost() != null;
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

		// La provocazione del Colosso non ha flag: non muove e non guarda, quindi può girare
		// insieme a qualunque altra cosa l'ombra stia facendo.
		this.goalSelector.addGoal(1, new ShadowGoals.Taunt(this));

		// Tornare al posto ordinato viene prima di combattere, ed è il solo modo perché "tenete
		// la posizione" voglia dire qualcosa: un'ombra che insegue un mob per trenta blocchi e non
		// torna più ha ricevuto un ordine che il gioco ha ignorato. Dentro il raggio questo goal
		// non parte nemmeno, quindi la mischia lì dentro resta normale.
		this.goalSelector.addGoal(2, new ShadowGoals.Hold(this));

		// I tre goal d'archetipo stanno alla stessa priorità perché si escludono a vicenda: nessuna
		// ombra è insieme Bestia e Mago. La lancia prende lo sguardo e la ritirata il movimento,
		// così un Mago mira e indietreggia nello stesso momento.
		this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F) {
			@Override
			public boolean canUse() {
				return archetype() == ShadowArchetype.BEAST && super.canUse();
			}
		});
		this.goalSelector.addGoal(3, new ShadowGoals.Lance(this));
		this.goalSelector.addGoal(3, new ShadowGoals.KeepDistance(this));

		// Sotto la lancia: finché il Mago ha il bersaglio a tiro e in vista, lo sguardo è occupato
		// e la mischia non parte. È esattamente quello che deve succedere — e quando il bersaglio
		// sparisce dietro un muro la lancia molla, e il Mago va a cercarselo come tutti gli altri.
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(5, new ShadowGoals.Interpose(this));

		// Segue da vicino ma non incolla: 8 blocchi di distanza massima, 3 per fermarsi. E non
		// segue affatto quando c'è un ordine di tenere la posizione, o i due si azzufferebbero
		// sul controllo del movimento e l'ombra oscillerebbe fra il posto e il Monarca.
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.15, 8.0F, 3.0F) {
			@Override
			public boolean canUse() {
				return !isHolding() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !isHolding() && super.canContinueToUse();
			}
		});

		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

		// Un ordine esplicito batte tutto il resto, postura compresa: priorità zero.
		this.targetSelector.addGoal(0, new ShadowGoals.Focus(this));

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
	 * <p>Restano intoccabili in ogni postura il proprietario, gli altri giocatori, le altre ombre e
	 * gli animali addomesticati dallo stesso giocatore: la regola sta in
	 * {@link ShadowGoals#isProtected}, in un posto solo perché la usano anche i goal.
	 */
	private boolean shouldEngage(LivingEntity target) {
		if (!(this.getOwner() instanceof ServerPlayer owner)
				|| ShadowGoals.isProtected(target, owner)) {
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
	 * <p>Tre sorgenti si sommano qui, ed è l'unico posto in cui succede:
	 * <ol>
	 *   <li>l'<strong>ombra</strong> — mob d'origine, livello e archetipo, cioè
	 *       {@code data.maxHealth} e {@code data.attackDamage};
	 *   <li>l'<strong>aura di comando</strong> — quanto la rendono più forte i Comandanti che ha
	 *       accanto in campo. Moltiplicativa, e non si conta da sola;
	 *   <li>il <strong>dono del Monarca</strong> — una frazione della vita e del danno del
	 *       giocatore, additiva. È il motivo per cui la prima ombra estratta non diventa inutile:
	 *       nel manhwa la forza dell'esercito <em>è</em> la forza di chi lo comanda.
	 * </ol>
	 *
	 * <p>L'aura la calcola {@link ShadowManager#auraFor} invece di arrivare come argomento: così
	 * nessuna delle chiamate sparse per il gestore può dimenticarsela e lasciare un'ombra
	 * silenziosamente più debole delle sue sorelle.
	 *
	 * @param healToFull vero all'evocazione; falso quando l'ombra sale di livello mentre combatte,
	 *                   dove la vita guadagnata si aggiunge ma quella persa non si recupera
	 */
	public void applyData(ShadowData data, ServerPlayer owner, boolean healToFull) {
		ShadowConfig config = AriseConfig.get().shadows();
		ShadowConfig.Legion legion = config.legion();
		ShadowConfig.Archetype tuning = data.archetype().tuning(legion);
		ShadowManager.Aura aura = ShadowManager.auraFor(owner, data.id());

		this.shadowId = data.id();
		this.named = data.named().orElse(null);
		this.setOwner(owner);
		this.setTame(true, false);
		this.setArchetype(data.archetype());
		this.setCustomName(Component.translatable("arise.shadow.entity_name",
				data.displayName(), data.grade(config).label(), data.level()));

		float healthBefore = this.getMaxHealth();

		double health = data.maxHealth(config) * (1.0 + aura.health())
				+ owner.getMaxHealth() * legion.monarchHealthShare();
		double damage = data.attackDamage(config) * (1.0 + aura.damage())
				+ owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * legion.monarchDamageShare();

		setAttribute(Attributes.MAX_HEALTH, health);
		setAttribute(Attributes.ATTACK_DAMAGE, damage);
		setAttribute(Attributes.MOVEMENT_SPEED, config.movementSpeed() * tuning.speed());
		setAttribute(Attributes.FOLLOW_RANGE, config.followRange() * tuning.followRange());
		setAttribute(Attributes.SCALE, tuning.scale());
		// Tank non si sposta: uno pieno, non lo 0,6 del Colosso qualunque. In un gioco dove ogni
		// colpo spinge indietro, un'ombra che resta dov'e' e' una pedina che si puo' piazzare — ed
		// e' l'unica cosa che quell'ombra fa di diverso, quindi deve essere assoluta.
		setAttribute(Attributes.KNOCKBACK_RESISTANCE,
				named == NamedShadow.TANK ? 1.0 : tuning.knockbackResistance());
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

	private void setAttribute(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public UUID getShadowId() {
		return shadowId;
	}

	/** L'ombra nominata che questa entita' rappresenta, o {@code null}. */
	public NamedShadow named() {
		return named;
	}

	/** Vero se questa entita' e' quella delle sette nominate che le si passa. */
	public boolean is(NamedShadow which) {
		return named == which;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			spawnAura();
			return;
		}

		// Beru guarda il Monarca invece del nemico, ed e' l'unica che lo fa. Il suo battito e' piu'
		// fitto di quello di pulizia qui sotto perche' una cura che arriva ogni due secondi e' una
		// cura che arriva sempre tardi.
		if (named == NamedShadow.BERU) {
			tendTheMonarch();
		}

		// Le ombre non sopravvivono all'assenza del proprietario: se il giocatore esce, l'entità
		// se ne va e i dati restano nell'esercito. Evita eserciti orfani che vagano nel mondo.
		if (this.tickCount % 40 != 0) {
			return;
		}

		if (!(this.getOwner() instanceof ServerPlayer owner)) {
			this.discard();
			return;
		}

		// E non sopravvivono nemmeno all'essere state dimenticate. Chi comanda l'esercito è
		// l'elenco delle evocazioni vive, non le entità nel mondo: un'ombra che non compare più in
		// quell'elenco è un residuo — di un viaggio fra dimensioni, di un riavvio del server, di un
		// chunk tornato a caricarsi troppo tardi — e due copie della stessa ombra sono peggio di
		// nessuna, perché il giocatore non sa quale delle due sta comandando.
		//
		// I primi due secondi sono di grazia: l'entità entra nel mondo un istante prima di essere
		// scritta nell'elenco.
		if (this.tickCount > 40 && !ShadowManager.isSummoned(owner, this.getUUID())) {
			this.discard();
		}
	}

	/**
	 * Beru cura il Monarca sceso sotto meta' vita: un cuore ogni cinque secondi, non una fontana.
	 *
	 * <p>Il Re delle Formiche e' l'unica ombra che guarda chi la comanda invece di chi ha davanti,
	 * e in una mod senza classi di supporto e' la classe di supporto. La soglia a meta' vita non e'
	 * decorativa: sopra, il giocatore non deve nemmeno accorgersi che Beru c'e'; sotto, deve
	 * accorgersene subito.
	 *
	 * <p>Non cura se stessa e non cura le altre ombre: quelle cadono e si riprendono in un minuto,
	 * che e' gia' la loro rete. Curare tutti farebbe di Beru il motivo per non giocare bene.
	 */
	private void tendTheMonarch() {
		if (this.tickCount % NamedShadow.BERU_INTERVAL != 0
				|| !(this.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		if (owner.getHealth() >= owner.getMaxHealth() * NamedShadow.BERU_THRESHOLD
				|| owner.getHealth() <= 0.0F) {
			return;
		}

		// La cura arriva solo se Beru puo' vedere chi cura: da mezzo mondo di distanza sarebbe una
		// rigenerazione passiva travestita da ombra.
		if (this.distanceToSqr(owner) > NamedShadow.BERU_REACH * NamedShadow.BERU_REACH) {
			return;
		}

		owner.heal(NamedShadow.BERU_HEAL);
		AriseFx.namedBoon(this.level(), owner.position(), this.getColor());
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

		output.store("Archetype", ShadowArchetype.CODEC, archetype());

		// Il nome si risalva perche' un chunk ricaricato non rifa' applyData: senza, Iron tornerebbe
		// a provocare a dodici blocchi finche' qualcuno non lo richiama e lo rievoca.
		if (named != null) {
			output.store("Named", NamedShadow.CODEC, named);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shadowId = input.read("ShadowId", UUIDUtil.CODEC).orElse(null);
		setArchetype(input.read("Archetype", ShadowArchetype.CODEC).orElse(ShadowArchetype.GUARD));
		this.named = input.read("Named", NamedShadow.CODEC).orElse(null);
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
	 * passiva non attacca nessuno — a meno che non le sia stato ordinato.
	 *
	 * <p>L'eccezione dell'ordine conta: indicare un bersaglio e vedere l'esercito ignorarti perché
	 * la postura è passiva sarebbe la definizione di comando che non comanda.
	 */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (target == this.getOwner()) {
			return false;
		}

		if (this.getOwner() instanceof ServerPlayer owner) {
			boolean ordered = ShadowManager.orders(owner).focus()
					.map(focus -> focus.equals(target.getUUID()))
					.orElse(false);

			if (!ordered && ShadowManager.stance(owner) == ShadowStance.PASSIVE) {
				return false;
			}
		}

		if (target instanceof ShadowEntity other && other.getOwner() == this.getOwner()) {
			return false;
		}

		return super.canAttack(target);
	}
}
