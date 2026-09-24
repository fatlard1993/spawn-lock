package justfatlard.spawn_lock.mixin;

import justfatlard.spawn_lock.Gate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
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
		spawnLock$hold(packet.command(), ci);
	}

	/**
	 * The other door into the same room. A command carrying signed arguments arrives on its own
	 * packet and its own handler, and guarding only the unsigned one left it open: the server
	 * takes an empty signature list as "nothing to verify" and runs the command anyway, so a
	 * client that sends this packet with no signatures needs no key, no session and no online
	 * mode. Which is exactly the client someone would be using to arrive under a name they
	 * do not own.
	 */
	@Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
	private void spawnLock$onlyLoginSigned(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
		spawnLock$hold(packet.command(), ci);
	}

	private void spawnLock$hold(String raw, CallbackInfo ci) {
		if (this.player == null || !Gate.isWaiting(this.player)) return;
		String command = raw.trim();
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
