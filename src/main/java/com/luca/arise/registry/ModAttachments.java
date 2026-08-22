package com.luca.arise.registry;

import com.luca.arise.AriseMod;
import com.luca.arise.ability.AbilityCooldowns;
import com.luca.arise.gate.ReturnPoint;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.shop.ShopStock;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowStance;
import com.luca.arise.shadow.SummonedShadows;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {

	/**
	 * Lo stato del Sistema, agganciato al giocatore.
	 *
	 * <ul>
	 *   <li>{@code persistent} — sopravvive al riavvio del server;
	 *   <li>{@code copyOnDeath} — sopravvive alla morte. Senza questo si perde il livello morendo,
	 *       che e' il bug numero uno delle mod di progressione;
	 *   <li>{@code syncWith(targetOnly)} — ogni client riceve solo i propri dati, non quelli altrui.
	 * </ul>
	 */
	public static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.<PlayerProgress>builder()
			.initializer(() -> PlayerProgress.INITIAL)
			.persistent(PlayerProgress.CODEC)
			.copyOnDeath()
			.syncWith(PlayerProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("progress"));

	/**
	 * L'esercito d'ombra conservato: dati, non entità (design §3.5).
	 *
	 * <p>Stesse garanzie della progressione — morire non deve costare l'esercito.
	 */
	public static final AttachmentType<ShadowArmy> ARMY = AttachmentRegistry.<ShadowArmy>builder()
			.initializer(() -> ShadowArmy.EMPTY)
			.persistent(ShadowArmy.CODEC)
			.copyOnDeath()
			.syncWith(ShadowArmy.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("army"));

	/**
	 * A che punto della catena degli incarichi è il giocatore.
	 *
	 * <p>Due numeri, ed è da questi due che si ricava quali sistemi della mod sono accesi. Persiste
	 * e sopravvive alla morte come tutto il resto: perdere il Sistema morendo sarebbe assurdo, dato
	 * che lo si è ricevuto proprio morendo.
	 */
	public static final AttachmentType<PlayerQuests> QUESTS = AttachmentRegistry.<PlayerQuests>builder()
			.initializer(() -> PlayerQuests.INITIAL)
			.persistent(PlayerQuests.CODEC)
			.copyOnDeath()
			.syncWith(PlayerQuests.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("quests"));

	/**
	 * L'equipaggiamento del Cacciatore: indossato e da parte (design §8.1).
	 *
	 * <p>Stesse garanzie della progressione e dell'esercito. {@code copyOnDeath} soprattutto:
	 * perdere l'equipaggiamento morendo sarebbe lo stesso bug che si evita per il livello, con in
	 * piu' la beffa di aver perso ore di bottino.
	 */
	public static final AttachmentType<PlayerGear> GEAR = AttachmentRegistry.<PlayerGear>builder()
			.initializer(() -> PlayerGear.EMPTY)
			.persistent(PlayerGear.CODEC)
			.copyOnDeath()
			.syncWith(PlayerGear.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("gear"));

	/**
	 * L'assortimento dell'Abyss Shop, uno per giocatore.
	 *
	 * <p>Quello che va salvato non e' l'assortimento in se': quello si rigenera dal seed. Sono
	 * <em>cosa e' gia' stato comprato</em> e <em>quanti ritiri sono stati pagati</em>. Ecco perche'
	 * anche questo attachment sopravvive alla morte: senza, morire regalerebbe un negozio nuovo di
	 * zecca e i ritiri tornerebbero a costare il minimo.
	 */
	public static final AttachmentType<ShopStock> SHOP = AttachmentRegistry.<ShopStock>builder()
			.initializer(() -> ShopStock.EMPTY)
			.persistent(ShopStock.CODEC)
			.copyOnDeath()
			.syncWith(ShopStock.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("shop"));

	/**
	 * Proiezione sincronizzata di quali ombre sono evocate ora.
	 *
	 * <p>Niente {@code persistent} e niente {@code copyOnDeath}: è uno stato di sessione, e
	 * salvarlo significherebbe promettere al client entità che dopo un riavvio non esistono.
	 */
	public static final AttachmentType<SummonedShadows> SUMMONED = AttachmentRegistry.<SummonedShadows>builder()
			.initializer(() -> SummonedShadows.EMPTY)
			.syncWith(SummonedShadows.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("summoned"));

	/** La postura di combattimento dell'esercito. Persiste e si sincronizza per l'HUD. */
	public static final AttachmentType<ShadowStance> STANCE = AttachmentRegistry.<ShadowStance>builder()
			.initializer(() -> ShadowStance.DEFENSIVE)
			.persistent(ShadowStance.CODEC)
			.copyOnDeath()
			.syncWith(ShadowStance.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("stance"));

	/**
	 * Da dove si è entrati nel Gate. Persiste: se il server cade mentre qualcuno è dentro, al
	 * rientro deve poter tornare a casa.
	 */
	public static final AttachmentType<ReturnPoint> RETURN_POINT = AttachmentRegistry.<ReturnPoint>builder()
			.persistent(ReturnPoint.CODEC)
			.copyOnDeath()
			.buildAndRegister(AriseMod.id("return_point"));

	/**
	 * Tempi di ricarica delle abilità. Sincronizzati per la barra dell'HUD, non persistenti:
	 * contano tick di gioco assoluti, che dopo un riavvio non vogliono più dire la stessa cosa.
	 */
	public static final AttachmentType<AbilityCooldowns> COOLDOWNS = AttachmentRegistry.<AbilityCooldowns>builder()
			.initializer(() -> AbilityCooldowns.EMPTY)
			.syncWith(AbilityCooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("cooldowns"));

	private ModAttachments() {
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
	}
}
