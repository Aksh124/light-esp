package lightesp;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Three on/off switches, from /esp menu, a key, or Mod Menu. */
public final class EspScreen extends Screen {
	private final Screen parent;
	private final Map<LightEspClient.Kind, CycleButton<Boolean>> switches = new EnumMap<>(LightEspClient.Kind.class);

	public EspScreen(Screen parent) {
		super(Component.literal("Light ESP"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = width / 2 - 100;
		int y = height / 2 - 44;
		String[] tips = {
			"Red outline on other players, through walls.",
			"Orange outline on hostile mobs and green on the rest, through walls.",
			"Boxes on chests, ender chests, shulker boxes, barrels, spawners, hoppers, droppers, dispensers, crafters and furnaces.",
		};
		for (LightEspClient.Kind kind : LightEspClient.Kind.values()) {
			CycleButton<Boolean> button = addRenderableWidget(CycleButton.onOffBuilder(kind.on())
				.create(x, y, 200, 20, Component.literal(kind.label), (cycle, value) -> kind.set(value)));
			button.setTooltip(Tooltip.create(Component.literal(tips[kind.ordinal()])));
			switches.put(kind, button);
			y += 24;
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 12, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 66, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** For the game test. */
	public CycleButton<Boolean> switchFor(LightEspClient.Kind kind) {
		return switches.get(kind);
	}
}
