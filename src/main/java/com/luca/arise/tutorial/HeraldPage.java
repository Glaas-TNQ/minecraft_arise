package com.luca.arise.tutorial;

import net.minecraft.network.chat.Component;

/**
 * Cosa dice l'Araldo, e in che ordine.
 *
 * <p>Sei pagine, una per clic. Non e' una scelta di comodita': un muro di testo che arriva tutto
 * insieme si salta, e chi lo salta resta senza sapere che esiste un tasto per gli incarichi. Sei
 * clic sono sei momenti in cui chi legge decide di andare avanti, ed e' abbastanza poco da non
 * annoiare chi la mod la conosce gia'.
 *
 * <p><strong>I tasti si scrivono da soli.</strong> Le due pagine che parlano di comandi non
 * nominano nessuna lettera: passano {@link Component#keybind}, e il client sostituisce il tasto che
 * quel giocatore ha davvero configurato. Scrivere "premi K" e' giusto finche' qualcuno non
 * rimappa, e poi diventa una bugia che nessuno viene a correggere.
 */
public enum HeraldPage {

	/** Dove sei, chi sono, cos'e' successo. */
	GREETING("greeting"),
	/** Cos'e' il Sistema, e il riquadro in alto a sinistra. */
	SYSTEM("system"),
	/** La catena: un incarico per volta, e ognuno apre qualcosa. */
	QUESTS("quests"),
	/** I tasti che servono da subito. */
	KEYS("keys"),
	/** Cosa arriva dopo: l'esercito, e perche' vale la pena arrivarci. */
	ARMY("army"),
	/** L'ultima, quella che riapre la porta. */
	FAREWELL("farewell");

	private final String name;

	HeraldPage(String name) {
		this.name = name;
	}

	public String getSerializedName() {
		return name;
	}

	/**
	 * La battuta.
	 *
	 * <p>Le pagine che parlano di comandi ricevono i tasti come argomenti. Se un giorno una pagina
	 * nuova ne volesse altri, il posto e' questo: la chiave e gli argomenti stanno insieme, e non
	 * c'e' modo di aggiungerne uno alla stringa senza vedere che qui non arriva.
	 */
	public Component line() {
		return switch (this) {
			case KEYS -> Component.translatable("arise.herald." + name,
					key("status"), key("quests"), key("map"));
			case ARMY -> Component.translatable("arise.herald." + name,
					key("extract"), key("summon"), key("army"));
			default -> Component.translatable("arise.herald." + name);
		};
	}

	private static Component key(String mapping) {
		return Component.keybind("key.arise." + mapping);
	}

	public static int count() {
		return values().length;
	}
}
