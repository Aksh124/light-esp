package lightesp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What's shown and how. Players and mobs use the game's own glowing outline; storage blocks get
 * boxes drawn on top of everything with the game's debug drawing.
 */
public final class Esp {
	private static final Logger LOGGER = LoggerFactory.getLogger("lightesp");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lightesp.properties");

	/** Outline colours, RGB. */
	public static final int PLAYER = 0xFF5555;
	public static final int HOSTILE = 0xFFAA00;
	public static final int PASSIVE = 0x55FF55;
	public static final int PEARL = 0x33E0FF;
	/** Portal box colour, ARGB. */
	public static final int PORTAL = 0xFFB050FF;

	/** The most boxes drawn at once, nearest first. */
	static final int MAX_BOXES = 1500;
	private static final int RESCAN_TICKS = 10;

	public static boolean players;
	public static boolean mobs;
	public static boolean blocks;
	public static boolean pearls;
	public static boolean portals;

	public record Box(AABB box, int color) {}

	private static List<Box> boxes = List.of();
	private static int ticksSinceScan = RESCAN_TICKS;

	private Esp() {}

	/** The outline colour for an entity, or 0 for none. */
	public static int outlineColor(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player || entity == mc.getCameraEntity()) {
			return 0;
		}
		if (entity instanceof Player) {
			return players ? PLAYER : 0;
		}
		if (entity instanceof Mob) {
			return mobs ? (entity instanceof Enemy ? HOSTILE : PASSIVE) : 0;
		}
		if (entity instanceof ThrownEnderpearl) {
			return pearls ? PEARL : 0;
		}
		return 0;
	}

	/** The box colour for a block entity, ARGB, or 0 if it isn't storage worth showing. */
	public static int blockColor(BlockEntity blockEntity) {
		if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return 0xFFFF55FF;
		}
		if (blockEntity instanceof EnderChestBlockEntity) {
			return 0xFF9B5CFF;
		}
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return 0xFFFF5533;
		}
		if (blockEntity instanceof ChestBlockEntity) {
			return 0xFFFFC233;
		}
		if (blockEntity instanceof BarrelBlockEntity) {
			return 0xFFC08040;
		}
		if (blockEntity instanceof SpawnerBlockEntity) {
			return 0xFFFF2222;
		}
		if (blockEntity instanceof HopperBlockEntity || blockEntity instanceof DispenserBlockEntity
			|| blockEntity instanceof CrafterBlockEntity || blockEntity instanceof AbstractFurnaceBlockEntity) {
			return 0xFFB0B0B0;
		}
		return 0;
	}

	public static List<Box> boxes() {
		return boxes;
	}

	static void tick(Minecraft mc) {
		if ((!blocks && !portals) || mc.level == null || mc.player == null) {
			boxes = List.of();
			ticksSinceScan = RESCAN_TICKS;
			return;
		}
		if (++ticksSinceScan >= RESCAN_TICKS) {
			ticksSinceScan = 0;
			int radius = mc.options.getEffectiveRenderDistance();
			List<Box> found = new ArrayList<>();
			if (blocks) {
				found.addAll(scan(mc.level, mc.player.position(), radius));
			}
			if (portals) {
				found.addAll(scanPortals(mc.level, mc.player.position(), radius));
			}
			Vec3 here = mc.player.position();
			found.sort(Comparator.comparingDouble(box -> box.box().getCenter().distanceToSqr(here)));
			boxes = found.size() > MAX_BOXES ? List.copyOf(found.subList(0, MAX_BOXES)) : List.copyOf(found);
		}
		draw(mc);
	}

	/** Storage blocks in the loaded chunks around the player, nearest first. */
	private static List<Box> scan(ClientLevel level, Vec3 around, int radius) {
		int centerX = BlockPos.containing(around).getX() >> 4;
		int centerZ = BlockPos.containing(around).getZ() >> 4;
		List<Box> found = new ArrayList<>();
		for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
			for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					int color = blockColor(blockEntity);
					if (color != 0 && !blockEntity.isRemoved()) {
						found.add(new Box(boxFor(level, blockEntity), color));
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(box -> box.box().getCenter().distanceToSqr(around)));
		return found.size() > MAX_BOXES ? List.copyOf(found.subList(0, MAX_BOXES)) : List.copyOf(found);
	}

	/** Lit nether portals in the loaded chunks, one box per portal, nearest first. */
	private static List<Box> scanPortals(ClientLevel level, Vec3 around, int radius) {
		int centerX = BlockPos.containing(around).getX() >> 4;
		int centerZ = BlockPos.containing(around).getZ() >> 4;
		Set<BlockPos> portal = new HashSet<>();
		for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
			for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = 0; index < sections.length; index++) {
					LevelChunkSection section = sections[index];
					// Only sections whose palette has portal blocks get looked at block by block.
					if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL))) {
						continue;
					}
					int minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (section.getBlockState(x, y, z).is(Blocks.NETHER_PORTAL)) {
									portal.add(new BlockPos(cx * 16 + x, minY + y, cz * 16 + z));
								}
							}
						}
					}
				}
			}
		}
		// Touching portal blocks are one portal.
		List<Box> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos start : portal) {
			if (!seen.add(start)) {
				continue;
			}
			AABB box = new AABB(start);
			ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				box = box.minmax(new AABB(pos));
				for (Direction direction : Direction.values()) {
					BlockPos next = pos.relative(direction);
					if (portal.contains(next) && seen.add(next)) {
						queue.add(next);
					}
				}
			}
			found.add(new Box(box, PORTAL));
		}
		return found;
	}
	private static AABB boxFor(ClientLevel level, BlockEntity blockEntity) {
		BlockPos pos = blockEntity.getBlockPos();
		VoxelShape shape = blockEntity.getBlockState().getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}

	/** Per-tick debug drawing, on top of everything. It's shown every frame until the next tick. */
	private static void draw(Minecraft mc) {
		try (var collecting = mc.collectPerTickGizmos()) {
			for (Box box : boxes) {
				int fill = (box.color() & 0xFFFFFF) | 0x30000000;
				Gizmos.cuboid(box.box(), GizmoStyle.strokeAndFill(box.color(), 2.0F, fill)).setAlwaysOnTop();
			}
		}
	}

	// Saved toggles

	static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			LOGGER.warn("Couldn't read {}", FILE, e);
			return;
		}
		players = Boolean.parseBoolean(properties.getProperty("players", Boolean.toString(players)));
		mobs = Boolean.parseBoolean(properties.getProperty("mobs", Boolean.toString(mobs)));
		blocks = Boolean.parseBoolean(properties.getProperty("blocks", Boolean.toString(blocks)));
		pearls = Boolean.parseBoolean(properties.getProperty("pearls", Boolean.toString(pearls)));
		portals = Boolean.parseBoolean(properties.getProperty("portals", Boolean.toString(portals)));
	}

	public static void save() {
		Properties properties = new Properties();
		properties.setProperty("players", Boolean.toString(players));
		properties.setProperty("mobs", Boolean.toString(mobs));
		properties.setProperty("blocks", Boolean.toString(blocks));
		properties.setProperty("pearls", Boolean.toString(pearls));
		properties.setProperty("portals", Boolean.toString(portals));
		try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
			properties.store(writer, "Light ESP");
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}
}
