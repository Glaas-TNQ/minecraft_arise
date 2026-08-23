package com.luca.arise.gate;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * Gli affissi dei nemici: quello che rende una stanza diversa dalla stanza prima.
 *
 * <p>Fino a ieri l'unico modo di alzare la difficolta' di un Gate era alzare i numeri, ed e'
 * esattamente il modo che rende un gioco frustrante invece che difficile. Un affisso alza la
 * difficolta' <strong>aggiungendo una regola</strong>: il mob fa la stessa quantita' di male, ma
 * chiede di comportarsi in un modo che prima non serviva.
 *
 * <p>Due regole dure, e vengono dall'errore piu' documentato del genere — gli affissi elite di
 * Diablo III, dove la lamentela unanime riguarda quelli che <em>tolgono il controllo</em>:
 *
 * <ol>
 *   <li><strong>mai due affissi sullo stesso mob</strong>;
 *   <li><strong>mai piu' di un mob con affisso per stanza</strong>.
 * </ol>
 *
 * <p>E una terza, sul contenuto: nessuno dei sei nega l'input al giocatore. Ognuno chiede un
 * <em>cambio di comportamento</em> — non scappare in linea retta, non farsi colpire, cambiare
 * squadra — e per ognuno la contromisura e' scritta nel tooltip che il giocatore legge sopra la
 * testa del mob.
 *
 * <p>Il piu' prezioso dei sei e' <strong>Divoratore d'Ombre</strong>: e' l'unico che rende la
 * composizione della squadra una decisione tattica invece della lista dei quattro piu' forti.
 */
public enum MobAffix implements StringRepresentable {

	/** Va la meta' piu' veloce. Non scappare in linea retta. */
	SWIFT("swift", 0xFFE8A34F),

	/** Si cura di una quota del danno che infligge. Non farti colpire. */
	THIRSTY("thirsty", 0xFFE86A6A),

	/** Rimanda una quota del danno incassato. Colpi grossi e lenti, o mandaci le ombre. */
	THORNED("thorned", 0xFF9BA8B8),

	/** Fa doppio danno alle ombre. Cambia squadra, o combatti tu. */
	SHADOW_EATER("shadow_eater", 0xFF8E6BFF),

	/** Chi lo colpisce in mischia si rallenta. Colpiscilo da lontano, o con un Mago. */
	FROSTBOUND("frostbound", 0xFF8FD8F0),

	/** Alla morte lascia un'area di danno, e la annuncia. Allontanati quando cade. */
	VOLATILE("volatile", 0xFFFFD54F);

	public static final Codec<MobAffix> CODEC = StringRepresentable.fromEnum(MobAffix::values);

	/** Quanto piu' veloce va un Rapido. */
	public static final double SWIFT_SPEED = 1.5;

	/** Quale quota del danno inferto si riprende un Assetato. */
	public static final float THIRSTY_SHARE = 0.35F;

	/** Quale quota del danno incassato rimanda un Riflesso. */
	public static final float THORNS_SHARE = 0.20F;

	/** Quante volte tanto fa male un Divoratore d'Ombre a un'ombra. */
	public static final float SHADOW_EATER_FACTOR = 2.0F;

	/** Quanto dura il gelo di un Gelido, in tick, e quanto e' forte. */
	public static final int FROST_TICKS = 60;
	public static final int FROST_LEVEL = 1;

	/**
	 * Quanto passa fra la morte di un Volatile e lo scoppio, in tick.
	 *
	 * <p>Venticinque: poco piu' di un secondo. E' il preavviso standard della mod per un danno ad
	 * area, ed e' abbastanza per uscire dal raggio camminando — che e' il punto. Un'esplosione
	 * istantanea alla morte non sarebbe un affisso, sarebbe una tassa su chi ha ucciso.
	 */
	public static final int VOLATILE_FUSE = 25;

	/** Raggio e danno dello scoppio di un Volatile. */
	public static final double VOLATILE_RADIUS = 3.5;
	public static final float VOLATILE_DAMAGE = 8.0F;

	/**
	 * Da quale rango in su i mob possono portare un affisso.
	 *
	 * <p>Dal C. Sotto, il giocatore sta ancora imparando cosa fa un varco, e un nemico che si cura
	 * colpendo mentre non hai ancora capito come funziona l'esercito non insegna niente: confonde.
	 */
	public static final int MIN_RANK_ORDINAL = 2;

	private final String name;
	private final int color;

	MobAffix(String name, int color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.affix." + name);
	}

	/** Cosa fa, in una riga. Sta sopra la testa del mob, insieme al nome. */
	public Component description() {
		return Component.translatable("arise.affix." + name + ".desc");
	}

	/** Il nome del mob affisso: "Rapido Zombie". */
	public Component nameFor(Component creature) {
		return Component.translatable("arise.affix.name", label(), creature)
				.withStyle(style -> style.withColor(color));
	}

	public static MobAffix random(RandomSource random) {
		MobAffix[] values = values();
		return values[random.nextInt(values.length)];
	}
}
