package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * L'Abisso: la discesa che non finisce.
 *
 * <p>Era la lacuna numero uno del PRD, e la piu' semplice da descrivere: al diciottesimo incarico il
 * Sistema dice «da qui in avanti taccio e guardo», e da li' in poi non c'e' <em>niente</em>. Nessun
 * traguardo che ne apra un altro, nessuna sfida che scali, nessun motivo per aprire il varco numero
 * cinquanta dopo aver aperto il quarantanovesimo.
 *
 * <p>L'Abisso e' un varco a cui si chiede una <strong>profondita'</strong>. Si scende un gradino
 * per volta — mai un salto — e ogni gradino e' piu' duro del precedente in un modo che si puo'
 * nominare. Il cronometro conta dall'ingresso, e il tempo migliore resta scritto.
 *
 * <h2>Come si fa piu' dura</h2>
 *
 * <p>Non con un moltiplicatore. La regola che regge tutto il blocco e' <strong>una regola nuova
 * ogni cinque gradini</strong>, e sono regole con un nome: il giocatore che perde deve poter dire
 * <em>perche'</em> ha perso, e «i mob hanno piu' vita» non e' una risposta che si possa usare.
 *
 * <ul>
 *   <li><strong>5</strong> — ogni stanza ha un mob con un affisso, non piu' solo dal rango C;
 *   <li><strong>10</strong> — il Sovrano parte gia' in seconda fase: spazza le ombre dall'inizio;
 *   <li><strong>15</strong> — l'esercito scende con meta' dei posti in campo;
 *   <li><strong>20</strong> — le ombre cadute non si riprendono fino all'uscita.
 * </ul>
 *
 * <p>Le quattro si sommano: al venticinquesimo ci sono tutte, e a quel punto non e' piu' un varco
 * piu' grosso, e' un altro gioco. Il rango sale ogni tre gradini fino a S e poi si ferma — oltre,
 * a crescere sono soltanto le regole, perche' oltre il rango S non c'e' una tabella di mob piu'
 * cattivi e inventarla sarebbe esattamente il moltiplicatore che si vuole evitare.
 */
public final class Abyss {

	/** Ogni quanti gradini sale il rango, finche' c'e' rango da salire. */
	public static final int RANK_EVERY = 3;

	private Abyss() {
	}

	public static AbyssRecord record(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.ABYSS);
	}

	public static void setRecord(ServerPlayer player, AbyssRecord record) {
		player.setAttached(ModAttachments.ABYSS, record);
	}

	/**
	 * Il rango di un varco a questa profondita'.
	 *
	 * <p>Sale ogni tre gradini e si ferma a S. Oltre, la difficolta' arriva solo dalle regole:
	 * inventare una tabella di mob peggiori di quella di rango S sarebbe il moltiplicatore
	 * travestito che questo blocco esiste per non fare.
	 */
	public static Rank rankAt(int depth) {
		Rank[] ranks = Rank.values();
		return ranks[Math.clamp((depth - 1) / RANK_EVERY, 0, ranks.length - 1)];
	}

	/** Le regole in vigore a questa profondita'. Vuota sotto il quinto gradino. */
	public static List<AbyssRule> rulesAt(int depth) {
		List<AbyssRule> rules = new ArrayList<>();

		for (AbyssRule rule : AbyssRule.values()) {
			if (depth >= rule.depth()) {
				rules.add(rule);
			}
		}

		return rules;
	}

	public static boolean hasRule(int depth, AbyssRule rule) {
		return depth >= rule.depth();
	}

	/**
	 * Il preventivo di una discesa.
	 *
	 * <p>Il seme e' la profondita' e non un numero a caso, ed e' una scelta: due Cacciatori che
	 * scendono al gradino dodici trovano la <strong>stessa pianta</strong>, e possono parlarne. Un
	 * contenuto competitivo con un cronometro ha bisogno che la prova sia la stessa per tutti,
	 * altrimenti il record non confronta niente.
	 */
	public static GateOffer offer(GateConfig config, int depth) {
		return GateOffer.roll(config, rankAt(depth), seedFor(depth));
	}

	/** Il seme di un gradino. Fisso, e diverso da quello di ogni altro. */
	public static long seedFor(int depth) {
		return 0x5DEEC_ABE5L * depth + 0x9E3779B97F4A7C15L;
	}

	/**
	 * Quanti posti in campo restano a questa profondita'.
	 *
	 * <p>Meta', arrotondata per eccesso, dal quindicesimo in giu'. Per eccesso e non per difetto:
	 * un Cacciatore che scendesse con zero ombre non starebbe giocando alla stessa mod.
	 */
	public static int summonLimitAt(int depth, int limit) {
		return hasRule(depth, AbyssRule.THINNED) ? Math.max(1, (limit + 1) / 2) : limit;
	}

	/** Le righe che si leggono prima di scendere: profondita', rango, regole in vigore. */
	public static List<Component> brief(ServerPlayer player, int depth) {
		List<Component> lines = new ArrayList<>();
		AbyssRecord record = record(player);

		lines.add(Component.translatable("arise.msg.abyss.descend", depth, rankAt(depth).label())
				.withStyle(ChatFormatting.DARK_PURPLE));

		for (AbyssRule rule : rulesAt(depth)) {
			lines.add(Component.translatable("arise.msg.abyss.rule",
					rule.label(), rule.description()).withStyle(ChatFormatting.GRAY));
		}

		if (record.bestTicks() > 0) {
			lines.add(Component.translatable("arise.msg.abyss.record",
					record.deepest(), seconds(record.bestTicks())).withStyle(ChatFormatting.GRAY));
		}

		return lines;
	}

	/**
	 * Una discesa chiusa: aggiorna il record e dice se e' stato battuto qualcosa.
	 *
	 * @param ticks quanto e' durata
	 */
	public static List<Component> completed(ServerPlayer player, int depth, long ticks) {
		AbyssRecord before = record(player);
		AbyssRecord after = before.with(depth, ticks);

		setRecord(player, after);

		List<Component> lines = new ArrayList<>();

		lines.add(Component.translatable("arise.msg.abyss.cleared", depth, seconds(ticks))
				.withStyle(ChatFormatting.LIGHT_PURPLE));

		if (after.deepest() > before.deepest()) {
			lines.add(Component.translatable("arise.msg.abyss.deeper", after.next())
					.withStyle(ChatFormatting.GOLD));
		}

		if (before.bestTicks() > 0 && after.bestTicks() < before.bestTicks()) {
			lines.add(Component.translatable("arise.msg.abyss.faster",
					seconds(before.bestTicks() - after.bestTicks())).withStyle(ChatFormatting.GOLD));
		}

		return lines;
	}

	/** Quanto rende una discesa, in piu' di un varco normale del suo rango. */
	public static double reward(int depth) {
		return 1.0 + depth * AriseConfig.get().gates().abyssRewardPerDepth();
	}

	private static long seconds(long ticks) {
		return Math.max(1L, ticks / 20L);
	}
}
