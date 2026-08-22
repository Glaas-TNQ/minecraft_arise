package com.luca.arise.city;

/**
 * Come è disegnata una città: la maglia stradale, il profilo delle altezze, la forma degli isolati.
 *
 * <p>La tavolozza da sola non basta. Cinque città con la stessa griglia, gli stessi lotti e gli
 * stessi parallelepipedi restano la stessa città ridipinta cinque volte: si visita la prima e le
 * altre quattro sono un viaggio sprecato. Qui cambia la <em>pianta</em> — quanto è grande un
 * isolato, quanto è larga una strada, se le altezze crescono verso il centro o restano allineate,
 * e soprattutto che cosa si costruisce dentro un lotto.
 *
 * <p>Le misure sono percentuali della griglia di base che sta in {@code CityConfig}: chi vuole
 * città più fitte o più larghe cambia un numero solo nella config e tutte e cinque si adeguano
 * mantenendo le proporzioni fra loro.
 */
public enum CityStyle {

	/**
	 * Manhattan: isolati regolari, avenue più larghe ogni tre incroci, torri che rientrano a
	 * scalini e crescono verso il centro. È l'unica città con un vero skyline.
	 */
	GRIDIRON(100, 117, 3, 4, 24, true, 8, false, false),

	/**
	 * Shitamachi: isolati piccolissimi, vicoli invece di strade, ogni lotto spezzato in più
	 * palazzine addossate di altezza diversa. Fitta, bassa, disordinata al punto giusto.
	 */
	SHITAMACHI(60, 66, 4, 2, 10, true, 6, false, false),

	/**
	 * Antica: isolati grandi e bassi, cortili interni, tetti di coccio, e un anello di
	 * circonvallazione attorno al centro. Nessun edificio supera il quarto piano: la cupola del
	 * monumento deve restare la cosa più alta che si vede.
	 */
	ANTICA(125, 100, 0, 2, 4, false, 14, true, false),

	/**
	 * Manzana: l'isolato spagnolo chiuso — costruito su tutti e quattro i lati, patio nel mezzo,
	 * cornicione alla stessa quota per tutti. Da sopra è un nido d'ape.
	 */
	MANZANA(106, 100, 4, 4, 6, false, 10, false, false),

	/**
	 * Boulevard: strade larghe, isolati profondi con cortile, mansarde, e un viale monumentale che
	 * taglia la città da parte a parte fino al monumento.
	 */
	BOULEVARD(125, 133, 3, 4, 8, false, 14, false, true);

	private final int blockScale;
	private final int roadScale;
	private final int avenueEvery;
	private final int minFloors;
	private final int maxFloors;
	private final boolean taper;
	private final int parkChance;
	private final boolean ringRoad;
	private final boolean boulevard;

	CityStyle(int blockScale, int roadScale, int avenueEvery, int minFloors, int maxFloors,
			boolean taper, int parkChance, boolean ringRoad, boolean boulevard) {
		this.blockScale = blockScale;
		this.roadScale = roadScale;
		this.avenueEvery = avenueEvery;
		this.minFloors = minFloors;
		this.maxFloors = maxFloors;
		this.taper = taper;
		this.parkChance = parkChance;
		this.ringRoad = ringRoad;
		this.boulevard = boulevard;
	}

	/** Il passo della griglia: isolato più strada. */
	public int blockSize(int base) {
		return Math.max(12, base * blockScale / 100);
	}

	public int roadWidth(int base) {
		return Math.max(3, base * roadScale / 100);
	}

	/** Ogni quante strade ne passa una larga il doppio. Zero: nessuna. */
	public int avenueEvery() {
		return avenueEvery;
	}

	public int minFloors() {
		return minFloors;
	}

	public int maxFloors() {
		return maxFloors;
	}

	/** Se le altezze crescono verso il centro invece di restare tutte uguali. */
	public boolean taper() {
		return taper;
	}

	/** Probabilità in percentuale che un isolato sia un giardino invece che un edificio. */
	public int parkChance() {
		return parkChance;
	}

	/** Se attorno al centro corre una circonvallazione. */
	public boolean ringRoad() {
		return ringRoad;
	}

	/** Se una via monumentale attraversa la città da est a ovest. */
	public boolean boulevard() {
		return boulevard;
	}
}
