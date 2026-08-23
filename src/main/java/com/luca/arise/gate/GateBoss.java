package com.luca.arise.gate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.Rank;
import com.luca.arise.shadow.ShadowEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Il Sovrano del Gate: da mob vanilla con due attributi moltiplicati a incontro.
 *
 * <p>Il boss aveva sei volte la vita e il doppio del danno, e nient'altro. Un sacco di punti vita
 * non e' una sfida: e' la stessa cosa di prima, piu' lunga. Un incontro invece <em>insegna</em> —
 * il giocatore ne esce sentendo di aver vinto perche' ha capito, non perche' aveva piu' pozioni.
 *
 * <h2>Tre fasi, una regola nuova per fase</h2>
 *
 * <ul>
 *   <li><strong>Prima</strong> (100-66%): la martellata. Un anello rosso a terra, poco piu' di un
 *       secondo, poi il colpo. E' l'unico pattern, e serve a impararlo.
 *   <li><strong>Seconda</strong> (66-33%): la martellata diventa piu' fitta e il Sovrano
 *       <strong>spazza le ombre</strong>. Non le distrugge: le fa cadere, e cadere costa un minuto
 *       di recupero. L'esercito smette di essere un autopilota e diventa un cuscinetto che si
 *       consuma.
 *   <li><strong>Terza</strong> (33-0%): <strong>l'aggro si stacca dall'esercito</strong>. Il
 *       Sovrano vede attraverso l'ombra e punta il Monarca, ed e' il momento in cui bisogna
 *       giocare in prima persona almeno una volta per incontro.
 * </ul>
 *
 * <p>Nessuna fase aggiunge un attacco nuovo dopo il primo: aggiungono <em>regole</em>. E' la
 * differenza fra un boss che si impara e uno che si subisce, ed e' anche l'unica via per alzare la
 * difficolta' senza rompere la leggibilita'.
 *
 * <p>Non ha un battito suo: gira dentro {@code GateManager.tick}, una volta al secondo, per il solo
 * giocatore che sta dentro quel varco. Un boss che ticchettasse per conto proprio sarebbe un boss
 * che ticchetta anche quando nella sua sala non c'e' nessuno.
 */
public final class GateBoss {

	/** A che frazione di vita comincia la seconda fase, e a quale la terza. */
	private static final float PHASE_TWO = 0.66F;
	private static final float PHASE_THREE = 0.33F;

	/** Ogni quante chiamate (cioe' secondi) il Sovrano martella, per fase. */
	private static final int[] SLAM_EVERY = {6, 4, 3};

	/** Quanto e' larga la martellata, e quanto fa male rispetto al danno del Sovrano. */
	private static final double SLAM_RADIUS = 4.0;
	private static final double SLAM_SHARE = 0.8;

	/** Quanto passa fra l'anello e il colpo. Un secondo e un quarto: si esce camminando. */
	private static final int SLAM_FUSE = 25;

	/** Quanto lontano arriva la spazzata che fa cadere le ombre, in seconda fase. */
	private static final double SWEEP_RADIUS = 6.0;

	/**
	 * Quale quota della vita di un'ombra si porta via la spazzata.
	 *
	 * <p>Un terzo, non la meta'. A meta' bastavano due martellate — sei secondi in seconda fase —
	 * per svuotare il campo, e dopo quelle il giocatore restava senza esercito per un minuto intero
	 * di recupero: non un cuscinetto che si consuma, un esercito cancellato. A un terzo servono tre
	 * colpi, ed e' il tempo perche' la perdita si veda arrivare e si possa decidere di richiamarle.
	 *
	 * <p>Il raggio invece resta stretto apposta: chi combatte in mischia lo prende, un Mago a sedici
	 * blocchi no. La spazzata non e' una tassa sull'esercito — e' una tassa su <em>quel modo</em> di
	 * schierarlo, ed e' cosi' che diventa una scelta di squadra.
	 */
	private static final float SWEEP_SHARE = 0.35F;

	/** giocatore → a che fase e' arrivato il suo Sovrano. Si azzera con l'istanza. */
	private static final Map<UUID, Integer> PHASE = new HashMap<>();

	/** giocatore → quante volte il battito ha girato da quando il Sovrano ha martellato. */
	private static final Map<UUID, Integer> SINCE_SLAM = new HashMap<>();

	private GateBoss() {
	}

	/**
	 * Un battito del Sovrano, chiamato una volta al secondo mentre il giocatore e' nel varco.
	 *
	 * <p>Non fa niente finche' il giocatore non e' nella sala: un boss che martella mentre chi lo
	 * combatte e' tre stanze piu' indietro sta solo sprecando i suoi anelli rossi.
	 */
	public static void tick(ServerPlayer player, Mob boss, Rank rank, double reach) {
		if (boss == null || !boss.isAlive() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		if (player.distanceToSqr(boss.position()) > reach * reach) {
			return;
		}

		int phase = phaseOf(boss);
		announce(player, boss, rank, phase, level);

		int since = SINCE_SLAM.merge(player.getUUID(), 1, Integer::sum);

		if (since < SLAM_EVERY[phase]) {
			return;
		}

		SINCE_SLAM.put(player.getUUID(), 0);
		slam(level, player, boss, phase);
	}

	/** Zero, uno o due, letta dalla vita. Non e' salvata da nessuna parte: e' una divisione. */
	private static int phaseOf(Mob boss) {
		float fraction = boss.getMaxHealth() <= 0.0F
				? 1.0F
				: boss.getHealth() / boss.getMaxHealth();

		if (fraction <= PHASE_THREE) {
			return 2;
		}

		return fraction <= PHASE_TWO ? 1 : 0;
	}

	/**
	 * Il cambio di fase si annuncia una volta sola, e si sente.
	 *
	 * <p>Il terzo dei segnali di preavviso del PRD: un suono grave dedicato significa «da adesso le
	 * regole sono cambiate». Senza, il giocatore si accorge della fase nuova solo perche' comincia
	 * a perdere, e non sa dire perche'.
	 */
	private static void announce(ServerPlayer player, Mob boss, Rank rank, int phase,
			ServerLevel level) {
		int known = PHASE.getOrDefault(player.getUUID(), 0);

		if (phase <= known) {
			return;
		}

		PHASE.put(player.getUUID(), phase);

		AriseFx.bossPhase(level, boss.position(), rank);
		Overlay.title(player, title(phase).withStyle(ChatFormatting.RED), subtitle(phase));

		if (phase == 2) {
			// La fase che stacca l'aggro dall'esercito: il Sovrano vede attraverso l'ombra. E'
			// dichiarata invece che nascosta perche' e' esattamente la lamentela classica delle
			// build a evocazioni — i nemici che ignorano i minion — usata di proposito, una volta
			// per incontro, come meccanica invece che come difetto.
			boss.setTarget(player);
		}
	}

	/**
	 * Il titolo di una fase, scritto per esteso invece che composto.
	 *
	 * <p>Uno {@code switch} e non {@code "arise.title.boss_phase_" + phase}: il collaudo statico
	 * verifica che ogni chiave nominata nel codice esista davvero nei file di lingua, e una chiave
	 * costruita con un numero non la sa leggere. Perdere quel controllo per risparmiare tre righe
	 * significa scoprire una traduzione mancante in gioco, dove diventa testo grezzo sullo schermo.
	 */
	private static MutableComponent title(int phase) {
		return switch (phase) {
			case 1 -> Component.translatable("arise.title.boss_phase_1");
			default -> Component.translatable("arise.title.boss_phase_2");
		};
	}

	private static Component subtitle(int phase) {
		return switch (phase) {
			case 1 -> Component.translatable("arise.subtitle.boss_phase_1");
			default -> Component.translatable("arise.subtitle.boss_phase_2");
		};
	}

	/**
	 * La martellata: anello adesso, colpo fra un secondo, dove il giocatore era.
	 *
	 * <p>Punta il giocatore e non il Sovrano, ed e' la scelta che rende il pattern schivabile: il
	 * cerchio compare sotto i piedi di chi lo deve evitare, non sotto quelli di chi lo tira.
	 */
	private static void slam(ServerLevel level, ServerPlayer player, Mob boss, int phase) {
		Vec3 where = player.position();

		float damage = (float) (boss.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * SLAM_SHARE);

		DelayedStrike.schedule(level, where, SLAM_RADIUS, damage, SLAM_FUSE);

		if (phase >= 1) {
			sweep(level, boss);
		}
	}

	/**
	 * La spazzata: le ombre vicine cadono, non muoiono.
	 *
	 * <p>Toglie meta' della vita a chi e' in campo, e chi cade torna disponibile dopo il minuto di
	 * recupero che l'Officina ha gia' scritto. Cancellare l'esercito sarebbe una punizione che
	 * costa ore; consumarlo e' una risorsa che finisce, ed e' quello che deve essere.
	 */
	private static void sweep(ServerLevel level, Mob boss) {
		for (ShadowEntity shadow : level.getEntitiesOfClass(ShadowEntity.class,
				boss.getBoundingBox().inflate(SWEEP_RADIUS), LivingEntity::isAlive)) {
			shadow.hurtServer(level, level.damageSources().magic(),
					shadow.getMaxHealth() * SWEEP_SHARE);
		}

		AriseFx.telegraphStrike(level, boss.position(), SWEEP_RADIUS);
	}

	/** Il Sovrano di questo giocatore non c'e' piu': dimentica la sua fase. */
	public static void forget(UUID player) {
		PHASE.remove(player);
		SINCE_SLAM.remove(player);
	}

	/** Il Sovrano di questa istanza, o {@code null} se e' gia' caduto. */
	public static Mob find(ServerLevel gate, UUID bossId) {
		return bossId != null && gate.getEntity(bossId) instanceof Mob boss ? boss : null;
	}

	/** La distanza entro cui il Sovrano combatte davvero: il raggio della sua sala. */
	public static double reach(GateConfig config) {
		return config.halfRoom(GateLayout.Kind.BOSS);
	}

	/** Scorciatoia: la config dei Gate, senza farla passare da tre firme. */
	public static GateConfig config() {
		return AriseConfig.get().gates();
	}
}
