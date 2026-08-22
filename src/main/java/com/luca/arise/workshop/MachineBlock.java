package com.luca.arise.workshop;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Il corpo di un macchinario. Uno solo, per tutti e quattro.
 *
 * <p>La differenza fra il Crogiolo e il Pozzo non e' nel blocco: e' nel {@link MachineKind} che
 * il blocco si porta dietro e che la sua {@link MachineBlockEntity} legge. Quattro classi di
 * blocco identiche tranne una costante sarebbero state quattro posti dove ricordarsi di
 * aggiungere lo stesso {@code useWithoutItem}.
 *
 * <p><strong>{@code getRenderShape} va riscritto.</strong> {@link BaseEntityBlock} risponde
 * {@code INVISIBLE}, perche' presume che a disegnare ci pensi un renderer della block entity —
 * vale per i forzieri e gli stendardi, non per noi. Senza questa riga i quattro macchinari
 * esistono, si aprono e funzionano, ma nel mondo non si vede niente.
 */
public class MachineBlock extends BaseEntityBlock {

	public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			MachineKind.CODEC.fieldOf("kind").forGetter(MachineBlock::kind),
			propertiesCodec()
	).apply(instance, MachineBlock::new));

	private final MachineKind kind;

	public MachineBlock(MachineKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public MachineKind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MachineBlockEntity(pos, state);
	}

	/**
	 * Il battito del macchinario, solo lato server.
	 *
	 * <p>{@code createTickerHelper} serve a una cosa sola ma indispensabile: garantire che il tipo
	 * di block entity trovato nel mondo sia davvero il nostro prima di passarglielo. Senza,
	 * un blocco sostituito da un'altra mod farebbe esplodere il tick del chunk.
	 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}

		return createTickerHelper(type, ModBlocks.MACHINE, MachineBlockEntity::serverTick);
	}

	/**
	 * Click destro: si apre il macchinario.
	 *
	 * <p>Il menu nasce sul server e basta. Il client non decide niente qui dentro — riceve la
	 * finestra gia' fatta, con dentro il {@link MachineKind} che gli serve per disegnarla.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer server)) {
			return InteractionResult.CONSUME;
		}

		if (!AriseConfig.get().workshop().enabled()) {
			server.sendSystemMessage(Component.translatable("arise.msg.workshop.disabled"));
			return InteractionResult.CONSUME;
		}

		// L'Officina si apre con la Via dell'Artigiano. Il Progetto e' gia' un lucchetto sulla
		// costruzione, ma un macchinario piazzato in creativa o trovato in un laboratorio non deve
		// scavalcare la catena: il permesso si chiede qui, dove si apre.
		Component locked = QuestManager.require(server, Unlock.WORKSHOP);
		if (locked != null) {
			server.sendSystemMessage(locked);
			return InteractionResult.CONSUME;
		}

		if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			server.openMenu(machine);
		}

		return InteractionResult.CONSUME;
	}

	/**
	 * Chi ha piazzato la macchina e' chi ne incassa i soul coin.
	 *
	 * <p>Il Pozzo dell'Abisso paga un giocatore, non "il mondo": senza un proprietario scritto nel
	 * blocco, non ci sarebbe nessuno a cui accreditare la resa quando chi l'ha costruito e' a
	 * mille blocchi di distanza.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (placer instanceof Player player
				&& level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			machine.setOwner(player.getUUID());
		}
	}

	/**
	 * Rompendo il macchinario le anime tornano per terra.
	 *
	 * <p>Non e' una cortesia: le anime installate sono operaie, non ingredienti, e perderle
	 * rompendo un blocco sarebbe la cosa peggiore che questo sistema possa fare a un giocatore.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean moving) {
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			Containers.dropContents(level, pos, machine);
		}

		super.affectNeighborsAfterRemoval(state, level, pos, moving);
	}
}
