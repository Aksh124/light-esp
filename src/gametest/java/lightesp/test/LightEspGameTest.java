package lightesp.test;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lightesp.Esp;
import lightesp.EspScreen;
import lightesp.LightEspClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Husk;

/**
 * A stone wall with a husk, a cow, a stand-in player and storage blocks behind it, switched on
 * and off by command and from the screen.
 */
public class LightEspGameTest implements FabricClientGameTest {
	private static final int STAND_IN_ID = -4242;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			for (String command : new String[] {
				"difficulty easy", "time set noon", "gamemode creative @a",
				"fill -8 99 -8 8 99 12 stone", "fill -8 100 -8 8 106 12 air", "kill @e[type=!player]",
				"fill -8 100 3 8 105 3 stone",
				"summon husk -3 100 7 {NoAI:1b,PersistenceRequired:1b}",
				"summon cow 3 100 7 {NoAI:1b}",
				"setblock 0 100 6 chest", "setblock 1 100 8 ender_chest", "setblock -1 100 8 barrel",
				"setblock 2 100 10 spawner", "setblock -2 100 10 oak_sign",
				"tp @a 0.5 100 0.5 0 10",
			}) {
				world.getServer().runCommand(command);
			}
			context.waitTicks(40);
			addStandInPlayer(context);

			Husk husk = find(context, Husk.class);
			Cow cow = find(context, Cow.class);

			context.takeScreenshot("lightesp-off");
			// Everything starts off.
			check(context.computeOnClient(mc -> !mc.shouldEntityAppearGlowing(husk) && !mc.shouldEntityAppearGlowing(standIn(mc))
				&& Esp.boxes().isEmpty()), "something showed with ESP off");

			// Mobs: orange hostile, green passive, players still off.
			command(context, "esp mobs");
			check(context.computeOnClient(mc -> mc.shouldEntityAppearGlowing(husk) && husk.getTeamColor() == Esp.HOSTILE
				&& mc.shouldEntityAppearGlowing(cow) && cow.getTeamColor() == Esp.PASSIVE && !mc.shouldEntityAppearGlowing(standIn(mc))),
				"mob outlines wrong");
			check(context.computeOnClient(mc -> !mc.shouldEntityAppearGlowing(mc.player)), "you outline yourself");
			log("mobs: husk orange, cow green, you and the player not outlined");

			// Players: red.
			command(context, "esp players on");
			check(context.computeOnClient(mc -> mc.shouldEntityAppearGlowing(standIn(mc)) && standIn(mc).getTeamColor() == Esp.PLAYER),
				"player outline wrong");
			log("players: red outline");
			context.takeScreenshot("lightesp-entities");

			// Storage blocks: chest, ender chest, barrel and spawner boxed; the sign isn't.
			command(context, "esp blocks");
			context.waitTicks(15);
			List<BlockPos> boxed = context.computeOnClient(mc -> Esp.boxes().stream()
				.map(box -> BlockPos.containing(box.box().getCenter())).toList());
			check(boxed.containsAll(List.of(new BlockPos(0, 100, 6), new BlockPos(1, 100, 8), new BlockPos(-1, 100, 8),
				new BlockPos(2, 100, 10))) && !boxed.contains(new BlockPos(-2, 100, 10)), "boxes at " + boxed);
			int drawn = context.computeOnClient(mc -> mc.getPerTickGizmos().size());
			check(drawn >= boxed.size(), "only " + drawn + " boxes drawn for " + boxed.size());
			log("blocks: " + boxed.size() + " boxed and drawn, sign left out");
			context.takeScreenshot("lightesp-through-wall");

			// Something glowing for real keeps its own colour.
			world.getServer().runCommand("effect give @e[type=cow] glowing 30 0 true");
			waitUntil(context, 5, () -> cow.isCurrentlyGlowing(), () -> "cow never got the glowing effect");
			check(context.computeOnClient(mc -> cow.getTeamColor() != Esp.PASSIVE), "a really glowing mob got ESP's colour");
			log("a really glowing cow keeps its own outline colour");

			// The screen: switching mobs off saves it.
			command(context, "esp menu");
			waitUntil(context, 5, () -> Minecraft.getInstance().gui.screen() instanceof EspScreen, () -> "/esp menu didn't open");
			context.takeScreenshot("lightesp-menu");
			context.runOnClient(mc -> ((EspScreen) mc.gui.screen()).switchFor(LightEspClient.Kind.MOBS)
				.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(0, 0))));
			check(context.computeOnClient(mc -> !Esp.mobs && !mc.shouldEntityAppearGlowing(husk)), "screen didn't switch mobs off");
			check(context.computeOnClient(mc -> {
				try {
					String file = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
						.resolve("lightesp.properties"));
					return file.contains("mobs=false") && file.contains("players=true") && file.contains("blocks=true");
				} catch (java.io.IOException e) {
					return false;
				}
			}), "switches not saved");
			context.runOnClient(mc -> mc.gui.screen().onClose());
			log("screen switched mobs off and saved it");

			// All off.
			command(context, "esp off");
			context.waitTicks(2);
			check(context.computeOnClient(mc -> !mc.shouldEntityAppearGlowing(standIn(mc)) && Esp.boxes().isEmpty()
				&& mc.getPerTickGizmos().isEmpty()), "/esp off left something showing");
			log("/esp off clears everything");
		}
	}

	/** Another player, only on this client, standing behind the wall. */
	private static void addStandInPlayer(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Someone"));
			player.setId(STAND_IN_ID);
			player.snapTo(0.5, 100, 9.5, 180, 0);
			mc.level.addEntity(player);
		});
	}

	private static Entity standIn(Minecraft mc) {
		return mc.level.getEntity(STAND_IN_ID);
	}

	private static <T extends Entity> T find(ClientGameTestContext context, Class<T> type) {
		waitUntil(context, 5, () -> !Minecraft.getInstance().level.getEntitiesOfClass(type,
			Minecraft.getInstance().player.getBoundingBox().inflate(20)).isEmpty(), () -> "no " + type.getSimpleName());
		return context.computeOnClient(mc -> mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(20)).getFirst());
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(mc -> mc.player.connection.sendCommand(command));
		context.waitTicks(2);
	}

	private static void waitUntil(ClientGameTestContext context, int seconds, BooleanSupplier done, Supplier<String> failure) {
		for (int tick = 0; tick < seconds * 20; tick++) {
			if (context.computeOnClient(mc -> done.getAsBoolean())) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError(failure.get());
	}

	private static void check(boolean condition, String failure) {
		if (!condition) {
			throw new AssertionError(failure);
		}
	}

	private static void log(String text) {
		System.out.println("[lightesp-gametest] " + text);
	}
}
