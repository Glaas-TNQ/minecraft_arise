package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.luca.arise.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Un contenitore vero, appoggiato all'attachment del giocatore.
 *
 * <p>E' il pezzo che permette al menu del Cacciatore di comportarsi come una cassa: trascinamento,
 * click con lo shift, doppio click che raccoglie, tutto quanto arriva gratis perche' vanilla parla
 * con un {@link Container} e questo lo e'.
 *
 * <p>Non tiene una copia di lavoro. Ogni lettura passa dall'attachment e ogni scrittura lo
 * riscrive: due copie dello stesso inventario sono due copie che prima o poi vanno in disaccordo,
 * e qui il disaccordo si tradurrebbe in pezzi che riappaiono dopo un riavvio. Il costo e' rifare
 * una lista di ventisette elementi a ogni click, che e' niente.
 *
 * <p>{@link #setChanged()} riscrive con delle <em>copie</em> degli oggetti. Serve: il codice dei
 * menu modifica gli {@code ItemStack} sul posto e poi avvisa, quindi senza un'istanza nuova la
 * sincronizzazione non avrebbe modo di accorgersi che qualcosa e' cambiato.
 */
public final class GearInventory implements Container {

	private final ServerPlayer player;
	private final Function<PlayerGear, List<ItemStack>> read;
	private final java.util.function.BiFunction<PlayerGear, List<ItemStack>, PlayerGear> write;

	private GearInventory(ServerPlayer player, Function<PlayerGear, List<ItemStack>> read,
			java.util.function.BiFunction<PlayerGear, List<ItemStack>, PlayerGear> write) {
		this.player = player;
		this.read = read;
		this.write = write;
	}

	/** Le caselle degli accessori: guanti, anelli, tutto cio' che vanilla non sa indossare. */
	public static GearInventory hunter(ServerPlayer player) {
		return new GearInventory(player, PlayerGear::hunter, PlayerGear::withHunter);
	}

	/** Lo spazio dimensionale. */
	public static GearInventory dimensional(ServerPlayer player) {
		return new GearInventory(player, PlayerGear::dimensional, PlayerGear::withDimensional);
	}

	private PlayerGear gear() {
		return player.getAttachedOrCreate(ModAttachments.GEAR);
	}

	private List<ItemStack> items() {
		return read.apply(gear());
	}

	private void store(List<ItemStack> items) {
		player.setAttached(ModAttachments.GEAR, write.apply(gear(), items));
	}

	// ---------------------------------------------------------------- Container

	@Override
	public int getContainerSize() {
		return items().size();
	}

	@Override
	public boolean isEmpty() {
		return items().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int index) {
		List<ItemStack> items = items();
		return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		List<ItemStack> items = new ArrayList<>(items());
		ItemStack removed = ContainerHelper.removeItem(items, index, count);

		if (!removed.isEmpty()) {
			store(items);
		}

		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		List<ItemStack> items = new ArrayList<>(items());
		ItemStack removed = ContainerHelper.takeItem(items, index);

		if (!removed.isEmpty()) {
			store(items);
		}

		return removed;
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		List<ItemStack> items = new ArrayList<>(items());

		if (index < 0 || index >= items.size()) {
			return;
		}

		items.set(index, stack);
		store(items);
	}

	@Override
	public void setChanged() {
		List<ItemStack> copies = new ArrayList<>(items().size());

		for (ItemStack stack : items()) {
			copies.add(stack.copy());
		}

		store(copies);
	}

	@Override
	public boolean stillValid(Player who) {
		return who == player;
	}

	@Override
	public void clearContent() {
		store(List.of());
	}

	/** Una casella per volta: gli oggetti del Sistema non si impilano mai. */
	@Override
	public int getMaxStackSize() {
		return 1;
	}
}
