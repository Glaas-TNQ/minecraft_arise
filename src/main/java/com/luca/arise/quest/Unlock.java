package com.luca.arise.quest;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * I sistemi della mod, e il fatto che all'inizio siano tutti spenti.
 *
 * <p>Prima si nasceva con tutto acceso: HUD, quattro abilita', ventiquattro slot d'equipaggiamento
 * e un negozio, tutto addosso al primo secondo di gioco. Era la cosa piu' vicina a un difetto che
 * questa mod avesse — non perche' fosse troppo, ma perche' non c'era nessun momento in cui
 * qualcosa <em>arrivava</em>.
 *
 * <p>Adesso ogni sistema si apre quando un incarico lo concede. Non e' una lista di permessi
 * salvata da qualche parte: si ricava da quanto lontano si e' arrivati nella catena degli
 * incarichi (vedi {@link PlayerQuests}), cosi' non c'e' un secondo valore da tenere in sincronia
 * con il primo.
 */
public enum Unlock implements StringRepresentable {

	/** Il Sistema stesso: HUD, XP, livelli. Senza questo non esiste nient'altro. */
	SYSTEM("system"),
	/** La schermata di stato e i punti da spendere. */
	STATS("stats"),
	/** Estrazione ed esercito d'ombra. */
	ARMY("army"),
	/** Le quattro abilita' attive. */
	ABILITIES("abilities"),
	/** L'equipaggiamento del Cacciatore. */
	GEAR("gear"),
	/** I varchi si aprono, e ci si puo' entrare. */
	GATES("gates"),
	/** L'Abyss Shop. */
	SHOP("shop"),
	/** Viaggio fra Associazioni. */
	CITIES("cities"),
	/** Gemme e incastonature. */
	GEMS("gems"),

	// --- la Via dell'Artigiano ----------------------------------------------------------------
	/** La Moneta d'Anima: il Banco comincia a coniare. */
	COIN("coin"),
	/** L'Officina: le anime in esubero, e i macchinari si aprono. */
	WORKSHOP("workshop"),
	/** Il Richiamo d'Anime. */
	LURE("lure"),
	/** Il Crogiolo delle Anime. */
	CRUCIBLE("crucible"),
	/** La Fucina d'Ombra. */
	FORGE("forge"),
	/** Il Pozzo dell'Abisso. */
	WELL("well"),
	/** Arruolare un'anima nell'esercito. */
	ENLIST("enlist"),
	/** I servizi del mercato: varchi su ordinazione. */
	SERVICES("services"),
	/** La catena e' finita. Non apre niente: e' il segno che non manca piu' niente. */
	MASTERY("mastery");

	public static final Codec<Unlock> CODEC = StringRepresentable.fromEnum(Unlock::values);

	private final String name;

	Unlock(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Component label() {
		return Component.translatable("arise.unlock." + name);
	}

	/** Il messaggio da mostrare a chi prova a usare un sistema che non ha ancora. */
	public Component refusal() {
		return Component.translatable("arise.msg.locked", label());
	}

	/**
	 * La riga che dice <em>come si usa</em>, mostrata nell'istante in cui il sistema si apre.
	 *
	 * <p>E' la meta' che mancava. La catena degli incarichi consegnava i sistemi uno alla volta e
	 * annunciava il nome di quello appena arrivato — "Sbloccato: l'esercito d'ombra" — e poi
	 * lasciava il giocatore a indovinare che c'e' un tasto, che va premuto vicino a un cadavere, e
	 * che il cadavere deve essere fresco. Un incarico che concede qualcosa senza dire dove si preme
	 * e' un premio che va cercato su internet.
	 *
	 * <p>Dove serve, il tasto arriva da {@link Component#keybind}: cosi' la riga dice il tasto che
	 * quel giocatore ha davvero, non quello che avevamo in mente noi.
	 */
	public MutableComponent hint() {
		return switch (this) {
			case STATS -> Component.translatable("arise.unlock." + name + ".hint", key("status"));
			case ARMY -> Component.translatable("arise.unlock." + name + ".hint",
					key("extract"), key("army"));
			case ABILITIES -> Component.translatable("arise.unlock." + name + ".hint",
					key("ability_1"), key("ability_4"));
			case GEAR -> Component.translatable("arise.unlock." + name + ".hint", key("gear"));
			case GATES -> Component.translatable("arise.unlock." + name + ".hint", key("map"));
			case SHOP -> Component.translatable("arise.unlock." + name + ".hint", key("shop"));
			default -> Component.translatable("arise.unlock." + name + ".hint");
		};
	}

	private static Component key(String mapping) {
		return Component.keybind("key.arise." + mapping);
	}
}
