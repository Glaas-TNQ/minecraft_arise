package com.luca.arise.tutorial;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.AwakeningConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gate.ReturnPoint;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * La scena del risveglio, e la mezz'ora dopo.
 *
 * <p>Prima di questo file il momento piu' importante della mod era una riga in chat: si arrivava a
 * un passo dalla morte, il Sistema salvava, e il giocatore restava esattamente dov'era con un
 * riquadro nuovo in alto a sinistra e nessuna idea di cosa farne. Il difetto non era la mancanza di
 * spiegazioni — era che <em>non succedeva niente</em>. Adesso il colpo che non uccide porta via.
 *
 * <p>Tre secondi di buio, una stanza in fondo alla dimensione dei varchi, qualcuno che spiega, e si
 * torna esattamente da dove si e' stati portati via. La spiegazione arriva perche' il giocatore la
 * chiede un clic per volta, e finisce quando decide lui.
 *
 * <p><strong>Il teletrasporto non avviene dentro l'evento della morte.</strong> {@code ALLOW_DEATH}
 * scatta mentre Minecraft sta ancora decidendo cosa fare di quel danno: spostare un giocatore in
 * un'altra dimensione da li' dentro significa cambiargli il mondo sotto i piedi a meta' calcolo.
 * Il risveglio si <em>prenota</em> ({@link #schedule}) e il battito del server lo esegue qualche
 * tick dopo — che e' anche il motivo per cui c'e' tempo per una cecita' che copre il passaggio.
 */
public final class AwakeningManager {

	/** Chi deve essere portato nella Sala, e fra quanti tick. */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();

	/**
	 * Chi e' appena arrivato nella Sala, e fra quanti tick si guarda se l'Araldo c'e'.
	 *
	 * <p>Il ritardo non e' scenografia: le entita' di un chunk si caricano quando il chunk diventa
	 * vivo, e nell'istante del teletrasporto non lo e' ancora. Cercare l'Araldo subito significa non
	 * trovarlo e metterne un altro accanto a quello che c'era gia'.
	 */
	private static final Map<UUID, Integer> SETTLING = new HashMap<>();

	/** Quanto si aspetta prima di guardare chi c'e' nella stanza: un secondo abbondante. */
	private static final int SETTLE_TICKS = 25;

	/** Quanto dura il buio che copre il passaggio: un filo piu' dell'attesa, o si vede il salto. */
	private static final int BLINDNESS_MARGIN = 25;

	private AwakeningManager() {
	}

	public static PlayerTutorial get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.TUTORIAL);
	}

	private static void set(ServerPlayer player, PlayerTutorial tutorial) {
		player.setAttached(ModAttachments.TUTORIAL, tutorial);
	}

	private static AwakeningConfig config() {
		return AriseConfig.get().awakening();
	}

	// ---------------------------------------------------------------- il primo saluto

	/**
	 * Due righe alla prima entrata, e nient'altro per un bel po'.
	 *
	 * <p>Servono a una cosa sola: dire che non c'e' niente da fare. Senza, chi installa la mod apre
	 * il mondo, non vede nessun riquadro, nessun oggetto e nessun comando, e conclude
	 * ragionevolmente che non si sia installata.
	 */
	public static void welcome(ServerPlayer player) {
		PlayerTutorial tutorial = get(player);

		if (tutorial.welcomed()) {
			return;
		}

		// Segnato comunque, anche quando il saluto non si dice: il controllo qui sotto e' per i
		// mondi che esistevano gia'. Un personaggio di livello 25 non ha mai ricevuto questo
		// saluto, e si vedrebbe dire che non ha nessun potere e nessun tasto con l'HUD acceso
		// davanti — una volta sola, ma sarebbe quella sbagliata.
		set(player, tutorial.welcomed(true));

		if (QuestManager.has(player, Unlock.SYSTEM)) {
			return;
		}

		title(player, Component.translatable("arise.tutorial.welcome.title")
						.withStyle(ChatFormatting.AQUA),
				Component.translatable("arise.tutorial.welcome.subtitle")
						.withStyle(ChatFormatting.GRAY));

		player.sendSystemMessage(Component.translatable("arise.tutorial.welcome.1")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		player.sendSystemMessage(Component.translatable("arise.tutorial.welcome.2")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	// ---------------------------------------------------------------- l'ingresso

	/**
	 * Prenota il risveglio: fra qualche tick, e non adesso.
	 *
	 * <p>Chiamato da {@code QuestManager.awaken}, cioe' da dentro l'evento che annulla la morte.
	 * Qui si accende solo la cecita' — quella si puo' fare subito, ed e' la tenda che si chiude.
	 */
	public static void schedule(ServerPlayer player) {
		AwakeningConfig config = config();

		if (!config.sanctum() || get(player).done()) {
			return;
		}

		PENDING.put(player.getUUID(), config.delayTicks());
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
				config.delayTicks() + BLINDNESS_MARGIN, 0));
	}

	/** Il battito: scala le due attese e fa quello che era prenotato. Una volta per tick. */
	public static void tick(MinecraftServer server) {
		countdown(server, PENDING, AwakeningManager::enter);
		countdown(server, SETTLING, AwakeningManager::settle);
	}

	private static void countdown(MinecraftServer server, Map<UUID, Integer> queue,
			java.util.function.Consumer<ServerPlayer> action) {
		if (queue.isEmpty()) {
			return;
		}

		queue.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				return true;
			}

			if (entry.getValue() > 0) {
				entry.setValue(entry.getValue() - 1);
				return false;
			}

			action.accept(player);
			return true;
		});
	}

	/**
	 * Un secondo dopo l'arrivo: la stanza e' viva, quindi adesso si puo' guardare chi c'e' dentro.
	 *
	 * <p>Se nel frattempo il giocatore se n'e' andato — un comando, una disconnessione, un altro
	 * teletrasporto — non si tocca niente: mettere un Araldo in una sala vuota e' esattamente il
	 * difetto che questo ritardo esiste per evitare.
	 */
	private static void settle(ServerPlayer player) {
		if (!inSanctum(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		AwakeningConfig config = config();
		Sanctum.sweep(level, config);
		Sanctum.ensureHerald(level, config);
	}

	/**
	 * Porta il giocatore nella Sala.
	 *
	 * <p>Se la dimensione dei varchi non c'e' — mondo creato prima della mod, datapack rifiutato —
	 * non succede niente di male: il risveglio resta quello di prima, una riga in chat e il Sistema
	 * acceso. E' l'unico modo di far dipendere una scena da una dimensione senza rischiare di
	 * bloccare qualcuno fuori dal proprio gioco.
	 */
	public static void enter(ServerPlayer player) {
		AwakeningConfig config = config();
		MinecraftServer server = player.level().getServer();
		ServerLevel sanctum = server == null ? null : server.getLevel(GateManager.GATE_DIMENSION);

		if (sanctum == null) {
			return;
		}

		Sanctum.ensureRoom(sanctum, config);

		// La via di casa si scrive prima del viaggio, e sul disco: se il server cade mentre
		// qualcuno e' nella Sala, al rientro deve poter tornare da dove e' stato portato via.
		player.setAttached(ModAttachments.RETURN_POINT,
				new ReturnPoint(player.level().dimension(), player.position()));

		set(player, get(player).rewound());

		net.minecraft.world.phys.Vec3 arrival = Sanctum.arrival(config);
		player.teleportTo(sanctum, arrival.x(), arrival.y(), arrival.z(), Set.of(),
				Sanctum.arrivalYaw(), 0.0F, true);
		player.removeEffect(MobEffects.BLINDNESS);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);

		AriseFx.awakening(sanctum, arrival);

		// L'Araldo si controlla fra un secondo, quando la stanza sara' viva. Vedi settle().
		SETTLING.put(player.getUUID(), SETTLE_TICKS);

		title(player, Component.translatable("arise.tutorial.sanctum.title")
						.withStyle(ChatFormatting.AQUA),
				Component.translatable("arise.tutorial.sanctum.subtitle")
						.withStyle(ChatFormatting.GRAY));

		player.sendSystemMessage(Component.translatable("arise.tutorial.sanctum.arrived")
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	// ---------------------------------------------------------------- il discorso

	/**
	 * Un clic sull'Araldo: la prossima pagina.
	 *
	 * <p>Chi ha gia' sentito tutto e ci ritorna riceve l'ultima riga e la porta aperta, non il
	 * discorso da capo. Un personaggio che ripete la stessa spiegazione a chi gli ha gia' detto di
	 * aver capito e' il modo piu' rapido di rendere insopportabile un tutorial.
	 */
	public static void talk(ServerPlayer player) {
		PlayerTutorial tutorial = get(player);
		HeraldPage page = tutorial.current();

		if (page == null) {
			leave(player);
			return;
		}

		player.sendSystemMessage(Component.translatable("arise.tutorial.herald.says", page.line())
				.withStyle(ChatFormatting.WHITE));

		set(player, tutorial.next());

		if (player.level() instanceof ServerLevel level) {
			AriseFx.marketDeal(level, player.position());
		}

		if (get(player).done()) {
			reward(player);
			leave(player);
			return;
		}

		player.sendSystemMessage(Component.translatable("arise.tutorial.herald.more")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** L'acconto del Sistema: serve a vedere il contatore dei soul coin muoversi una volta. */
	private static void reward(ServerPlayer player) {
		long souls = config().welcomeSouls();

		if (souls > 0) {
			ProgressManager.addSouls(player, souls);
			player.sendSystemMessage(Component.translatable("arise.tutorial.herald.gift", souls)
					.withStyle(ChatFormatting.GOLD));
		}
	}

	// ---------------------------------------------------------------- l'uscita

	/**
	 * Rimanda nel mondo, con qualche secondo di respiro.
	 *
	 * <p>Il respiro non e' generosita': si torna esattamente nel punto in cui qualcosa stava per
	 * uccidere, e quel qualcosa e' ancora li'. Senza la protezione, meta' dei risvegli finirebbe
	 * con una morte vera due secondi dopo la scena che spiega come si evita.
	 */
	public static void leave(ServerPlayer player) {
		AwakeningConfig config = config();

		GateManager.sendHome(player);

		player.setHealth(player.getMaxHealth());
		player.removeEffect(MobEffects.BLINDNESS);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, config.graceTicks(), 1));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, config.graceTicks(), 0));

		set(player, new PlayerTutorial(HeraldPage.count(), true));

		title(player, Component.translatable("arise.tutorial.leave.title")
						.withStyle(ChatFormatting.AQUA),
				Component.translatable("arise.tutorial.leave.subtitle")
						.withStyle(ChatFormatting.GRAY));

		QuestManager.announceNext(player);
	}

	// ---------------------------------------------------------------- reti di sicurezza

	/** Vero se il giocatore e' dentro la Sala adesso. */
	public static boolean inSanctum(ServerPlayer player) {
		return Sanctum.contains(player, config());
	}

	/**
	 * All'ingresso nel mondo: chi si e' disconnesso dentro la Sala la ritrova in piedi.
	 *
	 * <p>Va chiamato <em>prima</em> della rete di sicurezza dei Gate, che rispedirebbe a casa
	 * chiunque si trovi in quella dimensione senza un'istanza aperta — e la Sala non e' un'istanza.
	 *
	 * @return vero se il giocatore era nella Sala, cioe' se i Gate devono lasciarlo stare
	 */
	public static boolean onJoin(ServerPlayer player) {
		if (!inSanctum(player)) {
			return false;
		}

		MinecraftServer server = player.level().getServer();
		ServerLevel level = server == null ? null : server.getLevel(GateManager.GATE_DIMENSION);

		if (level == null) {
			return false;
		}

		// La stanza puo' essere sopravvissuta e l'Araldo no, se qualcuno ci ha giocato con i
		// comandi. Rimetterlo costa una ricerca e vale un giocatore che non resta chiuso dentro —
		// ma non adesso: al login la stanza attorno al giocatore non e' ancora viva.
		Sanctum.ensureRoom(level, config());
		SETTLING.put(player.getUUID(), SETTLE_TICKS);

		player.sendSystemMessage(Component.translatable("arise.tutorial.sanctum.resumed")
				.withStyle(ChatFormatting.DARK_AQUA));

		return true;
	}

	/**
	 * Tutto il discorso dell'Araldo, in chat, senza andare da nessuna parte.
	 *
	 * <p>E' la risposta alla domanda "come si faceva a estrarre?" tre giorni dopo il risveglio.
	 * Rimandare nella Sala per rileggere sei righe sarebbe stato un teletrasporto gratuito verso un
	 * posto sicuro, cioe' una via di fuga da qualunque combattimento; e riaprire il discorso da
	 * dove era rimasto non serve a chi lo vuole tutto.
	 */
	public static Component recap(ServerPlayer player) {
		for (HeraldPage page : HeraldPage.values()) {
			player.sendSystemMessage(Component.translatable("arise.tutorial.herald.says", page.line()));
		}

		return Component.translatable("arise.tutorial.recap");
	}

	/** Per i comandi: rientra nella Sala da capo, ovunque si sia. */
	public static Component restart(ServerPlayer player) {
		set(player, get(player).rewound());
		enter(player);
		return Component.translatable("arise.tutorial.restarted");
	}

	/**
	 * Tira su la Sala e ci mette l'Araldo, senza portarci nessuno.
	 *
	 * <p>Serve dalla console di un server, dove non c'e' nessun giocatore da cui partire: e' l'unico
	 * modo di guardare se la stanza viene su davvero senza dover morire per vederla. Idempotente
	 * come tutto il resto — chiamarlo su una Sala gia' in piedi rimette solo cio' che manca.
	 */
	public static Component build(MinecraftServer server) {
		ServerLevel level = server.getLevel(GateManager.GATE_DIMENSION);

		if (level == null) {
			return Component.translatable("arise.msg.gate.no_dimension");
		}

		AwakeningConfig config = config();
		Sanctum.ensureRoom(level, config);

		return Component.translatable("arise.tutorial.built",
				config.sanctumX(), config.floorY(), config.sanctumZ());
	}

	/** Per i comandi, e per chi resta bloccato: fuori dalla Sala, discorso chiuso. */
	public static Component skip(ServerPlayer player) {
		leave(player);
		return Component.translatable("arise.tutorial.skipped");
	}

	// ---------------------------------------------------------------- aiuti

	private static void title(ServerPlayer player, Component title, Component subtitle) {
		Overlay.title(player, title, subtitle);
	}
}
