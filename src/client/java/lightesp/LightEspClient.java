package lightesp;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public final class LightEspClient implements ClientModInitializer {
	public enum Kind {
		PLAYERS("Players"), MOBS("Mobs"), BLOCKS("Storage blocks");

		final String label;

		Kind(String label) {
			this.label = label;
		}

		public boolean on() {
			return switch (this) {
				case PLAYERS -> Esp.players;
				case MOBS -> Esp.mobs;
				case BLOCKS -> Esp.blocks;
			};
		}

		public void set(boolean on) {
			switch (this) {
				case PLAYERS -> Esp.players = on;
				case MOBS -> Esp.mobs = on;
				case BLOCKS -> Esp.blocks = on;
			}
			Esp.save();
		}
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lightesp", "keys"));
	private static final KeyMapping TOGGLE_PLAYERS = key("players");
	private static final KeyMapping TOGGLE_MOBS = key("mobs");
	private static final KeyMapping TOGGLE_BLOCKS = key("blocks");
	private static final KeyMapping OPEN_MENU = key("menu");
	private static boolean openMenuNextTick;

	private static KeyMapping key(String name) {
		return new KeyMapping("key.lightesp." + name, InputConstants.UNKNOWN.getValue(), CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		Esp.load();
		for (KeyMapping key : new KeyMapping[] {TOGGLE_PLAYERS, TOGGLE_MOBS, TOGGLE_BLOCKS, OPEN_MENU}) {
			KeyMappingHelper.registerKeyMapping(key);
		}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> registerCommands(dispatcher));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (TOGGLE_PLAYERS.consumeClick()) {
				toggle(Kind.PLAYERS);
			}
			while (TOGGLE_MOBS.consumeClick()) {
				toggle(Kind.MOBS);
			}
			while (TOGGLE_BLOCKS.consumeClick()) {
				toggle(Kind.BLOCKS);
			}
			while (OPEN_MENU.consumeClick()) {
				openMenuNextTick = true;
			}
			if (openMenuNextTick && mc.player != null) {
				openMenuNextTick = false;
				mc.gui.setScreen(new EspScreen(null));
			}
			Esp.tick(mc);
		});
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		var root = literal("esp").executes(context -> {
			context.getSource().sendFeedback(status());
			return Command.SINGLE_SUCCESS;
		});
		for (Kind kind : Kind.values()) {
			String name = kind.name().toLowerCase(java.util.Locale.ROOT);
			root.then(literal(name)
				.executes(context -> {
					toggle(kind);
					return Command.SINGLE_SUCCESS;
				})
				.then(literal("on").executes(context -> {
					kind.set(true);
					announce(kind);
					return Command.SINGLE_SUCCESS;
				}))
				.then(literal("off").executes(context -> {
					kind.set(false);
					announce(kind);
					return Command.SINGLE_SUCCESS;
				})));
		}
		root.then(literal("off").executes(context -> {
			for (Kind kind : Kind.values()) {
				kind.set(false);
			}
			context.getSource().sendFeedback(status());
			return Command.SINGLE_SUCCESS;
		}));
		root.then(literal("menu").executes(context -> {
			openMenuNextTick = true;
			return Command.SINGLE_SUCCESS;
		}));
		dispatcher.register(root);
	}

	public static void toggle(Kind kind) {
		kind.set(!kind.on());
		announce(kind);
	}

	private static void announce(Kind kind) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("ESP " + kind.label + ": ")
				.append(kind.on() ? Component.literal("on").withStyle(ChatFormatting.GREEN) : Component.literal("off").withStyle(ChatFormatting.RED)));
		}
	}

	static Component status() {
		MutableComponent text = Component.literal("ESP ");
		for (Kind kind : Kind.values()) {
			text.append(kind.label + " ").append(kind.on() ? Component.literal("on").withStyle(ChatFormatting.GREEN)
				: Component.literal("off").withStyle(ChatFormatting.RED));
			if (kind != Kind.BLOCKS) {
				text.append(", ");
			}
		}
		return text.append(". /esp <players|mobs|blocks> to switch one, /esp menu for the screen.");
	}
}
