package lightesp.mixin;

import lightesp.Esp;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The outline's colour comes from the team colour. Entities glowing only because of ESP get
 * ESP's colour; ones glowing for real (spectral arrows, /effect) keep theirs.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void lightesp$color(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() && !self.isCurrentlyGlowing()) {
			int color = Esp.outlineColor(self);
			if (color != 0) {
				cir.setReturnValue(color);
			}
		}
	}
}
