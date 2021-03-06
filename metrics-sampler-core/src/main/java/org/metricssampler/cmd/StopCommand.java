package org.metricssampler.cmd;

import java.io.IOException;
import java.net.ConnectException;

import com.beust.jcommander.Parameters;

@Parameters(commandNames="stop", commandDescriptionKey="help.stop.command")
public class StopCommand extends ControlCommand {
	@Override
	protected void runBootstrapped() {
		shutdown(bootstrapper.getControlHost(), bootstrapper.getControlPort());
	}

	protected String shutdown(final String host, final int port) {
		try {
			execute(host, port, "shutdown");
			return "Stopped";
		} catch (final ConnectException e) {
			return "No daemon running on port " + port;
		} catch (final IOException e) {
			return "Failed to stop: " + e.getMessage();
		}
	}
	
}
