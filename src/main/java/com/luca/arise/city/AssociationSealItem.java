package com.luca.arise.city;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.event.CityEvents;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Il Sigillo dell'Associazione: la rete di viaggio, in tasca.
 *
 * <p>Il viaggio fra le cinque Associazioni esisteva gia' — lo Sportello, dentro ogni citta' — e
 * aveva un difetto che si vede solo giocando: <strong>per usarlo bisogna essere in una citta'</strong>,
 * e le citta' stanno a duecentomila blocchi dallo spawn, tutte in fila a quindicimila blocchi
 * l'una dall'altra. Arrivare la prima volta e' una traversata; tornarci dopo esserne usciti lo e'
 * di nuovo. La rete c'era, ma il primo nodo non si raggiungeva.
 *
 * <p>Il Sigillo apre lo stesso pannello dello Sportello, da qualunque punto del mondo, per sempre.
 * Il prezzo e' l'attesa: cinque minuti fra un viaggio e l'altro, contati dal gioco stesso con il
 * tempo di ricarica degli oggetti — quello che disegna la spazzola grigia sulla casella, cosi' non
 * c'e' nessun numero da ricordare o schermata da aprire per sapere quando sara' pronto.
 *
 * <p>Arriva con l'incarico che concede il viaggio, e non prima: e' l'incarico stesso a dire che da
 * quel momento le Associazioni sono collegate fra loro.
 */
public final class AssociationSealItem extends Item {

	public AssociationSealItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		Component locked = QuestManager.require(serverPlayer, Unlock.CITIES);
		if (locked != null) {
			serverPlayer.sendSystemMessage(locked);
			return InteractionResult.CONSUME;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		// La ricarica parte all'apertura del pannello e non alla partenza, e la differenza e'
		// voluta: chiudere l'elenco senza scegliere niente e' un'informazione che si e' comunque
		// chiesta al Sistema. Consultare la rete a costo zero, una volta al secondo, sarebbe una
		// bussola gratuita per cinque punti fissi del mondo.
		player.getCooldowns().addCooldown(stack, AriseConfig.get().cities().sealCooldownTicks());
		CityEvents.openHub(serverPlayer);

		return InteractionResult.SUCCESS_SERVER;
	}
}
