package com.luca.arise.progress;

import java.util.List;
import java.util.stream.Stream;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Le statistiche del Sistema, e l'attributo vanilla su cui ciascuna agisce.
 *
 * <p>Qui stanno solo i <em>collegamenti</em> (quale attributo, quale operazione): quanto vale un
 * punto e qual e' il tetto sono numeri di bilanciamento e vivono nella config.
 *
 * <p>Le prime quattro sono <strong>spendibili</strong>: sono le sole su cui si mettono i punti
 * guadagnati salendo di livello, e le sole che compaiono nella schermata di stato. Le altre
 * esistono perche' l'equipaggiamento possa estrarle (design §8.2): senza quella varieta' due pezzi
 * dello stesso slot sarebbero indistinguibili. Nessuna differenza nel modo in cui diventano
 * attributi — la somma delle sorgenti passa per lo stesso imbuto.
 */
public enum Stat implements StringRepresentable {
	/** Vita massima. */
	VITALITY("vitality", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, true),
	/** Velocita' di movimento, in percentuale sulla base (l'operazione additiva secca qui e' inusabile). */
	AGILITY("agility", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
	/** Danno da mischia. */
	STRENGTH("strength", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, true),
	/** Armatura. */
	ENDURANCE("endurance", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, true),

	/** Tempra: riduce quanto l'armatura viene bucata dai colpi forti. */
	TOUGHNESS("toughness", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, false),
	/** Prontezza: velocita' d'attacco, in percentuale. */
	HASTE("haste", Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
	/** Saldezza: resistenza al contraccolpo. L'attributo vanilla arriva a 1, quindi passi minuscoli. */
	RESOLVE("resolve", Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, false),
	/** Slancio: altezza del salto, in percentuale. */
	LEAP("leap", Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
	/** Allungo: distanza a cui si colpisce. */
	REACH("reach", Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, false),
	/** Egida: cuori gialli di assorbimento. */
	ABSORPTION("absorption", Attributes.MAX_ABSORPTION, AttributeModifier.Operation.ADD_VALUE, false),
	/** Fortuna: pesa sulle tabelle di bottino di vanilla. */
	FORTUNE("fortune", Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, false),
	/** Impeto: quanto lontano vola chi incassa. */
	IMPACT("impact", Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, false);

	public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

	/**
	 * Le statistiche su cui si spendono punti, nell'ordine in cui vanno mostrate.
	 *
	 * <p>Calcolata una volta: la schermata di stato la percorre a ogni frame.
	 */
	public static final List<Stat> SPENDABLE = Stream.of(values()).filter(Stat::spendable).toList();

	private final String name;
	private final Holder<Attribute> attribute;
	private final AttributeModifier.Operation operation;
	private final boolean spendable;
	private final Identifier modifierId;
	private final String translationKey;

	Stat(String name, Holder<Attribute> attribute, AttributeModifier.Operation operation, boolean spendable) {
		this.name = name;
		this.attribute = attribute;
		this.operation = operation;
		this.spendable = spendable;
		// Id fisso: e' cio' che permette di rimpiazzare il modificatore invece di accumularlo.
		this.modifierId = AriseMod.id("stat/" + name);
		this.translationKey = "arise.stat." + name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Holder<Attribute> attribute() {
		return attribute;
	}

	public AttributeModifier.Operation operation() {
		return operation;
	}

	/** Vero se ci si possono mettere i punti guadagnati salendo di livello. */
	public boolean spendable() {
		return spendable;
	}

	/** Vero se il valore va letto come percentuale: e' cio' che decide come lo si scrive. */
	public boolean percentage() {
		return operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
	}

	public Identifier modifierId() {
		return modifierId;
	}

	public String translationKey() {
		return translationKey;
	}

	/**
	 * Che cosa fa questa statistica, detto nei termini del gioco.
	 *
	 * <p>"Forza" non e' un'informazione: e' un'etichetta. Quello che il giocatore vuole sapere
	 * davanti alla schermata di stato e' che la forza diventa <em>danno da mischia</em> — e senza
	 * questa riga l'unico modo di scoprirlo era spendere un punto e andare a colpire qualcosa
	 * contando i cuori.
	 */
	public Component effect() {
		return Component.translatable(translationKey + ".effect");
	}

	/** Cosa cambia davvero, in partita: la riga che sta nel riquadro che compare passandoci sopra. */
	public Component description() {
		return Component.translatable(translationKey + ".desc");
	}

	/**
	 * Il valore come lo legge un giocatore: "+12,5%" oppure "+3,4".
	 *
	 * <p>I decimali si tolgono guardando il numero, non tagliando la stringa: su una macchina
	 * italiana {@code String.format} scrive la virgola, e un taglio di ".00" non troverebbe mai
	 * niente da tagliare.
	 */
	public String format(double amount) {
		if (percentage()) {
			return String.format("%+.1f%%", amount * 100.0);
		}

		return amount == Math.rint(amount)
				? String.format("%+.0f", amount)
				: String.format("%+.2f", amount);
	}
}
