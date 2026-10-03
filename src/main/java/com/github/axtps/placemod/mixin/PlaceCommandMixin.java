package com.github.axtps.placemod.mixin;

import com.github.axtps.placemod.PlaceMod;
import net.minecraft.core.SectionPos;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 让 /place structure 生成的结构"跟自然生成一样"。
 *
 * 原版 PlaceCommand.placeStructure 只调了 StructureStart.placeInChunk 摆放方块，
 * 从不把 StructureStart 注册进 StructureManager / 区块 StructureAccess。
 * 结果：结构不进 StructureManager、不写区块 NBT 引用 → 重启丢失、专属刷怪规则不激活。
 *
 * 本 Mixin 在 placeStructure 返回前，补做自然生成的注册步骤：
 *   1. 对结构所在区块 setStartForStructure，把 start 存进区块；
 *   2. 调 ChunkGenerator.createReferences 写周围区块引用（跟自然生成同款逻辑）；
 *   3. markUnsaved 标记涉及的区块，强制存盘。
 */
@Mixin(PlaceCommand.class)
public class PlaceCommandMixin {

	@Inject(method = "placeStructure", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
	private static void placemod$registerStructure(net.minecraft.commands.CommandSourceStack source,
												   net.minecraft.core.Holder.Reference<Structure> structureHolder,
												   net.minecraft.core.BlockPos pos,
												   CallbackInfo ci,
												   ServerLevel level,
												   Structure structure,
												   ChunkGenerator generator,
												   StructureStart start) {
		if (start == null || !start.isValid()) {
			return;
		}

		try {
			StructureManager structureManager = level.structureManager();
			BoundingBox box = start.getBoundingBox();

			// 结构包围盒覆盖的区块范围
			ChunkPos minChunk = new ChunkPos(
					SectionPos.blockToSectionCoord(box.minX()),
					SectionPos.blockToSectionCoord(box.minZ()));
			ChunkPos maxChunk = new ChunkPos(
					SectionPos.blockToSectionCoord(box.maxX()),
					SectionPos.blockToSectionCoord(box.maxZ()));

			// 1) 把 StructureStart 存进它自己所在的区块
			//    （自然生成在 ChunkGenerator.createStructures 里做）
			ChunkPos startChunk = start.getChunkPos();
			ChunkAccess startChunkAccess = level.getChunk(startChunk.x(), startChunk.z());
			structureManager.setStartForStructure(
					SectionPos.bottomOf(startChunkAccess), structure, start, startChunkAccess);
			startChunkAccess.markUnsaved();

			// 2) 引用写入：对涉及的每个区块调 createReferences，
			//    它内部会扫 17x17 邻居，把相交的 StructureStart 引用写进区块。
			//    跟自然生成用的是同一个方法，保证"完全一样"。
			ChunkPos.rangeClosed(minChunk, maxChunk).forEach(cp -> {
				ChunkAccess chunk = level.getChunk(cp.x(), cp.z());
				try {
					generator.createReferences(level, structureManager, chunk);
					chunk.markUnsaved();
				} catch (Exception e) {
					PlaceMod.LOGGER.error("[place-mod] createReferences 失败 @ {}: {}", cp, e.toString());
				}
			});

			PlaceMod.LOGGER.info("[place-mod] 已为结构 {} 补注册 StructureStart @ {}",
					structure, startChunk);
		} catch (Throwable t) {
			// 注册失败不能影响命令本身的成功返回
			PlaceMod.LOGGER.error("[place-mod] 补注册结构时出错（已忽略）: ", t);
		}
	}
}