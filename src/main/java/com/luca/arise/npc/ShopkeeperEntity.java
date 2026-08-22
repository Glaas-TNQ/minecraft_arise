package com.luca.arise.npc;

import com.luca.arise.fx.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Chi sta dietro il bancone. Una persona, non un villager.
 *
 * <p>Un villager e' una macchina complicata: dorme, cerca un letto, si lega a un punto
 * d'interesse, cambia mestiere se gli si toglie il blocco di lavoro, scappa se ha paura e diventa
 * una strega se lo colpisce un fulmine. Tutto questo esiste per un gioco di villaggi. Qui serve
 * l'opposto: qualcuno che stia fermo dietro un bancone finche' il mondo esiste. Quindi niente IA,
 * niente inventario, niente professione — e nessuno dei bug che quella macchina si porta dietro.
 *
 * <p>Implementa {@link Merchant} direttamente, e per una ragione precisa: {@code openTradingScreen}
 * apre la <strong>finestra di scambio di Minecraft</strong>, l'unica interfaccia di questa mod che
 * nessuno deve imparare. Chi ha comprato qualcosa da un villager sa gia' usare la prima bottega di
 * Arise, e non c'e' una riga da spiegargli.
 */
public class ShopkeeperEntity extends PathfinderMob implements Merchant {

	/**
	 * Che bottega e'. Sincronizzato perche' il client deve saperlo per disegnare la skin giusta.
	 *
	 * <p>Un intero e non una stringa: sono nove voci, e l'ordinale viaggia in un byte.
	 */
	private static final EntityDataAccessor<Integer> ROLE =
			SynchedEntityData.defineId(ShopkeeperEntity.class, EntityDataSerializers.INT);

	private Player tradingPlayer;

	/**
	 * L'assortimento di questo NPC.
	 *
	 * <p>Si costruisce alla prima richiesta e poi si salva, perche' un {@code MerchantOffer} porta
	 * con se' quante volte e' stato usato: ricostruirlo a ogni apertura significherebbe scorte
	 * infinite, e le scorte limitate sono l'unica cosa che impedisce a una bottega di diventare un
	 * distributore automatico.
	 */
	private MerchantOffers offers;

	public ShopkeeperEntity(EntityType<? extends ShopkeeperEntity> type, Level level) {
		super(type, level);

		// Fermo dietro il bancone, e li' resta: niente IA, niente spinte, niente despawn.
		this.setNoAi(true);
		this.setPersistenceRequired();
		this.setInvulnerable(true);
		this.setCustomNameVisible(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ROLE, 0);
	}

	@Override
	protected void registerGoals() {
		// Nessuno. Vedi il javadoc della classe: e' il punto.
	}

	// ---------------------------------------------------------------- il ruolo

	public Shopkeeper role() {
		int index = this.entityData.get(ROLE);
		return Shopkeeper.values()[Math.clamp(index, 0, Shopkeeper.values().length - 1)];
	}

	public void setRole(Shopkeeper role) {
		this.entityData.set(ROLE, role.ordinal());
		this.setCustomName(role.label());
		this.offers = null;
	}

	// ---------------------------------------------------------------- interazione

	/**
	 * Click destro sul bancone.
	 *
	 * <p>Due strade e nessuna via di mezzo: chi vende apre la finestra di scambio, chi fa un
	 * servizio lo fa. La decisione sta nel {@link Shopkeeper}, non qui: aggiungere una bottega
	 * nuova non deve significare toccare questo metodo.
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer server)) {
			return InteractionResult.CONSUME;
		}

		Shopkeeper role = role();

		if (!role.merchant()) {
			ShopkeeperServices.open(role, server, this);
			return InteractionResult.CONSUME;
		}

		if (this.tradingPlayer != null) {
			return InteractionResult.CONSUME;
		}

		this.setTradingPlayer(player);
		this.openTradingScreen(player, role.label(), 1);
		return InteractionResult.CONSUME;
	}

	// ---------------------------------------------------------------- Merchant

	@Override
	public void setTradingPlayer(Player player) {
		this.tradingPlayer = player;
	}

	@Override
	public Player getTradingPlayer() {
		return this.tradingPlayer;
	}

	@Override
	public MerchantOffers getOffers() {
		if (this.offers == null) {
			this.offers = ShopkeeperTrades.build(role(), this.getRandom());
		}

		return this.offers;
	}

	@Override
	public void overrideOffers(MerchantOffers replacement) {
		this.offers = replacement;
	}

	/** Un affare concluso: il verso e la scorta che cala. */
	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();

		if (this.level() instanceof ServerLevel level) {
			com.luca.arise.fx.AriseFx.marketDeal(level, this.position());
		}
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
		// Niente: il verso lo fa {@link #notifyTrade}, e ripeterlo a ogni oggetto messo sul
		// bancone trasformerebbe la finestra di scambio in un campanello.
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	/**
	 * Niente barra dei livelli.
	 *
	 * <p>Le botteghe non salgono di grado: quello che vendono lo decide il Quartiere, non quante
	 * volte ci hai comprato. Una barra sempre a zero sarebbe una promessa non mantenuta.
	 */
	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return ModSounds.SHOP_DEAL;
	}

	@Override
	public boolean isClientSide() {
		return this.level().isClientSide();
	}

	@Override
	public boolean stillValid(Player player) {
		return this.tradingPlayer == player && this.isAlive()
				&& player.distanceToSqr(this) <= 64.0;
	}

	// ---------------------------------------------------------------- fermo e intoccabile

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public Component getName() {
		return this.hasCustomName() ? super.getName() : role().label();
	}

	// ---------------------------------------------------------------- salvataggio

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);

		output.store("role", Shopkeeper.CODEC, role());

		if (this.offers != null && !this.offers.isEmpty()) {
			output.store("offers", MerchantOffers.CODEC, this.offers);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);

		input.read("role", Shopkeeper.CODEC).ifPresent(role -> this.entityData.set(ROLE, role.ordinal()));
		this.offers = input.read("offers", MerchantOffers.CODEC).orElse(null);
	}
}
