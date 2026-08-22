package com.luca.arise.npc;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Le nove botteghe del Quartiere del Mercato, e chi ci sta dietro il bancone.
 *
 * <p>Nove volti diversi e <strong>zero file nuovi</strong>: le texture sono le nove skin
 * predefinite che Minecraft si porta dietro da anni, quelle che compaiono nel selettore quando non
 * si e' ancora scelto un aspetto. Sono gia' nel gioco di chiunque, sono gia' disegnate bene, e
 * hanno esattamente la qualita' che serve qui — <em>gente</em> riconoscibile, non un cast di
 * personaggi da ricordare.
 *
 * <p>Due famiglie, e la differenza e' netta. Un {@link #merchant()} apre la finestra di scambio di
 * Minecraft, quella che tutti sanno gia' usare; un servizio apre una schermata della mod o fa
 * qualcosa. Nessuno fa entrambe le cose: un bancone che a volte vende e a volte apre un pannello
 * sarebbe un bancone che va provato per scoprire cosa fa.
 *
 * @param skin la skin vanilla, in {@code textures/entity/player/wide/}
 */
public enum Shopkeeper implements StringRepresentable {

	// --- chi vende ----------------------------------------------------------------------------
	/** Lingotti, carbone, bacchette di blaze: quello che la Fucina si mangia. */
	SMELTER("smelter", "efe", true),
	/** Ardesia e ossidiana: i muri dei macchinari. */
	QUARRIER("quarrier", "steve", true),
	/** Sabbia delle anime, frammenti d'eco, catalizzatori bassi. */
	SOUL_TRADER("soul_trader", "zuri", true),
	/** I Progetti dei macchinari, e la ferramenta per automatizzarli. */
	DRAFTSMAN("draftsman", "makena", true),
	/** Cibo e materiale da spedizione: quello che serve per non morire di fame in un Gate. */
	HERBALIST("herbalist", "sunny", true),

	// --- chi fa qualcosa ----------------------------------------------------------------------
	/** Il Banco dell'Abisso: conia soul coin in monete, e le riprende indietro. */
	BANKER("banker", "ari", false),
	/** Apre un varco del rango richiesto, a pagamento. */
	GATE_BROKER("gate_broker", "kai", false),
	/** Il banco delle gemme, fuori dall'Associazione. */
	JEWELLER("jeweller", "noor", false),
	/** Viaggio fra le citta' e Abyss Shop. */
	CLERK("clerk", "alex", false);

	public static final Codec<Shopkeeper> CODEC = StringRepresentable.fromEnum(Shopkeeper::values);

	private final String name;
	private final String skin;
	private final boolean merchant;

	Shopkeeper(String name, String skin, boolean merchant) {
		this.name = name;
		this.skin = skin;
		this.merchant = merchant;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public String skin() {
		return skin;
	}

	/** Vero se apre la finestra di scambio invece di fare qualcosa. */
	public boolean merchant() {
		return merchant;
	}

	/** Il nome sopra la testa: e' anche il titolo della finestra di scambio. */
	public Component label() {
		return Component.translatable("arise.npc." + name);
	}

	/** La riga che dice cosa fa, per chi apre un servizio. */
	public Component greeting() {
		return Component.translatable("arise.npc." + name + ".greeting");
	}

	/** L'insegna della bottega, usata dal costruttore del quartiere. */
	public Component shopName() {
		return Component.translatable("arise.npc." + name + ".shop");
	}
}
