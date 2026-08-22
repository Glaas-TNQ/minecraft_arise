package com.luca.arise.city;

/**
 * Un rettangolo sul piano orizzontale, estremi inclusi.
 *
 * <p>Serve a ragionare di lotti, isolati e zone riservate senza passare in giro quattro interi che
 * nessuno ricorda in che ordine vanno. Un lotto, la piazza e l'area del monumento sono la stessa
 * cosa vista da tre punti diversi: un rettangolo che qualcun altro non deve occupare.
 */
public record Rect(int minX, int minZ, int maxX, int maxZ) {

	/** Un quadrato centrato, di lato {@code half * 2 + 1}. */
	public static Rect around(int centreX, int centreZ, int half) {
		return new Rect(centreX - half, centreZ - half, centreX + half, centreZ + half);
	}

	public int width() {
		return maxX - minX + 1;
	}

	public int depth() {
		return maxZ - minZ + 1;
	}

	public int centreX() {
		return (minX + maxX) / 2;
	}

	public int centreZ() {
		return (minZ + maxZ) / 2;
	}

	/** Il lato corto: quello che decide se qui ci sta ancora qualcosa. */
	public int shortSide() {
		return Math.min(width(), depth());
	}

	public Rect shrink(int amount) {
		return new Rect(minX + amount, minZ + amount, maxX - amount, maxZ - amount);
	}

	public Rect grow(int amount) {
		return shrink(-amount);
	}

	public boolean overlaps(Rect other) {
		return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
	}
}
