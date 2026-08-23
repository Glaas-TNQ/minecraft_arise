package com.luca.arise.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A che punto e' il giocatore con la prima ora.
 *
 * <p>Due valori, e la stessa idea che regge {@link com.luca.arise.quest.PlayerQuests}: lo stato e'
 * un numero, non un insieme di cose fatte. {@code page} e' la prossima pagina che l'Araldo dira';
 * quando supera l'ultima, il discorso e' finito e non ricomincia.
 *
 * <p>Non c'e' nessun campo che dica "sono dentro la Sala", ed e' voluto: quello non e' uno stato da
 * salvare, e' un posto in cui si sta. Lo si chiede al mondo — {@link Sanctum#contains} — perche' un
 * booleano salvato e la posizione vera entrano in disaccordo alla prima disconnessione andata male,
 * e il disaccordo lascia un giocatore chiuso in una stanza senza porte.
 *
 * @param page     la prossima pagina del discorso dell'Araldo
 * @param welcomed se il Sistema ha gia' salutato questo giocatore la prima volta che e' entrato
 */
public record PlayerTutorial(int page, boolean welcomed) {

	public static final Codec<PlayerTutorial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("page", 0).forGetter(PlayerTutorial::page),
			Codec.BOOL.optionalFieldOf("welcomed", false).forGetter(PlayerTutorial::welcomed)
	).apply(instance, PlayerTutorial::new));

	/** Nessuno ha ancora detto niente a nessuno. */
	public static final PlayerTutorial INITIAL = new PlayerTutorial(0, false);

	/** La pagina che l'Araldo deve dire adesso, o {@code null} se ha finito. */
	public HeraldPage current() {
		return page >= 0 && page < HeraldPage.count() ? HeraldPage.values()[page] : null;
	}

	public boolean done() {
		return page >= HeraldPage.count();
	}

	public PlayerTutorial next() {
		return new PlayerTutorial(page + 1, welcomed);
	}

	public PlayerTutorial welcomed(boolean value) {
		return new PlayerTutorial(page, value);
	}

	/** Riporta il discorso all'inizio, senza toccare il saluto: serve a {@code /arise tutorial}. */
	public PlayerTutorial rewound() {
		return new PlayerTutorial(0, welcomed);
	}
}
