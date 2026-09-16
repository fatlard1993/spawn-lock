package justfatlard.spawn_lock.mixin;

import justfatlard.spawn_lock.Gate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** At the door, the only command is /login. Everything else waits with the rest. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatCommandMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
	private void spawnLock$onlyLogin(ServerboundChatCommandPacket packet, CallbackInfo ci) {
		if (this.player == null || !Gate.isWaiting(this.player)) return;
		String command = packet.command().trim();
		// In jail the password is no way out, so it is not the thing to be told to say.
		if (!Gate.isAtDoor(this.player)) {
			this.player.sendSystemMessage(Component.literal("Not while you are in jail.").withStyle(ChatFormatting.YELLOW));
			ci.cancel();
			return;
		}
		if (command.equals("login") || command.startsWith("login ")) return;
		this.player.sendSystemMessage(Component.literal("Say the password first.").withStyle(ChatFormatting.YELLOW));
		ci.cancel();
	}
}
