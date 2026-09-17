package justfatlard.spawn_lock.gametest;

import justfatlard.spawn_lock.Gate;
import justfatlard.spawn_lock.Main;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * The picture for the readme and the mod page: what a newcomer sees at the door - held at the
 * spawn, asked for the password, with the wall of the pen around them.
 *
 * <p>Held through {@link Gate#arrive}, the call a join makes, so the screen is the one a stranger
 * meets. Run it under xvfb-run with :runClientGameTest; the frame lands in
 * build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("gamemode survival @a");

			// The door, as a newcomer meets it: taken to the spawn and asked for the word.
			server.runOnServer(s -> Gate.arrive(connection.getServerPlayer(), Main.config));
			// Long enough for the title to be up and the countdown to have started.
			context.waitTicks(60);
			shoot(context, "the-door");
		}
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
