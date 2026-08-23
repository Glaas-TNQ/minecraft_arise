package com.luca.arise.fx;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.FxConfig;
import com.luca.arise.progress.Rank;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Tutti gli effetti visivi e sonori della mod, in un posto solo.
 *
 * <p>Prima erano sparsi: una {@code sendParticles} nel gestore dell'esercito, una
 * {@code playSound} in quello dei Gate, ognuna con i suoi numeri. Il risultato era che due momenti
 * importanti suonavano uguali e nessuno se ne accorgeva. Qui ogni momento del gioco ha un metodo
 * col suo nome, e chi cambia la logica non deve decidere anche come suona.
 *
 * <p>Gira <strong>solo sul server</strong>: {@code sendParticles} e {@code playSound} mandano i
 * pacchetti a chi è nel raggio, quindi funzionano anche in multiplayer senza codice di rete
 * dedicato. L'unico effetto client-side è l'aura continua delle ombre, che sta in
 * {@code ShadowEntity.tick} perché un pacchetto ogni tre tick per ogni ombra sarebbe uno spreco.
 */
public final class AriseFx {

	/** Il viola-blu delle ombre, quando non c'è un colore scelto dal giocatore. */
	private static final int SHADOW_VIOLET = 0x6B4FC3;

	/** Forma dell'anello del varco: un'ellisse in piedi, più alta che larga, come una porta. */
	private static final int VARCO_POINTS = 14;
	private static final double VARCO_RADIUS_X = 0.9;
	private static final double VARCO_RADIUS_Y = 1.3;

	private AriseFx() {
	}

	private static FxConfig config() {
		return AriseConfig.get().fx();
	}

	// ---------------------------------------------------------------- esercito d'ombra

	/** Il momento in cui si tende la mano sul cadavere: una spirale che sale, prima dell'esito. */
	public static void extractionRitual(ServerLevel level, Vec3 corpse) {
		FxConfig fx = config();
		spiral(level, corpse, 0.9, 1.8, fx.scaled(28), dust(SHADOW_VIOLET, 1.1F));
		level.sendParticles(ParticleTypes.SCULK_SOUL, corpse.x(), corpse.y() + 0.4, corpse.z(),
				fx.scaled(16), 0.35, 0.4, 0.35, 0.01);
	}

	/** L'estrazione riesce: colonna che sale e il verso che dà il nome alla mod. */
	public static void extractionSuccess(ServerLevel level, Vec3 corpse, Rank rank) {
		FxConfig fx = config();
		column(level, corpse, 2.6, fx.scaled(30), dust(rank.color(), 1.3F));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, corpse.x(), corpse.y() + 0.6, corpse.z(),
				fx.scaled(20), 0.3, 0.7, 0.3, 0.03);

		play(level, corpse, ModSounds.SHADOW_ARISE, 1.0F, 1.0F);
		play(level, corpse, ModSounds.ABILITY_CAST, 0.5F, 0.6F);
	}

	/**
	 * Il dono di un'ombra nominata: un anello sottile del suo colore, e niente altro.
	 *
	 * <p>Deliberatamente piccolo. Beru cura ogni cinque secondi finche' il Monarca sta male, e un
	 * effetto grosso ripetuto dodici volte in un minuto smetterebbe di dire "sta succedendo
	 * qualcosa di buono" e comincerebbe a dire "non riesco a vedere il nemico".
	 */
	public static void namedBoon(net.minecraft.world.level.Level world, Vec3 position, int color) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}

		ring(level, position.add(0.0, 0.2, 0.0), 0.7, config().scaled(10), dust(color, 0.8F), 0.05);
		play(level, position, ModSounds.SOUL_ENLIST, 0.4F, 1.6F);
	}

	/** L'estrazione fallisce: fumo che si spegne, nessun trionfo. */
	public static void extractionFailed(ServerLevel level, Vec3 corpse) {
		level.sendParticles(ParticleTypes.SMOKE, corpse.x(), corpse.y() + 0.5, corpse.z(),
				config().scaled(14), 0.3, 0.2, 0.3, 0.01);
		play(level, corpse, ModSounds.SHADOW_REFUSED, 0.8F, 1.0F);
	}

	/** Un'ombra si materializza: anello che si chiude verso il centro e colonna del suo colore. */
	public static void summon(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		ring(level, position, 1.1, fx.scaled(20), dust(color, 1.0F), -0.15);
		column(level, position, 2.0, fx.scaled(14), dust(color, 0.9F));
		level.sendParticles(ParticleTypes.SCULK_SOUL, position.x(), position.y() + 0.8, position.z(),
				fx.scaled(10), 0.25, 0.4, 0.25, 0.01);
	}

	/** Il suono dell'evocazione, uno solo anche quando escono cinque ombre insieme. */
	public static void summonSound(ServerLevel level, Vec3 position) {
		play(level, position, ModSounds.SHADOW_SUMMON, 0.9F, 0.9F);
	}

	/** Un'ombra viene richiamata: implode verso il basso invece di sparire e basta. */
	public static void recall(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		ring(level, position.add(0.0, 1.2, 0.0), 1.0, fx.scaled(16), dust(color, 0.9F), -0.35);
		level.sendParticles(ParticleTypes.SCULK_SOUL, position.x(), position.y() + 0.9, position.z(),
				fx.scaled(8), 0.15, 0.3, 0.15, 0.01);
		play(level, position, ModSounds.SHADOW_RECALL, 0.7F, 1.0F);
	}

	/** Un'ombra cade: si sfalda verso l'alto, perché torna nell'esercito e non muore davvero. */
	public static void shadowFell(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		column(level, position, 1.6, fx.scaled(18), dust(color, 1.0F));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x(), position.y() + 0.9, position.z(),
				fx.scaled(10), 0.3, 0.3, 0.3, 0.02);
		play(level, position, ModSounds.SHADOW_DEATH, 0.9F, 1.0F);
	}

	/**
	 * La provocazione del Colosso: un'onda bassa che si allarga, e un tonfo.
	 *
	 * <p>Deve vedersi da lontano e non coprire la mischia: un anello a terra, niente colonne. Che
	 * l'aggro sia cambiato e' l'informazione piu' utile di uno scontro difficile, e senza un segno
	 * il giocatore non ha modo di sapere se il suo Colosso sta funzionando.
	 */
	public static void shadowTaunt(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		ring(level, position, 2.2, fx.scaled(24), dust(color, 1.3F), 0.05);
		play(level, position, ModSounds.SHADOW_TAUNT, 0.8F, 0.6F);
	}

	/**
	 * La lancia d'ombra: una linea di polvere dal Mago al bersaglio.
	 *
	 * <p>Il colpo e' istantaneo, quindi la linea <em>e'</em> il proiettile: senza, il danno
	 * arriverebbe dal nulla e sembrerebbe un bug. Sedici punti bastano a sedici blocchi.
	 */
	public static void shadowLance(ServerLevel level, Vec3 from, Vec3 to, int color) {
		FxConfig fx = config();
		int steps = Math.max(4, fx.scaled(16));
		ParticleOptions bolt = dust(color, 0.9F);

		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.lerp(to.add(0.0, 0.9, 0.0), (double) i / steps);
			level.sendParticles(bolt, point.x(), point.y(), point.z(), 1, 0.03, 0.03, 0.03, 0.0);
		}

		level.sendParticles(ParticleTypes.SCULK_SOUL, to.x(), to.y() + 0.9, to.z(),
				fx.scaled(6), 0.2, 0.3, 0.2, 0.01);
		play(level, from, ModSounds.SHADOW_LANCE, 0.7F, 1.2F);
	}

	/** Il bersaglio indicato all'esercito: un anello stretto attorno a lui, e nient'altro. */
	public static void shadowFocus(ServerLevel level, Vec3 target) {
		FxConfig fx = config();
		ring(level, target, 0.8, fx.scaled(16), dust(0xE86A6A, 1.1F), 0.0);
		play(level, target, ModSounds.SHADOW_ORDER, 0.8F, 1.3F);
	}

	/** Il punto da tenere: un anello a terra largo quanto il raggio dell'ordine non e', ma si vede. */
	public static void shadowHold(ServerLevel level, Vec3 post) {
		FxConfig fx = config();
		ring(level, post, 1.6, fx.scaled(20), dust(0x4FC3F7, 1.0F), 0.0);
		play(level, post, ModSounds.SHADOW_ORDER, 0.8F, 0.8F);
	}

	// ---------------------------------------------------------------- il Sistema

	/** Salita di livello: colonna dorata attorno al giocatore. */
	public static void levelUp(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 position = player.position();
		FxConfig fx = config();

		column(level, position, 3.0, fx.scaled(40), dust(0xFFD54F, 1.2F));
		ring(level, position, 1.6, fx.scaled(24), dust(0x4FC3F7, 1.0F), 0.25);
		level.sendParticles(ParticleTypes.END_ROD, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(20), 0.4, 0.9, 0.4, 0.04);

		play(level, position, ModSounds.SYSTEM_LEVEL_UP, 1.0F, 1.0F);
	}

	/** Un'ombra cambia rango: l'anello prende il colore del rango nuovo. */
	public static void rankUp(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position.add(0.0, 1.0, 0.0), 0.9, fx.scaled(22), dust(rank.color(), 1.2F), 0.15);
		play(level, position, ModSounds.SYSTEM_RANK_UP, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- i Gate

	/** L'ingresso in un Gate, dal lato di dentro. */
	public static void gateEntered(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position, 2.0, fx.scaled(30), dust(rank.color(), 1.2F), 0.1);
		level.sendParticles(ParticleTypes.PORTAL, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(40), 0.6, 1.0, 0.6, 0.3);
		play(level, position, ModSounds.GATE_OPEN, 0.9F, 1.0F);
	}

	/**
	 * Il respiro del Gate mentre si esplora: un battito lontano, appena udibile.
	 *
	 * <p>Suonato solo per chi ci sta dentro e con un tono leggermente diverso ogni volta, così non
	 * diventa un metronomo. Un dungeon silenzioso sembra una stanza vuota.
	 */
	public static void gateAmbience(ServerPlayer player) {
		float pitch = 0.7F + player.getRandom().nextFloat() * 0.2F;
		// Suonato alla posizione del giocatore e non spedito solo a lui: dentro un Gate è da solo
		// per costruzione, e così non serve un pacchetto di suono scritto a mano.
		play(player.level(), player.position(), ModSounds.GATE_AMBIENCE, SoundSource.AMBIENT, 0.5F, pitch);
	}

	/**
	 * L'anello del varco, ridisegnato di continuo finché resta aperto.
	 *
	 * <p>Un'ellisse in piedi nel piano X/Y — una porta, non una pozza. Ridisegnarla ogni tre tick
	 * con quattordici punti costa poco e dà le due cose che servono: un varco che si vede da
	 * lontano, e il rango leggibile dal colore prima ancora di avvicinarsi.
	 */
	public static void gateVarco(ServerLevel level, Vec3 base, int color) {
		ParticleOptions particle = dust(color, 1.4F);
		Vec3 centre = base.add(0.0, 1.3, 0.0);

		for (int i = 0; i < VARCO_POINTS; i++) {
			double angle = Math.PI * 2 * i / VARCO_POINTS;
			level.sendParticles(particle,
					centre.x() + Math.cos(angle) * VARCO_RADIUS_X,
					centre.y() + Math.sin(angle) * VARCO_RADIUS_Y,
					centre.z(),
					0, 0.0, 0.0, 0.0, 0.0);
		}

		level.sendParticles(ParticleTypes.PORTAL, centre.x(), centre.y(), centre.z(),
				config().scaled(2), 0.35, 0.6, 0.1, 0.0);
	}

	/** Il varco compare. */
	public static void gateVarcoOpened(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		column(level, position, 3.0, fx.scaled(26), dust(rank.color(), 1.3F));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x(), position.y() + 1.3, position.z(),
				fx.scaled(40), 0.5, 0.8, 0.5, 0.4);
		play(level, position, ModSounds.GATE_OPEN, 0.9F, 0.8F);
	}

	/** Il varco si richiude senza che nessuno lo abbia attraversato. */
	public static void gateVarcoClosed(ServerLevel level, Vec3 position, int color) {
		ring(level, position.add(0.0, 1.3, 0.0), 1.1, config().scaled(16), dust(color, 1.0F), -0.3);
		play(level, position, ModSounds.SHADOW_RECALL, 0.6F, 0.6F);
	}

	/**
	 * Il varco comincia a cedere: l'anello si allarga invece di stringersi.
	 *
	 * <p>La direzione e' l'informazione. Ogni altro anello della mod converge verso il centro —
	 * un'evocazione, un'estrazione, una chiusura sono tutte cose che <em>raccolgono</em>. Questo si
	 * apre, ed e' l'unico: significa che sta per uscire qualcosa.
	 */
	public static void gateBreaching(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		ring(level, position.add(0.0, 0.4, 0.0), 1.4, fx.scaled(22), dust(0xE86A6A, 1.2F), 0.25);
		ring(level, position.add(0.0, 1.4, 0.0), 1.0, fx.scaled(16), dust(color, 1.0F), 0.2);
		play(level, position, ModSounds.GATE_BREACHING, 1.0F, 0.7F);
	}

	/** Il varco cede: colonna, onda a terra, e il rosso che copre il colore del rango. */
	public static void gateBreak(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();

		column(level, position, 5.0, fx.scaled(48), dust(0xE86A6A, 1.6F));
		ring(level, position, 2.0, fx.scaled(32), dust(0xE86A6A, 1.4F), 0.6);
		ring(level, position.add(0.0, 0.2, 0.0), 4.0, fx.scaled(40), dust(color, 1.1F), 0.8);

		level.sendParticles(ParticleTypes.LARGE_SMOKE, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(40), 1.2, 1.0, 1.2, 0.05);

		play(level, position, ModSounds.GATE_BREACH, 1.2F, 0.6F);
	}

	/** Una creatura esce dal varco ceduto: fumo sotto i piedi, niente di piu'. */
	public static void gateBreachMob(ServerLevel level, Vec3 position) {
		level.sendParticles(ParticleTypes.SMOKE, position.x(), position.y() + 0.2, position.z(),
				config().scaled(10), 0.3, 0.1, 0.3, 0.02);
	}

	/**
	 * L'ultima difesa e' intervenuta: un lampo dorato attorno a chi doveva morire.
	 *
	 * <p>Dorato e non rosso, e non e' una scelta di gusto: il rosso in questa mod significa
	 * "qualcosa sta per farti male" (l'ordine in corso sull'HUD, l'anello del cedimento). Un colpo
	 * a cui sei sopravvissuto e' l'opposto, e deve dirlo con l'altro colore.
	 */
	public static void lastStand(net.minecraft.world.level.Level world, Vec3 position) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}

		FxConfig fx = config();
		ring(level, position.add(0.0, 1.0, 0.0), 1.6, fx.scaled(30), dust(0xFFD54F, 1.4F), -0.4);
		column(level, position, 2.4, fx.scaled(20), dust(0xFFD54F, 1.1F));
		play(level, position, ModSounds.SYSTEM_LEVEL_UP, 0.9F, 1.4F);
	}

	/** Un affisso ha appena fatto la sua parte: un lampo breve del suo colore, e basta. */
	public static void affixPulse(ServerLevel level, Vec3 position, int color) {
		level.sendParticles(dust(color, 0.9F), position.x(), position.y() + 1.0, position.z(),
				config().scaled(6), 0.3, 0.3, 0.3, 0.0);
	}

	/**
	 * <strong>L'anello rosso a terra: sta per arrivare un danno ad area, spostati.</strong>
	 *
	 * <p>E' il primo dei tre segnali di preavviso della mod, e da qui in avanti significa sempre e
	 * solo questo. Non si riusa per altro, non cambia colore, non cambia forma: e' quello che
	 * permette di impararlo una volta sola e riconoscerlo per il resto della partita — dal
	 * Volatile che cade in una stanza qualunque alla martellata del Sovrano.
	 */
	public static void telegraph(ServerLevel level, Vec3 centre, double radius) {
		ring(level, centre.add(0.0, 0.1, 0.0), radius, config().scaled(36), dust(0xE86A6A, 1.2F), 0.0);
		play(level, centre, ModSounds.GATE_BREACHING, 0.7F, 1.6F);
	}

	/** E il colpo, quando cade dove l'anello aveva detto. */
	public static void telegraphStrike(ServerLevel level, Vec3 centre, double radius) {
		FxConfig fx = config();
		ring(level, centre.add(0.0, 0.2, 0.0), radius, fx.scaled(40), dust(0xE86A6A, 1.5F), 0.4);
		column(level, centre, 2.0, fx.scaled(20), dust(0xFFD54F, 1.2F));
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x(), centre.y() + 0.5, centre.z(),
				1, 0.0, 0.0, 0.0, 0.0);
		play(level, centre, ModSounds.GATE_BREACH, 0.8F, 1.4F);
	}

	/** Il Sovrano cambia fase: un colpo di gong e la colonna del suo rango. */
	public static void bossPhase(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		column(level, position, 4.0, fx.scaled(40), dust(rank.color(), 1.5F));
		ring(level, position, 3.0, fx.scaled(30), dust(rank.color(), 1.2F), 0.5);
		play(level, position, ModSounds.GATE_BOSS, 1.0F, 0.7F);
	}

	/**
	 * Il varco rosso si chiude alle spalle: un anello rosso che si stringe attorno a chi e' entrato.
	 *
	 * <p>Si stringe, non si allarga. L'anello che si allarga significa "sta per uscire qualcosa"
	 * (il cedimento); questo significa il contrario, e la direzione e' l'unica cosa che il
	 * giocatore deve leggere senza saperlo gia'.
	 */
	public static void redGateSealed(ServerLevel level, Vec3 position) {
		FxConfig fx = config();
		ring(level, position.add(0.0, 0.3, 0.0), 4.0, fx.scaled(44), dust(0xE05A5A, 1.4F), -0.9);
		column(level, position, 3.0, fx.scaled(24), dust(0xE05A5A, 1.1F));
		play(level, position, ModSounds.GATE_BREACHING, 1.0F, 0.5F);
	}

	/** Il gelo morde: fiocchi e un tintinnio, ogni due secondi finche' si sta al freddo. */
	public static void frostBite(net.minecraft.world.level.Level world, Vec3 position) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}

		level.sendParticles(ParticleTypes.SNOWFLAKE, position.x(), position.y() + 1.0, position.z(),
				config().scaled(12), 0.4, 0.6, 0.4, 0.01);
		play(level, position, ModSounds.SHADOW_HURT, 0.35F, 1.8F);
	}

	/**
	 * Un Cubo dell'Abisso si apre. Due effetti diversi, perche' sono due scelte diverse.
	 *
	 * <p>Il benedetto sale, dorato e ordinato. Il maledetto si allarga a terra, viola, e non fa
	 * rumore di festa: quello che ne esce non serve adesso, e l'effetto non deve promettere che
	 * serva.
	 */
	public static void cubeOpened(net.minecraft.world.level.Level world, Vec3 position, Rank rank,
			boolean cursed) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}

		FxConfig fx = config();

		if (cursed) {
			ring(level, position.add(0.0, 0.2, 0.0), 1.8, fx.scaled(28), dust(0x8E6BFF, 1.2F), 0.3);
			ring(level, position.add(0.0, 0.2, 0.0), 1.0, fx.scaled(18), dust(rank.color(), 1.0F), 0.2);
			play(level, position, ModSounds.SHOP_OPEN, 0.8F, 0.6F);
			return;
		}

		column(level, position, 2.2, fx.scaled(26), dust(0xFFD54F, 1.2F));
		ring(level, position.add(0.0, 1.0, 0.0), 1.2, fx.scaled(20), dust(rank.color(), 1.0F), -0.2);
		play(level, position, ModSounds.SHOP_DEAL, 0.9F, 1.2F);
	}

	/** Il boss entra in scena: si sente da tutta la stanza. */
	public static void gateBoss(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		column(level, position, 3.5, fx.scaled(36), dust(rank.color(), 1.4F));
		level.sendParticles(ParticleTypes.SOUL, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(24), 0.7, 0.8, 0.7, 0.02);
		play(level, position, ModSounds.GATE_BOSS, 1.0F, 1.0F);
	}

	/** Gate completato. */
	public static void gateClear(ServerPlayer player, Rank rank) {
		ServerLevel level = player.level();
		Vec3 position = player.position();
		FxConfig fx = config();

		column(level, position, 4.0, fx.scaled(50), dust(rank.color(), 1.3F));
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, position.x(), position.y() + 1.2, position.z(),
				fx.scaled(40), 0.6, 0.8, 0.6, 0.4);
		play(level, position, ModSounds.GATE_CLEAR, 1.0F, 1.0F);
	}

	// ---------------------------------------------------------------- città

	/** Una città ha finito di alzarsi: una colonna alta, visibile da tutta la piazza. */
	public static void cityRaised(ServerLevel level, Vec3 centre, int color) {
		FxConfig fx = config();
		column(level, centre, 12.0, fx.scaled(80), dust(color, 1.6F));
		level.sendParticles(ParticleTypes.END_ROD, centre.x(), centre.y() + 2.0, centre.z(),
				fx.scaled(60), 1.5, 2.0, 1.5, 0.05);
		play(level, centre, ModSounds.GATE_CLEAR, 1.0F, 0.9F);
	}

	/** Partenza e arrivo di un viaggio fra Associazioni. */
	public static void cityTravel(ServerLevel level, Vec3 position, int color) {
		FxConfig fx = config();
		ring(level, position, 1.4, fx.scaled(22), dust(color, 1.2F), 0.3);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(30), 0.4, 0.8, 0.4, 0.3);
		play(level, position, ModSounds.GATE_OPEN, 0.7F, 1.3F);
	}

	/**
	 * Il richiamo di un varco che si e' aperto lontano.
	 *
	 * <p>Suonato alla posizione di chi riceve l'avviso, non a quella del varco: a centocinquanta
	 * blocchi non lo sentirebbe nessuno, e il punto e' proprio far alzare la testa. Chi passa di
	 * li' lo sente insieme a lui, il che va benissimo — il varco e' li' per tutti.
	 */
	public static void gateVarcoDistant(ServerPlayer player) {
		play(player.level(), player.position(), ModSounds.GATE_OPEN, SoundSource.AMBIENT, 0.35F, 0.6F);
	}

	// ---------------------------------------------------------------- gli incarichi

	/**
	 * Il risveglio: il colpo che avrebbe ucciso, e non uccide.
	 *
	 * <p>E' l'effetto piu' grosso della mod e suona una volta sola in tutta la partita, quindi puo'
	 * permettersi di essere sproporzionato: una cupola, una colonna e un anello che si apre.
	 */
	public static void awakening(ServerLevel level, Vec3 position) {
		FxConfig fx = config();

		dome(level, position, 3.0, fx.scaled(120), dust(0x4FC3F7, 1.6F));
		column(level, position, 6.0, fx.scaled(70), dust(SHADOW_VIOLET, 1.4F));
		ring(level, position, 2.2, fx.scaled(40), dust(0x4FC3F7, 1.2F), 0.35);

		level.sendParticles(ParticleTypes.END_ROD, position.x(), position.y() + 1.0, position.z(),
				fx.scaled(50), 0.8, 1.4, 0.8, 0.12);

		play(level, position, ModSounds.SYSTEM_AWAKENING, 1.0F, 1.0F);
	}

	/** Un incarico completato: piccolo, ma si sente. */
	public static void questCompleted(ServerLevel level, Vec3 position) {
		FxConfig fx = config();
		ring(level, position, 1.2, fx.scaled(18), dust(0x4FC3F7, 1.1F), 0.3);
		play(level, position, ModSounds.QUEST_COMPLETED, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- gemme

	/** Una gemma entra in un'incastonatura. */
	public static void gemSocketed(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position, 0.6, fx.scaled(12), dust(rank.color() & 0xFFFFFF, 1.0F), 0.5);
		play(level, position, ModSounds.SHOP_DEAL, 0.7F, 1.4F);
	}

	/** Una gemma ne esce intatta, al banco dell'Associazione. */
	public static void gemExtracted(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position, 0.9, fx.scaled(14), dust(rank.color() & 0xFFFFFF, 1.0F), -0.1);
		play(level, position, ModSounds.SHOP_REFRESH, 0.7F, 1.2F);
	}

	// ---------------------------------------------------------------- l'Abyss Shop

	/** La finestra del Sistema che si apre. Un tintinnio e nient'altro: non e' un evento del mondo. */
	public static void shopOpened(ServerLevel level, Vec3 position) {
		play(level, position, ModSounds.SHOP_OPEN, 0.6F, 1.1F);
	}

	/** Affare concluso: il colore e' quello del rango comprato. */
	public static void shopDeal(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position, 0.8, fx.scaled(16), dust(rank.color() & 0xFFFFFF, 1.1F), 0.25);
		play(level, position, ModSounds.SHOP_DEAL, 0.8F, 1.0F);
	}

	/** L'assortimento che cambia. */
	public static void shopRefreshed(ServerLevel level, Vec3 position) {
		FxConfig fx = config();
		ring(level, position, 1.0, fx.scaled(14), dust(SHADOW_VIOLET, 0.9F), 0.4);
		play(level, position, ModSounds.SHOP_REFRESH, 0.7F, 0.9F);
	}

	/**
	 * Un pezzo che cade: un anello di polvere del colore del rango, attorno all'oggetto.
	 *
	 * <p>Serve a farlo <em>notare</em>. Un oggetto per terra in mezzo alla roba lasciata da un mob
	 * si confonde con il resto, e un pezzo di rango A che passa inosservato e' un premio sprecato.
	 */
	public static void gearDropped(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		ring(level, position, 0.5, fx.scaled(10), dust(rank.color() & 0xFFFFFF, 1.0F), 0.2);
		play(level, position, ModSounds.GEAR_DROP, 0.7F, 0.8F + rank.ordinal() * 0.08F);
	}

	// ---------------------------------------------------------------- abilità

	/** Scatto d'ombra: una scia fra le due posizioni, così si legge la direzione. */
	public static void dash(ServerLevel level, Vec3 from, Vec3 to) {
		FxConfig fx = config();
		int steps = fx.scaled(12);
		ParticleOptions trail = dust(SHADOW_VIOLET, 1.0F);

		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.lerp(to, (double) i / steps);
			level.sendParticles(trail, point.x(), point.y() + 1.0, point.z(), 1, 0.08, 0.25, 0.08, 0.0);
		}

		level.sendParticles(ParticleTypes.SCULK_SOUL, to.x(), to.y() + 1.0, to.z(),
				fx.scaled(12), 0.25, 0.5, 0.25, 0.02);
		play(level, to, ModSounds.ABILITY_CAST, 0.6F, 1.4F);
	}

	/** Scambio d'ombra: le due colonne partono insieme, una per capo. */
	public static void exchange(ServerLevel level, Vec3 a, Vec3 b, int color) {
		FxConfig fx = config();
		column(level, a, 2.2, fx.scaled(18), dust(color, 1.1F));
		column(level, b, 2.2, fx.scaled(18), dust(color, 1.1F));
		play(level, a, ModSounds.ABILITY_CAST, 0.8F, 0.7F);
		play(level, b, ModSounds.ABILITY_CAST, 0.8F, 0.7F);
	}

	/** Dominio del Monarca: una cupola, non una nuvola. */
	public static void domain(ServerLevel level, Vec3 centre, double radius) {
		FxConfig fx = config();
		dome(level, centre, radius, fx.scaled(60), dust(0x7E57C2, 1.2F));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x(), centre.y() + 0.4, centre.z(),
				fx.scaled(30), radius / 3.0, 0.4, radius / 3.0, 0.01);
		play(level, centre, ModSounds.ABILITY_DOMAIN, 1.0F, 1.0F);
	}

	/** Autorità del Sovrano: onda che si allarga a terra fino al raggio dichiarato. */
	public static void authority(ServerLevel level, Vec3 centre, double radius) {
		FxConfig fx = config();
		ParticleOptions wave = dust(0x4FC3F7, 1.3F);

		for (int step = 1; step <= 4; step++) {
			ring(level, centre.add(0.0, 0.2, 0.0), radius * step / 4.0,
					fx.scaled(10 + step * 6), wave, 0.05);
		}

		play(level, centre, ModSounds.ABILITY_AUTHORITY, 0.9F, 1.0F);
	}

	// ---------------------------------------------------------------- il mercato

	/**
	 * Un affare concluso al bancone.
	 *
	 * <p>Sommesso e corto: al mercato si compra spesso, e un effetto da rango S ripetuto dodici
	 * volte di fila diventerebbe rumore. Il colore e' l'oro della moneta, che e' l'unica cosa che
	 * distingue questo momento dagli altri.
	 */
	public static void marketDeal(ServerLevel level, Vec3 position) {
		FxConfig fx = config();
		ring(level, position.add(0.0, 1.0, 0.0), 0.45, fx.scaled(8), dust(0xFFD54F, 0.8F), 0.15);
		play(level, position, ModSounds.SHOP_DEAL, 0.5F, 1.25F);
	}

	// ---------------------------------------------------------------- l'Officina delle Anime

	/**
	 * Un macchinario a meta' del suo giro di lavoro.
	 *
	 * <p>Poche particelle e un suono sottovoce, di proposito: questo effetto puo' ripetersi ogni
	 * secondo per ogni macchina accesa, e un'officina di dodici blocchi non deve suonare come una
	 * fanfara. Serve solo a dire "sta andando" a chi ci passa davanti.
	 */
	public static void machineWorking(ServerLevel level, Vec3 centre, int color) {
		FxConfig fx = config();
		level.sendParticles(dust(color, 0.7F), centre.x(), centre.y() + 0.45, centre.z(),
				fx.scaled(3), 0.28, 0.22, 0.28, 0.01);
		play(level, centre, ModSounds.MACHINE_WORK, SoundSource.BLOCKS, 0.22F, 1.4F);
	}

	/** Un giro finito: l'anello dice che c'e' qualcosa da prendere. */
	public static void machineDone(ServerLevel level, Vec3 centre, int color) {
		FxConfig fx = config();
		ring(level, centre, 0.55, fx.scaled(10), dust(color, 0.9F), 0.18);
		play(level, centre, ModSounds.MACHINE_DONE, SoundSource.BLOCKS, 0.5F, 1.0F);
	}

	/**
	 * La fusione riuscita nel Crogiolo.
	 *
	 * <p>Questo si', invece, e' rumoroso: fondere quattro anime e un catalizzatore e' un evento
	 * raro e costoso, e deve sentirsi diverso dal ronzio di una macchina che macina ferro.
	 */
	public static void soulFused(ServerLevel level, Vec3 centre, Rank rank) {
		FxConfig fx = config();
		column(level, centre, 1.8, fx.scaled(22), dust(rank.color(), 1.2F));
		ring(level, centre, 0.9, fx.scaled(16), dust(rank.color(), 1.0F), 0.3);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x(), centre.y() + 0.7, centre.z(),
				fx.scaled(8), 0.2, 0.3, 0.2, 0.02);
		play(level, centre, ModSounds.SOUL_FUSE, 0.9F, 0.8F + rank.ordinal() * 0.07F);
	}

	/** Un'anima arruolata: l'esercito ha un membro in piu'. */
	public static void soulEnlisted(ServerLevel level, Vec3 position, Rank rank) {
		FxConfig fx = config();
		spiral(level, position, 0.7, 1.6, fx.scaled(20), dust(rank.color(), 1.1F));
		play(level, position, ModSounds.SOUL_ENLIST, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- primitive

	/** Un anello orizzontale; {@code rise} è la velocità verticale di ogni particella. */
	private static void ring(ServerLevel level, Vec3 centre, double radius, int count,
			ParticleOptions particle, double rise) {
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count;
			double x = centre.x() + Math.cos(angle) * radius;
			double z = centre.z() + Math.sin(angle) * radius;

			// count 0 con velocità esplicita: è il modo di sparare una particella *diretta*
			// invece di una nuvola casuale. Gli altri tre numeri diventano il vettore velocità.
			level.sendParticles(particle, x, centre.y() + 0.1, z, 0,
					-Math.cos(angle) * 0.05, rise, -Math.sin(angle) * 0.05, 1.0);
		}
	}

	/** Una colonna che sale, con un po' di dispersione. */
	private static void column(ServerLevel level, Vec3 base, double height, int count,
			ParticleOptions particle) {
		for (int i = 0; i < count; i++) {
			double progress = (double) i / count;
			double angle = progress * Math.PI * 6;
			double radius = 0.5 * (1.0 - progress * 0.6);

			level.sendParticles(particle,
					base.x() + Math.cos(angle) * radius,
					base.y() + progress * height,
					base.z() + Math.sin(angle) * radius,
					0, 0.0, 0.08, 0.0, 1.0);
		}
	}

	/** Una spirale stretta che sale, per i momenti rituali. */
	private static void spiral(ServerLevel level, Vec3 base, double radius, double height,
			int count, ParticleOptions particle) {
		for (int i = 0; i < count; i++) {
			double progress = (double) i / count;
			double angle = progress * Math.PI * 4;

			level.sendParticles(particle,
					base.x() + Math.cos(angle) * radius * (1.0 - progress),
					base.y() + progress * height,
					base.z() + Math.sin(angle) * radius * (1.0 - progress),
					0, 0.0, 0.05, 0.0, 1.0);
		}
	}

	/** Punti sparsi sulla superficie di una semisfera: il bordo del Dominio. */
	private static void dome(ServerLevel level, Vec3 centre, double radius, int count,
			ParticleOptions particle) {
		for (int i = 0; i < count; i++) {
			// Spirale di Fibonacci sulla semisfera: distribuzione uniforme senza numeri casuali,
			// quindi la cupola ha sempre lo stesso aspetto e non "sfarfalla" fra un uso e l'altro.
			double y = (double) i / count;
			double r = Math.sqrt(1.0 - y * y);
			double angle = i * Math.PI * (3.0 - Math.sqrt(5.0));

			level.sendParticles(particle,
					centre.x() + Math.cos(angle) * r * radius,
					centre.y() + y * radius,
					centre.z() + Math.sin(angle) * r * radius,
					0, 0.0, 0.01, 0.0, 1.0);
		}
	}

	/** Polvere colorata: l'unico particellare vanilla che accetta un colore arbitrario. */
	public static DustParticleOptions dust(int color, float scale) {
		return new DustParticleOptions(color & 0xFFFFFF, scale);
	}

	private static void play(ServerLevel level, Vec3 position, SoundEvent sound, float volume, float pitch) {
		play(level, position, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void play(ServerLevel level, Vec3 position, SoundEvent sound, SoundSource source,
			float volume, float pitch) {
		level.playSound(null, position.x(), position.y(), position.z(), sound, source, volume, pitch);
	}

	/** Comodità per gli effetti centrati su un'entità. */
	public static void play(Entity entity, SoundEvent sound, float volume, float pitch) {
		if (entity.level() instanceof ServerLevel level) {
			play(level, entity.position(), sound, volume, pitch);
		}
	}
}
