package lightesp.mixin;

import lightesp.Esp;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players and mobs that ESP is on for get the vanilla glowing outline, which shows through walls. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void lightesp$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Esp.outlineColor(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
