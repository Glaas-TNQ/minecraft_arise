package com.luca.arise.gate;

import com.luca.arise.config.AriseConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * La Bussola dell'Abisso: non punta a un nord fisso come una bussola di lodestone, punta al varco
 * aperto piu' vicino nella dimensione di chi la usa.
 *
 * <p>Non serve tracciare niente di nuovo: {@link GateRegistry} gia' sa dove sono i varchi, perche'
 * e' la stessa fonte che disegna la mappa (M2). Qui si legge solo la voce piu' vicina e si dice a
 * voce, in gioco, senza aprire schermate — un attrezzo pensato per chi e' gia' in cammino.
 */
public final class AbyssCompassItem extends Item {

	public AbyssCompassItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		player.sendSystemMessage(locate(serverPlayer));
		player.getCooldowns().addCooldown(stack, AriseConfig.get().gates().compassCooldownTicks());

		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Cosa direbbe la bussola a questo giocatore adesso. Usato anche dal comando di debug.
	 *
	 * <p>Distanza e direzione, non le coordinate — stessa scelta di {@code GateSpawner.announce},
	 * di cui questo riusa anche il calcolo dell'ottavo: due formule uguali scritte due volte
	 * sarebbero due modi di sbagliarla in futuro.
	 */
	public static Component locate(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.position();

		GateRecord nearest = null;
		double nearestDistSqr = Double.MAX_VALUE;

		for (GateRecord record : GateRegistry.reconciled(level.getServer())) {
			if (!record.dimension().equals(level.dimension())) {
				continue;
			}

			double distSqr = Vec3.atCenterOf(record.pos()).distanceToSqr(from);
			if (distSqr < nearestDistSqr) {
				nearestDistSqr = distSqr;
				nearest = record;
			}
		}

		if (nearest == null) {
			return Component.translatable("arise.msg.compass.none");
		}

		int distance = (int) Math.round(Math.sqrt(nearestDistSqr));
		Component direction = GateSpawner.direction(from, Vec3.atCenterOf(nearest.pos()));

		return Component.translatable("arise.msg.compass.found", nearest.rank().label(), distance, direction)
				.withStyle(nearest.rank().chatColor());
	}
}
