package com.luca.arise;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Accende quel tanto di Minecraft che serve a far girare la logica della mod fuori dal gioco.
 *
 * <p>Quasi tutto quello che vogliamo provare tocca almeno di striscio una classe di Minecraft: un
 * {@code Identifier}, un {@code Component}, un registro. Senza questa chiamata quelle classi
 * esplodono al primo uso con un errore che non parla di quello che e' successo davvero.
 *
 * <p>Si accende una volta sola per esecuzione: {@code Bootstrap.bootStrap()} sa gia' di essere
 * chiamato piu' volte, ma la costante rende evidente che e' una cosa che si fa all'inizio e basta.
 */
public final class GameBootstrap {

	private static boolean started;

	private GameBootstrap() {
	}

	public static synchronized void ensure() {
		if (started) {
			return;
		}

		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		started = true;
	}
}
