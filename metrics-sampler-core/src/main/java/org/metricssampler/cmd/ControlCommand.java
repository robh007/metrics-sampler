package org.metricssampler.cmd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.io.IOUtils;
import org.metricssampler.service.Bootstrapper;
import org.metricssampler.service.DefaultBootstrapper;

import com.beust.jcommander.ParametersDelegate;

/**
 * Base class for control commands that only require the control host and port to operate.
 */
public abstract class ControlCommand extends BootstrappedCommand {
	@ParametersDelegate
	private final ControlCommandDelegate control = new ControlCommandDelegate();

	@Override
	protected Bootstrapper createBootstrapper() {
		return DefaultBootstrapper.bootstrap(control.getHost(), control.getPort());
	}

	protected String execute(final String host, final int port, final String cmd) throws UnknownHostException, IOException {
		try(final Socket socket = new Socket(host, port)) {
			try(final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
				try(final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
					writer.write(cmd);
					writer.write("\n");
					writer.flush();
					return IOUtils.toString(reader);
				}
			}
		}
	}

}
