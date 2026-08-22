package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GearConfig;
import com.luca.arise.config.GemConfig;
import com.luca.arise.config.LootConfig;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.gem.Gem;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemRoll;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Il bottino di un Gate completato.
 *
 * <p>Chiude il ciclo che rendeva la mod una progressione senza acquisizione: si entra, si combatte,
 * si esce con qualcosa che prima non c'era. E' la ragione per cui l'Abyss Shop puo' permettersi di
 * essere caro (design §8.5).
 *
 * <p>Tre cose cadono, e sono tre cose diverse di proposito: <strong>pezzi</strong> quasi sempre,
 * perche' sono il pane; una <strong>gemma</strong> ogni tanto, perche' cambia una regola e non un
 * numero; una <strong>pergamena</strong>, che e' l'unico modo di guadagnare punti statistica senza
 * salire di livello — e che non si compra da nessuna parte, altrimenti sarebbero statistiche
 * infinite con i soul coin.
 */
public final class GateLoot {

	private GateLoot() {
	}

	/** Assegna il bottino e restituisce le righe da mostrare al giocatore. */
	public static List<Component> award(ServerPlayer player, Rank rank) {
		AriseConfig config = AriseConfig.get();
		LootConfig loot = config.gates().loot();
		GearConfig gear = config.gear();
		GemConfig gems = config.gems();
		RandomSource random = player.level().getRandom();

		List<Component> lines = new ArrayList<>();

		for (int i = 0; i < count(loot.pieceCount(rank.ordinal()), random); i++) {
			Rank pieceRank = random.nextDouble() < loot.upgradeChance() ? next(rank) : rank;
			GearPiece piece = GearRoll.rollAny(gear, pieceRank, random);

			// grant() sa gia' dire che lo zaino e' pieno: si inoltra il suo messaggio invece di
			// far sparire il pezzo in silenzio, che e' il modo migliore di far arrabbiare qualcuno.
			lines.add(GearManager.grant(player, piece));
		}

		if (random.nextDouble() < loot.gemChanceAt(rank.ordinal())) {
			Gem gem = GemRoll.rollAny(gems, gear, rank, random);
			lines.add(GemManager.grant(player, gem));
		}

		int points = count(loot.scrollPointsAt(rank.ordinal()), random);
		if (points > 0) {
			scroll(player, points);
			lines.add(Component.translatable("arise.msg.loot.scroll", points));
		}

		return lines;
	}

	/**
	 * La pergamena: punti statistica in piu', da spendere dove si vuole.
	 *
	 * <p>Non e' un oggetto da tenere in tasca. Un consumabile da usare quando si vuole avrebbe
	 * voluto dire un'altra lista da salvare, un'altra schermata e un altro bottone, per una scelta
	 * che nessuno rimanderebbe mai — i punti si prendono e basta. La scelta vera, su quale
	 * statistica metterli, resta intatta e sta gia' nella schermata di stato.
	 */
	private static void scroll(ServerPlayer player, int points) {
		PlayerProgress progress = player.getAttachedOrCreate(ModAttachments.PROGRESS);
		player.setAttached(ModAttachments.PROGRESS,
				progress.withUnspentPoints(progress.unspentPoints() + points));
		ProgressManager.applyAttributes(player);
	}

	/**
	 * Un conteggio con la virgola diventa un numero intero.
	 *
	 * <p>La parte intera e' garantita, la parte decimale e' la probabilita' di uno in piu'. Un
	 * semplice arrotondamento avrebbe reso identici due ranghi vicini e poi saltato di colpo.
	 */
	private static int count(double amount, RandomSource random) {
		int whole = (int) Math.floor(amount);
		return random.nextDouble() < amount - whole ? whole + 1 : whole;
	}

	private static Rank next(Rank rank) {
		return Rank.values()[Math.min(rank.ordinal() + 1, Rank.values().length - 1)];
	}
}
