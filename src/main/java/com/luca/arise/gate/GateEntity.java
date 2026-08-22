package com.luca.arise.gate;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.ModSounds;
import com.luca.arise.network.GateOfferPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Il varco: un Gate che esiste nel mondo ma non è ancora stato aperto.
 *
 * <p>Prima si veniva catapultati dentro un dungeon appena richiesto. Ora il Gate <em>compare</em>,
 * si guarda, si analizza — e si decide. È la differenza fra un comando e una scelta, ed è il modo
 * in cui funziona il materiale di partenza: gli hunter valutano un varco prima di entrarci.
 *
 * <p>Il varco non costruisce niente. Porta con sé un {@link GateOffer}, cioè un preventivo con il
 * seme della pianta: la geometria vera nasce solo se qualcuno attraversa. Un varco ignorato scade
 * e sparisce senza aver toccato un blocco.
 */
public class GateEntity extends Entity {

	/** Ogni quanti tick si ridisegna l'anello. Tre: sotto si vede lo sfarfallio dei pacchetti. */
	private static final int PARTICLE_INTERVAL = 3;

	/** Ogni quanto il varco si fa sentire. */
	private static final int SOUND_INTERVAL = 120;

	private GateOffer offer;

	/** Tick rimasti prima di svanire. Conta alla rovescia, così si salva e si riprende com'era. */
	private int remainingTicks;

	public GateEntity(EntityType<? extends GateEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// Niente da sincronizzare: il nome fluttuante ci pensa {@code Entity}, i particellari li
		// manda il server, e il pannello di analisi arriva su richiesta con un pacchetto suo.
		// Sincronizzare l'intero preventivo a chiunque passi vicino sarebbe traffico per nulla.
	}

	public void configure(GateOffer offer) {
		configure(offer, AriseConfig.get().gates().offerLifetimeTicks());
	}

	/**
	 * Come sopra, ma con una durata decisa da fuori.
	 *
	 * <p>Serve ai varchi spontanei: uno che si apre a centocinquanta blocchi deve dare il tempo di
	 * arrivarci, mentre uno evocato davanti ai piedi con un comando no.
	 */
	public void configure(GateOffer offer, int lifetimeTicks) {
		this.offer = offer;
		this.remainingTicks = lifetimeTicks;
		this.setCustomName(Component.translatable("arise.gate.varco_name",
				offer.rank().label(), offer.theme().label()));
		this.setCustomNameVisible(true);
	}

	public GateOffer offer() {
		return offer;
	}

	public int remainingTicks() {
		return remainingTicks;
	}

	@Override
	public void tick() {
		super.tick();

		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		if (offer == null || --remainingTicks <= 0) {
			AriseFx.gateVarcoClosed(level, this.position(), tint());
			this.discard();
			return;
		}

		if (this.tickCount % PARTICLE_INTERVAL == 0) {
			AriseFx.gateVarco(level, this.position(), tint());
		}

		if (this.tickCount % SOUND_INTERVAL == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GATE_AMBIENCE,
					net.minecraft.sounds.SoundSource.AMBIENT, 0.4F, 0.6F);
		}
	}

	/** Il colore del varco è quello del rango: si legge la difficoltà da lontano, senza avvicinarsi. */
	private int tint() {
		return offer == null ? GateTheme.DEPTHS.color() : offer.rank().color();
	}

	/**
	 * Click destro: si apre il pannello di analisi, non il Gate.
	 *
	 * <p>Il preventivo viaggia adesso e solo verso chi ha cliccato. Il pannello non decide nulla:
	 * quando si preme "Entra" torna indietro un pacchetto e il server rifà i suoi controlli.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer && offer != null) {
			ServerPlayNetworking.send(serverPlayer, new GateOfferPayload(this.getId(), offer, remainingTicks));
		}

		return InteractionResult.SUCCESS;
	}

	/** Attraversabile: è un varco, non un muro. */
	@Override
	public boolean canBeCollidedWith(Entity entity) {
		return false;
	}

	/** Ma cliccabile, altrimenti non lo si potrebbe analizzare. */
	@Override
	public boolean isPickable() {
		return true;
	}

	/** Non si rompe a pugni: si attraversa o si aspetta che scada. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerable() {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (offer != null) {
			output.store("Offer", GateOffer.CODEC, offer);
			output.putInt("Remaining", remainingTicks);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.offer = input.read("Offer", GateOffer.CODEC).orElse(null);
		this.remainingTicks = input.getIntOr("Remaining", 0);
	}
}
