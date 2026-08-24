package com.luca.arise.gate;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La Chiave del Varco: un Gate di rango E, quando vuoi tu.
 *
 * <p>Nasce da un difetto della catena degli incarichi che si vede solo giocandola. Il settimo
 * incarico dice «chiudi un varco» — e i varchi <strong>si aprono da soli</strong>, vicino a chi
 * gioca, ogni tanto. Ogni tanto non e' un compito: e' un'attesa. Un Cacciatore che ha appena
 * ricevuto i Gate e vuole vederne uno puo' solo camminare finche' il caso non decide, e nel
 * frattempo la catena e' ferma su un incarico che non dipende da lui.
 *
 * <p>La Chiave lo rende una cosa che si fa. Si tiene in mano, si preme il tasto destro, e il varco
 * si apre li' vicino — sempre di rango E, che e' il piu' basso, perche' il suo scopo e' insegnare
 * cos'e' un Gate e non regalare bottino.
 *
 * <h2>Perche' non si consuma, e perche' poi sparisce</h2>
 *
 * <p>Non si consuma all'uso di proposito: il primo varco di un Cacciatore e' anche il primo in cui
 * puo' morire, e una chiave a uso singolo trasformerebbe un tentativo andato male in un vicolo
 * cieco — esattamente quello che era prima, ma peggio, perche' adesso la colpa sarebbe sua.
 *
 * <p>Sparisce invece nel momento in cui il primo varco e' chiuso, e la toglie la catena stessa
 * (vedi {@code Quest#revokes}). E' la sola cosa che impedisce che un oggetto nato per insegnare
 * diventi un rubinetto di Gate di rango E: da li' in poi i varchi si trovano nel mondo, si comprano
 * dal Sensale o si aprono col Cubo dell'Abisso, che sono tre cose che costano.
 */
public final class GateKeyItem extends Item {

	/** Il rango del varco che apre. Sempre lo stesso: e' una chiave, non un'offerta. */
	private static final Rank RANK = Rank.E;

	public GateKeyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		Component locked = QuestManager.require(serverPlayer, Unlock.GATES);
		if (locked != null) {
			serverPlayer.sendSystemMessage(locked);
			return InteractionResult.CONSUME;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		// Dentro un Gate non se ne apre un altro: due istanze annidate non hanno nessun modo di
		// venire fuori nell'ordine giusto, e la via d'uscita di una sovrascriverebbe l'altra.
		if (GateManager.isInGate(serverPlayer)) {
			serverPlayer.sendSystemMessage(Component.translatable("arise.msg.gate.key_in_gate")
					.withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}

		Component result = GateSpawner.spawnRank(serverPlayer, RANK);

		if (result == null) {
			serverPlayer.sendSystemMessage(Component.translatable("arise.msg.gate.key_no_room")
					.withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}

		// La ricarica parte solo se il varco si e' davvero aperto: un tentativo fallito perche'
		// non c'era posto non deve costare l'attesa.
		player.getCooldowns().addCooldown(stack, AriseConfig.get().gates().spawn().keyCooldownTicks());
		serverPlayer.sendSystemMessage(result);

		return InteractionResult.SUCCESS_SERVER;
	}
}
