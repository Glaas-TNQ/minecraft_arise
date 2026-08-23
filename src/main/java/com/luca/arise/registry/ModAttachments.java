package com.luca.arise.registry;

import java.util.List;

import com.luca.arise.AriseMod;
import com.luca.arise.ability.AbilityCooldowns;
import com.luca.arise.daily.DailyQuest;
import com.luca.arise.gate.AbyssRecord;
import com.luca.arise.gate.GateRegistry;
import com.luca.arise.gate.MobAffix;
import com.luca.arise.gate.ReturnPoint;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.shop.ShopStock;
import com.luca.arise.tutorial.PlayerTutorial;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowDowntime;
import com.luca.arise.shadow.ShadowOrders;
import com.luca.arise.shadow.ShadowSquad;
import com.luca.arise.shadow.ShadowStance;
import com.luca.arise.shadow.SummonedShadows;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.server.level.ServerPlayer;

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
	/**
	 * L'affisso di un mob dentro un Gate. E' l'unico attachment che non sta su un giocatore.
	 *
	 * <p>Persistente perche' un Gate resta aperto finche' chi lo percorre non lo finisce, e in
	 * mezzo un chunk puo' scaricarsi: un Assetato che smette di curarsi perche' il giocatore e'
	 * uscito dalla stanza e poi e' tornato sarebbe un difetto invisibile — la difficolta'
	 * cambierebbe senza che nessuno capisca perche'.
	 *
	 * <p>Non sincronizzato: cio' che il client deve vedere e' il nome sopra la testa, e quello e'
	 * gia' un {@code CustomName}. Mandare l'affisso in rete non aggiungerebbe niente da disegnare.
	 */
	public static final AttachmentType<MobAffix> MOB_AFFIX = AttachmentRegistry.<MobAffix>builder()
			.persistent(MobAffix.CODEC)
			.buildAndRegister(AriseMod.id("mob_affix"));

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

	/**
	 * Quali ombre sono ancora a terra dopo essere cadute.
	 *
	 * <p>Come {@link #SUMMONED} e {@link #COOLDOWNS}: sincronizzato ma non salvato, perche' conta
	 * tick di gioco assoluti. Vedi {@link ShadowDowntime} per il perche' non e' persistente.
	 */
	public static final AttachmentType<ShadowDowntime> DOWNTIME = AttachmentRegistry.<ShadowDowntime>builder()
			.initializer(() -> ShadowDowntime.EMPTY)
			.syncWith(ShadowDowntime.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("downtime"));

	/**
	 * La squadra: quali ombre escono col tasto di evocazione, e in che ordine.
	 *
	 * <p>Stesse garanzie dell'esercito, {@code copyOnDeath} compreso. Una squadra che si azzera
	 * morendo costringerebbe a ricomporla a ogni incidente, che e' esattamente il fastidio che
	 * questo attachment esiste per togliere.
	 */
	public static final AttachmentType<ShadowSquad> SQUAD = AttachmentRegistry.<ShadowSquad>builder()
			.initializer(() -> ShadowSquad.EMPTY)
			.persistent(ShadowSquad.CODEC)
			.copyOnDeath()
			.syncWith(ShadowSquad.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("squad"));

	/**
	 * Gli ordini in corso: un bersaglio da uccidere, un punto da tenere.
	 *
	 * <p>Come {@link #SUMMONED} e {@link #DOWNTIME}: sincronizzato ma non salvato. Un'entita'
	 * bersaglio e un punto in una dimensione sono cose che dopo un riavvio non vogliono piu' dire
	 * niente, e un esercito che al ritorno tiene una posizione dimenticata sembrerebbe rotto.
	 */
	public static final AttachmentType<ShadowOrders> ORDERS = AttachmentRegistry.<ShadowOrders>builder()
			.initializer(() -> ShadowOrders.NONE)
			.syncWith(ShadowOrders.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("orders"));

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

	/**
	 * A che punto e' il giocatore con la prima ora: il saluto e il discorso dell'Araldo.
	 *
	 * <p>Persiste e sopravvive alla morte, come tutto il resto — un tutorial che ricomincia perche'
	 * si e' morti sarebbe la definizione di fastidio. Non si sincronizza: il client non disegna
	 * niente che dipenda da questo, perche' quello che l'Araldo dice arriva in chat e i tasti li
	 * scrive il gioco da solo.
	 */
	public static final AttachmentType<PlayerTutorial> TUTORIAL = AttachmentRegistry.<PlayerTutorial>builder()
			.initializer(() -> PlayerTutorial.INITIAL)
			.persistent(PlayerTutorial.CODEC)
			.copyOnDeath()
			.buildAndRegister(AriseMod.id("tutorial"));

	/**
	 * L'indice dei varchi aperti, per la mappa. Sta sull'<em>Overworld</em>, non sul giocatore: un
	 * varco è di tutti, e la mappa di chiunque lo deve mostrare.
	 *
	 * <p>Persistente e non sincronizzato: il client lo riceve già riconciliato, su richiesta, con
	 * un pacchetto suo. Niente {@code copyOnDeath}: non è un dato del giocatore.
	 */
	public static final AttachmentType<GateRegistry> GATE_REGISTRY = AttachmentRegistry.<GateRegistry>builder()
			.initializer(() -> GateRegistry.EMPTY)
			.persistent(GateRegistry.CODEC)
			.buildAndRegister(AriseMod.id("gate_registry"));

	/**
	 * Quanto in fondo all'Abisso e' arrivato questo Cacciatore, e in quanto tempo.
	 *
	 * <p>Persistente e {@code copyOnDeath} come tutto il resto della progressione: perdere la
	 * profondita' massima morendo vorrebbe dire che l'unico contenuto senza fine della mod si
	 * azzera per una distrazione, il che e' l'opposto di cio' per cui esiste.
	 *
	 * <p>Sincronizzato perche' un giorno la schermata dovra' mostrarlo — e perche' il costo di
	 * mandare due numeri e' zero.
	 */
	public static final AttachmentType<AbyssRecord> ABYSS = AttachmentRegistry.<AbyssRecord>builder()
			.initializer(() -> AbyssRecord.NONE)
			.persistent(AbyssRecord.CODEC)
			.copyOnDeath()
			.syncWith(AbyssRecord.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("abyss"));

	/**
	 * La giornata di un Cacciatore secondo il Sistema: quattro contatori e il giorno a cui vanno.
	 *
	 * <p>Persistente perche' il giorno finisce anche mentre il gioco e' chiuso, e {@code copyOnDeath}
	 * perche' morire non deve azzerare una giornata quasi completa — sarebbe una penalita' pagata
	 * due volte, e la penalita' e' gia' quella che c'e'.
	 */
	public static final AttachmentType<DailyQuest> DAILY = AttachmentRegistry.<DailyQuest>builder()
			.initializer(() -> DailyQuest.NONE)
			.persistent(DailyQuest.CODEC)
			.copyOnDeath()
			.syncWith(DailyQuest.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(AriseMod.id("daily"));

	/**
	 * I tredici attachment che il client deve conoscere, in un elenco solo.
	 *
	 * <p>Esiste per {@link #resync}: un elenco scritto a mano si dimentica del tredicesimo, e la
	 * dimenticanza si vede come una schermata vuota sei mesi dopo.
	 */
	private static final List<AttachmentType<?>> SYNCED = List.of(
			PROGRESS, ARMY, QUESTS, GEAR, SHOP, SUMMONED, DOWNTIME, SQUAD, ORDERS, STANCE,
			COOLDOWNS, ABYSS, DAILY);

	private ModAttachments() {
	}

	/**
	 * Rimanda al client tutto quello che gia' sa il server.
	 *
	 * <p>Questo metodo esiste per un difetto visto giocando: <strong>ogni tanto l'HUD spariva e non
	 * tornava piu'</strong>. La causa e' che la sincronizzazione parte quando qualcuno scrive un
	 * attachment, e il client tiene i valori <em>sull'entita'</em>. Morire e cambiare dimensione
	 * costruiscono un'entita' nuova sul client: quella nasce senza niente, e ci resta finche' il
	 * server non riscrive qualcosa.
	 *
	 * <p>Per la progressione «qualcosa» arriva al primo mob ucciso. Per gli incarichi puo' non
	 * arrivare per ore — e l'HUD non si disegna senza gli incarichi, perche' e' da li' che sa se il
	 * Sistema e' stato concesso. Ecco perche' spariva «ogni tanto» e sembrava definitivo: lo era.
	 *
	 * <p>La cura e' riscrivere ogni valore con se stesso nei tre momenti in cui l'entita' del client
	 * e' nuova. Riscrivere lo stesso valore non cambia niente sul server — sono tutti record
	 * immutabili — e sul client rimette tutto a posto.
	 */
	public static void resync(ServerPlayer player) {
		for (AttachmentType<?> type : SYNCED) {
			resyncOne(player, type);
		}
	}

	/** Il generico serve solo a convincere il compilatore che il valore riletto e' del tipo giusto. */
	private static <A> void resyncOne(ServerPlayer player, AttachmentType<A> type) {
		A value = player.getAttached(type);

		if (value != null) {
			player.setAttached(type, value);
		}
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
	}
}
